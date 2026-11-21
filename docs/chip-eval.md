# Source chip retrieval and evaluation

`SourceRetriever` performs retrieval only: it never calls the LLM or generates an answer. It scopes a query to one collection, preserves health-person isolation, ranks keyword overlap with collection-local IDF weights and an exact-phrase bonus, and returns either code-attached source chips or the fixed collection-specific cannot-find result.

Photo capture can run the optional `OcrRecognizer` after the photo is stored. `LibraryRepository.setOcr` persists both the full OCR text and bounding-box lines and updates the non-health FTS row. OCR failure leaves the captured photo intact.

Run the sample chip relevance evaluation with:

```sh
python3 scripts/chip-eval.py \
  scripts/chip-eval-sample/library.json \
  scripts/chip-eval-sample/questions.json
```

The library JSON contains `collectionTitle` and `items`; each item has `kind` plus any of `text`, `transcript`, `ocrText`, or `generatedText`. The questions JSON contains `answerable` and `unanswerable` arrays of objects with a `question` field. The command prints each question, selected chip labels and source snippets, followed by the unanswerable cannot-find count.

## Answer check

Run the checked-answer evaluation over the same 30-question sample with:

```sh
python3 scripts/answer-eval.py \
  scripts/chip-eval-sample/library.json \
  scripts/chip-eval-sample/questions.json
```

The command prints the final answer, labels, unverified spans, and consulted chips with snippets for each question. Without recorded model output it uses the top retrieved item's text as a deterministic end-to-end stub. To evaluate answers recorded from the on-device model, set `ANSWER_EVAL_ANSWERS` to a JSON file containing a map from each question to its model answer:

```sh
ANSWER_EVAL_ANSWERS=/path/to/answers.json \
  python3 scripts/answer-eval.py \
  scripts/chip-eval-sample/library.json \
  scripts/chip-eval-sample/questions.json
```

Nick records whether each consulted chip supports its answer. The target is 90 percent; running this harness does not by itself establish that the target is met.
