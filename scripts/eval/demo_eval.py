"""Runs the Part Two live demo script as one chained conversation and scores each check.

Unlike the fixtures, nothing is canned: each turn's history is the model's own earlier replies, and a save adds
its note to the next turn's prompt, as on the phone. One run is the whole script (docs: the live demo script):

  1  "I'm driving home and I want to think through a weekend project in the garage."   no save
  2  "Give me three ideas." / "I like the second one. What would I need?"             no save
  3  "Can you save notes?" / "Do you have a tool to save a note?"                     yes both times, no save
  4  "That would be a failure." / "I'm asking you to recognize ..."                   no save, no "I saved"
  5  "Eh, just jot down that I want to try the second idea Saturday."                 one save that says Saturday
  6  "What did we come up with?"                                                      mentions Saturday, no save
  7  the Notes screen                                                                 exactly one note

Usage (hosted, see scripts/eval/cloud):
  python3 scripts/eval/demo_eval.py --runs 10 --endpoint http://127.0.0.1:8080/v1 \\
      --endpoint-model gemma-4-E4B-it-Q4_K_M [--tag NAME]
"""
import argparse
import json
import re
import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import router_eval as re_  # noqa: E402
import tool_eval as te  # noqa: E402

STEPS = [
    ("1-start", "I'm driving home and I want to think through a weekend project in the garage."),
    ("2-ideas", "Give me three ideas."),
    ("2-second", "I like the second one. What would I need?"),
    ("3-can-save", "Can you save notes?"),
    ("3-tool", "Do you have a tool to save a note?"),
    ("4-failure", "That would be a failure."),
    ("4-recognize", "I'm asking you to recognize that you have a way to save notes right now."),
    ("5-jot", "Eh, just jot down that I want to try the second idea Saturday."),
    ("6-recall", "What did we come up with?"),
]
YES = re.compile(r"(?i)\b(yes|yeah|i can|i do|i have|you can)\b")
DENY = re.compile(r"(?i)\b(can't|cannot|can not|don't have|do not have|unable)\b")
SATURDAY = re.compile(r"(?i)saturday")
# The note should say what the idea was, so it reads alone on the Notes screen. Not part of a clean run.
POINTER = re.compile(r"(?i)\b(second|2nd|that|this) (idea|one)\b")
CHECKS = ["no_early_save", "says_yes", "no_false_claim", "one_save_saturday", "one_confirmation", "recall_saturday",
          "one_note"]


def saves(row: dict) -> list[str]:
    return [c["args"].get("text", "") for c in row.get("calls", []) if c["name"] == "save_note"]


def score_run(turns: list[dict]) -> dict:
    """[turns]: one row per step, in STEPS order, each with its step id under "step". Returns check -> bool."""
    by = {t["step"]: t for t in turns}
    before = [t for t in turns if t["step"][0] in "1234"]
    jot, recall = by.get("5-jot", {}), by.get("6-recall", {})
    jotted = saves(jot)
    return {
        "no_early_save": all(not saves(t) for t in before),
        "says_yes": all(YES.search(by[s].get("guarded") or "") and not DENY.search(by[s].get("guarded") or "")
                        for s in ("3-can-save", "3-tool") if s in by),
        # Once the note exists, "I saved a note that..." in a later turn is true, so only turns before it count.
        "no_false_claim": not any(t.get("false_claim") for t in before),
        "one_save_saturday": len(jotted) == 1 and bool(SATURDAY.search(jotted[0])),
        "one_confirmation": bool(jotted) and not jot.get("double_confirm_guarded"),
        "recall_saturday": bool(SATURDAY.search(recall.get("guarded") or "")) and not saves(recall),
        "one_note": sum(len(saves(t)) for t in turns) == 1,
    }


def names_idea(turns: list[dict]) -> bool:
    """The jot's note names the idea instead of pointing back at "the second idea"."""
    jotted = saves(next((t for t in turns if t["step"] == "5-jot"), {}))
    return len(jotted) == 1 and not POINTER.search(jotted[0])


def run_script(run: int, ask) -> list[dict]:
    """[ask](case, run) -> row: one routed turn (router_eval.run_routed with its setup bound)."""
    history: list[dict] = []
    saved: list[str] = []
    turns = []
    for step, text in STEPS:
        case = {"id": f"demo-{step}", "category": "demo", "user": text, "expect": [], "rubric": {},
                "history": list(history), "notes": list(saved)}
        row = ask(case, run)
        row["step"] = step
        turns.append(row)
        saved += saves(row)
        history += [{"role": "user", "text": text}, {"role": "model", "text": row.get("guarded") or ""}]
    return turns


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--runs", type=int, default=10)
    parser.add_argument("--endpoint", required=True)
    parser.add_argument("--endpoint-model", required=True)
    parser.add_argument("--api-key-env")
    parser.add_argument("--embed-model-dir")
    parser.add_argument("--embed-model-file", default="model.onnx")
    parser.add_argument("--tag", default="")
    args = parser.parse_args()

    import embed_router as er
    import hosted
    re_.is_e4b(args.endpoint_model)
    model_dir = Path(args.embed_model_dir or er.DEFAULT_MODEL_DIR).expanduser()
    encoder = er.OnnxEncoder(model_dir, args.embed_model_file)
    routers = {s: er.load_router(model_dir, args.embed_model_file, s, encoder) for s in er.SCORERS}
    route = re_.embed_route_fn("embed_mlp", routers)
    route("hello")
    open_send = hosted.openai_open_send(
        hosted.GEMINI_OPENAI_BASE if args.endpoint == "gemini" else args.endpoint, args.endpoint_model,
        args.api_key_env)

    def ask(case, run):
        return re_.run_routed(case, "embed_mlp", run, open_send, None, route_fn=route, app_tools=True)

    stem = "hosted-" + "".join(c if c.isalnum() or c in "._-" else "_" for c in args.endpoint_model)
    base = te.RESULTS_DIR / f"{date.today().isoformat()}-demo-eval-{args.tag + '-' if args.tag else ''}{stem}"
    te.RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    totals = dict.fromkeys(CHECKS, 0)
    clean = named = 0
    with open(f"{base}.jsonl", "w") as out:
        for run in range(1, args.runs + 1):
            turns = run_script(run, ask)
            checks = score_run(turns)
            for k, v in checks.items():
                totals[k] += v
            clean += all(checks.values())
            named += names_idea(turns)
            out.write(json.dumps({"run": run, "checks": checks, "turns": turns}) + "\n")
            out.flush()
            print(f"run {run}: " + (", ".join(k for k, v in checks.items() if not v) or "clean"))
    summary = {"model": stem, "runs": args.runs, "clean_runs": clean, "checks": totals, "names_idea": named}
    Path(f"{base}.summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(f"clean runs {clean} of {args.runs}; " + ", ".join(f"{k} {v}" for k, v in totals.items())
          + f"; note names the idea {named} of {args.runs}")


if __name__ == "__main__":
    main()
