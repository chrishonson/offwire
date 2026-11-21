# test-import-check: skip (fg_caller_eval is scripts/eval/fg_caller_eval.py, imported from the pytest rootdir like the sibling tests)
import json

import fg_caller_eval as fg

TIMER = {"id": "t", "category": "action", "user": "Give me a ten minute timer",
         "expect": {"shipped": [], "proposed": [{"name": "do_action",
                                                  "args": {"action": "^remind_me$", "arg": "\\b(10|ten)\\b"}}]}}
NAV = {"id": "n", "category": "action", "user": "Navigate home",
       "expect": {"shipped": [], "proposed": [{"name": "do_action", "args": {"action": "^navigate_to$", "arg": "home"}}]}}
SAVE = {"id": "s", "category": "save", "user": "Add milk to my notes",
        "expect": [{"name": "save_note", "args": {"text": "milk"}}]}
CHAT = {"id": "c", "category": "no_call", "user": "Write me a haiku", "expect": []}


def test_neutral_functions_map_to_the_apps_actions():
    assert fg.to_action("neutral", "set_timer", {"minutes": 10, "label": "tea"}) == ("remind_me", "10 min: tea")
    assert fg.to_action("neutral", "get_directions", {"destination": "home"}) == ("navigate_to", "drive: home")
    assert fg.to_action("neutral", "get_directions", {"destination": "the park", "mode": "walk"}) == (
        "navigate_to", "walk: the park")
    assert fg.to_action("neutral", "read_calendar", {"day": "tomorrow"}) == ("whats_on", "tomorrow")
    assert fg.to_action("neutral", "email_notes_to_self", {}) == ("send_notes_to_me", "")
    assert fg.to_action("neutral", "turn_on_flashlight", {}) is None


def test_native_functions_cover_only_maps_and_email():
    assert fg.to_action("native", "show_map", {"query": "hardware store"}) == ("navigate_to", "drive: hardware store")
    assert fg.to_action("native", "send_email", {"to": "me", "subject": "notes"}) == ("send_notes_to_me", "notes")
    assert fg.to_action("native", "create_calendar_event", {"title": "timer", "datetime": "x"}) is None


def test_do_action_passes_through():
    assert fg.to_action("do_action", "do_action", {"action": "whats_on", "arg": "today"}) == ("whats_on", "today")
    assert fg.to_action("do_action", "save_note", {"text": "x"}) is None


def test_score_right_action_and_arguments():
    good = fg.score(TIMER, "neutral", [("set_timer", {"minutes": "10"})])
    assert good["right_action"] and good["args_ok"] and not good["refused"]
    wrong_minutes = fg.score(TIMER, "neutral", [("set_timer", {"minutes": "5"})])
    assert wrong_minutes["right_action"] and not wrong_minutes["args_ok"]
    wrong_action = fg.score(TIMER, "native", [("create_calendar_event", {"title": "timer", "datetime": "x"})])
    assert not wrong_action["right_action"] and not wrong_action["args_ok"]
    assert fg.score(NAV, "native", [("show_map", {"query": "home"})])["args_ok"]


def test_score_two_calls_is_not_the_right_action():
    twice = fg.score(TIMER, "neutral", [("set_timer", {"minutes": "10"}), ("read_calendar", {"day": "today"})])
    assert not twice["right_action"]


def test_score_refusals():
    assert fg.score(CHAT, "neutral", [])["refused"]
    assert fg.score(SAVE, "neutral", [])["expected_action"] is None
    missed = fg.score(TIMER, "neutral", [])
    assert missed["refused"] and not missed["right_action"]


def test_load_heldout_rows_scores_the_action_only(tmp_path):
    path = tmp_path / "heldout.jsonl"
    rows = [{"text": "any events tomorrow", "intent": "ACTION", "tag": "cal",
             "call": {"name": "do_action", "args": {"action": "whats_on", "arg": "tomorrow"}}},
            {"text": "the oven timer is beeping", "intent": "NONE", "call": None, "tag": "trap_timer"},
            {"text": "note the gate code", "intent": "SAVE", "tag": "explicit",
             "call": {"name": "save_note", "args": {"text": "the gate code"}}}]
    path.write_text("".join(json.dumps(r) + "\n" for r in rows))
    cases = fg.load_cases(path)
    assert [fg.expected_action(c) is not None for c in cases] == [True, False, False]
    assert fg.score(cases[0], "neutral", [("read_calendar", {"day": "today"})])["right_action"]
    assert not cases[0]["args_scored"]


def test_run_and_summarize_count_both_ways_of_reaching_the_model():
    replies = {"Give me a ten minute timer": [("set_timer", {"minutes": "10"})],
               "Navigate home": [],
               "Add milk to my notes": [("email_notes_to_self", {})],
               "Write me a haiku": []}
    routes = {"Give me a ten minute timer": "ACTION", "Navigate home": "NONE", "Add milk to my notes": "SAVE",
              "Write me a haiku": "ACTION"}

    def ask(text, specs):
        return {"calls": replies[text], "text": "", "seconds": 0.2, "parse_error": None, "prompt_tokens": 100}

    rows = fg.run([TIMER, NAV, SAVE, CHAT], ["neutral"], routes.get, ask)
    s = fg.summarize(rows)["neutral"]
    assert s["action_phrasings_routed_to_action"] == [1, 2]
    assert s["behind_router"]["calls_made"] == 2
    assert s["behind_router"]["right_action"] == [1, 1]
    assert s["behind_router"]["refused_when_no_action_wanted"] == [1, 1]
    assert s["direct"]["right_action"] == [1, 2]
    assert s["direct"]["correct_args"] == [1, 2]
    assert s["direct"]["refused_when_no_action_wanted"] == [1, 2]
    assert s["direct"]["refused_a_wanted_action"] == [1, 2]
    assert s["by_action_direct"] == {"remind_me": [1, 1], "navigate_to": [0, 1]}
    assert "1 of 2" in fg.table(fg.summarize(rows))
