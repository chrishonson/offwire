# test-import-check: skip (notes is scripts/eval/notes.py, imported from the pytest rootdir like the sibling tests)
import re
from pathlib import Path

from litert_lm.tools import tool_from_function

from notes import build, run_cases, score, system_prompt

SAVE_NOTE_KT = Path(__file__).parents[2] / "core/src/commonMain/kotlin/com/chrishonson/gemmavoice/notes/SaveNote.kt"


def saving(text):
    """A stub model that saves [text] when given a tool list."""
    def ask(system, user, seed, functions, history=None):
        functions[0](text=text)
        return "Saved it."
    return ask


def test_no_tools_prompt_names_no_tool_and_no_notes():
    prompt = system_prompt(None)
    assert "You have no tools" in prompt
    assert "saved notes" not in prompt


def test_without_tools_only_tool_free_cases_run_and_get_no_functions():
    cases = [{"id": "save", "user": "u", "rubric": {"must_call": ["save_note"]}},
             {"id": "recall", "user": "u", "notes": ["n"], "rubric": {}},
             {"id": "plain", "user": "u", "rubric": {}}]
    seen = []

    def ask(system, user, seed, functions, history=None):
        seen.append((system, functions))
        return "ok"
    results = run_cases(cases, ask, runs=1, tools=False)
    assert [r["id"] for r in results] == ["plain"]
    assert seen == [(system_prompt(None), [])]


def kotlin_const(name):
    body = SAVE_NOTE_KT.read_text().split(f"const val {name} =")[1].split("const val")[0].split("\n\n")[0]
    return "".join(re.findall(r'"([^"]*)"', body))


def test_tool_sends_the_model_the_apps_descriptions():
    function = tool_from_function(build()[0][0]).get_tool_description()["function"]
    assert function["description"] == kotlin_const("SAVE_NOTE_DESCRIPTION")
    assert function["parameters"]["properties"]["text"]["description"] == kotlin_const("SAVE_NOTE_TEXT_DESCRIPTION")


def test_prompt_lists_notes_or_says_there_are_none():
    assert "- MRI at Edward" in system_prompt(["MRI at Edward"])
    assert "No notes yet." in system_prompt([])


def test_tool_records_the_note_text():
    functions, calls = build()
    assert [f.__name__ for f in functions] == ["save_note"]
    functions[0](text="brake pads 3 mm")
    assert calls == [{"name": "save_note", "args": {"text": "brake pads 3 mm"}}]


def test_a_save_passes_only_when_the_note_matches():
    case = {"id": "c", "user": "u", "rubric": {"must_call": ["save_note"], "note_match": "lease"}}
    assert run_cases([case], saving("lawyer needs the lease"), runs=1)[0]["failures"] == []
    assert run_cases([case], saving("lawyer stuff"), runs=1)[0]["failures"] == ["note missing lease: lawyer stuff"]


def test_more_than_one_note_fails():
    calls = [{"name": "save_note", "args": {"text": "a"}}] * 2
    assert score("ok", calls, {}) == ["saved 2 notes"]


def test_a_false_claim_without_a_call_fails():
    rubric = {"must_not_call": ["save_note"], "must_not_match": [r"\bsaved\b"]}
    assert score("I saved that.", [], rubric) == [r"matched forbidden: \bsaved\b"]


def test_a_reply_shorter_than_min_words_fails():
    assert score("Once upon a time.", [], {"min_words": 5}) == ["only 4 words, wanted 5"]
    assert score("one two three four five", [], {"min_words": 5}) == []


def test_results_from_another_model_get_its_name(monkeypatch):
    from model import DEFAULT_MODEL_PATH, results_suffix
    monkeypatch.delenv("GEMMA_MODEL", raising=False)
    assert results_suffix() == ""
    monkeypatch.setenv("GEMMA_MODEL", DEFAULT_MODEL_PATH)
    assert results_suffix() == ""
    monkeypatch.setenv("GEMMA_MODEL", "/models/gemma-4-E4B-it.litertlm")
    assert results_suffix() == "-gemma-4-E4B-it"
