from scoring import parse_json_list, score_extraction, score_rubric


def test_passing_rubric_has_no_failures():
    rubric = {"must_match": ["save"], "must_match_any": ["doctor", "physician"],
              "must_not_match": ["search"]}
    assert score_rubric("I can SAVE this for your Doctor", rubric) == []


def test_must_match_reports_the_pattern():
    assert score_rubric("hello", {"must_match": ["save"]}) == ["missing required: save"]


def test_must_match_any_reports_when_none_match():
    assert score_rubric("hello", {"must_match_any": ["a1", "b2"]}) == ["missing any of: a1, b2"]


def test_must_not_match_reports_the_pattern():
    assert score_rubric("I can search the web", {"must_not_match": ["search"]}) == ["matched forbidden: search"]


def test_extraction_counts_missed_and_extra():
    r = score_extraction([{"kind": "habit", "text": "walked 30 minutes"},
                          {"kind": "habit", "text": "likes blue"}],
                         [{"kind": "habit", "match": "walk"}, {"kind": "goal", "match": "130"}])
    assert len(r["missed"]) == 1 and len(r["extra"]) == 1 and r["wrong_kind"] == []


def test_extraction_reports_wrong_kind_and_does_not_count_it_missed():
    r = score_extraction([{"kind": "goal", "text": "walk daily"}],
                         [{"kind": "habit", "match": "walk"}])
    assert r["wrong_kind"] == [{"kind": "goal", "text": "walk daily"}]
    assert r["missed"] == [] and r["extra"] == []


def test_extraction_uses_each_expected_entry_once():
    r = score_extraction([{"kind": "habit", "text": "walk a"}, {"kind": "habit", "text": "walk b"}],
                         [{"kind": "habit", "match": "walk"}])
    assert len(r["extra"]) == 1 and r["missed"] == []


def test_parse_json_list_skips_prose():
    assert parse_json_list('Sure:\n[{"kind":"goal","text":"x"}]') == [{"kind": "goal", "text": "x"}]


def test_parse_json_list_returns_none_without_array():
    assert parse_json_list("nothing here") is None


def test_parse_json_list_skips_non_json_brackets():
    assert parse_json_list('see [note] then [{"a": 1}]') == [{"a": 1}]


def test_parse_json_list_rejects_arrays_with_non_dict_entries():
    assert parse_json_list('["walks daily"]') is None
