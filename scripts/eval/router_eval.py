"""Compares the intent router (router.py) with the shipped app on the 48 tool-eval cases.

Setups, all on one warm Gemma engine with the same seeds:
  shipped         the app today: save_note always offered, notes in the prompt (tool_eval.py's shipped setup)
  gemma_alone     no router: Gemma sees save_note and do_action on every turn, scored like the routed setups
  gemma_alone_tuned   gemma_alone with a tool paragraph tuned to call less often on turns that need no tool
  rules           phrase rules only; near-misses count as NONE
  rules_e2b_gate  rules, then a short Gemma call labels near-misses (same loaded engine, separate conversation)
  rules_fg_gate   rules, then FunctionGemma 270M Mobile Actions labels near-misses (needs --functiongemma)
  embed_knn       embedding router (embed_router.py), nearest route examples with fitted thresholds; no rules
  embed_linear    embedding router, small linear head on the same embeddings with fitted thresholds; no rules
  embed_mlp       embedding router with the one-hidden-layer head (the app's router before mobilebert); no rules
  embed_rules     rules first, embed_mlp instead of a gate, and embed_mlp can veto a rule hit on a question
  mobilebert      the MobileBERT classifier router (mobilebert_router.py) instead of the embedding router; no rules
  <setup>_cd      the same setup with LiteRT-LM constrained decoding on for every reply conversation that has tools,
                  as Google AI Edge Gallery runs it. shipped_cd and rules_e2b_gate_cd run by default.
The embed setups need the MiniLM ONNX model (--embed-model-dir) and `python embed_router.py fit` run once.

How a routed turn answers:
  rule hit, save          the app saves the words after the phrase and says "Saved." No model call.
  rule hit, timer/email/route   the app runs do_action and says a fixed line. No model call.
  rule hit, calendar      the app reads the calendar, then Gemma answers from it with no tools.
  SAVE or ACTION from the gate or embedding, or "save that"   Gemma sees only that one tool.
  SAVE+ACTION from the embedding ("remember X and remind me in 10 minutes")   Gemma sees both tools.
  NONE                    Gemma sees no tool schemas at all.
read_notes is gone: the notes are in every prompt, as in the shipped app.

Per turn it logs router time, the reply's time to first token, their sum, and approximate prompt tokens per call.
LiteRT-LM parse errors become parse_error rows. Rows are written as they are scored.

Usage:
  .venv/bin/python scripts/eval/router_eval.py --runs 3 [--model E2B.litertlm] [--functiongemma FG.litertlm]
  python3 scripts/eval/router_eval.py --setups embed_mlp --endpoint URL --endpoint-model NAME   (hosted, see hosted.py)
      [--setups shipped,rules,rules_e2b_gate,rules_fg_gate,shipped_cd,rules_e2b_gate_cd,gemma_alone,
                gemma_alone_tuned,embed_knn,embed_linear,embed_mlp,embed_rules]
      [--embed-model-dir DIR] [--embed-model-file model.onnx] [--only ID_PREFIX] [--limit N] [--fixture PATH]

Results for one model, day and fixture share one summary: a later run with other --setups adds its setups to the
comparison table instead of replacing it. A fixture other than the default gets its own file names.
"""
import argparse
import json
import os
import statistics
import time
from collections import Counter
from datetime import date
from pathlib import Path
from typing import Callable

import notes
import router
import tool_eval as te

BASE_SETUPS = ("shipped", "rules", "rules_e2b_gate", "rules_fg_gate")
CONSTRAINED = "_cd"
ALONE_SETUPS = ("gemma_alone", "gemma_alone_tuned")
EMBED_SETUPS = ("embed_knn", "embed_linear", "embed_mlp", "embed_rules")
# embed_mlp for ACTION only: save_note is on every turn and Gemma decides whether the message asks for a save.
TOOL_ALWAYS = "tool_always"
ROUTED_EMBED_SETUPS = EMBED_SETUPS + (TOOL_ALWAYS,)
# The MobileBERT classifier router (mobilebert_router.py) in place of the embedding router, Gemma as in embed_mlp.
MOBILEBERT = "mobilebert"
VALID_SETUPS = (BASE_SETUPS + tuple(s + CONSTRAINED for s in BASE_SETUPS) + ALONE_SETUPS + ROUTED_EMBED_SETUPS
                + (MOBILEBERT,))
SETUPS = BASE_SETUPS + ("shipped_cd", "rules_e2b_gate_cd") + ALONE_SETUPS + EMBED_SETUPS  # the default run
TOOLS = {router.SAVE: "save_note", router.ACTION: "do_action"}
FG_SYSTEM = "You are a model that can do function calling with the following functions"
GATE_MAX_TOKENS = 2
FG_MAX_TOKENS = 64
SAVED = "Saved."
# tool_eval's PROPOSED_TOOLS_TEXT without read_notes: the notes are in the prompt.
ALONE_TOOLS_TEXT = (
    "You have two tools. save_note saves a note to the user's notes on this phone. do_action does one of four "
    "things: remind_me starts a timer that rings with a message, send_notes_to_me opens an email of their notes "
    "addressed to themselves for them to send, navigate_to opens directions in Maps, and whats_on reads their "
    "calendar for today or tomorrow. "
    "Use a tool only when this message asks for what it does. Call it once per request, before you say anything, "
    "then tell the user once, in a sentence, what you did. Most messages need no tool, just a spoken reply. "
    "Never say you saved, noted, set, sent or added anything unless you called the tool for it.\n"
)
# Leads with talk as the default and says when each tool applies. Tuned on tool-eval.json and tool-eval-agentdev.json.
ALONE_TUNED_TOOLS_TEXT = (
    "Most messages are conversation and need no tool: answer questions, give ideas, tell stories and chat with a "
    "spoken reply alone. Thanks and remarks about the user's day are conversation too. "
    "You have two tools. save_note saves a new note to the user's notes on this phone: use it only when they tell "
    "you to note, remember, write down, log or save something. do_action does one of four things: remind_me starts "
    "a timer that rings with a message, send_notes_to_me opens an email of their notes addressed to themselves for "
    "them to send, navigate_to opens directions in Maps, and whats_on reads their calendar for today or tomorrow. "
    "Never save a question, a request for ideas or a story, or something the user only mentions. save_note cannot "
    "look anything up: when the saved notes below answer a question, just say the answer and never save it again. "
    "When a tool is needed, call it once per request, before you say anything, then tell the user once, in a "
    "sentence, what you did. "
    "Never say you saved, noted, set, sent or added anything unless you called the tool for it.\n"
)
# routedSystemInstruction in SystemInstruction.kt: every Android prompt says the app saves notes, so a chat turn
# never denies the feature.
SAVES_NOTES = "This app can save notes to the user's Notes screen on this phone."
NO_TOOL_LINE = (SAVES_NOTES + " The app gives you that tool only on a message that asks for a save. This message does "
                "not, so you have no tools in this reply. When asked what you can do, say you can talk, answer questions "
                "and save notes. When asked whether you can save notes, say yes. Never say you saved, noted, set, sent or "
                "added anything. When you brainstorm or suggest ideas, give two or three in a few sentences and ask at most "
                "one question. When the user points back to something you said, like the second one or the flight one, "
                "build on that exact idea from your last reply.\n")
# Gemma 4 E4B only (SystemInstruction.kt adds it when the E4B file is loaded). E4B ran long after "shorter please,
# I'm driving" (0 of 3 under 45 words) and this fixed it, voice brainstorm 33 to 36 of 36; on E2B the same sentence
# cost a brainstorm follow-up and brought back a false "I saved that", so E2B keeps the line above unchanged.
SHORTER_ON_ASK = "When the user asks for shorter answers, answer in one or two sentences.\n"
# Gemma 4 E4B only, chat turns. Nick found the voice over polite. Filler (fluff.py: openers, apologies, praise, offers,
# "does that sound good?") over brainstorm, voice brainstorm and facts went 31 to 13 of 249 replies, mean words 30 to
# 25, confident wrong answers unchanged (25 of 87). On E2B the same line doubled confident wrong answers (13 to 26),
# as did every other no-filler or radio-dispatch line tried on either model in the intro, so E2B goes without it.
NO_FLATTERY = "Don't praise the user's ideas, and don't end by asking whether that sounds good."
# Gemma 4 E4B only, loop round 23: "not the router part, the one before it" stayed on the router idea (0 of 3).
REACH_BACK = ("When the user says the one before or the one after, count through the topics in the order you "
              "raised them and answer about that one.\n")
shorter_on_ask = False  # set from --endpoint-model


def is_e4b(model: str) -> None:
    """The app's rule: the shorter sentence when the loaded file is E4B."""
    global shorter_on_ask
    shorter_on_ask = "e4b" in model.lower()


def no_tool_line() -> str:
    if not shorter_on_ask:
        return NO_TOOL_LINE
    return NO_TOOL_LINE[:-1] + " " + NO_FLATTERY + " " + SHORTER_ON_ASK[:-1] + " " + REACH_BACK


# tool_always: save_note on every turn, so the same conversation serves chat and saves.
TOOL_ALWAYS_LINE = (
    "You have one tool, save_note. " + SAVES_NOTES + " Use it only when the user's message asks you to note, "
    "remember, write down, log or save something: call it once, before you say anything, then tell the user once, "
    "in a sentence, what you did. Most messages need no tool, just a spoken reply, and that includes questions "
    "about saving notes and remarks about notes. When asked whether you can save notes, say yes. Never say you "
    "saved, noted, set, sent or added anything unless you called the tool for it.\n")
ALSO_DO_ACTION = ("You also have do_action. The user's message asks for what it does: call it once, before you say "
                  "anything.\n")
# The tool paragraph of each no-router setup.
ALONE_TOOLS = {"gemma_alone": ALONE_TOOLS_TEXT, "gemma_alone_tuned": ALONE_TUNED_TOOLS_TEXT}


# ---- What each case expects once routed --------------------------------------------------------------------

def routed_case(case: dict) -> dict:
    """The three-tool expectations minus read_notes (notes are in the prompt), with the three-tool rubric."""
    expected = [e for e in te.expected_calls(case, "proposed") or [] if e["name"] != "read_notes"]
    return {**case, "expect": expected, "rubric": case.get("rubric_proposed", case.get("rubric", {}))}


def intent_of(names) -> str:
    names = set(names)
    labels = [label for label, tool in ((router.SAVE, "save_note"), (router.ACTION, "do_action")) if tool in names]
    return "+".join(labels) or router.NONE


def expected_intent(case: dict) -> str:
    return intent_of(e["name"] for e in routed_case(case)["expect"])


# ---- Prompts and fixed replies ------------------------------------------------------------------------------

def routed_prompt(saved: list[str], tool: str | None = None, calendar: str | None = None, alone: str | None = None,
                  always: bool = False) -> str:
    """The shipped prompt's wording, with the tool line swapped for this turn's one tool (or two) or none.

    [alone] is a no-router setup's tool paragraph: both tools described, and Gemma decides whether the message
    needs one."""
    if always:
        tools = TOOL_ALWAYS_LINE + (ALSO_DO_ACTION if "do_action" in (tool or "") else "")
    elif alone:
        tools = alone
    elif tool and "+" in tool:
        first, second = tool.split("+")
        tools = (f"You have two tools, {first} and {second}. The user's message asks for what both do: call each "
                 "once, before you say anything, then tell the user once, in a sentence, what you did. Never say you "
                 "did anything the tools did not do.\n")
    elif tool:
        saves = f"{SAVES_NOTES} " if tool == "save_note" else ""
        tools = (f"You have one tool, {tool}. {saves}The user's message asks for what it does: call it once, before "
                 "you say anything, then tell the user once, in a sentence, what you did. Never say you did anything "
                 "the tool did not do.\n")
    else:
        tools = no_tool_line()
    listed = "\n".join(f"- {n}" for n in saved[-notes_limit():]) or "No notes yet."
    looked_up = f"The app just looked up their calendar: {calendar}\nAnswer from it.\n" if calendar else ""
    return (notes.INTRO + tools + notes.NO_LIVE + te.PROPOSED_LIMITS + f"The user's saved notes:\n{listed}\n"
            + looked_up + "When the notes answer what the user asks, answer from them with the exact words and values. "
            + (RECAP_NOTES if saved else "")
            + "When the user asks what they saved about something no note mentions, say they have no note about it. "
            f"Answer everything else {notes.OWN_KNOWLEDGE} " + (HONEST if shorter_on_ask else PUSHBACK))


# Gemma 4 E4B only, facts eval (eval/fixtures/facts.json, 87 hard, pushback and false-premise answers): confidently
# wrong 46 to 25 with no new refusals of known facts, every other suite within noise. On E2B it cut wrong answers 22
# to 14 but made it refuse easy ones ("I am not sure who sang Thriller"), known/hold misses 8 to 13, so E2B goes
# without it. An earlier wording ("say you may have it wrong") was parroted back and made E4B cave when it was right.
# Mirrors HONEST in SystemInstruction.kt.
HONEST = ("Say a name, date or number only when you are sure of it. If you are not sure, say you are not sure, or give "
          "your best guess as a guess. When the user says you are wrong, think again: keep your answer if you are sure "
          "of it, and otherwise say you are not sure. Never make up details about a person or thing you do not know.")
# Gemma 4 E2B only, in place of HONEST: facts eval confidently wrong 22 to 13 of 87, known/hold misses 8 to 9, every
# other suite within one case. Two wordings that also asked it to say "not sure" about names refused more easy ones
# (misses 12 and 16). Mirrors PUSHBACK in SystemInstruction.kt.
PUSHBACK = ("When the user says you are wrong, do not just agree: keep your answer if you know it, and otherwise say you "
            "are not sure. Never make up details about a person or thing you do not know.")
# Only with notes: demo recall 4 to 10 of 10 (E4B), 3 to 10 (E2B). Mirrors RECAP_NOTES in SystemInstruction.kt.
RECAP_NOTES = "When the user asks for a recap or what you came up with, include any note about it. "


def notes_limit() -> int:
    return 40  # MAX_NOTES_IN_INSTRUCTION in SystemInstruction.kt


def fixed_reply(args: dict, result: str) -> str | None:
    """What the app says itself after running a rule's tool. None for the calendar, which Gemma reads out."""
    if "text" in args:
        return SAVED
    action = args.get("action")
    if action == "remind_me":
        return result.split(":")[0].split(". ")[0] + "." if result.startswith("Timer set") else "How many minutes?"
    if action == "send_notes_to_me":
        return "Your notes are in an email to you, ready to send."
    if action == "navigate_to":
        return result.split(". Now")[0]
    return None


# ---- Gates --------------------------------------------------------------------------------------------------

def greedy(seed: int):
    import litert_lm
    return litert_lm.SamplerConfig(top_k=1, top_p=1.0, temperature=1.0, seed=seed)


def gemma_gate(engine) -> Callable[[str, int], dict]:
    """A separate short conversation on the loaded Gemma engine. Reads only the first word of its reply."""
    def ask(transcript: str, seed: int) -> dict:
        # Closed at once: a streamed conversation is never freed on its own (see te.litert_open_send).
        with engine.create_conversation(system_message=router.GATE_SYSTEM, sampler_config=greedy(seed)) as conversation:
            raw = "".join(text for text, _ in te.litert_parts(
                conversation, {"role": "user", "content": router.gate_user(transcript)},
                max_output_tokens=GATE_MAX_TOKENS))
        return {"label": router.parse_label(raw), "raw": raw,
                "prompt_tokens": len(engine.tokenize(router.GATE_SYSTEM + router.gate_user(transcript)))}
    return ask


def functiongemma_gate(engine) -> Callable[[str, int], dict]:
    """FunctionGemma sees save_note and do_action. A call to save_note is SAVE, any other call ACTION, text NONE."""
    specs = [te.SAVE_NOTE, te.DO_ACTION]

    def ask(transcript: str, seed: int) -> dict:
        text, calls = "", []
        with engine.create_conversation(system_message=FG_SYSTEM, sampler_config=greedy(seed),
                                        tools=te.litert_tools(specs), automatic_tool_calling=False) as conversation:
            for chunk, part_calls in te.litert_parts(conversation, transcript, max_output_tokens=FG_MAX_TOKENS):
                text += chunk
                calls += part_calls
        names = [name for name, _ in calls]
        label = router.SAVE if "save_note" in names else router.ACTION if names else router.NONE
        return {"label": label, "raw": text.strip()[:120] or json.dumps(calls)[:120],
                "prompt_tokens": len(engine.tokenize(FG_SYSTEM + json.dumps(specs) + transcript))}
    return ask


# ---- One turn -----------------------------------------------------------------------------------------------

def approx_tokens(count_tokens, system: str, specs: list, case: dict) -> int | None:
    if not count_tokens:
        return None
    history = " ".join(t["text"] for t in case.get("history", []))
    return count_tokens(system + (json.dumps(specs) if specs else "") + history + case["user"])


def app_tool(tool: str | None) -> str | None:
    """The tools the shipped app gives Gemma for a routed intent: save_note or nothing (TurnRouting.turnToolsFor).

    The app has no do_action, so an ACTION turn is plain chat and SAVE+ACTION keeps only save_note.
    """
    return "save_note" if tool and "save_note" in tool.split("+") else None


def app_cases(cases: list[dict]) -> list[dict]:
    """Cases the shipped app can pass: none that expects do_action, a tool it does not have."""
    return [c for c in cases if "do_action" not in {e["name"] for e in routed_case(c)["expect"]}]


def run_routed(case: dict, setup: str, run: int, open_send, gate, count_tokens=None,
               clock: Callable[[], float] = time.perf_counter, route_fn=None, app_tools: bool = False) -> dict:
    """[route_fn](transcript) -> (router.Route, info) replaces router.route and its gate (the embed setups).

    [app_tools]: give Gemma only the tools the shipped app has for the route (app_tool).
    """
    seed = run  # LiteRT-LM samples seed 0 the same as seed 1
    rc = routed_case(case)
    box = te.ToolBox("routed", case.get("notes", []), case.get("calendar"))
    row = {"id": case["id"], "category": case["category"], "config": setup, "run": run, "user": case["user"],
           "expected_intent": expected_intent(case)}
    gate_info: dict = {}

    def ask_gate(transcript: str) -> str:
        try:
            gate_info.update(gate(transcript, seed))
        except RuntimeError as error:  # a gate that writes something LiteRT-LM cannot parse counts as NONE
            gate_info.update(label=router.NONE, raw="", parse_error=str(error)[:200])
        return gate_info["label"]

    start = clock()
    if route_fn:
        decision, embed_info = route_fn(case["user"])
    else:
        decision, embed_info = router.route(case["user"], ask_gate if gate else None), None
    router_s = clock() - start
    row.update(route_intent=decision.intent, route_source=decision.source, router_s=round(router_s, 4),
               gate=gate_info or None)
    if embed_info:
        row["embed"] = embed_info
    try:
        if decision.source == "rule" and not (decision.args or {}).get("action") == "whats_on":
            result = box.run(decision.tool, decision.args)
            passes = [te.Pass(text=fixed_reply(decision.args, result), first_s=0.0, end_s=0.0)]
            row["prompt_tokens"] = 0
        else:
            calendar = box.run(decision.tool, decision.args) if decision.source == "rule" else None
            tool = "+".join(TOOLS[i] for i in decision.intent.split("+") if i in TOOLS) or None
            tool = None if calendar else tool
            if app_tools:
                tool = app_tool(tool)
            if setup == TOOL_ALWAYS:
                tool = "+".join(["save_note"] + [t for t in (tool or "").split("+") if t == "do_action"])
            specs = [s for s in te.tool_specs("routed") if s["function"]["name"] in (tool or "").split("+")]
            system = routed_prompt(case.get("notes", []), tool, calendar, always=setup == TOOL_ALWAYS)
            row["prompt_tokens"] = approx_tokens(count_tokens, system, specs, case)
            passes = te.run_turn(open_send(system, case.get("history", []), specs, seed), case["user"], box, clock)
        row.update(te.score_case(rc, "routed", passes, box.calls))
        rate = te.decode_rate(passes, count_tokens)
        if rate:
            row["decode_tok_s"] = rate
    except RuntimeError as error:
        row.update(te.parse_error_row(rc, "routed", error, box.calls))
    if setup == TOOL_ALWAYS:  # Gemma made the save decision, so the intent is what it called
        row.update(router_intent=decision.intent, route_intent=intent_of(row["called"]))
    return timed(row, router_s)


def base_setup(setup: str) -> str:
    """shipped_cd -> shipped: the setup without constrained decoding."""
    return setup.removesuffix(CONSTRAINED)


def is_constrained(setup: str) -> bool:
    return setup.endswith(CONSTRAINED)


def run_shipped(case: dict, run: int, open_send, count_tokens=None, clock=time.perf_counter,
                config: str = "shipped") -> dict:
    box = te.ToolBox("shipped", case.get("notes", []), case.get("calendar"))
    row = {"id": case["id"], "category": case["category"], "config": config, "run": run, "user": case["user"],
           "expected_intent": expected_intent(case), "route_source": "none", "router_s": 0.0}
    system = te.system_prompt("shipped", case.get("notes", []))
    row["prompt_tokens"] = approx_tokens(count_tokens, system, te.tool_specs("shipped"), case)
    try:
        passes = te.run_turn(open_send(system, case.get("history", []), te.tool_specs("shipped"), run),
                             case["user"], box, clock)
        row.update(te.score_case(case, "shipped", passes, box.calls))
        rate = te.decode_rate(passes, count_tokens)
        if rate:
            row["decode_tok_s"] = rate
    except RuntimeError as error:
        row.update(te.parse_error_row(case, "shipped", error, box.calls))
    row["route_intent"] = intent_of(row["called"])
    return timed(row, 0.0)


def run_alone(case: dict, setup: str, run: int, open_send, count_tokens=None, clock=time.perf_counter) -> dict:
    """Gemma with both routed tools on every turn and no router, scored against the routed expectations."""
    rc = routed_case(case)
    box = te.ToolBox("routed", case.get("notes", []), case.get("calendar"))
    row = {"id": case["id"], "category": case["category"], "config": setup, "run": run, "user": case["user"],
           "expected_intent": expected_intent(case), "route_source": "none", "router_s": 0.0}
    system, specs = routed_prompt(case.get("notes", []), alone=ALONE_TOOLS[setup]), te.tool_specs("routed")
    row["prompt_tokens"] = approx_tokens(count_tokens, system, specs, case)
    try:
        passes = te.run_turn(open_send(system, case.get("history", []), specs, run), case["user"], box, clock)
        row.update(te.score_case(rc, "routed", passes, box.calls))
        rate = te.decode_rate(passes, count_tokens)
        if rate:
            row["decode_tok_s"] = rate
    except RuntimeError as error:
        row.update(te.parse_error_row(rc, "routed", error, box.calls))
    row["route_intent"] = intent_of(row["called"])
    return timed(row, 0.0)


def timed(row: dict, router_s: float) -> dict:
    first = row["passes"][0]["first_s"] if row.get("passes") else None
    row["response_ttft_s"] = round(first, 4) if first is not None else None
    row["total_s"] = round(router_s + first, 4) if first is not None else None
    row["tool_turn"] = row.get("route_intent", router.NONE) != router.NONE
    return row


# ---- Summary ------------------------------------------------------------------------------------------------

def _median(values):
    values = [v for v in values if v is not None]
    return round(statistics.median(values), 4) if values else None


RUBRIC_FAILURES = ("missing any of", "matched forbidden")


def save_decision(rows: list[dict]) -> dict:
    """eval/fixtures/save-decision.json's three numbers: saves nobody asked for, asked-for saves not made, and
    capability questions answered honestly (the rubric passed, so no denial that the app saves notes)."""
    def count(pool, flag):
        return {"n": len(pool), "count": sum(1 for r in pool if flag(r))}
    no_save = [r for r in rows if "save_note" not in r["expected"]]
    saves = [r for r in rows if "save_note" in r["expected"]]
    capability = [r for r in rows if r["category"] == "capability"]
    return {"false_saves": count(no_save, lambda r: "save_note" in r["called"]),
            "missed_saves": count(saves, lambda r: "save_note" not in r["called"]),
            "capability_honest": count(capability, lambda r: not r.get("parse_error") and not any(
                f.startswith(RUBRIC_FAILURES) for f in r["failures"]))}


def summarize(rows: list[dict]) -> dict:
    base = te.summarize(rows)
    table: dict = {}
    for r in rows:
        table.setdefault(r["expected_intent"], Counter())[r.get("route_intent", router.NONE)] += 1
    timing = {}
    for name, pool in (("tool_turns", [r for r in rows if r["tool_turn"]]), ("no_tool_turns", [r for r in rows if not r["tool_turn"]])):
        timing[name] = {"n": len(pool), "router_s_median": _median(r["router_s"] for r in pool),
                        "response_ttft_s_median": _median(r["response_ttft_s"] for r in pool),
                        "total_s_median": _median(r["total_s"] for r in pool),
                        "prompt_tokens_median": _median(r.get("prompt_tokens") for r in pool)}
    gated = [r for r in rows if r.get("gate")]
    return {**base, "intent_table": {k: dict(v) for k, v in sorted(table.items())},
            "intent_match": round(sum(r["expected_intent"] == r.get("route_intent") for r in rows) / len(rows), 3) if rows else None,
            "route_sources": dict(Counter(r.get("route_source") for r in rows)),
            "gate": {"calls": len(gated), "parse_errors": sum(1 for r in gated if r["gate"].get("parse_error")),
                     "prompt_tokens_median": _median(r["gate"].get("prompt_tokens") for r in gated),
                     "router_s_median": _median(r["router_s"] for r in gated)},
            "timing": timing, "save_decision": save_decision(rows)}


def compare_table(summaries: dict) -> str:
    head = ("| Setup | Pass | Over-call | Precision | Recall | Intent match | False claim | Double (raw) | Parse errors "
            "| TTFT tool / no-tool | Total tool / no-tool | Gate calls |")
    lines = [head, "|" + "---|" * 12]
    for setup, s in summaries.items():
        t = s["timing"]
        lines.append(f"| {setup} | {s['pass_rate']} | {s['over_call']['rate']} | {s['call_precision']} | {s['call_recall']} "
                     f"| {s['intent_match']} | {s['false_claim']['rate']} | {s['double_confirm_raw']['rate']} "
                     f"| {s['parse_error']['count']} | {t['tool_turns']['response_ttft_s_median']} / "
                     f"{t['no_tool_turns']['response_ttft_s_median']} | {t['tool_turns']['total_s_median']} / "
                     f"{t['no_tool_turns']['total_s_median']} | {s['gate']['calls']} |")
    return "\n".join(lines) + "\n"


def embed_route_fn(setup: str, routers: dict):
    """(transcript) -> (router.Route, info) for an embed setup. info logs the route scores on the row."""
    import embed_router as er
    embedder = routers[er.APP_SCORER if setup in ("embed_rules", TOOL_ALWAYS) else setup.removeprefix("embed_")]

    def route(transcript: str):
        d = embedder.decide(transcript)
        decision = er.route_with_rules(embedder, transcript, d) if setup == "embed_rules" else er.embed_route(d)
        return decision, {"intent": d.intent, "scores": d.scores, "embed_s": round(d.seconds, 4)}
    return route


def mobilebert_route_fn():
    """(transcript) -> (router.Route, info) for the MobileBERT classifier router. info logs its four logits."""
    import embed_router as er
    import mobilebert_router
    classifier = mobilebert_router.MobileBertRouter()

    def route(transcript: str):
        d = classifier.decide(transcript)
        return er.embed_route(d), {"intent": d.intent, "scores": d.scores, "embed_s": round(d.seconds, 4)}
    return route


def output_base(out_dir: Path, day: str, stem: str, fixture: str) -> Path:
    """The default fixture keeps the original names; another fixture gets its stem added, so it cannot overwrite."""
    name = f"{day}-router-eval-{stem}"
    if Path(fixture).resolve() != te.FIXTURE.resolve():
        name += f"-{Path(fixture).stem}"
    return out_dir / name


def load_summaries(path: Path) -> dict:
    """Setups an earlier run wrote for the same model, day and fixture, so a later run adds to its table."""
    try:
        return json.loads(path.read_text())
    except (FileNotFoundError, json.JSONDecodeError):
        return {}


def run_setup(setup: str, cases: list[dict], runs: int, open_send, gate, count_tokens, on_row,
              route_fn=None, app_tools: bool = False) -> list[dict]:
    rows = []
    for case in cases:
        for run in range(1, runs + 1):
            row = (run_shipped(case, run, open_send, count_tokens, config=setup) if base_setup(setup) == "shipped"
                   else run_alone(case, setup, run, open_send, count_tokens) if setup in ALONE_TOOLS
                   else run_routed(case, setup, run, open_send, gate, count_tokens, route_fn=route_fn,
                                   app_tools=app_tools))
            rows.append(row)
            on_row(row)
    return rows


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--model", help="Gemma .litertlm (default: GEMMA_MODEL or model.DEFAULT_MODEL_PATH)")
    parser.add_argument("--functiongemma", help="FunctionGemma 270M Mobile Actions .litertlm, for rules_fg_gate")
    parser.add_argument("--setups", default=",".join(SETUPS))
    parser.add_argument("--embed-model-dir", help="all-MiniLM-L6-v2 ONNX folder (default: EMBED_MODEL_DIR or "
                                                  "~/models/all-MiniLM-L6-v2-router)")
    parser.add_argument("--embed-model-file", default="model.onnx", help="model.onnx or model_int8.onnx")
    parser.add_argument("--runs", type=int, default=3)
    parser.add_argument("--fixture", default=str(te.FIXTURE))
    parser.add_argument("--tag", default="", help="added to the output file names, so runs on one day don't overwrite")
    parser.add_argument("--only")
    parser.add_argument("--limit", type=int)
    parser.add_argument("--app-tools", action="store_true",
                        help="give Gemma only the shipped app's tools: save_note on a SAVE route, none otherwise, and "
                             "skip cases that expect do_action")
    parser.add_argument("--endpoint", help="OpenAI-compatible base URL, or 'gemini'; Gemma runs there instead of "
                                           "LiteRT-LM (see hosted.py). Rules and embed setups only")
    parser.add_argument("--endpoint-model", help="model name the endpoint expects")
    parser.add_argument("--api-key-env", help="environment variable holding the endpoint's key")
    args = parser.parse_args()

    setups = [s for s in args.setups.split(",") if s]
    unknown = set(setups) - set(VALID_SETUPS)
    if unknown:
        parser.error(f"unknown setups: {', '.join(sorted(unknown))}")
    for setup in [s for s in setups if base_setup(s) == "rules_fg_gate"]:
        if not args.functiongemma:
            print(f"skipping {setup}: pass --functiongemma PATH to run it")
            setups.remove(setup)
    needs_fg = any(base_setup(s) == "rules_fg_gate" for s in setups)
    cases = [c for c in json.loads(Path(args.fixture).read_text()) if not args.only or c["id"].startswith(args.only)]
    cases = cases[: args.limit]
    if args.app_tools:
        cases = app_cases(cases)

    if args.endpoint:
        litert_only = [s for s in setups if is_constrained(s) or base_setup(s) in ("rules_e2b_gate", "rules_fg_gate")]
        if litert_only:
            parser.error(f"--endpoint cannot run {', '.join(litert_only)}: they need LiteRT-LM")
        if not args.endpoint_model:
            parser.error("--endpoint needs --endpoint-model")
        is_e4b(args.endpoint_model)
    else:
        import litert_lm
        from model import DEFAULT_MODEL_PATH, load_engine
        path = args.model or os.environ.get("GEMMA_MODEL", DEFAULT_MODEL_PATH)
        is_e4b(path)
        for p in [path] + ([args.functiongemma] if needs_fg else []):
            if not Path(p).is_file():
                raise SystemExit(f"model file not found: {p}")
    route_fns = {}
    if any(s in ROUTED_EMBED_SETUPS for s in setups):  # before Gemma loads, so a missing model or config fails fast
        import embed_router as er
        model_dir = Path(args.embed_model_dir or er.DEFAULT_MODEL_DIR).expanduser()
        encoder = er.OnnxEncoder(model_dir, args.embed_model_file)
        routers = {s: er.load_router(model_dir, args.embed_model_file, s, encoder) for s in er.SCORERS}
        route_fns = {s: embed_route_fn(s, routers) for s in setups if s in ROUTED_EMBED_SETUPS}
    if MOBILEBERT in setups:
        route_fns[MOBILEBERT] = mobilebert_route_fn()
    for fn in route_fns.values():
        fn("hello")  # warm-up, untimed
    if args.endpoint:
        import hosted
        open_send = open_send_cd = hosted.openai_open_send(
            hosted.GEMINI_OPENAI_BASE if args.endpoint == "gemini" else args.endpoint, args.endpoint_model,
            args.api_key_env)
        count_tokens = None
        gates: dict = {"rules": None}
        stem = "hosted-" + "".join(c if c.isalnum() or c in "._-" else "_" for c in args.endpoint_model)
    else:
        engine = load_engine(path)
        open_send = te.litert_open_send(engine)
        open_send_cd = te.litert_open_send(engine, constrained=True)
        count_tokens = lambda t: len(engine.tokenize(t))  # noqa: E731
        gates = {"rules": None, "rules_e2b_gate": gemma_gate(engine)}
        if needs_fg:
            fg_engine = litert_lm.Engine(args.functiongemma, backend=litert_lm.Backend.CPU())
            gates["rules_fg_gate"] = functiongemma_gate(fg_engine)
            gates["rules_fg_gate"]("hello", 0)  # warm-up, untimed
        stem = Path(path).stem
    # Warm-up, untimed: the first call on a fresh engine is slower than the rest.
    list(open_send(te.system_prompt("shipped", []), [], te.tool_specs("shipped"), 0)(
        {"role": "user", "content": [{"type": "text", "text": "Hi"}]}))
    if "rules_e2b_gate" in gates:
        gates["rules_e2b_gate"]("hello", 0)
    if not args.endpoint and any(is_constrained(s) for s in setups):
        list(open_send_cd(te.system_prompt("shipped", []), [], te.tool_specs("shipped"), 0)(
            {"role": "user", "content": [{"type": "text", "text": "Hi"}]}))

    out_dir = te.RESULTS_DIR
    out_dir.mkdir(parents=True, exist_ok=True)
    base = output_base(out_dir, date.today().isoformat(), f"{args.tag}-{stem}" if args.tag else stem, args.fixture)
    summaries = load_summaries(Path(f"{base}.summary.json"))
    for setup in setups:
        with open(f"{base}-{setup}.jsonl", "w") as out:
            def write(row):
                out.write(json.dumps(row) + "\n")
                out.flush()
            rows = run_setup(setup, cases, args.runs, open_send_cd if is_constrained(setup) else open_send,
                             gates.get(base_setup(setup)), count_tokens, write, route_fns.get(setup), args.app_tools)
        summaries[setup] = {"model": stem, "setup": setup, "runs": args.runs, "constrained": is_constrained(setup),
                            "app_tools": args.app_tools,
                            **summarize(rows)}
        print(f"{setup}: pass {summaries[setup]['pass_rate']}, over-call {summaries[setup]['over_call']['rate']}")
        Path(f"{base}.summary.json").write_text(json.dumps(summaries, indent=2) + "\n")
    report = compare_table(summaries) + "\n" + "".join(
        te.report(s, [json.loads(line) for line in open(f"{base}-{k}.jsonl")], f"{stem} / {k}") + "\n"
        for k, s in summaries.items() if Path(f"{base}-{k}.jsonl").is_file())
    Path(f"{base}.md").write_text(report)
    print(compare_table(summaries))
    print(f"wrote {base}-*.jsonl, {base}.summary.json, {base}.md")
    if not args.endpoint:
        open_send.close()
        open_send_cd.close()
        engine.close()
        if needs_fg:
            fg_engine.close()


if __name__ == "__main__":
    main()
