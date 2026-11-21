from pathlib import Path

from build_e2 import REPO, build_cases
from scoring import score_rubric

LABELS = REPO / "eval" / "private" / "labels"


def test_string_answer_requires_only_the_drug():
    cases = build_cases({"a.png": "metformin"}, lambda p: "TEXT", LABELS)
    assert [c["id"] for c in cases] == ["e2-a-text", "e2-a-image"]
    assert cases[0]["rubric"] == {"must_match": ["metformin"]}
    assert cases[1]["image"] == "eval/private/labels/a.png"


def test_object_answer_forbids_pharmacy_and_patient_anywhere():
    answer = {"drug": "lisinopril", "pharmacy": "Corner Pharmacy", "patient": "Maria Gonzalez"}
    rubric = build_cases({"a.png": answer}, lambda p: "TEXT", LABELS)[0]["rubric"]
    assert score_rubric("Zestril, which is lisinopril", rubric) == []
    assert score_rubric("lisinopril for Maria Gonzalez", rubric)
    assert score_rubric("lisinopril from corner pharmacy", rubric)
    assert score_rubric("Zestril", rubric)
