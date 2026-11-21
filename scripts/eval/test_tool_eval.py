# test-import-check: skip (tool_eval is scripts/eval/tool_eval.py, imported from the pytest rootdir like the sibling tests)
import json
import re
from pathlib import Path

import notes
import tool_eval as te

KOTLIN = Path(__file__).parents[2] / "core/src"
SAVE_NOTE_KT = KOTLIN / "commonMain/kotlin/com/chrishonson/gemmavoice/notes/SaveNote.kt"
TOOL_TURN_KT = KOTLIN / "commonMain/kotlin/com/chrishonson/gemmavoice/llm/ToolTurn.kt"
ENGINE_KT = KOTLIN / "androidMain/kotlin/com/chrishonson/gemmavoice/llm/LiteRtLlmEngine.kt"


def kotlin_const(path, name):
    body = path.read_text().split(f"const val {name} =")[1].split("const val")[0].split("\n\n")[0]
    strings = re.findall(r'"([^"]*)"', body)
    return "".join(strings) if strings else body.strip()


def scripted(*passes):
    """A model that streams the given passes in order. Each pass is a list of (text_chunk, calls) pieces."""
    queue = list(passes)
    sent = []

    def send(message):
        sent.append(message)
        yield from queue.pop(0) if queue else []
    send.sent = sent
    return send


def ticking():
    t = [0.0]

    def clock():
        t[0] += 0.5
        return t[0]
    return clock


SAVE_CASE = {"id": "s", "category": "save", "user": "Remember the vet said dental", "expect": [
    {"name": "save_note", "args": {"text": "dental"}}]}
CHAT_CASE = {"id": "c", "category": "no_call", "user": "hi", "expect": []}


def turn(case, config, *passes):
    box = te.ToolBox(config, case.get("notes", []))
    result = te.run_turn(scripted(*passes), case["user"], box, ticking())
    return te.score_case(case, config, result, box.calls), box


def test_mirrors_the_apps_tool_text_and_turn_limits():
    assert te.SAVE_NOTE_DESCRIPTION == kotlin_const(SAVE_NOTE_KT, "SAVE_NOTE_DESCRIPTION")
    assert te.SAVE_NOTE_TEXT_DESCRIPTION == kotlin_const(SAVE_NOTE_KT, "SAVE_NOTE_TEXT_DESCRIPTION")
    assert te.SAVE_NOTE_RESULT in SAVE_NOTE_KT.read_text()
    assert str(te.MAX_TOOL_ROUNDS) == kotlin_const(TOOL_TURN_KT, "MAX_TOOL_ROUNDS")
    assert te.REPEATED_TOOL_CALL_RESULT == kotlin_const(TOOL_TURN_KT, "REPEATED_TOOL_CALL_RESULT")
    assert te.SAVED_WHEN_SILENT == kotlin_const(ENGINE_KT, "SAVED_WHEN_SILENT")


def test_shipped_config_uses_the_apps_prompt_and_one_tool():
    assert te.system_prompt("shipped", ["a"]) == notes.system_prompt(["a"])
    assert [s["function"]["name"] for s in te.tool_specs("shipped")] == ["save_note"]


def test_proposed_config_offers_three_tools_and_drops_the_listed_notes():
    prompt = te.system_prompt("proposed", ["secret note"])
    assert "secret note" not in prompt and "read_notes" in prompt
    assert "cannot set reminders, alarms or timers" not in prompt
    assert [s["function"]["name"] for s in te.tool_specs("proposed")] == ["save_note", "read_notes", "do_action"]
    assert te.DO_ACTION["function"]["parameters"]["properties"]["action"]["enum"] == te.ACTIONS


def test_text_on_both_sides_of_a_call_is_a_double_confirmation_main_speaks_and_toolturn_does_not():
    score, box = turn(SAVE_CASE, "shipped",
                      [("Sure, I've saved that.", []), ("", [("save_note", {"text": "vet: dental cleaning"})])],
                      [("I saved your note about the dental cleaning.", [])])
    assert box.saved == ["vet: dental cleaning"]
    assert score["double_confirm_raw"] and score["spoke_both_sides"]
    assert not score["double_confirm_guarded"]
    assert score["guarded"] == "Sure, I've saved that."
    assert score["failures"] == []


def test_a_silent_call_is_covered_by_the_apps_fixed_line():
    score, _ = turn(SAVE_CASE, "shipped", [("", [("save_note", {"text": "dental"})])], [("", [])])
    assert score["guarded"] == te.SAVED_WHEN_SILENT
    assert not score["double_confirm_raw"]


def test_one_confirmation_after_the_call_passes():
    score, _ = turn(SAVE_CASE, "shipped", [("", [("save_note", {"text": "dental"})])], [("Saved: dental cleaning for Max.", [])])
    assert score["tp"] == 1 and score["failures"] == []
    assert te.confirmations(score["raw"]) == 1


def test_a_repeated_identical_call_gets_the_already_done_result_and_fails():
    send = scripted([("", [("save_note", {"text": "dental"})])], [("", [("save_note", {"text": "dental"})])], [("Done.", [])])
    box = te.ToolBox("shipped", [])
    passes = te.run_turn(send, "u", box, ticking())
    assert box.saved == ["dental"]
    assert send.sent[2]["content"][0]["response"] == te.REPEATED_TOOL_CALL_RESULT
    score = te.score_case(SAVE_CASE, "shipped", passes, box.calls)
    assert score["repeat_call"] and "called 2 times" in score["failures"]


def test_many_ok_lets_different_calls_to_one_tool_pass_but_not_identical_ones():
    case = dict(SAVE_CASE, many_ok=True)
    box = te.ToolBox("shipped", [])
    passes = te.run_turn(scripted([("", [("save_note", {"text": "dental a"}), ("save_note", {"text": "dental b"})])],
                                  [("Saved both.", [])]), "u", box, ticking())
    assert not te.score_case(case, "shipped", passes, box.calls)["repeat_call"]
    assert te.score_case(SAVE_CASE, "shipped", passes, box.calls)["repeat_call"]
    box = te.ToolBox("shipped", [])
    passes = te.run_turn(scripted([("", [("save_note", {"text": "dental"})])], [("", [("save_note", {"text": "dental"})])],
                                  [("Done.", [])]), "u", box, ticking())
    assert te.score_case(case, "shipped", passes, box.calls)["repeat_call"]


def test_tool_rounds_stop_at_the_apps_cap():
    looping = [[("", [("save_note", {"text": str(i)})])] for i in range(10)]
    passes = te.run_turn(scripted(*looping), "u", te.ToolBox("shipped", []), ticking())
    assert len(passes) == te.MAX_TOOL_ROUNDS + 1


def test_over_call_false_claim_wrong_tool_and_bad_args_are_each_caught():
    over, _ = turn(CHAT_CASE, "shipped", [("", [("save_note", {"text": "hi"})])], [("Saved.", [])])
    assert over["fp"] == 1 and "unwanted call: save_note" in over["failures"]

    claim, _ = turn(CHAT_CASE, "shipped", [("Hi! I've noted that you said hello.", [])])
    assert claim["false_claim"] and "claims an action without a call" in claim["failures"]

    timer = {"id": "t", "category": "action", "user": "u",
             "expect": {"shipped": [], "proposed": [{"name": "do_action", "args": {"action": "^remind_me$", "arg": "20"}}]}}
    wrong, _ = turn(timer, "proposed", [("", [("save_note", {"text": "check oven in 20"})])], [("Saved.", [])])
    assert wrong["wrong_tool"]
    bad, _ = turn(timer, "proposed", [("", [("do_action", {"action": "remind_me", "arg": "5 min: oven"})])], [("Timer set.", [])])
    assert bad["bad_args"] and not bad["wrong_tool"]
    good, _ = turn(timer, "proposed", [("", [("do_action", {"action": "remind_me", "arg": "20 min: oven"})])], [("Your 20 minute timer is set.", [])])
    assert good["failures"] == []


def test_plain_answers_with_no_call_pass():
    score, _ = turn(CHAT_CASE, "shipped", [("Hey! Doing well. What's on your mind?", [])])
    assert score["failures"] == [] and not score["false_claim"]


def test_toolbox_fakes_the_phone():
    box = te.ToolBox("proposed", ["guest wifi password is sunflower42", "brake pads 3 mm"])
    assert "sunflower42" in box.run("read_notes", {"query": "wifi password"})
    assert "No note matched" in box.run("read_notes", {"query": "boat"})
    assert "20 minutes: oven" in box.run("do_action", {"action": "remind_me", "arg": "20 min: oven"})
    assert "60 minutes" in box.run("do_action", {"action": "remind_me", "arg": "1 hour: car"})
    assert "Ask the person how long" in box.run("do_action", {"action": "remind_me", "arg": "soon"})
    assert "dentist" in box.run("do_action", {"action": "whats_on", "arg": "today"})
    assert "no action called" in box.run("do_action", {"action": "set_alarm", "arg": "6"})
    assert "no tool called" in te.ToolBox("shipped", []).run("do_action", {"action": "whats_on", "arg": "today"})


def test_summary_rates():
    rows = [dict(turn(SAVE_CASE, "shipped", [("Saved!", [("save_note", {"text": "dental"})])], [("I saved it.", [])])[0],
                 category="save", decode_tok_s=10.0),
            dict(turn(CHAT_CASE, "shipped", [("", [("save_note", {"text": "hi"})])], [("Ok.", [])])[0], category="no_call"),
            dict(turn(CHAT_CASE, "shipped", [("Hello!", [])])[0], category="no_call")]
    s = te.summarize(rows)
    assert s["call_precision"] == 0.5 and s["call_recall"] == 1.0
    assert s["over_call"] == {"n": 2, "rate": 0.5}
    assert s["double_confirm_raw"] == {"n": 2, "rate": 0.5}
    assert s["double_confirm_guarded"]["rate"] == 0.0
    assert s["decode_tok_s_median"] == 10.0 and s["ttft_s_median"] is not None
    assert s["by_category"] == {"no_call": "1/2", "save": "1/1"}


def test_run_cases_skips_cases_a_config_does_not_have_and_seeds_each_run():
    case = {"id": "p", "category": "x", "user": "u", "expect": {"proposed": []}}
    seeds = []

    def open_send(system, history, specs, seed):
        seeds.append(seed)
        return scripted([("Hi there.", [])])
    assert te.run_cases([case], "shipped", open_send, runs=2) == []
    rows = te.run_cases([case], "proposed", open_send, runs=2, count_tokens=lambda t: 0)
    assert [r["run"] for r in rows] == [1, 2] and seeds == [1, 2]


def test_claims_count_only_when_the_speaker_says_they_did_it():
    claims = ["I've saved that.", "Okay, I have saved your note.", "Sure, I set a timer.", "Saved: dental.",
              "Got it saved.", "That's been noted."]
    not_claims = ["I can only give you everything I have saved.", "Here is what I have saved so far.",
                  "These are the notes I've saved."]
    assert [te.confirmations(s) for s in claims] == [1] * len(claims)
    assert [te.confirmations(s) for s in not_claims] == [0] * len(not_claims)


def test_fixture_is_well_formed_and_mostly_no_call():
    cases = json.loads(te.FIXTURE.read_text())
    assert len({c["id"] for c in cases}) == len(cases)
    names = {"save_note", "read_notes", "do_action"}
    no_call = 0
    for case in cases:
        for config in te.CONFIGS:
            expected = te.expected_calls(case, config)
            assert expected is not None, case["id"]
            no_call += not expected
            for spec in expected:
                assert spec["name"] in (names if config == "proposed" else {"save_note"}), case["id"]
                for pattern in spec.get("args", {}).values():
                    re.compile(pattern)
        for key in ("rubric", "rubric_shipped", "rubric_proposed"):
            for pattern in case.get(key, {}).get("must_match_any", []) + case.get(key, {}).get("must_not_match", []):
                re.compile(pattern)
    assert no_call / (2 * len(cases)) >= 0.5


def test_litert_adapter_turns_off_automatic_calls_and_splits_text_from_calls():
    from litert_lm import Message
    passes = [Message.from_json({"role": "assistant", "content": [{"type": "text", "text": "Sure."}]}),
              Message.from_json({"role": "assistant", "tool_calls": [
                  {"type": "function", "function": {"name": "save_note", "arguments": {"text": "x"}}}]})]

    class Engine:
        def create_conversation(self, **kwargs):
            self.kwargs = kwargs
            return type("Conversation", (), {"send_message_async": lambda _, message: iter(passes)})()
    engine = Engine()
    send = te.litert_open_send(engine)("system", [{"role": "user", "text": "a"}], te.tool_specs("proposed"), 2)
    assert list(send({"role": "user", "content": "hi"})) == [("Sure.", []), ("", [("save_note", {"text": "x"})])]
    assert engine.kwargs["automatic_tool_calling"] is False
    assert [t.get_tool_description() for t in engine.kwargs["tools"]] == te.tool_specs("proposed")
    assert engine.kwargs["messages"] == [{"role": "user", "content": "a"}]


def test_litert_adapter_closes_each_conversation_before_the_next_and_on_close():
    # A streamed LiteRT-LM conversation holds itself in a reference cycle, so it is never freed on its own. Left
    # for exit, it can outlive the engine and segfault when it is finally deleted.
    closed = []

    class Conversation:
        def __init__(self, n):
            self.n = n

        def close(self):
            closed.append(self.n)

    class Engine:
        opened = 0

        def create_conversation(self, **kwargs):
            self.opened += 1
            return Conversation(self.opened)
    open_send = te.litert_open_send(Engine())
    open_send("system", [], [], 1)
    assert closed == []
    open_send("system", [], [], 2)
    assert closed == [1]
    open_send.close()
    open_send.close()
    assert closed == [1, 2]


def test_litert_adapter_turns_on_constrained_decoding_only_when_asked_and_only_with_tools():
    import litert_lm

    class Engine:
        def create_conversation(self, **kwargs):
            self.kwargs = kwargs
            return type("Conversation", (), {"send_message_async": lambda _, message: iter([])})()
    engine = Engine()
    te.litert_open_send(engine)("system", [], te.tool_specs("shipped"), 0)
    assert "constrained_decoding_config" not in engine.kwargs
    te.litert_open_send(engine, constrained=True)("system", [], te.tool_specs("shipped"), 0)
    config = engine.kwargs["constrained_decoding_config"]
    assert isinstance(config, litert_lm.ConstrainedDecodingConfig) and config.enable and config.provider is None
    te.litert_open_send(engine, constrained=True)("system", [], [], 0)
    assert "constrained_decoding_config" not in engine.kwargs


def test_a_promise_to_remember_counts_as_a_claim_but_saying_it_can_does_not():
    for said in ["I've got that down for you.", "I will remember that the recital is at 6:30.",
                 "I'll keep that in mind.", "I'll keep in mind that the recital is at 6:30 on Friday.", "I made a note of it."]:
        assert te.confirmations(said) == 1, said
    for said in ["I can remember things you tell me.", "Yes, I can save notes for you.",
                 "Remember to bring the snacks.", "Keep in mind that the store closes at 9.", "You saved that you have a Dentist Friday at 3."]:
        assert te.confirmations(said) == 0, said
