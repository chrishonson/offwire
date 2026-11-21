"""Builds the FunctionGemma router fine-tune data from labelled utterances.

Input, one JSON object per line: {"text", "intent": NONE|SAVE|ACTION, "call": null | {"name", "args"}, "tag"}.
Outputs:
- train.jsonl and val.jsonl in chat format with tools, for TRL SFT. The developer turn and tools are the ones
  router_eval.py's FunctionGemma gate sends, so training and the eval see the same prompt.
- a router_eval.py fixture made from the held-out rows.
Training rows that match a held-out row or an eval fixture case, after normalising, are dropped.

Usage:
  .venv/bin/python scripts/eval/ft_data.py --data ~/models/functiongemma-ft/data [--val 0.1] [--seed 1]
"""
import argparse
import json
import random
import re
from pathlib import Path

import router_eval as re_
import tool_eval as te

NONE_REPLY = "NONE"  # Any text means NONE to the gate; one short word decodes fastest.
NEAR = 0.8  # word-set overlap at which two utterances count as the same
_FILLER = {"um", "uh", "er", "hmm", "like", "so", "ok", "okay", "hey", "please", "computer"}


def words(text: str) -> frozenset[str]:
    return frozenset(w for w in re.findall(r"[a-z0-9']+", text.lower()) if w not in _FILLER)


def same(a: frozenset[str], b: frozenset[str]) -> bool:
    return bool(a | b) and len(a & b) / len(a | b) >= NEAR


def drop_leaks(rows: list[dict], protected: list[str]) -> tuple[list[dict], list[dict]]:
    guard = [words(t) for t in protected]
    kept, dropped = [], []
    for row in rows:
        (dropped if any(same(words(row["text"]), g) for g in guard) else kept).append(row)
    return kept, dropped


def to_messages(row: dict) -> dict:
    call = row["call"]
    reply = ({"role": "assistant", "tool_calls": [{"type": "function", "function": {
        "name": call["name"], "arguments": call["args"]}}]} if call else {"role": "assistant", "content": NONE_REPLY})
    return {"messages": [{"role": "developer", "content": re_.FG_SYSTEM}, {"role": "user", "content": row["text"]},
                         reply],
            "tools": [te.SAVE_NOTE, te.DO_ACTION]}


def split(rows: list[dict], val: float, seed: int) -> tuple[list[dict], list[dict]]:
    """Validation takes [val] of each intent, so every intent is measured."""
    rng = random.Random(seed)
    train, held = [], []
    for intent in sorted({r["intent"] for r in rows}):
        group = [r for r in rows if r["intent"] == intent]
        rng.shuffle(group)
        n = max(1, round(len(group) * val))
        held += group[:n]
        train += group[n:]
    rng.shuffle(train)
    return train, held


def _key_word(text: str) -> str | None:
    found = sorted(re.findall(r"[a-z0-9]+", text.lower()), key=len, reverse=True)
    return re.escape(found[0]) if found else None


def to_fixture(rows: list[dict]) -> list[dict]:
    """Held-out rows as tool-eval cases. Arguments are checked on one distinctive word, as the fixtures do."""
    cases = []
    for i, row in enumerate(rows, 1):
        case = {"id": f"rh-{i:03d}-{row.get('tag', 'x')}", "user": row["text"]}
        call = row["call"]
        if not call:
            cases.append({**case, "category": "no_call", "expect": []})
        elif call["name"] == "save_note":
            word = _key_word(call["args"]["text"])
            cases.append({**case, "category": "save", "expect": [
                {"name": "save_note", "args": {"text": word} if word else {}}]})
        else:
            action, arg = call["args"]["action"], call["args"]["arg"]
            minutes = re.match(r"(\d+) min:", arg)
            pattern = rf"\b{minutes.group(1)}\b" if minutes else _key_word(arg.split(":", 1)[-1]) if arg else None
            args = {"action": f"^{action}$", **({"arg": pattern} if pattern else {})}
            cases.append({**case, "category": "action",
                          "expect": {"shipped": [], "proposed": [{"name": "do_action", "args": args}]}})
    return cases


def read_jsonl(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--val", type=float, default=0.1)
    parser.add_argument("--seed", type=int, default=1)
    parser.add_argument("--fixture-out", type=Path, default=te.FIXTURE.parent / "router-heldout.json")
    args = parser.parse_args()

    heldout = read_jsonl(args.data / "heldout.jsonl")
    rows = [r for name in ("train_none", "train_save", "train_action") for r in read_jsonl(args.data / f"{name}.jsonl")]
    fixtures = [c["user"] for f in (te.FIXTURE, te.FIXTURE.parent / "tool-eval-holdout.json")
                for c in json.loads(f.read_text())]
    rows, dropped = drop_leaks(rows, [r["text"] for r in heldout] + fixtures)
    train, val = split(rows, args.val, args.seed)
    for name, part in (("train", train), ("val", val)):
        (args.data / f"{name}.jsonl").write_text("".join(json.dumps(to_messages(r)) + "\n" for r in part))
    args.fixture_out.write_text(json.dumps(to_fixture(heldout), indent=1) + "\n")
    count = lambda part: {i: sum(r["intent"] == i for r in part) for i in ("NONE", "SAVE", "ACTION")}  # noqa: E731
    print(f"train {len(train)} {count(train)}, val {len(val)} {count(val)}, held-out {len(heldout)} {count(heldout)}")
    print(f"dropped {len(dropped)} training rows that match a held-out or fixture text:")
    for row in dropped:
        print(f"  {row['intent']}: {row['text']}")
    print(f"wrote {args.data}/train.jsonl, val.jsonl and {args.fixture_out}")


if __name__ == "__main__":
    main()
