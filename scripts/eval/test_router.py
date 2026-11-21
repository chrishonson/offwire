# test-import-check: skip (router and router_eval are scripts/eval modules, imported from the pytest rootdir like the sibling tests)
import json

import pytest

import router as r
import router_eval as re_
import tool_eval as te

CASES = {c["id"]: c for c in json.loads(te.FIXTURE.read_text())}


@pytest.mark.parametrize("text, intent, args", [
    ("Remember that the vet said Max needs a dental cleaning", r.SAVE, {"text": "the vet said Max needs a dental cleaning"}),
    ("Computer, could you please write down that the pads are worn", r.SAVE, {"text": "the pads are worn"}),
    ("Hey computer, note: no food after midnight", r.SAVE, {"text": "no food after midnight"}),
    ("Save this idea: a voice app that reads recipes", r.SAVE, {"text": "a voice app that reads recipes"}),
    ("Don't let me forget to buy batteries", r.SAVE, {"text": "buy batteries"}),
    ("Remind me in 20 minutes to check the oven", r.ACTION, {"action": "remind_me", "arg": "20 min: check the oven"}),
    ("Set a timer for an hour to move the car", r.ACTION, {"action": "remind_me", "arg": "60 min: move the car"}),
    ("set a timer for half an hour", r.ACTION, {"action": "remind_me", "arg": "30 min"}),
    ("Start a timer for ten minutes", r.ACTION, {"action": "remind_me", "arg": "10 min"}),
    ("Email my notes to myself", r.ACTION, {"action": "send_notes_to_me", "arg": ""}),
    ("Send me my notes with the subject talk ideas", r.ACTION, {"action": "send_notes_to_me", "arg": "talk ideas"}),
    ("Walk me to the library", r.ACTION, {"action": "navigate_to", "arg": "walk: the library"}),
    ("Get me driving directions to Home Depot", r.ACTION, {"action": "navigate_to", "arg": "drive: home depot"}),
    ("What's on my calendar today?", r.ACTION, {"action": "whats_on", "arg": "today"}),
    ("Do I have anything tomorrow evening?", r.ACTION, {"action": "whats_on", "arg": "tomorrow"}),
])
def test_rules_route_requests_and_take_the_words_after_the_phrase(text, intent, args):
    route = r.route(text)
    assert (route.intent, route.source, route.args) == (intent, "rule", args)


@pytest.mark.parametrize("text", [
    "I remember when phones had physical keyboards.", "Remember when we went camping?",
    "What's a good way to save money on groceries?", "My neighbor reminded me to water the plants",
    "What's the difference between a whole note and a half note?", "Is it faster to walk or bike two miles?",
    "Remind me in a bit", "Wake me up at 6 tomorrow", "Text Sam that I'm running late",
])
def test_tool_words_without_a_rule_are_near_misses(text):
    assert r.route(text).source == "near-miss"
    assert r.route(text).intent == r.NONE
    assert r.route(text, gate=lambda t: r.SAVE).intent == r.SAVE


def test_plain_messages_never_reach_the_gate():
    def gate(_):
        raise AssertionError("gate asked")
    for text in ("Tell me a joke", "How many ounces are in a cup?", "Thanks!"):
        assert r.route(text, gate) == r.Route(r.NONE, "plain")


def test_a_save_of_that_needs_the_model():
    assert r.route("Save that").source == "rule-ref"
    assert r.route("Remember this.").source == "rule-ref"
    assert r.route("remember it").args is None


def test_a_timer_without_a_duration_is_not_a_rule_hit():
    assert r.apply_rules("Remind me in a little while") is None
    assert r.timer_arg("5 hrs so the bread rises") == "300 min: the bread rises"


def test_gate_labels_parse_leniently_and_default_to_none():
    assert [r.parse_label(t) for t in ["NONE", " Save", "ACT", "action.", "", "maybe", "N"]] == \
        [r.NONE, r.SAVE, r.ACTION, r.ACTION, r.NONE, r.NONE, r.NONE]


def test_gate_examples_are_not_eval_cases():
    examples = [line.split("Message: ")[1].split(" Label:")[0] for line in r.GATE_SYSTEM.splitlines() if line.startswith("Message:")]
    users = {c["user"].lower() for c in CASES.values()}
    assert len(examples) == 8 and not users & {e.lower() for e in examples}
    assert sum("Label: NONE" in line for line in r.GATE_SYSTEM.splitlines()) >= 5


def test_every_fixture_case_has_an_expected_intent_and_rules_alone_route_most_of_them():
    hits = sum(r.route(c["user"]).intent == re_.expected_intent(c) for c in CASES.values())
    assert re_.expected_intent(CASES["recall-wifi"]) == r.NONE
    assert re_.expected_intent(CASES["multi-save-and-timer"]) == "SAVE+ACTION"
    assert hits >= 46  # the rules were written against these cases, so this is a floor, not a result


# ---- router_eval, with scripted models --------------------------------------------------------------------

def scripted(*passes):
    queue = list(passes)
    opened = []

    def open_send(system, history, specs, seed):
        opened.append({"system": system, "specs": [s["function"]["name"] for s in specs], "seed": seed})
        return lambda message: iter(queue.pop(0) if queue else [])
    open_send.opened = opened
    return open_send


def test_a_rule_save_is_saved_by_the_app_with_no_model_call():
    send = scripted()
    row = re_.run_routed(CASES["save-vet"], "rules", 1, send, None)
    assert send.opened == [] and row["guarded"] == "Saved." and row["failures"] == []
    assert row["response_ttft_s"] == 0.0 and row["prompt_tokens"] == 0 and row["tool_turn"]


def test_a_rule_timer_says_a_fixed_line():
    row = re_.run_routed(CASES["act-timer-oven"], "rules", 1, scripted(), None)
    assert row["guarded"] == "Timer set for 20 minutes." and row["failures"] == []


def test_calendar_rule_runs_the_lookup_then_gemma_reads_it_with_no_tools():
    send = scripted([("You have the dentist at 2 PM.", [])])
    row = re_.run_routed(CASES["act-cal-today"], "rules", 2, send, None)
    assert send.opened[0]["specs"] == [] and "dentist" in send.opened[0]["system"] and send.opened[0]["seed"] == 2
    assert row["failures"] == []


def test_none_routes_show_no_tools_and_gated_routes_show_one():
    send = scripted([("A whole note lasts twice as long.", [])])
    row = re_.run_routed(CASES["trap-music-note"], "rules_e2b_gate", 1, send,
                         lambda t, seed: {"label": r.NONE, "raw": "NONE", "prompt_tokens": 150})
    assert send.opened[0]["specs"] == [] and row["gate"]["raw"] == "NONE" and row["failures"] == []

    send = scripted([("", [("save_note", {"text": "x"})])], [("Saved it.", [])])
    row = re_.run_routed(CASES["trap-music-note"], "rules_e2b_gate", 1, send,
                         lambda t, seed: {"label": r.SAVE, "raw": "SAVE", "prompt_tokens": 150})
    assert send.opened[0]["specs"] == ["save_note"] and "unwanted call: save_note" in row["failures"]
    assert row["route_intent"] == r.SAVE and row["expected_intent"] == r.NONE


def test_save_that_gives_the_model_only_save_note():
    send = scripted([("", [("save_note", {"text": "Echo Pad"})])], [("Saved Echo Pad.", [])])
    row = re_.run_routed(CASES["save-that"], "rules", 1, send, None)
    assert send.opened[0]["specs"] == ["save_note"] and row["failures"] == []


def test_parse_errors_become_rows_in_the_reply_and_none_in_the_gate():
    def broken(system, history, specs, seed):
        def send(message):
            raise RuntimeError("Failed to parse tool call")
            yield
        return send
    row = re_.run_routed(CASES["chat-joke"], "rules", 1, broken, None)
    assert row["parse_error"] and row["failures"][0].startswith("parse_error")
    row = re_.run_shipped(CASES["chat-joke"], 1, broken)
    assert row["parse_error"] and row["route_intent"] == r.NONE

    def bad_gate(t, seed):
        raise RuntimeError("Failed to parse tool call")
    row = re_.run_routed(CASES["trap-save-money"], "rules_fg_gate", 1, scripted([("Buy store brands.", [])]), bad_gate)
    assert row["gate"]["parse_error"] and row["route_intent"] == r.NONE and row["failures"] == []


def test_shipped_rows_carry_intents_and_timings():
    row = re_.run_shipped(CASES["save-wifi"], 1, scripted([("", [("save_note", {"text": "guest wifi sunflower42"})])],
                                                         [("Saved the wifi password.", [])]))
    assert row["route_intent"] == r.SAVE and row["router_s"] == 0.0 and row["total_s"] == row["response_ttft_s"]


def test_summary_has_the_intent_table_and_split_timings_and_rows_are_written_as_they_go():
    written = []
    rows = re_.run_setup("rules", [CASES["save-vet"], CASES["chat-joke"], CASES["trap-music-note"]], 2,
                         scripted(*[[("Here's one.", [])]] * 4), None, None, written.append)
    assert len(written) == 6
    s = re_.summarize(rows)
    assert s["intent_table"] == {"NONE": {"NONE": 4}, "SAVE": {"SAVE": 2}}
    assert s["intent_match"] == 1.0 and s["timing"]["tool_turns"]["n"] == 2 and s["timing"]["no_tool_turns"]["n"] == 4
    assert s["route_sources"] == {"rule": 2, "plain": 2, "near-miss": 2}
    assert "| rules |" in re_.compare_table({"rules": s})


def test_routed_prompt_matches_the_shipped_wording_around_the_tool_line():
    none = re_.routed_prompt(["MRI at Edward"])
    assert none.startswith(te.notes.INTRO) and "- MRI at Edward" in none and "no tools" in none
    assert "You have one tool, do_action." in re_.routed_prompt([], "do_action")
    assert "No notes yet." in re_.routed_prompt([])


def test_holdout_fixture_is_well_formed_and_unseen():
    holdout = json.loads((te.FIXTURE.parent / "tool-eval-holdout.json").read_text())
    assert all(c["id"].startswith("ho-") for c in holdout) and not {c["user"] for c in holdout} & {c["user"] for c in CASES.values()}
    for case in holdout:
        assert re_.expected_intent(case) in (r.NONE, r.SAVE, r.ACTION)
        assert te.expected_calls(case, "shipped") is not None and te.expected_calls(case, "proposed") is not None


# ---- constrained decoding setups and output files ---------------------------------------------------------

def test_constrained_setups_are_their_base_setup_with_the_flag():
    assert set(re_.SETUPS) <= set(re_.VALID_SETUPS) and {"shipped_cd", "rules_e2b_gate_cd"} <= set(re_.SETUPS)
    assert re_.base_setup("rules_e2b_gate_cd") == "rules_e2b_gate" and re_.base_setup("shipped") == "shipped"
    assert re_.is_constrained("shipped_cd") and not re_.is_constrained("rules_fg_gate")


def test_a_constrained_setup_runs_its_base_and_is_labelled_with_its_own_name():
    rows = re_.run_setup("shipped_cd", [CASES["chat-joke"]], 1, scripted([("Here's one.", [])]), None, None, lambda r: None)
    assert rows[0]["config"] == "shipped_cd" and rows[0]["route_source"] == "none"
    send = scripted([("A whole note lasts twice as long.", [])])
    rows = re_.run_setup("rules_e2b_gate_cd", [CASES["trap-music-note"]], 1, send,
                         lambda t, seed: {"label": r.NONE, "raw": "NONE", "prompt_tokens": 150}, None, lambda r: None)
    assert rows[0]["config"] == "rules_e2b_gate_cd" and rows[0]["gate"]["raw"] == "NONE" and rows[0]["failures"] == []


def test_the_holdout_fixture_cannot_overwrite_the_main_results(tmp_path):
    main = re_.output_base(tmp_path, "2026-11-10", "gemma-4-E2B-it", str(te.FIXTURE))
    holdout = re_.output_base(tmp_path, "2026-11-10", "gemma-4-E2B-it", str(te.FIXTURE.parent / "tool-eval-holdout.json"))
    assert main.name == "2026-11-10-router-eval-gemma-4-E2B-it"
    assert holdout.name == "2026-11-10-router-eval-gemma-4-E2B-it-tool-eval-holdout"


def test_a_later_run_adds_its_setups_to_the_earlier_summary(tmp_path):
    path = tmp_path / "x.summary.json"
    assert re_.load_summaries(path) == {}
    path.write_text(json.dumps({"shipped": {"pass_rate": 0.5}}))
    assert re_.load_summaries(path) == {"shipped": {"pass_rate": 0.5}}
    path.write_text("{not json")
    assert re_.load_summaries(path) == {}


def test_save_decision_counts_false_saves_missed_saves_and_honest_capability_answers():
    rows = [{"expected": [], "called": ["save_note"], "category": "complaint", "failures": ["unwanted call: save_note"]},
            {"expected": [], "called": [], "category": "capability", "failures": []},
            {"expected": [], "called": [], "category": "capability", "failures": ["matched forbidden: no tool"]},
            {"expected": ["save_note"], "called": [], "category": "save", "failures": ["missing call: save_note"]},
            {"expected": ["save_note"], "called": ["save_note"], "category": "save", "failures": []}]
    assert re_.save_decision(rows) == {"false_saves": {"n": 3, "count": 1}, "missed_saves": {"n": 2, "count": 1},
                                       "capability_honest": {"n": 2, "count": 1}}


def test_save_decision_fixture_is_well_formed_and_balanced():
    cases = json.loads((te.FIXTURE.parent / "save-decision.json").read_text())
    assert len({c["id"] for c in cases}) == len(cases)
    saves = [c for c in cases if re_.expected_intent(c) == r.SAVE]
    assert all(c["category"] == "save" for c in saves) and 0.35 < len(saves) / len(cases) < 0.65
    assert {c["category"] for c in cases} == {"save", "capability", "complaint", "talk"}


def test_tool_always_offers_save_note_on_every_turn_and_scores_what_gemma_called():
    chat = (r.Route(r.NONE, "plain"), {})
    send = scripted([("A whole note lasts twice as long.", [])])
    row = re_.run_routed(CASES["trap-music-note"], re_.TOOL_ALWAYS, 1, send, None, route_fn=lambda t: chat)
    assert send.opened[0]["specs"] == ["save_note"] and re_.TOOL_ALWAYS_LINE in send.opened[0]["system"]
    assert row["failures"] == [] and row["route_intent"] == r.NONE and row["router_intent"] == r.NONE

    action = (r.Route(r.ACTION, "embed"), {})
    send = scripted([("Sure.", [])])
    re_.run_routed(CASES["trap-music-note"], re_.TOOL_ALWAYS, 1, send, None, route_fn=lambda t: action)
    assert send.opened[0]["specs"] == ["save_note", "do_action"] and re_.ALSO_DO_ACTION in send.opened[0]["system"]


def test_every_routed_prompt_says_the_app_saves_notes():
    for tool in (None, "save_note"):
        assert re_.SAVES_NOTES in re_.routed_prompt([], tool)
    assert "say yes" in re_.routed_prompt([]) and re_.SAVES_NOTES in re_.routed_prompt([], always=True)
