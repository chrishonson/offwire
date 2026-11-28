# Gemma 4 tool-calling eval

Date: 2026-11-10. Branch: `claude/gemma-eval`, started from `claude/demo-hardening` at 3d11b90.

## Status

The suite is built and its scorer is unit-tested. It has not been run against a model yet. That needs the
`.litertlm` file, which is on Nick's Mac and Pixel and not in this repo or the cloud machine that wrote it
(Hugging Face is blocked there). No numbers below are model results until a run fills them in.

## What it measures

`scripts/eval/tool_eval.py` runs 48 cases from `eval/fixtures/tool-eval.json` under two setups:

| Setup | Tools the model sees | Notes in the prompt | Source |
|---|---|---|---|
| `shipped` | `save_note` | yes, the newest 40 | the app's `systemInstruction(notes = ...)`, via `notes.py`'s mirror |
| `proposed` | `save_note`, `read_notes`, `do_action(action, arg)` | no, `read_notes` fetches them | Nov 10 survey pick; prompt and tool text are a draft, not in the app |

`do_action.action` is an enum of `remind_me` (timer, minutes parsed by the app), `send_notes_to_me` (email
composer to self), `navigate_to` (Maps) and `whats_on` (read-only calendar, today or tomorrow). Each takes one
short string `arg`.

Cases, by category (each runs under both setups unless noted):

| Category | Cases | What passes |
|---|---|---|
| no_call | 12 | chit-chat, brainstorming, general questions: no call, no claim of an action |
| over_call | 9 | tool words without a tool request ("I remember when...", "save money", "half note", "Thanks!" after a save): no call |
| save | 9 | one `save_note`, note text holds the key detail, one spoken confirmation |
| recall | 4 | shipped: no call, answer from the prompt. proposed: `read_notes`, then the exact value |
| action | 9 | proposed: the right `do_action` and arg. shipped: no call, and "cannot" for timers and email |
| cannot | 4 | alarm at a clock time, text, add an event, call: no call, says it cannot |
| multi | 1 | save plus timer in one message |

Under `shipped`, 38 of 48 cases expect no call; under `proposed`, 25 of 48.

### How a turn runs

It streams like `LiteRtLlmEngine.streamReply`: automatic tool calling off, the eval runs each call against a fake
phone (`ToolBox`), sends the result back, at most `MAX_TOOL_ROUNDS` (3) rounds, and repeats of an identical call
get `REPEATED_TOOL_CALL_RESULT`. Every streamed pass is kept. Unit tests check those constants and the
`save_note` text against the Kotlin source, so a change in the app breaks the mirror loudly.

Two spoken views are scored, because the double "I saved your note" hid from `notes.py`'s blocking call:

- **raw**: all passes joined, what `main` speaks (the library streams text before and after a call).
- **guarded**: what `ToolTurn` on `claude/demo-hardening` speaks (text after a call is dropped once anything was
  said; a silent call gets "I saved that to your notes.").

### Scores

Per case: missing call, unwanted call, wrong tool, bad args (regex per argument), repeated call, a claim of an
action with no call, and double confirmation (two or more sentences saying something was done, after a call that
did something). Pass or fail uses the guarded text. Per setup and model, in `*.summary.json`:

- `call_precision`, `call_recall` over expected vs called tool names
- `over_call` rate over no-call cases, `wrong_tool`, `bad_args`, `repeat_call`, `false_claim`
- `double_confirm_raw` vs `double_confirm_guarded`, and `spoke_both_sides` (text before and after the call)
- `ttft_s_median` (wall clock to the first streamed piece) and `decode_tok_s_median` (first pass's text tokens over
  its streaming time; tool-call tokens are not counted, so treat it as a lower bound)
- pass counts per category

## Running it on the Mac

```
cd ~/path/to/gemma-voice
git fetch origin claude/gemma-eval && git checkout claude/gemma-eval
python3 -m venv .venv && .venv/bin/pip install -r scripts/eval/requirements.txt
.venv/bin/python -m pytest scripts/eval -m "not model" -q          # scorer tests, no model
.venv/bin/python scripts/eval/tool_eval.py --dry-run                 # prompts and schemas only
.venv/bin/python scripts/eval/tool_eval.py --runs 3                  # E2B, both setups
.venv/bin/python scripts/eval/tool_eval.py --runs 3 \
    --model ~/models/gemma4/gemma-4-E2B-it.litertlm \
    --model /path/to/gemma-4-E4B-it.litertlm                         # E2B and E4B
.venv/bin/python scripts/eval/tool_eval.py --config proposed --only act- --runs 5   # one slice
```

The default model is `model.DEFAULT_MODEL_PATH` or `$GEMMA_MODEL`. Output goes to `eval/results/` (git-ignored):
`<date>-tool-eval-<setup>-<model>.jsonl` (every pass, call and score), `.summary.json`, and `.md` (summary plus
each failure with what the person would hear). Seeds are 0..runs-1 with the app's sampler (temperature 1.0,
top-k 64, top-p 0.95), so runs vary on purpose.

Mac CPU speed says nothing about the Pixel. Neither does any cloud CPU number. Read the speed fields only to
compare E2B and E4B on the same machine.

## Running it from a cloud session (no Mac, no phone)

`--endpoint` sends the same turns to any OpenAI-compatible server instead of LiteRT-LM. It is standard-library
Python, so a cloud session needs no installs (pytest only for the tests):

```
python3 scripts/eval/tool_eval.py --endpoint http://HOST:PORT/v1 --endpoint-model gemma-4-e2b --runs 3   # llama-server --jinja, Ollama, LM Studio
GEMINI_API_KEY=... python3 scripts/eval/tool_eval.py --endpoint gemini --endpoint-model gemma-4-26b-a4b-it --runs 3
python3 -m pytest scripts/eval/test_hosted.py -q                    # adapter tests, fake server, no model
```

Results are named `hosted-<model>` so they never overwrite the Mac's. Set any key as an environment secret of the
cloud environment, never in a file. Rate limits (429) are retried with backoff.

What a hosted run can and cannot tell you (Nov 14, 2026):

- No hosted service checked serves Gemma 4 E2B or E4B with tool calling. The Gemini API serves Gemma 4 only as
  26B A4B and 31B. Fireworks lists E4B on dedicated GPUs only, with function calling marked not supported.
- So a hosted run is either a bigger Gemma (fine for prompt and tool-text iteration and for smoke-testing the
  harness, not for E2B's over-call numbers) or E2B/E4B on a server you run, such as llama-server or Ollama at
  home behind a tunnel, whose hostname the cloud environment's network settings must allow.
- Even E2B on llama.cpp differs from the phone: its own quant and chat template parse tool calls, not LiteRT-LM's,
  so parse-error counts can differ. TTFT includes the network; decode speed is not measured.

## Measuring on the Pixel

Tool behavior on the phone uses the real engine, so check the eval's guarded results there:

```
adb -s <PIXEL_SERIAL> logcat -s GemmaVoice | grep -E "Tool call|Kept the reply|Completed generation"
```

Say a few save cases ("Remember the guest wifi password is sunflower42") and a few traps ("What's a good way to save
money on groceries?"). One `Tool call: save_note` per save, none per trap, and the reply spoken once.
"Kept the reply after a tool call silent" means the model confirmed on both sides and ToolTurn caught it.

Speed: the in-app readout on `claude/demo-hardening` shows TTFT and tok/s after each reply. For repeatable numbers
use the benchmark activity, with the serial pinned as AGENTS.md says:

```
adb -s <PIXEL_SERIAL> shell am start -n com.chrishonson.gemmavoice.classic/com.chrishonson.gemmavoice.bench.LlmBenchmarkActivity --es backend gpu --ei runs 3
adb -s <PIXEL_SERIAL> shell cat /sdcard/Android/data/com.chrishonson.gemmavoice.classic/files/bench/llm-gpu.json
```

The proposed setup can't run on the Pixel until `read_notes` and `do_action` exist in the app.

## What did not run

- No model inference. The model is not in the repo (only the `.onnx` speech and voice files are in LFS). The app
  downloads `gemma-4-E2B-it.litertlm` from Hugging Face on first run (`ModelSpec.kt`). On the cloud machine,
  `curl https://huggingface.co/...` failed with "CONNECT tunnel failed, response 403" from its proxy, and Nick
  then asked for the suite only.
- The LiteRT-LM runtime itself does install and load on Linux x86_64 (`litert-lm==0.18.1` from PyPI), and the
  adapter was exercised against its real `Message` and `ToolCall` types with a stub engine.
- No Gradle, device or iOS gates: this change touches only `scripts/eval`, `eval/fixtures` and this doc.

## Results

Fill in from `eval/results/*.summary.json` after a run.

| Model | Setup | Pass | Precision | Recall | Over-call | Wrong tool | Bad args | False claim | Double (raw) | Double (guarded) | TTFT | tok/s |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| E2B | shipped | | | | | | | | | | | |
| E2B | proposed | | | | | | | | | | | |
| E4B | shipped | | | | | | | | | | | |
| E4B | proposed | | | | | | | | | | | |

## Intent router

Nick's E2B run on his Mac (Nov 10, results not in the repo) over-called on 24% of no-call turns with the shipped
setup and 33% with three tools, and the three-tool setup never called `read_notes`. So the next step decides
before Gemma replies whether a turn needs a tool at all. `scripts/eval/router.py` holds the router and
`scripts/eval/router_eval.py` compares it with the shipped app.

1. Phrase rules on the transcript, after the wake word and "can you" are stripped. A request that starts with a
   known phrase ("remember", "set a timer", "directions to", "what's on my calendar") routes to that tool, and the
   words after the phrase are its argument. Timer durations are parsed in Python ("an hour" is 60).
2. Near-misses: a tool word somewhere ("I remember when", "save money", "half note") but no rule hit. Only these
   go to a gate, which answers NONE, SAVE or ACTION.
3. The reply. A rule save is saved by the app, which says "Saved." with no model call. A rule timer, email or route
   runs do_action and says a fixed line. A rule calendar check reads the calendar, then Gemma answers from it with
   no tools. A gate SAVE or ACTION, or "save that", shows Gemma that one tool only. NONE shows Gemma no tool
   schemas. `read_notes` is gone: the notes are in every prompt, as in the shipped app.

Setups, on one warm engine with the same seeds (0..runs-1):

| Setup | Router | Gate |
|---|---|---|
| `shipped` | none | none, save_note always offered |
| `rules` | phrase rules | near-misses are NONE |
| `rules_e2b_gate` | phrase rules | the loaded Gemma, separate conversation, about 150 tokens of examples (5 of 8 NONE), greedy, 2 output tokens, first word read |
| `rules_fg_gate` | phrase rules | FunctionGemma 270M Mobile Actions with save_note and do_action as its tools: a save_note call is SAVE, any other call ACTION, text NONE |

Per turn, the rows log router time, the reply's time to first token, their sum, approximate prompt tokens for the
reply and the gate call, the expected and routed intent, the route source (rule, rule-ref, near-miss, plain) and
the gate's raw answer. A LiteRT-LM `RuntimeError` in the reply becomes a `parse_error` row; in a gate it counts as
NONE and is logged on the row. Rows are written to disk as they are scored. The summary adds the intent table
(expected vs routed), intent match, route sources, gate calls and parse errors, and timings split by tool and
no-tool turns.

### What the rules do without a model

Checked here with no model, the rules alone route 47 of the 48 eval cases to the expected intent. That number is
not a result: the rules were written while reading those cases. The multi-request case misses, since a turn gets
one route ("remember X and remind me in 10 minutes" saves the whole sentence as a note).

`eval/fixtures/tool-eval-holdout.json` has 24 phrasings written after the rules and not used to tune them. Rules
alone route 9 of 24 correctly:

- 7 of 14 requests reach the gate as near-misses ("Add milk to my notes", "Give me a ten minute timer",
  "Navigate home"), so a gate can still catch them.
- 3 requests have no tool word and route straight to plain chat ("Can you hang onto this for me", "Am I busy this
  afternoon?", "What's on the agenda for tomorrow?"). No gate sees them.
- 4 of 10 no-call messages hit a rule and go to a tool with no gate check: "Remember that movie with the talking
  dog?", "Save the date cards, are they still a thing?", "Log cabins are cozy...", "Directions to make pancakes?".
  All four are questions, so sending question-shaped rule hits to the gate is one candidate fix; it is not built.

### Running it on the Mac

FunctionGemma is gated on Hugging Face: accept the Gemma license on the model page once while signed in, then:

```
pip install -U "huggingface_hub[cli]"
hf auth login
hf download litert-community/functiongemma-270m-ft-mobile-actions mobile_actions_q8_ekv1024.litertlm \
    --local-dir ~/models/functiongemma
```

`mobile_actions_q8_ekv1024.litertlm` is the CPU build; the repo's other two files target Tensor G5 and G6 NPUs.
On Nov 10 the download answered "requires approval": access to that repo may wait on a person, so the commands
below run everything else first and add the FunctionGemma setups once the file is there.

```
cd ~/git/gemma-voice-eval
git fetch origin claude/gemma-eval && git checkout claude/gemma-eval && git pull
.venv/bin/python -m pytest scripts/eval -m "not model" -q
.venv/bin/python scripts/eval/router_eval.py --runs 3 --model ~/models/gemma4/gemma-4-E2B-it.litertlm
.venv/bin/python scripts/eval/router_eval.py --runs 3 --fixture eval/fixtures/tool-eval-holdout.json \
    --model ~/models/gemma4/gemma-4-E2B-it.litertlm
```

Without `--functiongemma` those run shipped, rules, rules_e2b_gate, shipped_cd and rules_e2b_gate_cd. Once the
FunctionGemma file is downloaded, add its two setups to the same tables:

```
.venv/bin/python scripts/eval/router_eval.py --runs 3 --setups rules_fg_gate,rules_fg_gate_cd \
    --model ~/models/gemma4/gemma-4-E2B-it.litertlm \
    --functiongemma ~/models/functiongemma/mobile_actions_q8_ekv1024.litertlm
```

Output: `eval/results/<date>-router-eval-<model>-<setup>.jsonl` per setup, `<date>-router-eval-<model>.summary.json`
and `.md` with the comparison table first. The holdout fixture's files end in `-tool-eval-holdout` (before Nov 10's
second commit they had the same names as the main run's and overwrote them). A later run with other `--setups` adds
its rows to the same day's table rather than replacing it. Without `--functiongemma` the FunctionGemma setup is skipped; pick
setups with `--setups shipped,rules`. The stock Mobile Actions fine-tune was trained on other tools (flashlight,
email, maps), so how it reads save_note and do_action is part of what this measures.

### Constrained tool-call decoding

Google AI Edge Gallery turns on LiteRT-LM's constrained decoding for tool calls
(`ExperimentalFlags.enableConversationConstrainedDecoding = true` before `createConversation` in Kotlin 0.18.1; in
Python, `create_conversation(constrained_decoding_config=ConstrainedDecodingConfig(enable=True))`). A tool call is
then decoded against the tool schemas, so it comes out well-formed. It says nothing about whether to call, so it
should move parse errors and bad arguments more than over-calls.

Any setup with `_cd` on its name runs with it on for each reply conversation that has tools: `shipped_cd`,
`rules_cd`, `rules_e2b_gate_cd`, `rules_fg_gate_cd`. The gates are unchanged. `shipped_cd` is the one-flag change to
the app as it is; `rules_e2b_gate_cd` is the router with it. Both run by default. To add only those two to a run
that already has the other four:

```
.venv/bin/python scripts/eval/router_eval.py --runs 3 --setups shipped_cd,rules_e2b_gate_cd \
    --model ~/models/gemma4/gemma-4-E2B-it.litertlm
```

Not run here: no model on this machine. The 0.18.1 Python wheel has the option and its C entry point; tests check
that the adapter passes it only when asked and only with tools.

## Embedding router (alternative to the rules)

Branch `claude/embedding-router`, cut from `claude/gemma-eval`. `scripts/eval/embed_router.py` routes with a small
sentence-embedding model instead of phrase rules, in the style of aurelio semantic-router, whose authors report
that fitting per-route thresholds with "none" examples raised their accuracy from 35% to 89%.

- **Model:** all-MiniLM-L6-v2 as ONNX (22M parameters, 384 dims; 90 MB fp32, 23 MB after
  `embed_router.py quantize`). The app already depends on ONNX Runtime 1.31.1, and the tokenizer is WordPiece
  (`vocab.txt`), so a Kotlin port needs no new runtime.
- **Routes:** `eval/router/routes.json` has example utterances for SAVE, ACTION and NONE (165 in all). NONE includes
  question-shaped near-misses ("remember the good old days when gas was a dollar?", "how do I save a file as a
  PDF"), recall questions about saved notes, and requests the app can't do (alarms, texts, calls, adding events).
- **Embedding:** the whole message averaged with its first 3 or 4 words. MiniLM follows the topic, so "Save this
  idea: a voice app that reads recipes" lands near cooking; the request is nearly always in the first words.
- **Scorers:** `knn` scores a route by the mean similarity of its closest examples (semantic-router's way).
  `linear` is a 384x3 softmax layer trained on the same examples. Either way, SAVE and ACTION fire when they beat
  NONE by a fitted threshold, and both fire only when the message joins two requests ("and", "then").
- **Fitting:** thresholds, head length and top_k or l2 are chosen on `eval/router/fit.json` (104 labeled
  messages) plus intent match on `tool-eval.json`. The holdout fixture is never used to choose anything. The
  configs, including the linear weights, are committed as `eval/router/embed-all-MiniLM-L6-v2[-int8]-<scorer>.json`.
  The tests check that no route example or fit item is a fixture case.

`router_eval.py` gains three setups: `embed_knn` and `embed_linear` (embedding only, no rules) and `embed_rules`
(rules first, `embed_linear` instead of a gate, and on a question the embedding can overrule a rule hit, so
"Remember that movie with the talking dog?" goes to plain chat). An embedding route has no arguments, so Gemma sees
the routed tool, or both tools for SAVE+ACTION. Rows add `embed` (intent, route scores, embedding time).

### Routing only, measured in the cloud (no Gemma)

`python scripts/eval/embed_router.py score`, fp32 model. Intent match, then over-calls on no-call cases:

| Setup | tool-eval (48) | over-call | holdout (24) | over-call |
|---|---|---|---|---|
| rules | 47 | 0/29 | 9 | 4/10 |
| embed_knn | 46 | 1/29 | 18 | 3/10 |
| embed_linear | 45 | 3/29 | 20 | 1/10 |
| embed_rules | 44 | 3/29 | 20 | 1/10 |

- The tool-eval column is not a result: those cases helped pick the settings, and the rules were written against
  them. The holdout column is the fair one, and it has only 24 cases.
- The int8 model gives the same holdout picture (knn 19, linear 20, embed_rules 20; over-calls 3, 1, 1) but more
  over-calls on tool-eval (linear 5/29: "Thanks!" and "What else could we try?" go to SAVE).
- Settings move the numbers. Across the sweep, the knn holdout score ran from 16 to 21 of 24 with small changes
  in head length or top_k, so a two-case difference between setups is noise.
- Misses that remain on the holdout: "Timer, 5 minutes", "Add milk to my notes", "Navigate home" (no tool), and
  "I keep forgetting people's names, any tips?" (SAVE).
- Time: on the cloud's x86 CPU, one thread, one encoder run takes about 34 ms fp32 and 16 ms int8; embed_linear
  runs it twice (whole message and head). Not measured on a phone.

### Running it on the Mac

Use a separate worktree so the run on `claude/gemma-eval` is not disturbed, and start the Gemma runs after it ends,
since two runs at once would skew each other's timings.

```
cd ~/git/gemma-voice && git fetch origin
git worktree add ~/git/gemma-voice-embed claude/embedding-router
cd ~/git/gemma-voice-embed
python3 -m venv .venv && .venv/bin/pip install -r scripts/eval/requirements.txt

mkdir -p ~/models/all-MiniLM-L6-v2 && cd ~/models/all-MiniLM-L6-v2
curl -LO https://chroma-onnx-models.s3.amazonaws.com/all-MiniLM-L6-v2/onnx.tar.gz
shasum -a 256 onnx.tar.gz   # 913d7300ceae3b2dbc2c50d1de4baacab4be7b9380491c27fab7418616a16ec3
tar xzf onnx.tar.gz         # makes ~/models/all-MiniLM-L6-v2/onnx (model.onnx, tokenizer.json, vocab.txt)
cd ~/git/gemma-voice-embed

.venv/bin/python -m pytest scripts/eval -m "not model" -q
.venv/bin/python scripts/eval/embed_router.py score      # routing only, seconds; compare with the table above
.venv/bin/python scripts/eval/router_eval.py --runs 3 --tag embed \
    --setups shipped,rules,embed_knn,embed_linear,embed_rules
.venv/bin/python scripts/eval/router_eval.py --runs 3 --tag embed --fixture eval/fixtures/tool-eval-holdout.json \
    --setups shipped,rules,embed_knn,embed_linear,embed_rules
```

The tarball is the Chroma project's ONNX export of sentence-transformers/all-MiniLM-L6-v2 (its `config.json`
names that model). Output goes to `eval/results/<date>-router-eval-embed[-holdout]-<model>...`, so it does not
overwrite the rules run. Don't re-run `embed_router.py fit` before the Gemma runs: the committed configs are what
the table above measured. For the phone's int8 build, run `embed_router.py quantize` and add
`--embed-model-file model_int8.onnx` (the int8 configs are committed too).

## The embedding router in the app (claude/embed-router-app)

Nov 10 overnight runs on the Mac (E2B, 3 runs) picked `embed_linear`: on the 24 held-out cases it passed 79.2% with
10% over-calls and 83.3% intent match, against the shipped app's 86.1% pass, 19.6% over-calls and 56.9% intent
match. Turns with no tool came back about 200 ms sooner, since their prompt carries no tool schemas. Phrase rules
fell to 37.5% on the held-out phrasings; constrained decoding made no difference.

The app now runs that router before Gemma on every typed or transcribed turn (core `router/`):

- **Model:** the same fp32 `all-MiniLM-L6-v2` ONNX file the Mac scored with (SHA-256 `4f148ba8...393e6`), as an
  LFS asset at `core/src/androidMain/assets/router/all-MiniLM-L6-v2.onnx`, beside its `vocab.txt` and the fitted
  `embed-all-MiniLM-L6-v2-mlp.json` since Nov 11 (a test keeps that file equal to `eval/router/`). The cloud session could
  not upload LFS objects (GitHub's LFS verify call is refused through its proxy), so the model is not committed
  yet: `scripts/fetch-router-model.sh` puts it in place from `~/models/all-MiniLM-L6-v2/onnx` or the Chroma
  tarball, checking both hashes, and a commit from the Mac adds it to LFS. Without it the app still runs: the
  router fails to load, logs a warning, and every turn keeps save_note. int8 is 23 MB instead of 90 MB, but here
  it over-called 5 of 29 on the main fixture against 3 for fp32, so fp32 ships.
- **Routes:** NONE is a conversation with no tool schemas; SAVE (and SAVE+ACTION) has save_note alone. The app has
  no do_action, so ACTION turns are plain chat and the prompt's limits line has Gemma say it cannot. The prompts are
  `routedSystemInstruction` in `SystemInstruction.kt`, router_eval.py's `routed_prompt` with the app's limits. The
  notes stay in every prompt, so read_notes is not needed. Tools and the instruction are fixed per LiteRT-LM
  conversation, so a route change starts a fresh conversation seeded with the history; a run of chat turns stays on
  one.
- **Not routed:** audio and photo prompts (no words to read) keep save_note, as before. A router that fails to load
  or run logs a warning and the turn keeps save_note.
- **Off switch:** `AppConfig.intentRouter = false` in an app's `MainActivity` restores the shipped behaviour.
- **Readout:** the speed line ends with `route NONE 4 ms` (or `route ACTION as chat ...`); logcat tag GemmaVoice
  logs `Routed SAVE to SAVE_NOTE in ...ms {SAVE=..., ACTION=...}` for each turn.

Parity with Python: `scripts/eval/export_router_parity.py` writes `eval/router/parity-all-MiniLM-L6-v2-mlp.json`
(1,800 messages: both fixtures, every training sentence and every held-out sentence, plus 10 tokenizer edge
cases). The JVM test `RouterParityTest` checks the Kotlin normalizing, head words, split into pieces and tokenizer
ids against it exactly, and the scoring (hidden layer, pieces and the two-request rule) to 1e-4 on 112 messages
with Python's own vectors. On Nov 10, before the changes below, the Kotlin encoder on the JVM build of ONNX
Runtime 1.31.1 matched Python's intent on all 321 messages then in the file, margins within 1e-6, at a median of
4 ms per message on one cloud CPU thread. The Mac's 76 ms was Python padding every message to 128 tokens; the app
sends only the real tokens.

## The large held-out set and the second router (Nov 11)

The 24 held-out cases were too few to steer by, so `eval/router/heldout.jsonl` now has 601 sentences:

- **Who wrote them:** four separate writers (Claude subagents with no access to the repo, the route examples, the
  fit set or the fixtures), each given only the label definitions and a mix of kinds and styles. Near duplicates
  of any training sentence or fixture case were dropped. Two more subagents labeled every sentence blind; they
  agreed with the writer on all 601. Writers and labelers are the same model family, so the set may share its
  blind spots: real speech from the phone is still the better test.
- **What is in it:** 258 NONE, 153 ACTION, 130 SAVE, 60 SAVE+ACTION, by kind (calendar reads, directions, email
  my notes, timers; plain, casual, terse, rambling, question-shaped and indirect saves; chat, knowledge,
  brainstorms, recall questions, unsupported asks, near-miss statements and questions; two requests in one) and
  by style (376 clean, 146 with transcription slips, 79 long rambles).
- **dev and test:** split in half by id. Every change below was chosen by looking at dev misses only; test was
  scored once at the end. `python scripts/eval/router_heldout.py --split test` prints the counts per kind with a
  95% bootstrap range.

What the dev misses showed, and the fix for each cause:

1. **Requests at the end of a story** ("we landed, it's raining, the kids are cranky... get me to the hotel")
   scored as chat, because the vector follows the whole message and the head words are the story's. Now the
   message is also split into sentences and clauses (`segments()`), each piece is scored, and each tool keeps its
   best margin. Two pieces winning different tools count as two requests, which fixed most of the multi-intent
   misses.
2. **Too few examples:** 165 route examples. `eval/router/train.jsonl` adds 878 sentences from the same kind of
   independent writers (plus one round aimed at calendar reads and terse saves), all checked against the held-out
   set and fixtures.
3. **Thresholds fitted on sentences the layer had learned:** thresholds and settings are now chosen on
   out-of-fold scores (five folds over the whole training pool), so no fixture case is used to pick anything.
4. **Topic beat request:** "Navigate to Target" scored as shopping chat, since a straight 384x3 layer mixes up
   topic and request. The app now uses `mlp`, one hidden layer of 64 between (24,800 weights, about two seconds to
   fit on a CPU, same seed every time). Out of fold it cost 201 against the straight layer's 242, and 202 to 211
   across other seeds.

On the 304 test sentences, never looked at:

| | before (Nov 10 app) | split + data + fitting, straight layer | the app now (`mlp`) | phrase rules |
|---|---|---|---|---|
| right intent | 218 (202-233) | 258 (245-270) | 259 (247-271) | 172 |
| tool on a no-tool message (of 125) | 26 | 11 | 10 | 4 |
| request that got no tool (of 179) | 33 | 23 | 21 | 113 |
| an ACTION request reaches do_action (of 107) | 76 | | 88 | |
| do_action shown on a message with no action (of 197) | 22 | | 4 | |

- The hidden layer beat the straight one on dev (247 against 234 of 297) but tied on test (259 against 258), so
  its gain is not proven; it ships because it won the out-of-fold comparison, which uses neither half.
- On the two older fixtures (`embed_router.py score`), which are not in training either: 44 of 48 with 4 of 29
  over-calls on tool-eval (Nov 10: 45, 3), and 19 of 24 with 2 of 10 on the holdout (Nov 10: 20, 1). The new
  over-calls send unsupported commands ("Call the pharmacy", "Text Sam that I'm running late") to save_note, and
  two-word messages stay brittle: "Navigate home" misses while "navigate home please" and "Take me home" route.
  The Nov 10 tables above no longer reproduce from the committed configs.
- Still weak on all 601: directions 26 of 39, email my notes 23 of 36, long rambles 44 of 79. Calendar reads went
  from 26 to 33 of 39.
- Time: a message that splits runs the encoder twice per piece. 96 of the 601 split, into 4 pieces at most. Python
  on one cloud thread took a median of 74 ms per message and 218 ms for split ones; not measured on a phone.
- The int8 configs (`embed-all-MiniLM-L6-v2-int8-*.json`) are from Nov 10 and were not refitted; the app does not
  use them.

## Long messages got save_note (Nov 27)

On the phone, a long spoken message that asked for nothing often came back with a note saved. The router splits a
long message into sentences and clauses (`segments()`), scores each piece, and fires a tool when any piece clears the
threshold. Trained only on short sentences, the layer read a plain clause of a story ("and the inspector mentioned
the outlets in the bedrooms are two prong") as something to save. The more pieces, the more chances: on the 601
held-out sentences the shipped router sent 2.5% of one-piece no-tool messages to SAVE and 24% of split ones, and the
held-out set had no no-tool message over 60 words.

**R5, `eval/router/long.jsonl`:** 210 spoken messages of 45 to 160 words, from two writers who never saw the repo:
120 that ask for nothing (stories, venting, thinking aloud, long questions, brainstorms, talk about remembering or
notes, recall questions, unsupported asks, chit-chat) and 90 with a real request buried at the start, middle or end
(45 SAVE, 25 ACTION, 20 SAVE+ACTION). A third agent labeled all 210 blind and agreed with the writers on every one.
Split in half by id; only dev was looked at. `router_heldout.py --file eval/router/long.jsonl` scores it, with counts
by length. **R6, `eval/fixtures/tool-eval-long.json`:** the 105 test messages for `router_eval.py`, so Gemma's own
save_note calls are counted.

**Fix: a second layer for the pieces.** The whole message is still scored by the shipped layer, unchanged, so a
message that does not split routes exactly as before. The pieces of a split message are scored by a second 64-unit
layer on the same embedding (`piece_head` in the config, `embed_router.py fit-pieces`), fitted on the training pool
plus NONE pieces cut from `eval/router/train-stories.jsonl`: 100 more long no-request messages from a separate
writer, at most three pieces each (`load_story_pieces`). A piece's margin is put on the whole layer's scale (how far
it clears the piece layer's own threshold), so one threshold check covers both. `fit` now also tries the mlp's
starting seed 0 to 4 and keeps the lowest out-of-fold cost; the piece layer got seed 2, thresholds SAVE 0.3 and
ACTION 0.95. The Kotlin router reads the piece layer from the same config (`RouterConfig.pieceHead`).

What did not work, all chosen on dev: subtracting a fixed or log-scaled offset from every piece's margin (it cut
false saves only by missing about as many real buried requests), adding the long messages whole (worse on both),
and refitting the one layer with the story pieces. That last one fixed R5 but moved short messages too: with all
665 pieces it missed real short saves, and even with three per story it sent "Text Sam that I'm running late" and
"Call the pharmacy" to SAVE, which cost 6 R2 rows on E2B. Splitting the work between two layers keeps the short
messages where they were.

Test halves, the shipped config against the fix:

| | shipped | fix |
|---|---|---|
| R5 right intent (of 105) | 60 | 73 |
| R5 save_note on a long message that asks for nothing (of 64) | 22 | 8 |
| R5 save request that got no save (of 34) | 5 | 7 |
| R1 right intent (of 304) | 270 | 270 |
| R1 save_note on a no-tool message (of 125) | 4 | 1 |
| R1 save request that got no save (of 101) | 7 | 8 |

- R1 holds at 270; the rows that move are the ones that split.
- The cost is on long messages: 2 more buried save requests go unsaved, against 14 fewer long chats offered
  save_note.
- Writers and labeler are the same model family, so real phone speech is still the better test.
