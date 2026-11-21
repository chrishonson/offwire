"""Scores model replies against per-case rubrics and expected extractions."""
import json
import re


def _found(pattern: str, reply: str) -> bool:
    return re.search(pattern, reply, re.IGNORECASE) is not None


def score_rubric(reply: str, rubric: dict, calls: list[dict] = ()) -> list[str]:
    """Text keys judge the reply. Call keys judge which tools were called, by name."""
    failures = [f"missing required: {p}" for p in rubric.get("must_match", []) if not _found(p, reply)]
    any_of = rubric.get("must_match_any", [])
    if any_of and not any(_found(p, reply) for p in any_of):
        failures.append(f"missing any of: {', '.join(any_of)}")
    failures += [f"matched forbidden: {p}" for p in rubric.get("must_not_match", []) if _found(p, reply)]
    called = {c["name"] for c in calls}
    failures += [f"missing call: {n}" for n in rubric.get("must_call", []) if n not in called]
    any_call = rubric.get("must_call_any", [])
    if any_call and not called & set(any_call):
        failures.append(f"missing any call of: {', '.join(any_call)}")
    failures += [f"forbidden call: {n}" for n in rubric.get("must_not_call", []) if n in called]
    return failures


def score_extraction(items: list[dict], expected: list[dict]) -> dict:
    unused = list(expected)
    extra, wrong_kind = [], []
    for item in items:
        entry = next((e for e in unused if _found(e["match"], str(item.get("text", "")))), None)
        if entry is None:
            extra.append(item)
            continue
        unused.remove(entry)
        if item.get("kind") != entry["kind"]:
            wrong_kind.append(item)
    return {"missed": unused, "extra": extra, "wrong_kind": wrong_kind}


def parse_json_list(reply: str) -> list[dict] | None:
    decoder = json.JSONDecoder()
    for start in (i for i, ch in enumerate(reply) if ch == "["):
        try:
            value, _ = decoder.raw_decode(reply, start)
        except json.JSONDecodeError:
            continue
        if isinstance(value, list) and all(isinstance(v, dict) for v in value):
            return value
    return None
