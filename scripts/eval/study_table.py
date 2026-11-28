"""Collects R2, R3 and R6 rows from router_eval result files into one table for a study:
pass, tool call on a no-tool turn, save cases passed, and save_note called on a no-tool turn.

  python study_table.py eval/results/2026-11-27-router-eval-quant-A-gemma-4-E4B-it-embed_mlp.jsonl ... [--json OUT]
"""
import argparse
import json
from pathlib import Path


def row_counts(path: Path) -> dict:
    rows = [json.loads(line) for line in path.read_text().splitlines() if line.strip()]
    no_tool = [r for r in rows if not r["expected"]]
    saves = [r for r in rows if r["category"] == "save"]
    return {"file": path.name, "rows": len(rows), "pass": sum(not r["failures"] for r in rows),
            "no_tool": len(no_tool), "tool_on_no_tool": sum(bool(r["called"]) for r in no_tool),
            "save_note_on_no_tool": sum("save_note" in r["called"] for r in no_tool),
            "save_cases": len(saves), "save_pass": sum(not r["failures"] for r in saves)}


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("files", nargs="+")
    parser.add_argument("--json")
    args = parser.parse_args(argv)
    out = [row_counts(Path(f)) for f in args.files]
    for o in out:
        print(f"{o['file']:80} pass {o['pass']}/{o['rows']}  tool on no-tool {o['tool_on_no_tool']}/{o['no_tool']}  "
              f"save_note on no-tool {o['save_note_on_no_tool']}  saves {o['save_pass']}/{o['save_cases']}")
    if args.json:
        Path(args.json).write_text(json.dumps(out, indent=2) + "\n")


if __name__ == "__main__":
    main()
