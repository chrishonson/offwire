"""Runs an evaluation set against the model and records every reply.

Usage: .venv/bin/python scripts/eval/run.py <E1..E6 | fixture path> [--set E2] [--runs 3]
"""
import argparse
import json
import re
from datetime import date
from pathlib import Path

import tools as role_tools
import two_pass
from prompts import system_prompt
from scoring import score_extraction, score_rubric

REPO = Path(__file__).resolve().parents[2]
TEMPLATE_PATH = REPO / "eval" / "health-template.json"
RESULTS_DIR = REPO / "eval" / "results"
SET_ID = re.compile(r"E[1-6]")


def fixture_path(set_id: str) -> Path:
    return REPO / "eval" / "fixtures" / f"{set_id.lower()}.json"


def _extraction_failures(calls: list[dict], expected: list[dict]) -> list[str]:
    items = [c["args"] for c in calls if c["name"] == "note_personal_fact"]
    scored = score_extraction(items, expected)
    return ([f"missed: {e['kind']} {e['match']}" for e in scored["missed"]]
            + [f"extra: {i.get('text')}" for i in scored["extra"]]
            + [f"wrong kind: {i.get('text')}" for i in scored["wrong_kind"]])


def run_set(set_id: str, cases: list[dict], ask_fn, runs: int) -> list[dict]:
    template = json.loads(TEMPLATE_PATH.read_text())
    results = []
    for case in cases:
        system = system_prompt(set_id, template, case.get("online", False), case.get("record"))
        image = REPO / case["image"] if case.get("image") else None
        for run in range(1, runs + 1):
            functions, calls = role_tools.build(case.get("online", False))
            reply = ask_fn(system, case["user"], case.get("history"), image, run - 1,
                           None if set_id == "E2" else functions)
            failures = (_extraction_failures(calls, case["expected"]) if "expected" in case
                        else score_rubric(reply, case["rubric"], calls))
            results.append({"id": case["id"], "run": run, "reply": reply, "calls": calls, "failures": failures})
    return results


def pass_rate(results: list[dict]) -> float:
    return sum(not r["failures"] for r in results) / len(results) if results else 0.0


def write_results(fixture_stem: str, results: list[dict], directory: Path, day: str) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / f"{day}-{fixture_stem}.jsonl"
    path.write_text("".join(json.dumps(r) + "\n" for r in results))
    return path


def _report(results: list[dict]) -> None:
    for case_id in dict.fromkeys(r["id"] for r in results):
        mine = [r for r in results if r["id"] == case_id]
        print(f"{case_id}: {sum(not r['failures'] for r in mine)}/{len(mine)} passed")
        for r in mine:
            if r["failures"]:
                print(f"    run {r['run']}: reply {r['reply'][:120]!r} calls {[c['name'] for c in r['calls']]}")
            for failure in r["failures"]:
                print(f"    run {r['run']}: {failure}")
    print(f"pass rate: {pass_rate(results):.2f}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("target", help="set id E1..E6, or a fixture path (needs --set)")
    parser.add_argument("--set", dest="set_id", help="prompt set when target is a path")
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--two-pass", action="store_true", help="route to tools, run them, then reply")
    args = parser.parse_args()

    if SET_ID.fullmatch(args.target):
        set_id, path = args.target, fixture_path(args.target)
    elif args.set_id and SET_ID.fullmatch(args.set_id):
        set_id, path = args.set_id, Path(args.target)
    else:
        parser.error("a fixture path needs --set E1..E6")

    from model import ask, load_engine
    engine = load_engine()
    template = json.loads(TEMPLATE_PATH.read_text())
    def ask_fn(system, user, history, image, seed, functions):
        if args.two_pass and functions is not None:
            send = lambda s, u, h, i, sd, schema=None: ask(engine, s, u, h, i, sd, schema=schema)  # noqa: E731
            return two_pass.ask(send, system, user, history, image, seed, functions, template["expert"])
        return ask(engine, system, user, history, image, seed, functions)

    results = run_set(set_id, json.loads(path.read_text()), ask_fn, args.runs)
    print(f"wrote {write_results(path.stem, results, RESULTS_DIR, date.today().isoformat())}")
    _report(results)


if __name__ == "__main__":
    main()
