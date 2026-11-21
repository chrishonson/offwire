"""Tool-calling eval: when Gemma calls a tool, which one, with what arguments, and what the person hears.

Two tool setups run against the same cases:
- shipped: the app as it is on claude/demo-hardening. One tool, save_note, with the saved notes in the prompt.
- proposed: Nick's pick from the Nov 10 survey. save_note, read_notes and one do_action dispatcher
  (remind_me, send_notes_to_me, navigate_to, whats_on). It is not in the app yet; the prompt here is a draft.

Each turn streams like LiteRtLlmEngine.streamReply: automatic tool calling is off, the eval runs the calls itself and
sends the results back, up to MAX_TOOL_ROUNDS. Every streamed pass is kept, so the score sees what the speaker
would say, not only the last pass a blocking send_message returns. That blind spot is how the double
"I saved your note" hid from notes.py. Two spoken views are scored:
- raw: every pass joined, which is what main speaks (the library streams text before and after a call).
- guarded: what ToolTurn on claude/demo-hardening speaks (text after a tool call is dropped once anything was said).

Usage:
  .venv/bin/python scripts/eval/tool_eval.py [--config proposed|shipped|both] [--runs 3] [--model PATH ...]
      [--only ID_PREFIX] [--limit N] [--dry-run]

  Hosted instead of the Mac (standard library only, see hosted.py for what differs from the phone):
  python3 scripts/eval/tool_eval.py --endpoint BASE_URL --endpoint-model NAME [--api-key-env VAR] [--runs 3]
      BASE_URL ends before /chat/completions, e.g. http://localhost:8080/v1 for llama-server, or "gemini" for
      the Gemini API's OpenAI endpoint (key from GEMINI_API_KEY).
"""
import argparse
import json
import re
import statistics
import time
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path
from typing import Callable, Iterable, Iterator

import notes

REPO = Path(__file__).resolve().parents[2]
FIXTURE = REPO / "eval" / "fixtures" / "tool-eval.json"
RESULTS_DIR = REPO / "eval" / "results"
CONFIGS = ("shipped", "proposed")

# Mirrors core/src/commonMain/kotlin/com/chrishonson/gemmavoice/llm/ToolTurn.kt and LiteRtLlmEngine.kt.
MAX_TOOL_ROUNDS = 3
REPEATED_TOOL_CALL_RESULT = "Already done. Do not call it again and do not repeat what you already said."
SAVED_WHEN_SILENT = "I saved that to your notes."

# Mirrors core/src/commonMain/kotlin/com/chrishonson/gemmavoice/notes/SaveNote.kt (test_tool_eval checks it).
SAVE_NOTE_DESCRIPTION = ("Call when the person asks you to note, remember, write down, log or save something for "
                         "later. Saves it to their notes on this phone.")
SAVE_NOTE_TEXT_DESCRIPTION = "The note, worded close to how the person said it."
SAVE_NOTE_RESULT = "Saved to their notes. Now tell the person once what you saved."

ACTIONS = ["remind_me", "send_notes_to_me", "navigate_to", "whats_on"]
DEFAULT_CALENDAR = {"today": ["2 PM dentist", "4:30 PM pick up Luna from practice"],
                    "tomorrow": ["9 AM team standup", "7 PM AI meetup"]}

# Proposed tools. Draft wording, kept to one short string argument each, as the survey recommends for E2B.
SAVE_NOTE = {"type": "function", "function": {
    "name": "save_note", "description": SAVE_NOTE_DESCRIPTION,
    "parameters": {"type": "object", "properties": {"text": {"type": "string", "description": SAVE_NOTE_TEXT_DESCRIPTION}},
                   "required": ["text"]}}}
READ_NOTES = {"type": "function", "function": {
    "name": "read_notes",
    "description": "Call when the person asks about something they saved, noted or wrote down earlier. "
                   "Returns their matching saved notes.",
    "parameters": {"type": "object", "properties": {"query": {
        "type": "string", "description": "Words to look for, such as the topic they ask about."}}, "required": ["query"]}}}
DO_ACTION = {"type": "function", "function": {
    "name": "do_action",
    "description": "Call when the person asks you to start a timer or a reminder in some minutes, to email their notes "
                   "to themselves, for directions to a place, or what is on their calendar.",
    "parameters": {"type": "object", "properties": {
        "action": {"type": "string", "enum": ACTIONS,
                   "description": "remind_me, send_notes_to_me, navigate_to or whats_on."},
        "arg": {"type": "string",
                "description": "For remind_me, the minutes and the message, like 20 min: call the vet. "
                               "For navigate_to, walk or drive and the place, like walk: the library. "
                               "For whats_on, today or tomorrow. For send_notes_to_me, a subject, or empty."}},
        "required": ["action", "arg"]}}}

PROPOSED_TOOLS_TEXT = (
    "You have three tools. save_note saves a note to the user's notes on this {device}. read_notes looks up the "
    "notes they saved. do_action does one of four things: remind_me starts a timer that rings with a message, "
    "send_notes_to_me opens an email of their notes addressed to themselves for them to send, navigate_to opens "
    "directions in Maps, and whats_on reads their calendar for today or tomorrow. "
    "Use a tool only when this message asks for what it does. Call it once per request, before you say anything, "
    "then tell the user once, in a sentence, what you did. Most messages need no tool, just a spoken reply. "
    "Never say you saved, noted, set, sent or added anything unless you called the tool for it.\n"
)
PROPOSED_LIMITS = (
    "You cannot set an alarm for a clock time, call, text or message anyone, email anyone but the user, add "
    "calendar events, or book appointments. When asked, say plainly that you cannot. Never say you will search, "
    "check or get back to the user later.\n"
)


def system_prompt(config: str, saved: list[str], device: str = "phone") -> str:
    """shipped is the app's own prompt (via notes.py's mirror); proposed swaps the tool and limits lines and drops
    the listed notes, since read_notes fetches them."""
    if config == "shipped":
        return notes.system_prompt(saved)
    return (notes.INTRO + PROPOSED_TOOLS_TEXT.format(device=device) + notes.NO_LIVE + PROPOSED_LIMITS
            + "When the user asks about something they saved, use read_notes and answer from it with the exact "
            f"words and values. Answer everything else {notes.OWN_KNOWLEDGE}")


def tool_specs(config: str) -> list[dict]:
    """routed is router_eval.py's set: the router shows the model at most one of these per turn."""
    return {"shipped": [SAVE_NOTE], "routed": [SAVE_NOTE, DO_ACTION]}.get(config, [SAVE_NOTE, READ_NOTES, DO_ACTION])


class ToolBox:
    """Runs the model's calls against the case's fake phone state and records them."""

    def __init__(self, config: str, saved: list[str], calendar: dict | None = None):
        self.names = {s["function"]["name"] for s in tool_specs(config)}
        self.saved = list(saved)
        self.calendar = calendar or DEFAULT_CALENDAR
        self.calls: list[dict] = []

    def run(self, name: str, args: dict) -> str:
        self.calls.append({"name": name, "args": dict(args)})
        if name not in self.names:
            return f"There is no tool called {name}."
        return getattr(self, "_" + name)(**{k: str(v) for k, v in args.items()})

    def _save_note(self, text: str = "", **_) -> str:
        if not text.strip():
            return "Nothing was saved because the note was empty. Ask the person what to save."
        self.saved.append(text)
        return SAVE_NOTE_RESULT

    def _read_notes(self, query: str = "", **_) -> str:
        words = {w for w in re.findall(r"[a-z0-9]+", query.lower()) if len(w) > 2}
        hits = [n for n in self.saved if words & set(re.findall(r"[a-z0-9]+", n.lower()))]
        if hits:
            return "Their matching notes: " + " | ".join(hits)
        if not self.saved:
            return "They have no saved notes."
        return "No note matched. Their newest notes: " + " | ".join(self.saved[-5:])

    def _do_action(self, action: str = "", arg: str = "", **_) -> str:
        if action == "remind_me":
            m = re.search(r"(\d+)\s*(h|hr|hour|m|min|minute)?", arg, re.IGNORECASE)
            if not m:
                return "Nothing was set because no number of minutes was given. Ask the person how long."
            minutes = int(m.group(1)) * (60 if (m.group(2) or "m").lower().startswith("h") else 1)
            message = arg.split(":", 1)[1].strip() if ":" in arg else ""
            return f"Timer set for {minutes} minutes{': ' + message if message else ''}. Now tell the person once what you did."
        if action == "send_notes_to_me":
            return "An email of their notes, addressed to them, is open for them to send. Now tell the person once what you did."
        if action == "navigate_to":
            return f"Maps is open with directions to {arg.split(':', 1)[-1].strip() or 'that place'}."
        if action == "whats_on":
            day = "tomorrow" if "tomorrow" in arg.lower() else "today"
            events = self.calendar.get(day) or []
            return f"Their calendar {day}: " + ("; ".join(events) if events else "nothing scheduled") + "."
        return f"There is no action called {action}. The actions are {', '.join(ACTIONS)}."


# ---- One streamed turn ----------------------------------------------------------------------------------------

@dataclass
class Pass:
    text: str = ""
    calls: list = field(default_factory=list)
    first_s: float | None = None  # seconds from the turn's start to this pass's first streamed piece
    end_s: float = 0.0


def accumulate(current: str, chunk: str) -> str:
    """StreamAccumulator.append: chunks may be deltas or the text so far."""
    return chunk if chunk.startswith(current) else current + chunk


def run_turn(send: Callable[[dict], Iterable[tuple[str, list]]], user: str, box: ToolBox,
             clock: Callable[[], float] = time.perf_counter) -> list[Pass]:
    """[send] takes one message and streams (text_chunk, [(name, args), ...]) pieces for one model pass."""
    start = clock()
    passes: list[Pass] = []
    message: dict = {"role": "user", "content": [{"type": "text", "text": user}]}
    seen: set[str] = set()
    for _ in range(MAX_TOOL_ROUNDS + 1):
        current = Pass()
        for chunk, calls in send(message):
            if (chunk or calls) and current.first_s is None:
                current.first_s = clock() - start
            if chunk:
                current.text = accumulate(current.text, chunk)
            current.calls += [(name, dict(args)) for name, args in calls]
        current.end_s = clock() - start
        passes.append(current)
        if not current.calls or len(passes) > MAX_TOOL_ROUNDS:
            break
        responses = []
        for name, args in current.calls:
            key = json.dumps([name, args], sort_keys=True)
            result = box.run(name, args) if key not in seen else REPEATED_TOOL_CALL_RESULT
            if key in seen:
                box.calls.append({"name": name, "args": dict(args), "repeat": True})
            seen.add(key)
            responses.append({"type": "tool_response", "name": name, "response": result})
        message = {"role": "tool", "content": responses}
    return passes


def spoken_raw(passes: list[Pass]) -> str:
    """What main speaks: every pass, joined."""
    return " ".join(p.text.strip() for p in passes if p.text.strip())


def spoken_guarded(passes: list[Pass]) -> str:
    """What ToolTurn speaks: once text was spoken, text after a tool call is dropped; silence after a call gets a
    fixed line."""
    said = ""
    muted = False
    for i, p in enumerate(passes):
        if not muted and p.text.strip():
            said = (said + " " + p.text.strip()).strip()
        if p.calls and i < MAX_TOOL_ROUNDS and said:
            muted = True
    if not said and any(p.calls for p in passes):
        return SAVED_WHEN_SILENT
    return said


# ---- Scoring --------------------------------------------------------------------------------------------------

# The subject must open its clause, so "everything I have saved" is not a claim.
CLAIM = re.compile(r"(^|[,;:]|\b(and|so|okay|ok|sure|alright|great))\W*"
                   r"(I('ve| have| just)?|it's|that's|all)\s+(been\s+)?(saved|noted|jotted|written|added|logged|set|"
                   r"started|sent|created|opened|put)\b|(?<!you )(?<!you've )\b(saved|noted|added) (it|that|this)\b|"
                   r"\btimer (is )?(set|running|started)\b|\bgot (it|that|this|those)( all)? (down|saved|noted)\b|"
                   r"\b(I'll|I will|I'm going to)\s+(remember|keep (it |that |this )?in mind)\b|\b(made|making) a note of\b|"
                   r"^\W*(saved|noted|added|logged|done)\b", re.IGNORECASE)
SENTENCE = re.compile(r"[^.!?]+[.!?]?")


def confirmations(text: str) -> int:
    """Sentences that say an action was done."""
    return sum(1 for s in SENTENCE.findall(text) if CLAIM.search(s))


def _arg_ok(spec: dict, args: dict) -> bool:
    return all(re.search(pattern, str(args.get(key, "")), re.IGNORECASE) for key, pattern in spec.get("args", {}).items())


def expected_calls(case: dict, config: str) -> list[dict] | None:
    """None means the case does not run under [config]."""
    expect = case["expect"]
    return expect.get(config) if isinstance(expect, dict) else expect


def score_case(case: dict, config: str, passes: list[Pass], calls: list[dict]) -> dict:
    expected = expected_calls(case, config) or []
    fresh = [c for c in calls if not c.get("repeat")]
    called = {c["name"] for c in fresh}
    wanted = {e["name"] for e in expected}
    raw, guarded = spoken_raw(passes), spoken_guarded(passes)
    failures: list[str] = []
    bad_args = []
    for spec in expected:
        mine = [c for c in fresh if c["name"] == spec["name"]]
        if mine and not any(_arg_ok(spec, c["args"]) for c in mine):
            bad_args.append(spec["name"])
            failures.append(f"bad args for {spec['name']}: {[c['args'] for c in mine]} wanted {spec.get('args')}")
    failures += [f"missing call: {n}" for n in sorted(wanted - called)]
    failures += [f"unwanted call: {n}" for n in sorted(called - wanted)]
    repeats = len(calls) - len({json.dumps([c['name'], c['args']], sort_keys=True) for c in calls})
    # "Save all three" may be one note or three different ones; the app saves each, so a case can allow that.
    same_name_twice = 0 if case.get("many_ok") else len(fresh) - len({c["name"] for c in fresh})
    if repeats or same_name_twice:
        failures.append(f"called {len(calls)} times")
    false_claim = not fresh and confirmations(raw) > 0
    if false_claim:
        failures.append("claims an action without a call")
    # Double confirmation only means something after a call that did something: a save, timer, email or route.
    acted = any(c["name"] == "save_note" or (c["name"] == "do_action" and c["args"].get("action") != "whats_on")
                for c in fresh)
    double_raw = acted and confirmations(raw) >= 2
    double_guarded = acted and confirmations(guarded) >= 2
    spoke_both_sides = acted and bool(passes[0].text.strip()) and any(p.text.strip() for p in passes[1:])
    if double_guarded:
        failures.append("confirms twice (guarded)")
    rubric = case.get("rubric_" + config, case.get("rubric", {}))
    failures += [f"missing any of: {', '.join(rubric['must_match_any'])}"] if rubric.get("must_match_any") and not any(
        re.search(p, guarded, re.IGNORECASE) for p in rubric["must_match_any"]) else []
    failures += [f"matched forbidden: {p}" for p in rubric.get("must_not_match", []) if re.search(p, guarded, re.IGNORECASE)]
    return {
        "expected": sorted(wanted), "called": sorted(called), "calls": calls,
        "tp": len(wanted & called), "fp": len(called - wanted), "fn": len(wanted - called),
        "wrong_tool": bool(wanted) and bool(called) and not (wanted & called),
        "bad_args": bool(bad_args), "repeat_call": bool(repeats or same_name_twice),
        "acted": acted, "false_claim": false_claim, "double_confirm_raw": double_raw, "double_confirm_guarded": double_guarded,
        "spoke_both_sides": spoke_both_sides, "raw": raw, "guarded": guarded,
        "passes": [{"text": p.text, "calls": p.calls, "first_s": p.first_s, "end_s": p.end_s} for p in passes],
        "failures": failures,
    }


def summarize(rows: list[dict]) -> dict:
    """Rates over all rows of one config and model."""
    def rate(flag, of):
        pool = [r for r in rows if of(r)]
        return {"n": len(pool), "rate": round(sum(1 for r in pool if r[flag]) / len(pool), 3) if pool else None}
    tp, fp, fn = (sum(r[k] for r in rows) for k in ("tp", "fp", "fn"))
    no_call = [r for r in rows if not r["expected"]]
    ttft = [r["passes"][0]["first_s"] for r in rows if r["passes"] and r["passes"][0]["first_s"] is not None]
    tps = [r["decode_tok_s"] for r in rows if r.get("decode_tok_s")]
    by_category: dict = {}
    for r in rows:
        c = by_category.setdefault(r["category"], [0, 0])
        c[0] += not r["failures"]
        c[1] += 1
    return {
        "rows": len(rows), "pass_rate": round(sum(not r["failures"] for r in rows) / len(rows), 3) if rows else None,
        "call_precision": round(tp / (tp + fp), 3) if tp + fp else None,
        "call_recall": round(tp / (tp + fn), 3) if tp + fn else None,
        "over_call": {"n": len(no_call), "rate": round(sum(1 for r in no_call if r["called"]) / len(no_call), 3) if no_call else None},
        "wrong_tool": rate("wrong_tool", lambda r: r["expected"]),
        "bad_args": rate("bad_args", lambda r: set(r["expected"]) & set(r["called"])),
        "repeat_call": rate("repeat_call", lambda r: r["called"]),
        "false_claim": rate("false_claim", lambda r: not r["called"]),
        "double_confirm_raw": rate("double_confirm_raw", lambda r: r["acted"]),
        "double_confirm_guarded": rate("double_confirm_guarded", lambda r: r["acted"]),
        "spoke_both_sides": rate("spoke_both_sides", lambda r: r["acted"]),
        "parse_error": {"n": len(rows), "count": sum(1 for r in rows if r.get("parse_error"))},
        "ttft_s_median": round(statistics.median(ttft), 3) if ttft else None,
        "decode_tok_s_median": round(statistics.median(tps), 2) if tps else None,
        "by_category": {k: f"{v[0]}/{v[1]}" for k, v in sorted(by_category.items())},
    }


def parse_error_row(case: dict, config: str, error: Exception, calls: list[dict]) -> dict:
    """LiteRT-LM raises RuntimeError when it cannot parse what the model wrote (a broken tool call, for one).
    That turn becomes a failed row, so one bad turn cannot end a run."""
    expected = expected_calls(case, config) or []
    return {"expected": sorted({e["name"] for e in expected}), "called": sorted({c["name"] for c in calls}),
            "calls": calls, "tp": 0, "fp": 0, "fn": len({e["name"] for e in expected}), "wrong_tool": False,
            "bad_args": False, "repeat_call": False, "acted": False, "false_claim": False,
            "double_confirm_raw": False, "double_confirm_guarded": False, "spoke_both_sides": False, "raw": "",
            "guarded": "", "passes": [], "parse_error": str(error)[:300], "failures": [f"parse_error: {str(error)[:200]}"]}


def decode_rate(passes: list[Pass], count_tokens: Callable[[str], int] | None) -> float | None:
    first = passes[0] if passes else None
    if count_tokens and first and first.text and first.first_s is not None and first.end_s > first.first_s:
        tokens = count_tokens(first.text)
        if tokens >= 8:
            return round(tokens / (first.end_s - first.first_s), 2)
    return None


def run_cases(cases: list[dict], config: str, open_send: Callable[[str, list, list[dict]], Callable], runs: int,
              count_tokens: Callable[[str], int] | None = None, on_row: Callable[[dict], None] | None = None) -> list[dict]:
    """[open_send](system, history, tool_specs, seed) opens a fresh conversation and returns its send function.
    [on_row] sees each row as soon as it is scored, so results are written as the run goes."""
    rows = []
    for case in cases:
        if expected_calls(case, config) is None:
            continue
        for run in range(1, runs + 1):
            box = ToolBox(config, case.get("notes", []), case.get("calendar"))
            row = {"id": case["id"], "category": case["category"], "config": config, "run": run, "user": case["user"]}
            try:
                # Seeded with the run number: LiteRT-LM samples seed 0 the same as seed 1.
                send = open_send(system_prompt(config, case.get("notes", [])), case.get("history", []),
                                 tool_specs(config), run)
                passes = run_turn(send, case["user"], box)
                row.update(score_case(case, config, passes, box.calls))
                rate = decode_rate(passes, count_tokens)
                if rate:
                    row["decode_tok_s"] = rate
            except RuntimeError as error:
                row.update(parse_error_row(case, config, error, box.calls))
            rows.append(row)
            if on_row:
                on_row(row)
    return rows


# ---- LiteRT-LM adapter ----------------------------------------------------------------------------------------

def litert_tools(specs: list[dict]) -> list:
    """LiteRT-LM tools that describe [specs] to the model. They never run: callers turn automatic calls off."""
    import litert_lm

    class SpecTool(litert_lm.Tool):
        def __init__(self, spec):
            self.spec = spec

        def get_tool_description(self):
            return self.spec

        def execute(self, param):
            raise RuntimeError("the eval runs tool calls itself")
    return [SpecTool(s) for s in specs]


def litert_parts(conversation, message, **kwargs) -> Iterator[tuple[str, list]]:
    """One streamed pass as (text_chunk, [(name, args)]) pieces."""
    import litert_lm
    for part in conversation.send_message_async(message, **kwargs):
        text = "".join(c.text for c in part.contents if isinstance(c, litert_lm.Content.Text))
        yield text, [(tc.name, tc.arguments or {}) for tc in part.tool_calls]


def litert_open_send(engine, constrained: bool = False):
    """Opens LiteRT-LM conversations that stream with automatic tool calling off, like the app's engine.

    [constrained] turns on LiteRT-LM's constrained decoding for conversations that have tools, as Google AI Edge
    Gallery does (ExperimentalFlags.enableConversationConstrainedDecoding in Kotlin): a tool call is decoded
    against the tool schemas, so it comes out well-formed. A conversation with no tools has nothing to constrain.
    """
    import litert_lm
    from sampler import SAMPLER
    # A streamed conversation holds itself in a reference cycle (its ctypes callback), so it is never freed on its
    # own. One is open at a time, and [open_send.close] closes the last, before the engine is closed.
    open_now = []

    def close():
        while open_now:
            open_now.pop().close()

    def open_send(system, history, specs, seed):
        close()
        extra = {"constrained_decoding_config": litert_lm.ConstrainedDecodingConfig(enable=True)} if constrained and specs else {}
        conversation = engine.create_conversation(
            messages=[{"role": t["role"], "content": t["text"]} for t in history], system_message=system,
            sampler_config=litert_lm.SamplerConfig(**SAMPLER, seed=seed), tools=litert_tools(specs),
            automatic_tool_calling=False, **extra)
        open_now.append(conversation)
        return lambda message: litert_parts(conversation, message)
    open_send.close = close
    return open_send


def report(summary: dict, rows: list[dict], label: str) -> str:
    lines = [f"## {label}", "", "```", json.dumps(summary, indent=2), "```", "", "Failures:"]
    for r in rows:
        if r["failures"]:
            lines.append(f"- {r['id']} run {r['run']}: {'; '.join(r['failures'])} | heard: {r['guarded'][:140]!r}")
    return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--config", choices=[*CONFIGS, "both"], default="both")
    parser.add_argument("--runs", type=int, default=1)
    parser.add_argument("--model", action="append", help="a .litertlm path; repeat for E2B and E4B (default: GEMMA_MODEL or model.DEFAULT_MODEL_PATH)")
    parser.add_argument("--only", help="run only cases whose id starts with this")
    parser.add_argument("--limit", type=int, help="run only the first N cases")
    parser.add_argument("--dry-run", action="store_true", help="print the prompts and tool schemas, run nothing")
    parser.add_argument("--endpoint", help="OpenAI-compatible base URL, or 'gemini'; runs there instead of LiteRT-LM")
    parser.add_argument("--endpoint-model", help="model name the endpoint expects, e.g. gemma-4-26b-a4b-it")
    parser.add_argument("--api-key-env", help="environment variable holding the endpoint's key (default: "
                                              "GEMINI_API_KEY for the Gemini API, none otherwise)")
    args = parser.parse_args()

    cases = json.loads(FIXTURE.read_text())
    cases = [c for c in cases if not args.only or c["id"].startswith(args.only)][: args.limit]
    configs = CONFIGS if args.config == "both" else (args.config,)
    if args.dry_run:
        for config in configs:
            print(f"=== {config} ===\n{system_prompt(config, ['example note'])}\n{json.dumps(tool_specs(config), indent=2)}")
            print(f"{sum(expected_calls(c, config) is not None for c in cases)} cases")
        return

    day = date.today().isoformat()
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    if args.endpoint:
        if not args.endpoint_model:
            raise SystemExit("--endpoint needs --endpoint-model")
        import hosted
        base = hosted.GEMINI_OPENAI_BASE if args.endpoint == "gemini" else args.endpoint
        open_send = hosted.openai_open_send(base, args.endpoint_model, args.api_key_env)
        write_runs(cases, configs, open_send, args.runs, "hosted-" + re.sub(r"[^A-Za-z0-9._-]+", "_", args.endpoint_model),
                   day, None, {"endpoint": base})
        return

    from model import DEFAULT_MODEL_PATH, load_engine
    import os
    paths = args.model or [os.environ.get("GEMMA_MODEL", DEFAULT_MODEL_PATH)]
    for path in paths:
        if not Path(path).is_file():
            raise SystemExit(f"model file not found: {path}")
        load_start = time.perf_counter()
        engine = load_engine(path)
        print(f"loaded {path} in {time.perf_counter() - load_start:.1f}s")
        open_send = litert_open_send(engine)
        write_runs(cases, configs, open_send, args.runs, Path(path).stem, day, lambda t: len(engine.tokenize(t)))
        open_send.close()
        engine.close()


def write_runs(cases: list[dict], configs, open_send, runs: int, stem: str, day: str,
               count_tokens: Callable[[str], int] | None = None, extra: dict | None = None) -> None:
    for config in configs:
        base = RESULTS_DIR / f"{day}-tool-eval-{config}-{stem}"
        with base.with_suffix(".jsonl").open("w") as out:
            def write(row):
                out.write(json.dumps(row) + "\n")
                out.flush()
            rows = run_cases(cases, config, open_send, runs, count_tokens, write)
        summary = {"model": stem, "config": config, "runs": runs, **(extra or {}), **summarize(rows)}
        base.with_suffix(".summary.json").write_text(json.dumps(summary, indent=2) + "\n")
        text = report(summary, rows, f"{stem} / {config}")
        base.with_suffix(".md").write_text(text)
        print(text)
        print(f"wrote {base}.jsonl, .summary.json, .md")


if __name__ == "__main__":
    main()
