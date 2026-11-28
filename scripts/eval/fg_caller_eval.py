"""fg_caller_eval.py: FunctionGemma 270M Mobile Actions as the phone-action caller behind the embedding router.

The linear embedding router picks the route. On an ACTION route FunctionGemma writes the call, and no Gemma runs.
Each phrasing is scored twice per toolset: behind the router (only ACTION routes reach FunctionGemma) and direct
(every phrasing reaches it, which shows how it refuses without the router in front).

Toolsets, the functions FunctionGemma is shown:
  native     Google's 7 Mobile Actions functions, the ones the model was fine-tuned on
  neutral    our four phone actions as platform-neutral functions (set_timer, get_directions, read_calendar,
             email_notes_to_self); the app maps each to Android or iOS
  do_action  the app's one do_action tool, as Gemma sees it today

Usage:
  python scripts/eval/fg_caller_eval.py --functiongemma ~/models/functiongemma/mobile_actions_q8_ekv1024.litertlm \
      --fixture eval/fixtures/tool-eval-holdout.json
"""
from __future__ import annotations

import argparse
import json
import statistics
import time
from datetime import date, datetime
from pathlib import Path
from typing import Callable

import router
import router_eval as rev
import tool_eval as te

# The developer turn the Mobile Actions fine-tune was trained with (google/mobile-actions).
DEVELOPER = ("Current date and time given in YYYY-MM-DDTHH:MM:SS format: {now}\nDay of week is {day}\n"
             "You are a model that can do function calling with the following functions\n")
NOW = datetime(2026, 11, 10, 15, 0, 0)  # fixed, so runs repeat


def function(name: str, description: str, properties: dict | None = None, required: list[str] | None = None) -> dict:
    return {"type": "function", "function": {
        "name": name, "description": description,
        "parameters": {"type": "object", "properties": properties or {}, "required": required or []}}}


def string(description: str, **more) -> dict:
    return {"type": "string", "description": description, **more}


# Names, descriptions and arguments as in google/mobile-actions.
NATIVE = [
    function("turn_on_flashlight", "Turns the flashlight on."),
    function("turn_off_flashlight", "Turns the flashlight off."),
    function("create_contact", "Creates a contact in the phone's contact list.",
             {"first_name": string("The first name of the contact."), "last_name": string("The last name of the contact."),
              "phone_number": string("The phone number of the contact."),
              "email": string("The email address of the contact.")}, ["first_name", "last_name"]),
    function("send_email", "Sends an email.",
             {"to": string("The email address of the recipient."), "subject": string("The subject of the email."),
              "body": string("The body of the email.")}, ["to", "subject"]),
    function("show_map", "Shows a location on the map.",
             {"query": string("The location to search for. May be the name of a place, a business, or an address.")},
             ["query"]),
    function("open_wifi_settings", "Opens the Wi-Fi settings."),
    function("create_calendar_event", "Creates a new calendar event.",
             {"title": string("The title of the event."),
              "datetime": string("The date and time of the event in the format YYYY-MM-DDTHH:MM:SS.")},
             ["title", "datetime"]),
]
NEUTRAL = [
    function("set_timer", "Starts a timer that rings after some minutes, with an optional message.",
             {"minutes": string("How many minutes until the timer rings, as a number."),
              "label": string("What to remind the person of when it rings. Empty if they gave none.")}, ["minutes"]),
    function("get_directions", "Opens directions to a place in the maps app.",
             {"destination": string("The place to go to. May be the name of a place, a business, or an address."),
              "mode": string("walk or drive.", enum=["walk", "drive"])}, ["destination"]),
    function("read_calendar", "Reads what is on the person's calendar for a day.",
             {"day": string("today or tomorrow.", enum=["today", "tomorrow"])}, ["day"]),
    function("email_notes_to_self", "Opens an email of the person's saved notes addressed to themselves.",
             {"subject": string("A subject for the email. Empty if they gave none.")}),
]
TOOLSETS = {"native": NATIVE, "neutral": NEUTRAL, "do_action": [te.DO_ACTION]}


def to_action(toolset: str, name: str, args: dict) -> tuple[str, str] | None:
    """The app's (action, arg) for one FunctionGemma call, or None when the function is none of our actions.

    This is the per-platform mapping's shared half: each function name lands on one do_action."""
    get = lambda key: str(args.get(key) or "").strip()  # noqa: E731
    if toolset == "do_action":
        return (get("action"), get("arg")) if name == "do_action" else None
    if toolset == "neutral":
        return {
            "set_timer": lambda: ("remind_me", f"{get('minutes')} min: {get('label')}"),
            "get_directions": lambda: ("navigate_to", f"{get('mode') or 'drive'}: {get('destination')}"),
            "read_calendar": lambda: ("whats_on", get("day")),
            "email_notes_to_self": lambda: ("send_notes_to_me", get("subject")),
        }.get(name, lambda: None)()
    return {
        "show_map": lambda: ("navigate_to", f"drive: {get('query')}"),
        "send_email": lambda: ("send_notes_to_me", get("subject")),
    }.get(name, lambda: None)()


def load_cases(path: Path) -> list[dict]:
    """A tool_eval fixture, or FunctionGemma heldout rows ({text, intent, call, tag}), which score the action only."""
    if path.suffix != ".jsonl":
        return json.loads(path.read_text())
    cases = []
    for i, row in enumerate(json.loads(line) for line in path.read_text().splitlines() if line.strip()):
        action = (row.get("call") or {}).get("args", {}).get("action")
        expect = [{"name": "do_action", "args": {"action": f"^{action}$"}}] if action else []
        cases.append({"id": f"{row['tag']}-{i}", "category": row["intent"].lower(), "user": row["text"],
                      "expect": expect, "args_scored": False})
    return cases


def expected_action(case: dict) -> dict | None:
    """The do_action spec this phrasing should end in, or None when FunctionGemma should make no call."""
    return next((e for e in rev.routed_case(case)["expect"] if e["name"] == "do_action"), None)


def score(case: dict, toolset: str, calls: list[tuple[str, dict]]) -> dict:
    spec = expected_action(case)
    mapped = [to_action(toolset, name, args) for name, args in calls]
    out = {"expected_action": spec["args"]["action"].strip("^$") if spec else None,
           "calls": [{"name": name, "args": args} for name, args in calls],
           "mapped": [list(m) if m else None for m in mapped], "refused": not calls}
    if spec:
        ours = [{"action": m[0], "arg": m[1]} for m in mapped if m]
        only_action = {"args": {"action": spec["args"]["action"]}}
        out["right_action"] = len(calls) == 1 and any(te._arg_ok(only_action, c) for c in ours)
        out["args_ok"] = out["right_action"] and any(te._arg_ok(spec, c) for c in ours)
    return out


def functiongemma_caller(engine) -> Callable[[str, list[dict]], dict]:
    """(transcript, specs) -> the calls FunctionGemma wrote, any text, and the seconds until it finished."""
    system = DEVELOPER.format(now=NOW.isoformat(), day=NOW.strftime("%A"))

    def ask(transcript: str, specs: list[dict], clock: Callable[[], float] = time.perf_counter) -> dict:
        conversation = engine.create_conversation(system_message=system, sampler_config=rev.greedy(0),
                                                  tools=te.litert_tools(specs), automatic_tool_calling=False)
        text, calls, start = "", [], clock()
        try:
            for chunk, part_calls in te.litert_parts(conversation, transcript, max_output_tokens=rev.FG_MAX_TOKENS):
                text += chunk
                calls += part_calls
            error = None
        except RuntimeError as failure:  # a call LiteRT-LM cannot parse
            error = str(failure)[:200]
        return {"calls": calls, "text": text.strip()[:200], "seconds": round(clock() - start, 4), "parse_error": error,
                "prompt_tokens": len(engine.tokenize(system + json.dumps(specs) + transcript))}
    return ask


def run(cases: list[dict], toolsets: list[str], route: Callable[[str], str], ask) -> list[dict]:
    rows = []
    for case in cases:
        routed_action = router.ACTION in route(case["user"]).split("+")
        for toolset in toolsets:
            answer = ask(case["user"], TOOLSETS[toolset])
            rows.append({"id": case["id"], "category": case["category"], "user": case["user"], "toolset": toolset,
                         "routed_action": routed_action, "args_scored": case.get("args_scored", True),
                         **score(case, toolset, answer.pop("calls")), **answer})
    return rows


def counts(rows: list[dict]) -> dict:
    """Plain counts over one toolset's rows, for one way of reaching FunctionGemma."""
    wanted = [r for r in rows if r["expected_action"]]
    unwanted = [r for r in rows if not r["expected_action"]]
    scored = [r for r in wanted if r["args_scored"]]
    seconds = sorted(r["seconds"] for r in rows)
    return {
        "calls_made": len(rows),
        "right_action": [sum(r["right_action"] for r in wanted), len(wanted)],
        "correct_args": [sum(r["args_ok"] for r in scored), len(scored)],
        "refused_when_no_action_wanted": [sum(r["refused"] for r in unwanted), len(unwanted)],
        "refused_a_wanted_action": [sum(r["refused"] for r in wanted), len(wanted)],
        "parse_errors": sum(bool(r["parse_error"]) for r in rows),
        "seconds_median": round(statistics.median(seconds), 3) if seconds else None,
        "seconds_max": seconds[-1] if seconds else None,
        "prompt_tokens_median": statistics.median(r["prompt_tokens"] for r in rows) if rows else None,
    }


def summarize(rows: list[dict]) -> dict:
    out = {}
    for toolset in dict.fromkeys(r["toolset"] for r in rows):
        mine = [r for r in rows if r["toolset"] == toolset]
        wanted = [r for r in mine if r["expected_action"]]
        out[toolset] = {
            "phrasings": len(mine),
            "action_phrasings_routed_to_action": [sum(r["routed_action"] for r in wanted), len(wanted)],
            "behind_router": counts([r for r in mine if r["routed_action"]]),
            "direct": counts(mine),
            "by_action_direct": {
                action: [sum(r["right_action"] for r in wanted if r["expected_action"] == action),
                         sum(1 for r in wanted if r["expected_action"] == action)]
                for action in dict.fromkeys(r["expected_action"] for r in wanted)},
        }
    return out


def of(pair: list[int]) -> str:
    return f"{pair[0]} of {pair[1]}"


def table(summary: dict) -> str:
    lines = ["| Toolset | Reached by | Calls made | Right action | Correct arguments | Refused, no action wanted | "
             "Refused a wanted action | Seconds per call, median / max |", "|---|---|---|---|---|---|---|---|"]
    for toolset, s in summary.items():
        for reached in ("behind_router", "direct"):
            c = s[reached]
            lines.append(f"| {toolset} | {reached.replace('_', ' ')} | {c['calls_made']} | {of(c['right_action'])} | "
                         f"{of(c['correct_args'])} | {of(c['refused_when_no_action_wanted'])} | "
                         f"{of(c['refused_a_wanted_action'])} | {c['seconds_median']} / {c['seconds_max']} |")
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--functiongemma", required=True, help="FunctionGemma 270M Mobile Actions .litertlm")
    parser.add_argument("--fixture", default=str(te.FIXTURE), help="a tool_eval fixture, or heldout .jsonl rows")
    parser.add_argument("--toolsets", default=",".join(TOOLSETS))
    parser.add_argument("--embed-model-dir", help="all-MiniLM-L6-v2 ONNX folder (default: EMBED_MODEL_DIR or "
                                                  "~/models/all-MiniLM-L6-v2-router)")
    parser.add_argument("--embed-model-file", default="model.onnx")
    parser.add_argument("--tag", default="")
    args = parser.parse_args()
    toolsets = [t for t in args.toolsets.split(",") if t]
    if set(toolsets) - set(TOOLSETS):
        parser.error(f"unknown toolsets: {', '.join(sorted(set(toolsets) - set(TOOLSETS)))}")
    if not Path(args.functiongemma).expanduser().is_file():
        raise SystemExit(f"model file not found: {args.functiongemma}")
    cases = load_cases(Path(args.fixture))

    import embed_router as er
    import litert_lm
    model_dir = Path(args.embed_model_dir or er.DEFAULT_MODEL_DIR).expanduser()
    embedder = er.load_router(model_dir, args.embed_model_file, "linear", er.OnnxEncoder(model_dir, args.embed_model_file))
    route = lambda text: er.embed_route(embedder.decide(text)).intent  # noqa: E731
    engine = litert_lm.Engine(str(Path(args.functiongemma).expanduser()), backend=litert_lm.Backend.CPU())
    ask = functiongemma_caller(engine)
    for toolset in toolsets:  # warm-up, untimed
        ask("hello", TOOLSETS[toolset])

    rows = run(cases, toolsets, route, ask)
    summary = summarize(rows)
    fixture = Path(args.fixture).stem.removeprefix("tool-eval").strip("-") or "main"
    tag = "".join(f"-{part}" for part in (args.tag, fixture) if part)
    base = te.RESULTS_DIR / f"{date.today().isoformat()}-fg-caller{tag}-{Path(args.functiongemma).stem}"
    te.RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    Path(f"{base}.jsonl").write_text("".join(json.dumps(r) + "\n" for r in rows))
    Path(f"{base}.summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    Path(f"{base}.md").write_text(table(summary) + "\n")
    print(table(summary))
    print(json.dumps({t: s["by_action_direct"] for t, s in summary.items()}))
    print(f"wrote {base}.jsonl, {base}.summary.json, {base}.md")


if __name__ == "__main__":
    main()
