# test-import-check: skip (ft_data is scripts/eval/ft_data.py, imported from the pytest rootdir like the sibling tests)
import ft_data as fd
import router_eval as re_
import tool_eval as te

SAVE = {"text": "remember the gate code is 4512", "intent": "SAVE",
        "call": {"name": "save_note", "args": {"text": "the gate code is 4512"}}, "tag": "explicit"}
TIMER = {"text": "remind me in 20 minutes to check the oven", "intent": "ACTION",
         "call": {"name": "do_action", "args": {"action": "remind_me", "arg": "20 min: check the oven"}}, "tag": "remind_me"}
CHAT = {"text": "how's it going", "intent": "NONE", "call": None, "tag": "chat"}


def test_a_call_becomes_the_gates_tool_call_and_none_becomes_short_text():
    save, chat = fd.to_messages(SAVE), fd.to_messages(CHAT)
    assert save["tools"] == [te.SAVE_NOTE, te.DO_ACTION]
    assert save["messages"][0] == {"role": "developer", "content": re_.FG_SYSTEM}
    assert save["messages"][1] == {"role": "user", "content": SAVE["text"]}
    assert save["messages"][2]["tool_calls"] == [
        {"type": "function", "function": {"name": "save_note", "arguments": {"text": "the gate code is 4512"}}}]
    assert chat["messages"][2] == {"role": "assistant", "content": fd.NONE_REPLY}


def test_near_duplicates_of_protected_texts_are_dropped_from_training():
    train = [dict(CHAT, text="Am I busy this afternoon?"), dict(CHAT, text="um am i busy this afternoon"),
             dict(CHAT, text="am I busy on friday afternoon then"), CHAT]
    kept, dropped = fd.drop_leaks(train, ["am i busy this afternoon"])
    assert [k["text"] for k in kept] == ["am I busy on friday afternoon then", "how's it going"]
    assert len(dropped) == 2


def test_split_keeps_every_intent_in_validation_and_is_repeatable():
    rows = [dict(CHAT, text=f"chat {i}") for i in range(20)] + [dict(SAVE, text=f"save {i}") for i in range(10)]
    train, val = fd.split(rows, 0.2, seed=1)
    assert len(train) + len(val) == 30 and {r["intent"] for r in val} == {"NONE", "SAVE"}
    assert fd.split(rows, 0.2, seed=1) == (train, val)


def test_held_out_rows_become_router_fixture_cases_with_the_same_intent():
    cases = fd.to_fixture([SAVE, TIMER, CHAT])
    assert [re_.expected_intent(c) for c in cases] == ["SAVE", "ACTION", "NONE"]
    assert cases[1]["expect"]["proposed"][0]["args"] == {"action": "^remind_me$", "arg": r"\b20\b"}
    assert te.expected_calls(cases[1], "shipped") == []
    assert len({c["id"] for c in cases}) == 3
