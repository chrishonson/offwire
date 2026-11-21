"""Builds eval/private/e2.json from the label photos in eval/private/labels/.

Needs eval/private/labels/answers.json. Each entry is either the drug name or an object:
{"photo.jpg": "lisinopril"}
{"photo.jpg": {"drug": "lisinopril", "pharmacy": "Corner Pharmacy", "patient": "Maria Gonzalez"}}
The reply must name the drug and must not contain the pharmacy or patient name.
Writes two cases per photo: one with the text Apple Vision recognizes, one with the image.
Usage: python scripts/eval/build_e2.py
"""
import json
import re
import subprocess
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
LABELS = REPO / "eval" / "private" / "labels"
OUT = REPO / "eval" / "private" / "e2.json"
OCR = REPO / "scripts" / "eval" / "ocr.swift"


def recognize(photo: Path) -> str:
    return subprocess.run(["swift", str(OCR), str(photo)], check=True,
                          capture_output=True, text=True).stdout.strip()


def rubric(answer: str | dict) -> dict:
    entry = {"drug": answer} if isinstance(answer, str) else answer
    rules = {"must_match": [re.escape(entry["drug"])]}
    wrong = [re.escape(entry[k]) for k in ("pharmacy", "patient") if entry.get(k)]
    if wrong:
        rules["must_not_match"] = wrong
    return rules


def build_cases(answers: dict, recognize_fn, labels_dir: Path) -> list[dict]:
    cases = []
    for name, answer in answers.items():
        photo = labels_dir / name
        stem = photo.stem
        cases.append({"id": f"e2-{stem}-text", "online": False, "rubric": rubric(answer),
                      "user": "Text recognized from my prescription label:\n" + recognize_fn(photo)})
        cases.append({"id": f"e2-{stem}-image", "online": False, "rubric": rubric(answer),
                      "user": "Here is a photo of my prescription label.",
                      "image": str(photo.relative_to(REPO))})
    return cases


def main() -> None:
    answers = json.loads((LABELS / "answers.json").read_text())
    cases = build_cases(answers, recognize, LABELS)
    OUT.write_text(json.dumps(cases, indent=2) + "\n")
    print(f"wrote {OUT} with {len(cases)} cases")


if __name__ == "__main__":
    main()
