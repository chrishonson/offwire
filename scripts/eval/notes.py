"""Evaluates the one-tool notes assistant: save_note, with saved notes given back as context.

Usage: .venv/bin/python scripts/eval/notes.py [--runs 3] [--no-tools]
       python3 scripts/eval/notes.py --no-tools --endpoint URL --endpoint-model NAME   (hosted, see hosted.py)
"""
import argparse
import json
import re
from datetime import date
from pathlib import Path

from run import RESULTS_DIR, _report, write_results
from scoring import score_rubric

REPO = Path(__file__).resolve().parents[2]
FIXTURE = REPO / "eval" / "fixtures" / "notes.json"

# Mirrors systemInstruction(...) in core/src/commonMain/kotlin/com/chrishonson/gemmavoice/llm/SystemInstruction.kt,
# so the eval tests the prompts the app ships: with notes on Android, without tools on iOS. Keep the two in step.
INTRO = (
    "You are Computer, a voice assistant running fully on this phone. "
    "The user talks to you out loud or types, and your replies are read aloud, so speak in plain "
    "conversational sentences with no lists or markup. "
    "Never say you cannot hear, listen or speak.\n"
)
LIMITS = (
    "You cannot set reminders, alarms or timers, call, text, email or message anyone, "
    "or book appointments. When asked, say plainly that you cannot. Never say you will search, check or get "
    "back to the user later.\n"
)
NO_LIVE = "You cannot look anything up live, so you have no news, weather, scores or prices from today. "
OWN_KNOWLEDGE = "from what you learned in training: general knowledge, ideas, advice and conversation."
NO_TOOLS_PROMPT = (
    INTRO + "You have no tools. You cannot save notes. " + NO_LIVE + LIMITS
    + f"Answer {OWN_KNOWLEDGE}"
)
NOTES_PROMPT = (
    INTRO
    + "You have one tool. It saves a note to the user's notes on this phone, where they read them on the "
    "Notes screen. Use it only when the user asks you to note, remember, write down, log or save something. "
    "Call it once per request, before you say anything, then tell the user once, in a sentence, what you "
    "saved. Most messages need no tool, just a spoken reply. "
    "Never say you saved, noted, set or added anything unless you called the tool for it.\n"
    + NO_LIVE + LIMITS
    + "The user's saved notes:\n{notes}\n"
    "When the notes answer what the user asks, answer from them with the exact words and values. "
    "When the user asks what they saved about something no note mentions, say they have no note about it. "
    f"Answer everything else {OWN_KNOWLEDGE}"
)


def build() -> tuple[list, list[dict]]:
    """Returns the one tool and the list it records calls into."""
    calls: list[dict] = []

    def save_note(text: str) -> str:
        """Call when the person asks you to note, remember, write down, log or save something for later. Saves it to their notes on this phone.

        The runtime sends the model only this docstring's first line, so it holds SAVE_NOTE_DESCRIPTION whole.

        Args:
            text: The note, worded close to how the person said it.
        """
        calls.append({"name": "save_note", "args": {"text": text}})
        return "Saved to their notes. Now tell the person once what you saved."

    return [save_note], calls


def system_prompt(notes: list[str] | None) -> str:
    """None gives the no-tools prompt, a list the notes prompt with those notes."""
    if notes is None:
        return NO_TOOLS_PROMPT
    return NOTES_PROMPT.format(notes="\n".join(f"- {n}" for n in notes[-40:]) or "No notes yet.")


def needs_tool(case: dict) -> bool:
    """Saves and recalls only make sense when the model has the tool and the notes."""
    return "must_call" in case["rubric"] or "notes" in case


def score(reply: str, calls: list[dict], rubric: dict) -> list[str]:
    """Rubric keys as in scoring.score_rubric, plus note_match (a pattern every saved note must match) and min_words."""
    failures = score_rubric(reply, rubric, calls)
    pattern = rubric.get("note_match")
    if pattern:
        failures += [f"note missing {pattern}: {c['args']['text']}" for c in calls
                     if not re.search(pattern, c["args"]["text"], re.IGNORECASE)]
    words = len(reply.split())
    if words < rubric.get("min_words", 0):
        failures.append(f"only {words} words, wanted {rubric['min_words']}")
    if len(calls) > 1:
        failures.append(f"saved {len(calls)} notes")
    return failures


def run_cases(cases: list[dict], ask_fn, runs: int, tools: bool = True) -> list[dict]:
    """Without [tools], runs only the cases that need none, under the no-tools prompt."""
    results = []
    for case in cases if tools else [c for c in cases if not needs_tool(c)]:
        system = system_prompt(case.get("notes", []) if tools else None)
        for run in range(1, runs + 1):
            functions, calls = build()
            reply = ask_fn(system, case["user"], run - 1, functions if tools else [], case.get("history"))
            results.append({"id": case["id"], "run": run, "reply": reply, "calls": calls,
                            "failures": score(reply, calls, case["rubric"])})
    return results


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--no-tools", action="store_true", help="test the no-tools prompt the iOS app uses")
    parser.add_argument("--endpoint", help="OpenAI-compatible base URL, or 'gemini' (see hosted.py); --no-tools only")
    parser.add_argument("--endpoint-model", help="model name the endpoint expects")
    parser.add_argument("--api-key-env", help="environment variable holding the endpoint's key")
    args = parser.parse_args()

    if args.endpoint:
        if not args.no_tools or not args.endpoint_model:
            parser.error("--endpoint runs only with --no-tools and needs --endpoint-model")
        import hosted
        open_send = hosted.openai_open_send(hosted.GEMINI_OPENAI_BASE if args.endpoint == "gemini" else args.endpoint,
                                            args.endpoint_model, args.api_key_env)

        def ask_fn(system, user, seed, functions, history):
            send = open_send(system, history or [], [], seed)
            return "".join(text for text, _ in send({"role": "user", "content": [{"type": "text", "text": user}]}))
        suffix = "-hosted-" + "".join(c if c.isalnum() or c in "._-" else "_" for c in args.endpoint_model)
    else:
        from model import ask, load_engine, results_suffix
        engine = load_engine()

        def ask_fn(system, user, seed, functions, history):
            return ask(engine, system, user, history, seed=seed, tools=functions or None)
        suffix = results_suffix()
    results = run_cases(json.loads(FIXTURE.read_text()), ask_fn, args.runs, not args.no_tools)
    name = FIXTURE.stem + ("-no-tools" if args.no_tools else "") + suffix
    print(f"wrote {write_results(name, results, RESULTS_DIR, date.today().isoformat())}")
    _report(results)


if __name__ == "__main__":
    main()
