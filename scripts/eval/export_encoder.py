"""Exports a Hugging Face BERT-style encoder to ONNX in the layout embed_router.OnnxEncoder reads, so another stock
encoder can be scored against all-MiniLM-L6-v2 with the same router code.

  python export_encoder.py google/mobilebert-uncased --revision 1f90a6c24c7879273a291d34a849033eba2dbc0f \
      --out ~/models/mobilebert-uncased/onnx

Writes OUT/model.onnx (inputs input_ids, attention_mask, token_type_ids; output last_hidden_state, as the Chroma
export of MiniLM has), tokenizer.json, vocab.txt and config.json with _name_or_path set to the model id, so
embed_router names the configs embed-<name>[-int8]-<scorer>.json. Then `embed_router.py quantize --model-dir OUT`
makes model_int8.onnx the same way as MiniLM's. Needs torch, transformers and onnx (not in requirements.txt).
"""
import argparse
import json
import shutil
from pathlib import Path

import numpy as np
import torch
from huggingface_hub import snapshot_download
from transformers import AutoModel


class LastHidden(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, input_ids, attention_mask, token_type_ids):
        return self.model(input_ids=input_ids, attention_mask=attention_mask,
                          token_type_ids=token_type_ids).last_hidden_state


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("model_id")
    parser.add_argument("--revision", required=True, help="the hub commit, so the export is reproducible")
    parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)
    out = Path(args.out).expanduser()
    out.mkdir(parents=True, exist_ok=True)
    src = Path(snapshot_download(args.model_id, revision=args.revision,
                                 allow_patterns=["*.json", "*.txt", "*.safetensors", "*.bin"]))
    model = AutoModel.from_pretrained(src).eval()
    ids = torch.tensor([[101, 7592, 2088, 102]])
    inputs = (ids, torch.ones_like(ids), torch.zeros_like(ids))
    axes = {0: "batch", 1: "sequence"}
    torch.onnx.export(LastHidden(model), inputs, str(out / "model.onnx"), dynamo=False, opset_version=17,
                      input_names=["input_ids", "attention_mask", "token_type_ids"],
                      output_names=["last_hidden_state"],
                      dynamic_axes={"input_ids": axes, "attention_mask": axes, "token_type_ids": axes,
                                    "last_hidden_state": axes})
    for name in ("tokenizer.json", "vocab.txt"):
        shutil.copy(src / name, out / name)
    config = json.loads((src / "config.json").read_text())
    config["_name_or_path"] = args.model_id
    config["_revision"] = args.revision
    (out / "config.json").write_text(json.dumps(config, indent=2) + "\n")

    import onnxruntime as ort
    session = ort.InferenceSession(str(out / "model.onnx"), providers=["CPUExecutionProvider"])
    feed = dict(zip(["input_ids", "attention_mask", "token_type_ids"], (t.numpy() for t in inputs)))
    with torch.no_grad():
        want = LastHidden(model)(*inputs).numpy()
    got = session.run(None, feed)[0]
    print(f"wrote {out / 'model.onnx'} ({(out / 'model.onnx').stat().st_size / 1e6:.1f} MB), width {got.shape[-1]}, "
          f"largest difference from torch {float(np.abs(got - want).max()):.2e}")


if __name__ == "__main__":
    main()
