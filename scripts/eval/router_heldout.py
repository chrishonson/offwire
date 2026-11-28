"""Routing-only scores on the large held-out set, eval/router/heldout.jsonl, broken down by kind of request.

The held-out set was written by separate writers who never saw the route examples, the fit set or the eval
fixtures, and every sentence was labeled twice (see the "Held-out set" section of
docs/spikes/2026-11-10-tool-calling-eval.md). Each line: id, text, intent, kind, style (clean, stt, ramble) and split.
  dev   half the set, used to look at misses and choose fixes
  test  the other half, scored only to report; nothing is chosen on it
No Gemma: this measures the router alone, so it runs in seconds.

Usage (from the repo root):
  python scripts/eval/router_heldout.py [--split test|dev|all] [--setups rules,embed_mlp] [--misses]
      [--model-dir DIR] [--model-file model.onnx] [--json PATH] [--file eval/router/long.jsonl]

--file scores another set in the same format, such as eval/router/long.jsonl (R5: long spoken messages, see
docs/RELEASING.md). Rows are also counted by length in words.
"""
import argparse
import json
from collections import defaultdict
from pathlib import Path

import numpy as np

import embed_router as er
import router

HELDOUT = er.ROUTER_DIR / "heldout.jsonl"
BOOTSTRAP = 2000


def load_heldout(path: Path = HELDOUT, split: str = "all") -> list[dict]:
    rows = [json.loads(line) for line in Path(path).read_text().splitlines() if line.strip()]
    return [r for r in rows if split == "all" or r["split"] == split]


def interval(hits: np.ndarray, seed: int = 0) -> tuple[int, int]:
    """95% bootstrap interval for the number of hits, resampling the sentences."""
    if len(hits) == 0:
        return 0, 0
    rng = np.random.default_rng(seed)
    sums = rng.choice(hits, size=(BOOTSTRAP, len(hits)), replace=True).sum(axis=1)
    return int(np.percentile(sums, 2.5)), int(np.percentile(sums, 97.5))


def score(rows: list[dict], routed: list[str]) -> dict:
    """Intent match overall and per kind and style, and the two errors that matter: a tool on a NONE message, and
    a request that got no tool at all."""
    gold = [r["intent"] for r in rows]
    right = np.array([g == p for g, p in zip(gold, routed)], dtype=int)
    none = [i for i, g in enumerate(gold) if g == router.NONE]
    calls = [i for i, g in enumerate(gold) if g != router.NONE]
    over = np.array([routed[i] != router.NONE for i in none], dtype=int)
    missed = np.array([routed[i] == router.NONE for i in calls], dtype=int)
    by = {}
    for field in ("kind", "style", "intent"):
        groups = defaultdict(list)
        for i, r in enumerate(rows):
            groups[r[field]].append(i)
        by[field] = {k: (int(right[v].sum()), len(v)) for k, v in sorted(groups.items())}
    lengths = defaultdict(list)
    for i, r in enumerate(rows):
        lengths[length_bucket(r["text"])].append(i)
    by["words"] = {k: (int(right[v].sum()), len(v)) for k, v in sorted(lengths.items())}
    saves_on_none = [i for i in none if router.SAVE in routed[i].split("+")]
    return {"n": len(rows), "right": int(right.sum()), "right_95": interval(right),
            "over_call": int(over.sum()), "no_call_n": len(none), "over_call_95": interval(over),
            "save_on_none": len(saves_on_none),
            "missed": int(missed.sum()), "call_n": len(calls), "missed_95": interval(missed), "by": by}


WORD_BUCKETS = (20, 45, 80, 120)


def length_bucket(text: str) -> str:
    """'<20', '20-44', '45-79', '80-119', '120+' words."""
    n, lo = len(text.split()), 0
    for hi in WORD_BUCKETS:
        if n < hi:
            return f"<{hi}" if lo == 0 else f"{lo}-{hi - 1}"
        lo = hi
    return f"{lo}+"


def route_all(setup: str, texts: list[str], routers: dict) -> list[str]:
    if setup == "rules":
        return [router.route(t).intent for t in texts]
    if setup == "embed_rules":
        return [er.route_with_rules(routers[er.APP_SCORER], t).intent for t in texts]
    return [routers[setup.removeprefix("embed_")].decide(t).intent for t in texts]


def print_report(name: str, s: dict) -> None:
    lo, hi = s["right_95"]
    print(f"\n{name}: right intent {s['right']} of {s['n']} (95% range {lo}-{hi}); "
          f"tool on a no-tool message {s['over_call']} of {s['no_call_n']} ({s['over_call_95'][0]}-{s['over_call_95'][1]}); "
          f"request with no tool {s['missed']} of {s['call_n']} ({s['missed_95'][0]}-{s['missed_95'][1]}); "
          f"save_note on a no-tool message {s['save_on_none']} of {s['no_call_n']}")
    for field in ("intent", "kind", "style", "words"):
        print("  " + field + ": " + ", ".join(f"{k} {a}/{b}" for k, (a, b) in s["by"][field].items()))


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--split", default="test", choices=["test", "dev", "all"])
    parser.add_argument("--setups", default="rules,embed_mlp")
    parser.add_argument("--model-dir", default=str(er.DEFAULT_MODEL_DIR))
    parser.add_argument("--model-file", default="model.onnx")
    parser.add_argument("--misses", action="store_true", help="list every miss (use on dev, not test)")
    parser.add_argument("--json", help="also write the scores here")
    parser.add_argument("--file", default=str(HELDOUT), help="the set to score (default eval/router/heldout.jsonl)")
    args = parser.parse_args(argv)

    rows = load_heldout(Path(args.file), split=args.split)
    setups = [s for s in args.setups.split(",") if s]
    routers = {}
    if any(s != "rules" for s in setups):
        encoder = er.CachedEncoder(er.OnnxEncoder(Path(args.model_dir).expanduser(), args.model_file))
        wanted = {er.APP_SCORER if s == "embed_rules" else s.removeprefix("embed_") for s in setups if s != "rules"}
        routers = {s: er.load_router(Path(args.model_dir), args.model_file, s, encoder) for s in wanted}
    print(f"held-out split {args.split}: {len(rows)} sentences")
    out = {}
    for setup in setups:
        routed = route_all(setup, [r["text"] for r in rows], routers)
        out[setup] = score(rows, routed)
        print_report(setup, out[setup])
        if args.misses:
            for r, p in sorted(zip(rows, routed), key=lambda x: (x[0]["kind"], x[0]["id"])):
                if r["intent"] != p:
                    print(f"    {r['kind']:26} {r['intent']:>11} -> {p:11} {r['text'][:90]!r}")
    if args.json:
        Path(args.json).write_text(json.dumps(out, indent=2) + "\n")


if __name__ == "__main__":
    main()
