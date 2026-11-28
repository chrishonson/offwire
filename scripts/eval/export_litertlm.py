"""Packages all-MiniLM-L6-v2 as a .litertlm that LiteRT-LM's EmbeddingEngine runs, so the router could run on the
LiteRT-LM library the app already ships (eval/studies/2026-11-27-router-quant, step 8).

  python export_litertlm.py --revision REV --out ~/models/all-MiniLM-L6-v2/litertlm [--int8]

EmbeddingEngine (LiteRT-LM runtime/core/embedding_engine_impl.cc) runs two TFLite models from the file:
  embedder      token ids in, token vectors out (MiniLM's word embedding table, a gather)
  text_encoder  signatures named encoder<length>; inputs "embeddings" [1, length, 384] float and "input_mask"
                [1, length] float (1 real, 0 padding); output [1, 384]
The encoder adds MiniLM's position and token-type embeddings and its embedding LayerNorm, runs the six layers with
the mask, then mean-pools over the mask and L2-normalizes, as embed_router.OnnxEncoder does. The engine picks the
shortest signature that fits, tokenizes with the packed tokenizer.json and adds [CLS] and [SEP] from the metadata.
GELU is written out in its tanh form (export_tflite.TanhGelu). --int8 applies ai-edge-quantizer's
dynamic_wi8_afp32 to both models. Needs torch, transformers, litert-torch, ai-edge-quantizer and litert-lm-builder.
"""
import argparse
import subprocess
import sys
from pathlib import Path

import torch
from huggingface_hub import snapshot_download
from transformers import AutoModel

from export_tflite import LENGTHS, TanhGelu

METADATA = """embedding_model_type { generic_model {} }
bos_token { token_ids { ids: 101 } }
eos_token { token_ids { ids: 102 } }
max_input_length: 128
"""


class Embedder(torch.nn.Module):
    def __init__(self, table):
        super().__init__()
        self.table = table

    def forward(self, token_ids):
        return self.table(token_ids.long())


class Encoder(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, embeddings, input_mask):
        emb = self.model.embeddings
        length = embeddings.shape[1]
        x = embeddings + emb.position_embeddings.weight[:length].unsqueeze(0) + emb.token_type_embeddings.weight[0]
        x = emb.LayerNorm(x)
        bias = (1.0 - input_mask)[:, None, None, :] * -10000.0
        for layer in self.model.encoder.layer:
            x = layer(x, attention_mask=bias)
        weights = input_mask.unsqueeze(-1)
        pooled = (x * weights).sum(dim=1) / weights.sum(dim=1).clamp(min=1.0)
        return pooled / pooled.norm(dim=-1, keepdim=True)


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--model-id", default="sentence-transformers/all-MiniLM-L6-v2")
    parser.add_argument("--revision", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--int8", action="store_true")
    parser.add_argument("--embedder-length", type=int, default=1,
                        help="token ids per embedder call; the engine's lookup reads the output's 2nd dim")
    args = parser.parse_args(argv)
    import litert_torch
    from ai_edge_quantizer import quantizer, recipe

    out = Path(args.out).expanduser()
    out.mkdir(parents=True, exist_ok=True)
    src = Path(snapshot_download(args.model_id, revision=args.revision,
                                 allow_patterns=["*.json", "*.txt", "*.safetensors"]))
    model = AutoModel.from_pretrained(src, attn_implementation="eager")
    for layer in model.encoder.layer:
        layer.intermediate.intermediate_act_fn = TanhGelu()
    model.eval()
    width = model.config.hidden_size

    embedder = Embedder(model.embeddings.word_embeddings).eval()
    emb_path = out / "embedder.tflite"
    litert_torch.convert(embedder, (torch.zeros((1, args.embedder_length), dtype=torch.int32),)).export(str(emb_path))

    encoder, builder = Encoder(model).eval(), None
    for n in LENGTHS:
        sample = {"embeddings": torch.zeros((1, n, width)), "input_mask": torch.ones((1, n))}
        builder = (litert_torch if builder is None else builder).signature(f"encoder{n}", encoder, sample_kwargs=sample)
    enc_path = out / "text_encoder.tflite"
    builder.convert().export(str(enc_path))

    if args.int8:
        for path in (emb_path, enc_path):
            q = quantizer.Quantizer(str(path))
            q.load_quantization_recipe(recipe.dynamic_wi8_afp32())
            q.quantize().export_model(str(path), overwrite=True)

    # The hub's tokenizer.json pads every message to 128 tokens, and the engine would pool over the padding as text.
    from tokenizers import Tokenizer
    tokenizer = Tokenizer.from_file(str(src / "tokenizer.json"))
    tokenizer.no_padding()
    tokenizer.no_truncation()
    tokenizer.save(str(out / "tokenizer.json"))
    (out / "embedding_metadata.textproto").write_text(METADATA)
    name = "minilm-router" + ("-int8" if args.int8 else "")
    target = out / f"{name}.litertlm"
    target.unlink(missing_ok=True)
    builder_cli = Path(sys.executable).with_name("litert-lm-builder")
    subprocess.run([str(builder_cli), "system_metadata", "--str", "model_name", name,
                    "tflite_model", "--path", str(emb_path), "--model_type", "embedder",
                    "tflite_model", "--path", str(enc_path), "--model_type", "text_encoder",
                    "hf_tokenizer", "--path", str(out / "tokenizer.json"),
                    "embedding_metadata", "--path", str(out / "embedding_metadata.textproto"),
                    "output", "--path", str(target)], check=True)
    for p in (emb_path, enc_path, target):
        print(f"wrote {p} ({p.stat().st_size / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
