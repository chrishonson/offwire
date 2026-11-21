# Expert Visit Evaluation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Measure what Gemma 4 E2B can do for the expert visit companion (sets E1 to E6) on the Mac, before any feature is built.

**Architecture:** A small Python harness in `scripts/eval/` drives the phone's model file through the LiteRT-LM Python package at the app's version and sampler settings. Each set is a JSON fixture of cases with a rubric. A runner sends each case several times, scores the replies, and writes raw results plus a summary. A results doc closes the work with a recommendation per feature.

**Tech Stack:** Python 3.11, `litert-lm==0.18.1`, pytest, Swift (Apple Vision text recognition, via `swift` script).

**Spec:** `docs/superpowers/specs/2026-11-05-expert-visit-companion-design.md` (section "Step zero: evaluation on the Mac")

## Global Constraints

- Model file: `~/models/gemma4/gemma-4-E2B-it.litertlm`, overridable with env `GEMMA_MODEL`. Never copied into the repo.
- Runtime: `litert-lm==0.18.1`, matching `litertlm = "0.18.1"` in `gradle/libs.versions.toml`. Verified on 2026-11-05 to load this model on the Mac (5 s load, CPU backend).
- Sampler matches `core/src/androidMain/.../llm/LiteRtLlmEngine.kt`: `top_k=64`, `top_p=0.95`, `temperature=1.0`, `seed=0`.
- Mac only. No adb, no phone. Speed is not measured.
- No fixed reply text. Prompts give Gemma data (capability list, record, live state). They never script a sentence for it to say. Rubrics judge meaning with patterns, not exact wording.
- Real label photos contain personal data. They live in `eval/private/`, which is gitignored, and are never committed.
- Virtualenv at `.venv/` (already gitignored).
- Commit messages: imperative, why not what, no co-author line.

## Review Focus

- STT-shaped input: lowercase, no punctuation, numbers as words ("one thirty two over eighty five"). Expected: same result as clean text. Fixtures in Tasks 5 and 6 include these.
- Live state changes mid-conversation: history shows an online offer, the current turn is offline. Expected: no web hand-off offered. Task 5.
- Mixed utterance: a habit, a medical question and a new prescription in one sentence. Expected: habit extracted (E3), question offered for saving (E4), prescription sent to the library (E6). Tasks 5 and 6.
- Summary asked about something absent from the record. Expected: says it is not in the record, invents nothing. Task 7.
- Noisy label text: pharmacy name, patient name, warnings, "generic for Zestril". Expected: the dispensed drug's name only. Task 8.

---

### Task 1: Model harness

**Files:**
- Create: `scripts/eval/requirements.txt`
- Create: `scripts/eval/model.py`
- Test: `scripts/eval/test_model.py`

**Interfaces:**
- Produces: `load_engine(model_path: str | None = None) -> litert_lm.Engine` and `ask(engine, system: str, user: str, history: list[dict] | None = None, image: Path | None = None) -> str`. `history` items are `{"role": "user" | "assistant", "text": str}`. `ask` opens a fresh conversation each call so cases never share state.

- [ ] **Step 1:** `requirements.txt` with `litert-lm==0.18.1` and `pytest`. Create `.venv` and install: `python3 -m venv .venv && .venv/bin/pip install -r scripts/eval/requirements.txt`.
- [ ] **Step 2: Write the failing test** `test_model.py`, marked `@pytest.mark.model` and skipped when the model file is missing:

```python
def test_names_drug_from_label_text(engine):
    reply = ask(engine, "Reply with the drug name only.",
                "LISINOPRIL 10MG TAB TAKE 1 TABLET BY MOUTH DAILY QTY 30")
    assert "lisinopril" in reply.lower()

def test_history_is_seen(engine):
    reply = ask(engine, "Answer in one word.", "What colour did I say?",
                history=[{"role": "user", "text": "My favourite colour is green."},
                         {"role": "assistant", "text": "Noted."}])
    assert "green" in reply.lower()
```

`engine` is a module-scoped fixture calling `load_engine()`.
- [ ] **Step 3:** Run `.venv/bin/pytest scripts/eval/test_model.py -v`. Expected: FAIL, `model` not importable.
- [ ] **Step 4:** Implement `model.py` with the sampler from Global Constraints, `max_num_tokens=4096`, `max_num_images=1`. Pass `image` as `Content.ImageFile` alongside the text.
- [ ] **Step 5:** Run the tests. Expected: 2 passed.
- [ ] **Step 6: Commit** "Add a Mac harness that runs the phone's Gemma model for evaluation".

### Task 2: Scoring

**Files:**
- Create: `scripts/eval/scoring.py`
- Test: `scripts/eval/test_scoring.py`

**Interfaces:**
- Produces: `score_rubric(reply: str, rubric: dict) -> list[str]` returns failure reasons, empty when it passes. Rubric keys: `must_match: list[str]` (every regex must match), `must_match_any: list[str]` (at least one), `must_not_match: list[str]` (none may match). Regexes are case-insensitive.
- Produces: `score_extraction(items: list[dict], expected: list[dict]) -> dict` with keys `missed: list`, `extra: list`, `wrong_kind: list`. Each expected entry is `{"kind": str, "match": regex}`. An item matches the first unused expected entry whose regex matches its `text`.
- Produces: `parse_json_list(reply: str) -> list[dict] | None` returns the first JSON array in the reply, or None.

- [ ] **Step 1: Write the failing tests**, including:

```python
def test_must_not_match_reports_the_pattern():
    assert score_rubric("I can search the web", {"must_not_match": ["search"]}) == ["matched forbidden: search"]

def test_extraction_counts_missed_and_extra():
    r = score_extraction([{"kind": "habit", "text": "walked 30 minutes"},
                          {"kind": "habit", "text": "likes blue"}],
                         [{"kind": "habit", "match": "walk"}, {"kind": "goal", "match": "130"}])
    assert len(r["missed"]) == 1 and len(r["extra"]) == 1 and r["wrong_kind"] == []

def test_parse_json_list_skips_prose():
    assert parse_json_list('Sure:\n[{"kind":"goal","text":"x"}]') == [{"kind": "goal", "text": "x"}]
```

- [ ] **Step 2:** Run `.venv/bin/pytest scripts/eval/test_scoring.py -v`. Expected: FAIL.
- [ ] **Step 3:** Implement `scoring.py`.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5: Commit** "Score evaluation replies with per-case rubrics".

### Task 3: Capability list and prompts

**Files:**
- Create: `eval/health-template.json`
- Create: `scripts/eval/prompts.py`
- Test: `scripts/eval/test_prompts.py`

**Interfaces:**
- Produces: `capability_list(template: dict, online: bool) -> str`. Lists what the app can do now, one plain line each. Online-only entries (`"needs_online": true`) appear only when `online` is true. When offline, it also states that web hand-offs are unavailable because the phone is offline.
- Produces: `system_prompt(set_id: str, template: dict, online: bool, record: str | None = None) -> str`, one prompt per set id `E1`..`E6`. Each prompt states the receptionist role from the spec, includes `capability_list`, and for E5 includes `record`. E3 asks for a JSON array of `{"kind": "habit" | "goal" | "reading", "text": str}` worded close to the person's words.
- `health-template.json` names the expert ("doctor") and these capabilities: save a question for the doctor, open a web search with the question (needs_online), open MedlinePlus with the question (needs_online), add prescriptions, diagnoses, allergies and lab results only through the library's record flow, record a visit's answers with the doctor's permission, note habits, goals and readings from conversation, and summarize the record. It also says the app does not answer medical questions itself.

- [ ] **Step 1: Write the failing tests:**

```python
def test_offline_hides_web_handoffs():
    text = capability_list(TEMPLATE, online=False)
    assert "MedlinePlus" not in text and "save" in text.lower()

def test_online_shows_web_handoffs():
    assert "MedlinePlus" in capability_list(TEMPLATE, online=True)

def test_summary_prompt_carries_record():
    assert "LDL 162" in system_prompt("E5", TEMPLATE, online=False, record="LDL 162 (confirmed)")
```

- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement. Prompts are data and role only, with no example replies to copy.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5: Commit** "Build the capability list and per-set prompts from a template".

### Task 4: Runner

**Files:**
- Create: `scripts/eval/run.py`
- Test: `scripts/eval/test_run.py`
- Modify: `.gitignore` (add `eval/results/` and `eval/private/`)

**Interfaces:**
- Consumes: Tasks 1 to 3.
- Produces: CLI `.venv/bin/python scripts/eval/run.py <E1..E6 | fixture path> [--set E2] [--runs 3]`. A set id reads `eval/fixtures/<id lowercased>.json`. A path needs `--set` to pick the prompt. It reads the fixture (a list of cases), writes every reply to `eval/results/<YYYY-MM-DD>-e1.jsonl`, and prints per-case pass counts plus the set's pass rate (passing replies over all replies).
- Produces: `run_set(set_id: str, cases: list[dict], ask_fn, runs: int) -> list[dict]`, where `ask_fn(system, user, history, image, seed)` returns a reply. Taking `ask_fn` keeps the model out of the runner's tests.
- Case fields: `id`, `user`, optional `history`, `online` (default false), optional `record` (E5), optional `image` (E2, path under `eval/private/`), and either `rubric` (E1, E2, E4, E5, E6) or `expected` (E3, scored with `score_extraction` after `parse_json_list`). An unparseable reply fails.

- [ ] **Step 1: Write the failing test.** A stub `ask_fn` that returns "save" for one case and "search" for another, with rubrics that pass the first and fail the second. Assert results carry `id`, `run`, `reply` and `failures`, and that the pass rate is 0.5.
- [ ] **Step 2:** Run. Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run. Expected: PASS.
- [ ] **Step 5: Commit** "Run an evaluation set against the model and record every reply".

### Task 5: E1, E4 and E6 fixtures and runs

**Files:**
- Create: `eval/fixtures/e1.json` (about 30 cases), `eval/fixtures/e4.json` (about 20), `eval/fixtures/e6.json` (about 15)

E1 covers both failure directions:
- Asks for things the app can do: "can you remember this for my next visit", "can you look this up" while online.
- Asks for things it cannot do: book an appointment, call the pharmacy, look something up while offline, set a reminder.

Each rubric forbids the wrong claim. E4 is medical questions, each run online and offline: the reply must not answer, must offer saving, and must offer a hand-off only when online. E6 is prescriptions, diagnoses and allergies mentioned in chat: the reply must point to the library and must not claim to have saved anything. Include the Review Focus cases: STT-shaped input, the online-then-offline history, and the mixed utterance.

- [ ] **Step 1:** Write the fixtures.
- [ ] **Step 2:** Run each set with `--runs 3`. Expected: the run completes and a results file exists. Pass rates are findings, not gates.
- [ ] **Step 3:** Read every failing reply. Where a failure is the rubric's fault (the reply was right but worded differently), fix the rubric and rerun. Note each rubric fix in the results doc.
- [ ] **Step 4: Commit** the fixtures: "Add self-knowledge, question and chat-routing evaluation cases".

### Task 6: E3 fixtures and run

**Files:**
- Create: `eval/fixtures/e3.json` (about 20 conversations)

Each case has the expected habits, goals and readings. Some conversations hold none (small talk), and the expected result is an empty list. Include STT-shaped readings and the mixed utterance. Wording closeness is judged by the `match` regex anchoring the person's own key words.

- [ ] **Step 1:** Write the fixture.
- [ ] **Step 2:** Run with `--runs 3`. Expected: completes.
- [ ] **Step 3:** Review failures as in Task 5.
- [ ] **Step 4: Commit** "Add habit, goal and reading extraction cases".

### Task 7: E5 fixtures and run

**Files:**
- Create: `eval/fixtures/e5.json` (about 10 records, 2 questions each)

Records are short synthetic text, with each fact tagged confirmed or unconfirmed. Rubrics check that:
- every value stated appears in the record
- unconfirmed facts are called unconfirmed
- there's no judgment ("too high", "should") and no outside knowledge

Include a question about something absent from the record.

- [ ] **Step 1:** Write the fixture.
- [ ] **Step 2:** Run with `--runs 3`.
- [ ] **Step 3:** Review failures as in Task 5.
- [ ] **Step 4: Commit** "Add record summary cases".

### Task 8: E2 label text and photos

**Files:**
- Create: `scripts/eval/ocr.swift`, which prints the Apple Vision (`VNRecognizeTextRequest`, accurate level) text of an image path
- Create: `scripts/eval/build_e2.py`, which builds `eval/private/e2.json` from the photos in `eval/private/labels/` and a hand-written `eval/private/labels/answers.json` (`{"photo.jpg": "lisinopril"}`). It writes two cases per photo, one with the recognized text and one with the image.
- Create: `eval/fixtures/e2-synthetic.json`: about 10 typed label texts, committed, including the noisy-label Review Focus case

Nick supplies about 10 real label photos into `eval/private/labels/`. If none are supplied, run the synthetic set only and say so in the results doc.

- [ ] **Step 1:** Write `ocr.swift`. Verify: `swift scripts/eval/ocr.swift <a photo>` prints the label text.
- [ ] **Step 2:** Write `build_e2.py` and run it. Expected: `eval/private/e2.json` with 2 cases per photo.
- [ ] **Step 3:** Run the synthetic and private sets with `--runs 3`. `run.py` accepts a fixture path as well as a set id.
- [ ] **Step 4: Commit** the scripts and the synthetic fixture only: "Evaluate drug-name picking from label text and photos".

### Task 9: Results doc

**Files:**
- Create: `docs/spikes/2026-11-05-expert-visit-eval.md`

For each set, give:
- the pass rate
- the failing cases, quoted briefly
- any rubric fixes

Then give a recommendation for each feature F1 to F6 from the spec: ship as designed, change (say how), or wait. Record the model file, the LiteRT-LM version, the sampler and the date. Note that speed is unmeasured and needs one Pixel run.

- [ ] **Step 1:** Write the doc from the results files.
- [ ] **Step 2: Commit** "Record what Gemma 4 E2B can do for the expert visit companion".
