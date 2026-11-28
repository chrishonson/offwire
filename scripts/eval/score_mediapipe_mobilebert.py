"""Scores Google's published quantized MobileBERT LiteRT file as the router's encoder (R1 only), for
eval/studies/2026-11-27-router-quant. The only MobileBERT .tflite Google publishes is MediaPipe's text classifier,
fine-tuned for SST-2 sentiment: litert-community/MediaPipe-TextClassifier-MobileBERT, bert_classifier.tflite
(dynamic-range quantized, 25.7 MB). It outputs two sentiment scores, not a vector, so this reads the last encoder
layer's token vectors out of the interpreter (tensor tfl.add315, [1, 128, 512], before the [CLS] slice), mean-pools
them over the mask and L2-normalizes, as the MiniLM path does. Its vocab is MiniLM's (same SHA-256), so MiniLM's
tokenizer.json feeds it. The head is refitted with router_seed_spread (seeds 0-4).

  python score_mediapipe_mobilebert.py PATH/bert_classifier.tflite [--json OUT]
"""
import argparse
import json
from pathlib import Path

import numpy as np

import embed_router as er
import router_seed_spread as rs

LENGTH = 128
HIDDEN = "tfl.add315"


class MediapipeMobileBertEncoder:
    def __init__(self, path: Path, tokenizer_dir: Path = er.STOCK_MODEL_DIR):
        from ai_edge_litert.interpreter import Interpreter
        from tokenizers import Tokenizer
        self.name = "mediapipe-mobilebert-sst2"
        self.tokenizer = Tokenizer.from_file(str(Path(tokenizer_dir).expanduser() / "tokenizer.json"))
        self.tokenizer.enable_truncation(max_length=LENGTH)
        self.tokenizer.no_padding()
        self.interpreter = Interpreter(model_path=str(path), num_threads=1, experimental_preserve_all_tensors=True)
        self.interpreter.allocate_tensors()
        names = {d["name"]: d["index"] for d in self.interpreter.get_input_details()}
        self.ids, self.types, self.mask = (names[f"serving_default_input_{k}:0"] for k in ("word_ids", "type_ids", "mask"))
        self.hidden = next(t["index"] for t in self.interpreter.get_tensor_details() if t["name"] == HIDDEN)

    def encode(self, texts: list[str]) -> np.ndarray:
        rows = []
        for text in texts:
            ids = self.tokenizer.encode(text).ids
            feed, mask = np.zeros((1, LENGTH), np.int32), np.zeros((1, LENGTH), np.int32)
            feed[0, :len(ids)], mask[0, :len(ids)] = ids, 1
            self.interpreter.set_tensor(self.ids, feed)
            self.interpreter.set_tensor(self.types, np.zeros_like(feed))
            self.interpreter.set_tensor(self.mask, mask)
            self.interpreter.invoke()
            hidden = self.interpreter.get_tensor(self.hidden)[0, :len(ids)]
            pooled = hidden.mean(axis=0)
            rows.append(pooled / np.linalg.norm(pooled))
        return np.array(rows)


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("model")
    parser.add_argument("--seeds", default="0,1,2,3,4")
    parser.add_argument("--json")
    args = parser.parse_args(argv)
    encoder = er.CachedEncoder(MediapipeMobileBertEncoder(Path(args.model).expanduser()))
    out = rs.spread(encoder, [int(s) for s in args.seeds.split(",")])
    if args.json:
        Path(args.json).write_text(json.dumps(out, indent=2) + "\n")


if __name__ == "__main__":
    main()
