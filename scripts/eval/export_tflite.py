"""Exports a Hugging Face BERT-style sentence encoder to LiteRT (.tflite), fp32 and dynamic-range int8, for the
router-on-LiteRT question in eval/studies/2026-11-27-router-quant.

  python export_tflite.py sentence-transformers/all-MiniLM-L6-v2 --revision REV --out ~/models/all-MiniLM-L6-v2/onnx

Writes OUT/model_tflite.tflite and OUT/model_tflite_int8.tflite beside the ONNX files, so embed_router finds the
same tokenizer.json and config.json and names the configs embed-<name>-tflite[_int8]-<scorer>.json.

LiteRT runs fixed shapes, so the file has one signature per length (len16, len32, len64, len128). Each takes
input_ids and attention_mask (int32, [1, length], padded with 0) and returns the sentence vector [1, width]:
mean-pooled over the mask and L2-normalized inside the graph, the same vector embed_router.OnnxEncoder computes.
The encoder picks the shortest signature that fits the message. int8 is ai-edge-quantizer's dynamic_wi8_afp32
(int8 weights, float activations), the LiteRT counterpart of onnxruntime's quantize_dynamic.
Needs torch, transformers, litert-torch and ai-edge-quantizer (not in requirements.txt).
"""
import argparse
from pathlib import Path

import torch
from huggingface_hub import snapshot_download
from transformers import AutoModel

LENGTHS = (16, 32, 64, 128)


class TanhGelu(torch.nn.Module):
    """GELU in its tanh form, written out so litert-torch lowers it to plain ops: its aten.gelu lowering goes through
    JAX's erf and emits an fma op the converter rejects, in either approximate mode."""

    def forward(self, x):
        return 0.5 * x * (1.0 + torch.tanh(0.7978845608028654 * (x + 0.044715 * x * x * x)))


class SentenceVector(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, input_ids, attention_mask):
        ids, mask = input_ids.long(), attention_mask.long()
        hidden = self.model(input_ids=ids, attention_mask=mask, token_type_ids=torch.zeros_like(ids)).last_hidden_state
        weights = mask.unsqueeze(-1).to(hidden.dtype)
        pooled = (hidden * weights).sum(dim=1) / weights.sum(dim=1).clamp(min=1.0)
        return pooled / pooled.norm(dim=-1, keepdim=True)


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("model_id")
    parser.add_argument("--revision", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)
    import litert_torch
    from ai_edge_quantizer import quantizer, recipe

    out = Path(args.out).expanduser()
    src = Path(snapshot_download(args.model_id, revision=args.revision,
                                 allow_patterns=["*.json", "*.txt", "*.safetensors"]))
    # Exact GELU (erf) cannot be converted (TanhGelu). The tanh form moves the vectors a little: the study notes
    # (eval/studies/2026-11-27-router-quant) give the cosine to the ONNX vectors.
    model = AutoModel.from_pretrained(src, attn_implementation="eager")
    for layer in model.encoder.layer:
        layer.intermediate.intermediate_act_fn = TanhGelu()
    module = SentenceVector(model).eval()
    builder = None
    for n in LENGTHS:
        sample = {"input_ids": torch.zeros((1, n), dtype=torch.int32),
                  "attention_mask": torch.ones((1, n), dtype=torch.int32)}
        builder = (litert_torch if builder is None else builder).signature(f"len{n}", module, sample_kwargs=sample)
    fp32 = out / "model_tflite.tflite"
    builder.convert().export(str(fp32))
    int8 = out / "model_tflite_int8.tflite"
    q = quantizer.Quantizer(str(fp32))
    q.load_quantization_recipe(recipe.dynamic_wi8_afp32())
    q.quantize().export_model(str(int8), overwrite=True)
    for p in (fp32, int8):
        print(f"wrote {p} ({p.stat().st_size / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
