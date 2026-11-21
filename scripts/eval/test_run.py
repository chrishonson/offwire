import json

from run import fixture_path, pass_rate, run_set, write_results

CASES = [
    {"id": "saves", "user": "u1", "rubric": {"must_match": ["save"]}},
    {"id": "searches", "user": "u2", "rubric": {"must_not_match": ["search"]}},
]


def stub(system, user, history, image, seed, functions):
    return "save" if user == "u1" else "search"


def test_results_carry_reply_and_failures_and_rate_is_half():
    results = run_set("E4", CASES, stub, runs=1)
    assert [(r["id"], r["run"], r["reply"]) for r in results] == [("saves", 1, "save"), ("searches", 1, "search")]
    assert results[0]["failures"] == []
    assert results[1]["failures"] == ["matched forbidden: search"]
    assert pass_rate(results) == 0.5


def test_runs_repeat_every_case():
    results = run_set("E4", CASES, stub, runs=3)
    assert len(results) == 6 and {r["run"] for r in results} == {1, 2, 3}


def test_seed_is_the_run_index_so_run_one_keeps_seed_zero():
    seeds = []
    run_set("E4", CASES[:1], lambda *args: seeds.append(args[4]) or "save", runs=3)
    assert seeds == [0, 1, 2]


def test_ask_fn_gets_prompt_history_and_image_from_the_case():
    seen = []
    case = {"id": "c", "user": "hi", "history": [{"role": "user", "text": "x"}], "online": True,
            "record": "LDL 162", "image": "eval/private/a.png", "rubric": {}}
    run_set("E5", [case], lambda *args: seen.append(args) or "ok", runs=1)
    system, user, history, image, seed, functions = seen[0]
    assert "LDL 162" in system and "MedlinePlus" in system and len(functions) == 5
    assert user == "hi" and history == case["history"] and image.name == "a.png"


def noting(*facts):
    """A stub model that calls note_personal_fact once per (kind, text) pair."""
    def ask(system, user, history, image, seed, functions):
        note = {f.__name__: f for f in functions}["note_personal_fact"]
        for kind, text in facts:
            note(kind=kind, text=text)
        return "ok"
    return ask


def test_extraction_case_is_scored_against_note_calls():
    case = {"id": "x", "user": "u", "expected": [{"kind": "habit", "match": "walk"}]}
    assert run_set("E3", [case], noting(("habit", "walked daily")), runs=1)[0]["failures"] == []
    assert run_set("E3", [case], noting(("goal", "walked daily")), runs=1)[0]["failures"] == ["wrong kind: walked daily"]
    extra = run_set("E3", [case], noting(("habit", "walk"), ("goal", "z")), runs=1)
    assert extra[0]["failures"] == ["extra: z"]


def test_rubric_judges_calls_by_name():
    case = {"id": "x", "user": "u", "rubric": {"must_call": ["note_personal_fact"], "must_not_call": ["save_question"]}}
    result = run_set("E4", [case], noting(("habit", "walk")), runs=1)[0]
    assert result["failures"] == [] and result["calls"][0]["name"] == "note_personal_fact"


def test_missed_extraction_is_reported():
    case = {"id": "x", "user": "u", "expected": [{"kind": "goal", "match": "130"}]}
    assert run_set("E3", [case], noting(), runs=1)[0]["failures"] == ["missed: goal 130"]


def test_write_results_makes_one_json_line_per_reply(tmp_path):
    results = run_set("E4", CASES, stub, runs=1)
    path = write_results("e2-synthetic", results, tmp_path, "2026-11-05")
    assert path.name == "2026-11-05-e2-synthetic.jsonl"
    assert [json.loads(line)["id"] for line in path.read_text().splitlines()] == ["saves", "searches"]


def test_set_id_reads_the_lowercased_fixture():
    assert fixture_path("E2").as_posix().endswith("eval/fixtures/e2.json")
