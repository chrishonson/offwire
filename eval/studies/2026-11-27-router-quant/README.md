# Router encoder study: quantized MiniLM and MobileBERT against shipped fp32 MiniLM

Run on the Mac on 2026-11-27, branch `agent/router-quant`, all in one session. Nothing the app ships was changed.

**Verdict: keep A (fp32 MiniLM, ONNX) for now.** No quantized candidate passes the docs/RELEASING.md rule (no
release row worse by more than run-to-run spread). B (ONNX int8) loses 2 of the 9 main-set save cases at the router.
C (MobileBERT) cannot route at all. The best candidate is MiniLM int8 on LiteRT (D) or on LiteRT-LM's
EmbeddingEngine: it ties A on R1 within head-fit spread, is 2.5 times faster on the Mac, saves 65 MB of model, and
on the LiteRT-LM route also drops the standalone ONNX Runtime. But D still loses one main-set save case
(save-brakes: "Write down that the brake pads have three millimeters left") at the router, 3 rows on R2 per model,
just past the 2-row spread. Not enough evidence to ship D; it is the one to refit and rerun.

## Candidates

| | Encoder | File | Size | Source |
|---|---|---|---|---|
| A | all-MiniLM-L6-v2 fp32, ONNX | model.onnx | 90.4 MB | Chroma export, SHA-256 4f148ba8...393e6 (ships) |
| B | the same, int8 | model_int8.onnx | 22.9 MB | `embed_router.py quantize` (onnxruntime quantize_dynamic), e13dcd11...6021a |
| C | google/mobilebert-uncased int8, ONNX | model_int8.onnx | 26.4 MB | hub revision 1f90a6c2, `export_encoder.py` then `quantize`; fp32 3b43847c...88608, int8 e0ef653b...d452d |
| D | all-MiniLM-L6-v2 int8, LiteRT | model_tflite_int8.tflite | 26.6 MB | hub revision 1110a243, `export_tflite.py` (ai-edge-quantizer dynamic_wi8_afp32) |
| D' | the same, LiteRT-LM | minilm-router-int8.litertlm | 25.1 MB | `export_litertlm.py`, run by LiteRT-LM's EmbeddingEngine |

Each candidate's mlp head was refitted with `embed_router.py fit --scorers mlp`, the procedure and training pool
the shipped config used (head_words and l2 swept, out-of-fold thresholds). A uses the committed config as it ships.
The Python encoder no longer pads to 128 tokens (the Chroma tokenizer.json did; the app never has); A's R1 is 270
either way.

## Results

R1: `router_heldout.py --split test` (304 sentences). R2 and R3: `router_eval.py --setups embed_mlp --runs 3`, main
set (144 rows) and holdout set (72 rows). Counts of rows; percentages in brackets.

| Row | A | B | C | D | spread |
|---|---|---|---|---|---|
| R1 right intent | 270 (88.8%) | 265 (87.2%) | 125 (41.1%) | 265 (87.2%) | head refit: 263-269 |
| R1 save request with no save (of 101) | 7 | 12 | 101 | 8 | head refit: 6-15 |
| R1 tool on a no-tool sentence (of 125) | 6 | 9 | 0 | 5 | head refit: 5-8 |
| R2 E4B pass (of 144) | 139 (96.5%) | 133 (92.4%) | not run | 136 (94.4%) | 139-141 |
| R2 E4B tool on a no-tool turn (of 87) | 0 | 0 | | 0 | 0 |
| R2 E4B save cases (of 27) | 27 | 21 | | 24 | 26-27 |
| R3 E4B pass (of 72) | 59 (81.9%) | 62 (86.1%) | | 59 (81.9%) | 58-59 |
| R3 E4B tool on a no-tool turn (of 30) | 3 | 3 | | 0 | 3 |
| R2 E2B pass (of 144) | 141 (97.9%) | 135 (93.8%) | | 138 (95.8%) | 141-143 |
| R2 E2B tool on a no-tool turn (of 87) | 0 | 0 | | 0 | 0 |
| R2 E2B save cases (of 27) | 27 | 21 | | 24 | 27 |
| R3 E2B pass (of 72) | 57 (79.2%) | 60 (83.3%) | | 57 (79.2%) | 57 |
| R3 E2B tool on a no-tool turn (of 30) | 3 | 3 | | 0 | 3 |

- **Spread.** Identical runs are deterministic: A's E2B holdout rerun with the same seeds gave the same 72 replies
  word for word. So the spread is from seeds: A with Gemma seeds 1-3 (release seeds) against 4-6 moved rows by 0 to 2
  (E4B main 139 and 141, E2B main 141 and 143, E4B holdout 59 and 58, E2B holdout 57 and 57). R1's spread is the head
  fit: refitting A's own setting with mlp seeds 0-4 gives 263 to 269.
- **The router decides each case for all three runs**, so a router miss costs 3 rows. B's R2 drop is two save
  cases routed to NONE ("Write down that the brake pads have three millimeters left", "Don't let me forget to buy
  batteries for the smoke detector"). D's is the first of those. On R3, A over-calls one no-tool case (ho-log-cabin,
  save_note); D over-calls none; B over-calls a different one (ho-microwave-timer, do_action).
- **C does not route.** Stock MobileBERT's mean-pooled, normalized vectors are nearly the same for every sentence
  (pairwise cosine 0.9999 to 1.0000, against -0.09 to 0.15 for MiniLM), in fp32 as well as int8, so the head learns
  "always NONE" (out-of-fold right 0.513). Its end-to-end runs were skipped. This is the caveat in the request,
  measured: stock MobileBERT was not trained for sentence vectors.
- **Google's published quantized MobileBERT LiteRT file** is MediaPipe's SST-2 sentiment classifier
  (litert-community/MediaPipe-TextClassifier-MobileBERT, bert_classifier.tflite, 25.7 MB, revision ed51b398). Read
  from its last encoder layer (tensor tfl.add315) and mean-pooled, it does separate sentences: R1 254 to 270 over head
  seeds 0-4 (seed 0: 254). It always runs 128 tokens, at 33 ms per encoder run on the Mac. No embedding or
  hidden-state MobileBERT build was found; Kaggle's listing did not load.

## LiteRT (step 8)

| Encoder | Size | R1 right, head seeds 0-4 | R1 missed saves | Cosine to A's vectors (601 sentences) |
|---|---|---|---|---|
| A fp32 ONNX | 90.4 MB | 263-269 (shipped config 270) | 6-15 | 1 |
| B int8 ONNX | 22.9 MB | 256-267 | 8-22 | min 0.839, median 0.963 |
| MiniLM fp32 LiteRT | 91.2 MB | 263-270 | 5-9 | min 1.00000 |
| D int8 LiteRT | 26.6 MB | 265-270 | 6-8 | min 0.987, median 0.998 |
| D' int8 LiteRT-LM EmbeddingEngine | 25.1 MB | 262-272 | 7-9 | min 0.987, median 0.998 |
| MediaPipe MobileBERT int8 LiteRT | 25.7 MB | 254-270 | 4-14 | (other model) |

- LiteRT's int8 keeps activations in float (ai-edge-quantizer dynamic_wi8_afp32); onnxruntime's quantize_dynamic
  quantizes them too. That is why D tracks fp32 and B does not.
- LiteRT conversion needed GELU written out in its tanh form (litert-torch lowers aten.gelu through JAX's erf, which
  emits an fma op the converter rejects). The vectors still match A's to cosine 1.00000 in fp32.
- **LiteRT-LM already runs it.** The LiteRT-LM 0.19.1 Android library the app ships has no general LiteRT
  interpreter (its native library exports only its own 54 JNI entry points), but it has an EmbeddingEngine. That
  engine runs a .litertlm holding an embedder (token ids to token vectors) and a text_encoder with signatures named
  encoder&lt;length&gt; (inputs embeddings and input_mask), tokenizing with the packed tokenizer.json. `export_litertlm.py`
  builds one from MiniLM; on the Mac it reproduces D's vectors (median cosine 1.0000). The packed tokenizer must
  have padding turned off, or the engine pools over 128 pad tokens.
- The separate LiteRT library, if it were used instead: com.google.ai.edge.litert:litert 2.4.1, libLiteRt.so 5.9 MB
  for arm64 (2.5 MB compressed) plus liblitert_jni.so 0.2 MB and its Java dependencies.
- **Silero VAD through sherpa-onnx: yes.** The sherpa-onnx 2.0.1 AAR the app ships has `Vad` with `compute(float[])`
  (one window's speech probability, the same call `SileroVad.speechProbability` makes), backed by
  Java_com_k2fsa_sherpa_onnx_Vad_compute, with Silero v5 support (512-sample window at 16 kHz, context kept inside).
  Not run. sherpa-onnx's own ONNX Runtime is linked in statically and exports no ONNX Runtime API, so the router
  cannot share it.

**Net download for "router on LiteRT-LM, VAD through sherpa-onnx, standalone ONNX Runtime removed":**

| | APK (stored uncompressed) | Download (gzip -9, a proxy for Play's compression) |
|---|---|---|
| Router model 90.4 MB fp32 ONNX to 25.1 MB int8 .litertlm | -65.3 MB | -61.3 MB |
| Drop libonnxruntime.so, libonnxruntime4j_jni.so and its classes (arm64) | -34.5 MB | -13.0 MB |
| Add a library | none | none |
| Total | about -100 MB (365 to about 265 MB) | about -74 MB |

With the separate LiteRT library instead of LiteRT-LM's engine: about -92 MB APK and -71 MB download.

## Router time per message

`router_timing.py`, one thread, uncached encoder, fastest of three passes per message, 304 test sentences, on the
Mac with nothing else running. Not measured on a phone.

| Encoder | Median | 90th percentile | One encoder run (median) |
|---|---|---|---|
| A fp32 ONNX | 7.6 ms | 26.9 ms | 5.2 ms |
| B int8 ONNX | 3.1 ms | 10.7 ms | 2.0 ms |
| C MobileBERT int8 ONNX | 4.1 ms | 14.7 ms | 4.6 ms |
| MiniLM fp32 LiteRT | 12.5 ms | 45.7 ms | 6.3 ms |
| D int8 LiteRT | 2.9 ms | 11.1 ms | 1.4 ms |
| D' int8 LiteRT-LM EmbeddingEngine | 3.0 ms | 11.1 ms | 1.5 ms |
| MediaPipe MobileBERT int8 LiteRT | | | 33.0 ms (128 tokens fixed) |

## What shipping each would take

- **B:** swap the asset to model_int8.onnx and the config to the refitted int8 one. No code. MiniLmEncoder already
  feeds what the file takes.
- **C:** the same kind of swap (same vocab, SHA-256 07eced37; same lowercasing and accent stripping as
  WordPieceTokenizer.kt; inputs input_ids, attention_mask, token_type_ids, which MiniLmEncoder feeds; output width
  512, which meanPoolUnit and the head read from the data). Moot: it does not route.
- **D on LiteRT:** add com.google.ai.edge.litert, a new SentenceEncoder that picks the signature by length and pads
  with mask 0, and the .tflite asset.
- **D' on LiteRT-LM:** a new SentenceEncoder over com.google.ai.edge.litertlm.EmbeddingEngine (normalize on,
  insert_special_tokens on, overflow TRUNCATE) and the .litertlm asset; the WordPiece tokenizer is then used only for
  head words. Then move SileroVad onto sherpa-onnx's Vad and drop onnxruntime-android. Needs a Pixel check for speed.

## Files

r1-*.json (R1 per encoder), r1-seeds-*.json (R1 per head seed), e2e.json (R2/R3 rows), *.summary.json
(router_eval summaries), timing.json. Model files are not committed; the scripts above rebuild them.
