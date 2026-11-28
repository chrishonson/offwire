"""Router time per message on this machine for several encoders, measured back to back on the same messages, for
eval/studies/2026-11-27-router-quant. Each message runs decide() with a fresh, uncached encoder (tokenizing, every
encoder run, the head), one thread, and the fastest of three passes counts. Not a phone measurement.

  python router_timing.py --json OUT
"""
import argparse
import json
import time
from pathlib import Path

import numpy as np

import embed_router as er
import router_heldout as rh

MINILM = er.STOCK_MODEL_DIR  # the study compares encoders built from the stock model
ENCODERS = [  # label, model dir, model file
    ("A MiniLM fp32 ONNX", MINILM, "model.onnx"),
    ("B MiniLM int8 ONNX", MINILM, "model_int8.onnx"),
    ("C MobileBERT int8 ONNX", Path("~/models/mobilebert-uncased/onnx").expanduser(), "model_int8.onnx"),
    ("MiniLM fp32 LiteRT", MINILM, "model_tflite.tflite"),
    ("D MiniLM int8 LiteRT", MINILM, "model_tflite_int8.tflite"),
    ("MiniLM int8 LiteRT-LM EmbeddingEngine", Path("~/models/all-MiniLM-L6-v2/litertlm").expanduser(),
     "minilm-router-int8.litertlm"),
]


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--passes", type=int, default=3)
    parser.add_argument("--json")
    args = parser.parse_args(argv)
    texts = [r["text"] for r in rh.load_heldout(split="test")]
    out = {}
    for label, model_dir, model_file in ENCODERS:
        if not (model_dir / model_file).is_file():
            print(f"{label}: {model_dir / model_file} not found, skipped")
            continue
        timed = er.load_router(model_dir, model_file, er.APP_SCORER, er.make_encoder(model_dir, model_file))
        timed.decide("hello")
        ms = 1000 * np.array([min(timed.decide(t).seconds for _ in range(args.passes)) for t in texts])
        encoder_ms = []
        enc = er.make_encoder(model_dir, model_file)
        for t in texts:
            start = time.perf_counter()
            enc.encode([er.normalize(t)])
            encoder_ms.append(1000 * (time.perf_counter() - start))
        out[label] = {"file": model_file, "mb": round((model_dir / model_file).stat().st_size / 1e6, 1),
                      "median_ms": round(float(np.median(ms)), 2), "p90_ms": round(float(np.percentile(ms, 90)), 2),
                      "max_ms": round(float(ms.max()), 2), "one_encoder_run_median_ms": round(float(np.median(encoder_ms)), 2)}
        o = out[label]
        print(f"{label:40} {o['mb']:6} MB  per message median {o['median_ms']:6} ms  p90 {o['p90_ms']:6} ms  "
              f"max {o['max_ms']:7} ms  one encoder run {o['one_encoder_run_median_ms']} ms", flush=True)
    if args.json:
        Path(args.json).write_text(json.dumps(out, indent=2) + "\n")


if __name__ == "__main__":
    main()
