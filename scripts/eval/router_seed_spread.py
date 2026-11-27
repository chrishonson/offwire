"""R1's run-to-run spread for a refitted head. R1 is deterministic for one committed config, so the noise that
matters when an encoder changes is the head fit itself: `embed_router.py fit` with another seed for the mlp's
starting weights. This refits the whole mlp sweep (head_words x l2, out-of-fold thresholds) once per seed, in
memory, and scores each on the held-out split. Nothing under eval/router/ is written.

  python router_seed_spread.py [--model-dir DIR] [--model-file model_int8.onnx] [--seeds 0,1,2,3,4] [--json OUT]

Seed 0 is what `embed_router.py fit` writes.
"""
import argparse
import functools
import json
from pathlib import Path

import embed_router as er
import router_heldout as rh

KEYS = ("right", "over_call", "missed", "missed_save")


def spread(encoder: er.Encoder, seeds: list[int], split: str = "test") -> dict:
    rows = rh.load_heldout(split=split)
    texts, pool, original = [r["text"] for r in rows], er.load_pool(), er.train_mlp
    out = {"model": encoder.name, "split": split, "seeds": {}}
    try:
        for seed in seeds:
            er.train_mlp = functools.partial(original, seed=seed)
            best = er.sweep(encoder, "mlp", pool, log=lambda _: None)
            s = rh.score(rows, [best.decide(t).intent for t in texts])
            out["seeds"][seed] = {"head_words": best.head_words, "l2": best.l2, "thresholds": best.thresholds,
                                  **{k: s[k] for k in KEYS}}
            print(f"{encoder.name} seed {seed}: head_words {best.head_words} l2 {best.l2} thresholds "
                  f"{best.thresholds}; right {s['right']}/{s['n']}, tool on no-tool {s['over_call']}/{s['no_call_n']}, "
                  f"no tool {s['missed']}/{s['call_n']}, missed save {s['missed_save']}/{s['save_n']}", flush=True)
    finally:
        er.train_mlp = original
    for key in KEYS:
        values = [v[key] for v in out["seeds"].values()]
        out[f"{key}_range"] = [min(values), max(values)]
    print(f"{encoder.name}: right {out['right_range']}, tool on no-tool {out['over_call_range']}, "
          f"no tool {out['missed_range']}, missed save {out['missed_save_range']}")
    return out


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--model-dir", default=str(er.DEFAULT_MODEL_DIR))
    parser.add_argument("--model-file", default="model.onnx")
    parser.add_argument("--seeds", default="0,1,2,3,4")
    parser.add_argument("--split", default="test", choices=["test", "dev", "all"])
    parser.add_argument("--json")
    args = parser.parse_args(argv)
    encoder = er.CachedEncoder(er.make_encoder(Path(args.model_dir).expanduser(), args.model_file))
    out = spread(encoder, [int(s) for s in args.seeds.split(",")], args.split)
    if args.json:
        Path(args.json).write_text(json.dumps(out, indent=2) + "\n")


if __name__ == "__main__":
    main()
