# FunctionGemma router fine-tune

Goal: a router that is faster than the shipped single-model path and as accurate on phrasings it has never seen.
Bar to ship: within 5 points of shipped's pass rate on a new held-out set (shipped was 84.7% on the 24-case holdout),
with no-tool turns no slower than shipped and tool turns faster. If the fine-tuned gate cannot reach that, ship
without a router and fix over-calling in the reply prompt instead.

## What the evals showed (2026-11-10, E2B, router_eval.py)

- Rules alone: 95.8% on the fixture they were written against, 37.5% on the holdout. They miss plain phrasings
  ("Navigate home", "Give me a ten minute timer") and fire on keyword traps ("Directions to make pancakes?").
- The gate only sees near-misses (a tool word, no rule hit), so a request with no tool word ("Am I busy this
  afternoon?") can never reach it. That caps recall whatever model is the gate.
- FunctionGemma Mobile Actions as gate: 3 of 21 real intents. Its training set (google/mobile-actions, 9,654
  examples) has seven other functions and no example that ends without a call, so it never learned our tools or NONE.
- E2B as gate: 18 of 21 real intents, but a call on half the plain chat it sees, and 0.66 s per call on the
  engine that also writes the reply. FunctionGemma costs 0.36 to 0.46 s per call on its own CPU engine.

## Phases

P1. Access and toolchain
- [x] Training stack on this Mac: torch 2.14 (MPS), transformers 5.22, trl 1.17, peft 0.21, litert-torch 0.10,
      in ~/models/functiongemma-ft/.venv, outside the repo.
- [ ] Nick: request access to google/functiongemma-270m-it (manual review by Google). The only external blocker.
- [ ] Convert the untouched base model to .litertlm with litert_torch.generative.export_hf and check it loads in
      LiteRT-LM and gives the same gate labels as the published Mobile Actions file's format. This proves the
      export path before any training depends on it.

P2. Data (no model access needed, start now)
- [x] One schema: an utterance, its intent (NONE, SAVE, ACTION) and, for SAVE and ACTION, the exact call
      (save_note text, do_action action and arg). Built by scripts/eval/ft_data.py: 1,338 train and 149 validation
      rows in ~/models/functiongemma-ft/data, after dropping 43 that matched a held-out or eval fixture text. Same tool specs as tool_eval.py, so the gate can also return
      arguments and the app can run the tool without a second model pass.
- [x] Training set, about 1,500 utterances: half NONE, including keyword traps ("save the date cards", "log
      cabins", "remember that movie"), a fifth SAVE, the rest ACTION across remind_me, send_notes_to_me,
      navigate_to and whats_on. Varied register: terse commands, polite requests, speech-recognition noise,
      requests with no tool word ("am I busy this afternoon").
- [x] Held-out set, 160 utterances (70 NONE, 35 SAVE, 55 ACTION) by a separate writer that never saw the training
      set: eval/fixtures/router-heldout.json.
- [ ] About 30 more that Nick writes in his own words. Never trained on and never used to tune rules or prompts.
- [x] A builder that turns the schema into FunctionGemma chat format (the same developer turn and tools as the eval gate,
      user turn, assistant call or the word NONE), with a duplicate and leak check between the sets.

P3. Rules and routing
- [ ] Narrow the rules to explicit commands that cannot be read another way. Single words that open ordinary
      sentences (save, log, note, remember, directions to + a non-place) move to the gate.
- [ ] Add a routing mode where the gate sees every turn the rules do not claim, not only near-misses, and measure
      both modes. Gate cost on every turn is about 0.4 s, which is the trade being measured.

P4. Fine-tune
- [ ] LoRA on google/functiongemma-270m-it with TRL SFT on MPS, loss on assistant turns only. A full fine-tune
      also fits (270M on 64 GB), so try it if LoRA underfits.
- [ ] Hold back 10% of the training set as validation. Keep the checkpoint with the best validation intent
      accuracy, not the lowest loss.
- [ ] Merge, export to .litertlm (q8, ekv1024 like the published file), and check the converted model gives the
      same labels as the PyTorch one on the validation set.

P5. Evaluate
- [ ] router_eval.py with the new file as --functiongemma, on the main fixture, the old holdout and the new
      held-out set. 3 runs. Report intent accuracy per intent, over-call, argument accuracy, gate time, and
      total router + TTFT split by tool and no-tool turns, next to shipped and the E2B gate.
- [ ] Decide against the bar at the top. Only then port the gate to the app (Kotlin, a second LiteRT-LM engine).

## Other long-term recommendations

L1. Recall is broken in every setup (3 to 6 of 12): the proposed prompt never calls read_notes, and the shipped
    prompt saves a note while it answers. Fix this before or alongside the router, since a router cannot help it.
L2. LiteRtLlmEngine must catch the tool-call parse error E4B can produce (call:save_note{text: unquoted}). In the
    eval it crashed a whole run.
L3. Keep the ToolTurn guard: E4B confirms twice on 14 to 32% of tool turns without it, 0% with it.
L4. E4B with the proposed tools is the most accurate single model (90.3%, 1.3% over-call) but takes 4.4 s to its
    first token. If the router works, E4B behind it only runs for replies, which is where its quality matters.
L5. Eval gaps still open from the 2026-11-09 test review: answers to no-call turns are not checked for quality
    (T2), the claim regex misses common phrasings (T4), over-call denominators differ between configs (T7), and
    too few cases separate the configs (T9). The new held-out set in P2 addresses T9 for the router.
