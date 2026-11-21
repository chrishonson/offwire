import tools


def test_every_tool_records_its_call_by_name():
    functions, calls = tools.build(online=True)
    assert [f.__name__ for f in functions] == tools.NAMES
    {f.__name__: f for f in functions}["note_personal_fact"](kind="habit", text="walked")
    assert calls == [{"name": "note_personal_fact", "args": {"kind": "habit", "text": "walked"}}]


def test_web_tools_are_offered_only_online():
    offline, _ = tools.build(online=False)
    assert "open_web_search" not in [f.__name__ for f in offline]
    assert "open_reference" not in [f.__name__ for f in offline]


def test_docstrings_carry_no_medical_words():
    functions, _ = tools.build(online=True)
    text = " ".join(f.__doc__ for f in functions).lower()
    assert not any(word in text for word in ("doctor", "medical", "prescription", "health"))
