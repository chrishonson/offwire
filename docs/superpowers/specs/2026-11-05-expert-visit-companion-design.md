# Design: Expert visit companion (gemma-voice)

Date: 2026-11-05
Status: DRAFT, awaiting Nick's approval
Replaces: the blood pressure draft of 2026-11-04 (withdrawn, never committed)
Builds on: `docs/designs/local-ai-library.md` (collections, templates, items, exporter C8)

## Intent

The app acts like a receptionist between a person and an expert they see: a doctor first, later a mechanic, a vet, a lawyer or a school. It keeps the facts straight between visits. It records what the expert said, what the person does and wants, and what the person wants to ask next time. It helps them ask those questions at the visit and keeps the answers. Everything stays on the phone.

The first user is an older person with ordinary health issues who wants them kept private. Privacy is the reason to use a local model: 77% of US adults worry about medical information shared with AI tools (KFF via Darwin Research), and almost half would bar chatbot companies from keeping health data at all (Consumer Reports).

The app works from facts. It repeats what the expert said and summarizes what is in the record plus what is obvious from it. It does not judge, advise or supply expert knowledge of its own.

## Goals

- G1 A private, factual record of one person's dealings with one kind of expert, with the source of every expert fact kept.
- G2 Nothing to learn. The person talks normally. There are no command words.
- G3 The person arrives at a visit with their questions ready and leaves with the answers recorded.
- G4 The app is always right about what it can and cannot do.

## Non-goals

- Answering expert questions (medical, legal, mechanical) from the model's own knowledge.
- Diagnosing, judging readings or advising.
- Medication schedules, reminders or device sync.
- Cloud backup or sharing beyond the user-started export (C8).
- A general template engine beyond what the first template needs (see "Templates").
- Safety gates. The fixed safety responses restored on 2026-11-04 are a separate track with their own design. This design neither changes nor depends on them.

## Anti-goals

These are failures even if the feature works.

- The app says it cannot do something it can, or offers something it cannot do. (Nick named this the worst failure.)
- An expert fact enters the record without the person confirming it.
- The model writes or rewords any number in an expert fact.
- The model answers an expert question from its own knowledge.
- Fixed reply text. The model phrases every reply from data it is given.
- Any network request the person did not start with a tap.

## Constraints

- Strict MVI. New behavior lives in services behind interfaces (C12). `ChatStore` routes intents and does not grow storage or interpretation logic.
- Kotlin Multiplatform. Storage and logic in `core/commonMain` with unit tests. iOS keeps building (`run-gate.py ios`).
- Gemma 4 E2B on device, via LiteRT-LM.
- Text recognition on device: ML Kit on Android (already in `MlKitOcrRecognizer`, with its upload backend removed), Apple Vision (`VNRecognizeTextRequest`) on iOS behind the same recognizer interface.
- Minimum 60% unit test coverage for new code. Tests hit real behavior.
- Library content stays out of logs.

## Step zero: evaluation on the Mac

Nothing below is built until an evaluation shows what Gemma 4 E2B can do. Results decide which features ship as designed, which change, and which wait.

- Runs on the Mac, against the same model file the phone uses (`~/models/gemma4/gemma-4-E2B-it.litertlm`) under the LiteRT-LM desktop runner. Getting that runner working is the first task. If it cannot run this model on macOS, stop and report before choosing another runtime, because a different runtime or quantization would weaken how well Mac results predict the phone.
- Speed is not measured on the Mac. One later Pixel run confirms latency.
- Each set is inputs with known right answers, scored by a script. Fixtures and the runner live in the repo (`scripts/` beside `answer-eval.py`, fixtures under a new eval folder). The existing `answer-eval.py` uses a fake model and is not reused for scoring.

The six sets:

- E1 Self-knowledge. Given the capability list and live state (below), does Gemma say correctly what it can and cannot do? Scores both failure directions: claiming a missing ability and denying a present one.
- E2 Drug name. Given recognizer text from real labels, and separately the photo itself, does it pick the right drug name?
- E3 Habits and goals. Given ordinary conversation, does it extract the habits and goals, only those, worded close to what was said?
- E4 Questions for the expert. Does it recognize a medical question, decline to answer it, and offer the right options for the live state?
- E5 Summaries. Given a record, does it state only what is there plus what is obvious, never judge or add knowledge, and keep unconfirmed answers marked unconfirmed?
- E6 Expert facts in chat. When a prescription or diagnosis comes up in conversation, does it point the person to the library instead of recording it?

Output: a results doc under `docs/spikes/` with per-set accuracy and the failing cases, and a recommendation per feature. Nick reviews it before the plan is written.

## The record

Each collection belongs to one person and one template (health first). It holds two kinds of fact, with different rules.

### Expert facts

Anything the expert said or wrote: prescriptions, diagnoses, allergies, lab results, visit instructions, recorded answers.

- Enter only through the record flow in the library, never from chat.
- Confirmed by the person before they count as record facts.
- Keep their source attached: the photo, or the audio.

### Personal facts

The person's own habits and goals ("walked 30 minutes", "my goal is under 130").

- Extracted by Gemma from ordinary conversation and saved without confirmation.
- Worded close to what the person said.
- Visible and revisable in the library at any time.

## Features

### F1 Record flow for expert facts

A distinct set of library pages that interviews the person, one thing per page, in large type.

1. Asks for a photo of the label or paper, if they can take one. Without a photo the flow continues by voice or typing.
2. Text is recognized on the phone.
3. One fixed field: the drug name (or the title, for other kinds of fact). Gemma picks it from the text or the photo. The person confirms or changes it.
4. Everything else is one free-text box, prefilled with the recognizer's text word for word. No model rewording. The person changes it by voice or typing.
5. Saved only when the person confirms. The photo stays attached.

One flow serves every kind of expert fact. The page wording changes by kind. (Call D2.)

### F2 Personal facts from conversation

Gemma notes habits and goals as the person talks and saves them to the active collection. A library view lists them for review and editing.

### F3 Questions for the expert

When the person asks an expert question, Gemma does not answer it. It offers what the capability list allows:

- O1 Save it to "questions for my doctor". Always available, works offline.
- O2 Open a web search with the question filled in. Only when the phone is online.
- O3 Open MedlinePlus with the question filled in. Only when the phone is online.

The app checks connectivity before Gemma offers anything. O2 and O3 send the question off the phone, so Gemma says so, and nothing leaves until the person taps.

### F4 The visit

- Before: the person reviews the question list on the phone.
- In the room: the person takes the doctor's permission to record as an explicit step, then goes down the list. The app keeps the audio and an on-device transcript (Parakeet) for each answer, attached to its question. Nothing is confirmed in the room.
- After: the person plays back each answer and confirms or fixes the transcript. A confirmed transcript becomes an expert fact with the audio as its source. Until then it is marked unconfirmed, and Gemma treats it as unconfirmed.

### F5 Summaries

Asked about their record ("what did the doctor say about my cholesterol?"), Gemma answers from the record: what is there plus what is obvious, citing whether each fact is confirmed. No judgment, no outside knowledge.

### F6 Expert facts mentioned in chat

When a prescription or other expert fact comes up in conversation, Gemma tells the person to add it in the library and does not record it.

## Capability list

Gemma knows what the app can do from data, never from fixed reply text. Code builds a capability list from the template's configuration and live state, and passes it in Gemma's context each turn. Example entries: questions can be saved for the doctor. The phone is online, so a web search or MedlinePlus hand-off is possible. Prescriptions are added only in the library. A visit can be recorded with permission. Gemma phrases every offer and every refusal from this list. E1 measures whether that works.

## Templates

The pattern generalizes to any expert: expert facts, personal facts, questions, the visit, and the model's limits. A mechanic visit fits as is: the estimate is an expert fact, "grinding when I brake" a personal fact, "is this urgent?" a question.

The template file (existing `templates/*.json`) names the expert, the kinds of expert fact and their fixed field, the hand-off sites, and the capability entries. Health is built first. Nothing health-specific goes in code: the mechanic case is the check. No second template is built in this work. (Call D1.)

## Roadmap

- O4 Built-in health reference: MedlinePlus public-domain topics shipped with the app and searched on the phone, quoted word for word. Fully offline.
- More templates: mechanic, vet, lawyer, school (IEP and 504 meetings), caregiver.

## Prerequisites from the Pixel test run

- P-F14 Health collections cannot be created: the dialog requires a person ID and has no field for it. Blocks everything. Add the field for the health template in both apps.
- P-F12 Spoken questions bypass `LibraryAnswerPipeline`. F3 and F5 need the voice loop to route library questions through it.

## Calls made in this design (not yet confirmed by Nick)

- D1 Build health on the template structure, but only what health needs. The mechanic case checks nothing is hardcoded.
- D2 One record flow for every kind of expert fact, with wording by kind, rather than one flow per kind.
- D3 Apple Vision for text recognition on iOS rather than ML Kit's iOS SDK: built in, on device, no extra library, no Google usage logging.
- D4 The visit's permission step is an explicit tap, because several US states require everyone's consent to record.
- D5 Classic app first, on Android and iOS. The console app gets the same `core` behavior and its screens later.
- D6 A reading the person reports ("132 over 85 this morning") is a personal fact: saved from conversation without confirmation. The person can review and revise their readings in the library, like any personal fact. No trend math or charts in this work.
- D7 No new export work. The visit happens on the phone, so the existing exporter (C8) is left as is.

## Left to the builder

- Storage for questions, answers, audio and personal facts: new item kinds or tables, as long as the FTS index and health protection cover them.
- The capability list's format in the prompt.
- How Gemma's extraction of personal facts is triggered in the voice loop.
- Eval fixture sizes and scoring scripts, within the six sets above.
- Screen layout within the existing themes.
