"""The MobileBERT router: one small fine-tuned classifier reads the whole message and picks NONE, SAVE, ACTION or
SAVE+ACTION in one pass, in place of embed_router.py's MiniLM embeddings, fitted layer, thresholds and sentence
splitting.

The model is MediaPipe Model Maker's MobileBERT-tiny base (the "BERT classifier", 18.2M parameters) fine-tuned on the
router's training pool (routes.json + fit.json + train.jsonl) with a 4-way softmax, by train_mobilebert_router.py,
then exported to ONNX and dynamic-range int8 quantized. It reads the raw message: lowercased WordPiece with the same
vocab and rules as MiniLM's tokenizer (core/.../router/vocab.txt), [CLS] ... [SEP], cut to 128 tokens. The label is
the highest logit; there are no thresholds.

The app runs the same file on ONNX Runtime (core/src/androidMain/.../router/MobileBertRouter.kt), held to this one by
eval/router/parity-mobilebert-router.json (export with --parity).

Usage (from the repo root):
  python scripts/eval/mobilebert_router.py "save a note that the garage door is open"
  python scripts/eval/mobilebert_router.py --parity    writes eval/router/parity-mobilebert-router.json
"""
import argparse
import json
import time
from dataclasses import dataclass
from pathlib import Path

import numpy as np

import embed_router as er
import router

REPO = Path(__file__).resolve().parents[2]
ASSETS = REPO / "core" / "src" / "androidMain" / "assets" / "router"
DEFAULT_MODEL = ASSETS / "mobilebert-router.onnx"
VOCAB = ASSETS / "vocab.txt"
PARITY = er.ROUTER_DIR / "parity-mobilebert-router.json"
# The classifier's four outputs, in order. SAVE+ACTION is one class: the message holds both requests.
LABELS = ("SAVE", "ACTION", "NONE", "SAVE+ACTION")
MAX_TOKENS = 128
INPUTS = ("input_word_ids", "input_mask", "input_type_ids")


def intents_for(logits) -> list[str]:
    """Tool intents for the top class, best first: for SAVE+ACTION, whichever of SAVE and ACTION scores higher."""
    label = LABELS[int(np.argmax(logits))]
    if label == router.NONE:
        return []
    if label == "SAVE+ACTION":
        return sorted(["SAVE", "ACTION"], key=lambda i: -float(logits[LABELS.index(i)]))
    return [label]


@dataclass
class MobileBertRouter:
    model: Path = DEFAULT_MODEL
    vocab: Path = VOCAB
    threads: int = 1

    def __post_init__(self):
        import onnxruntime as ort
        from tokenizers import BertWordPieceTokenizer
        if not Path(self.model).is_file():
            raise SystemExit(f"MobileBERT router model not found: {self.model} (scripts/fetch-router-model.sh)")
        self.tokenizer = BertWordPieceTokenizer(str(self.vocab), lowercase=True)
        self.tokenizer.enable_truncation(max_length=MAX_TOKENS)
        options = ort.SessionOptions()
        options.intra_op_num_threads = self.threads
        self.session = ort.InferenceSession(str(self.model), options, providers=["CPUExecutionProvider"])

    def ids(self, transcript: str) -> list[int]:
        return self.tokenizer.encode(transcript).ids

    def logits(self, transcript: str) -> np.ndarray:
        ids = np.array([self.ids(transcript)], dtype=np.int32)
        feed = {INPUTS[0]: ids, INPUTS[1]: np.ones_like(ids), INPUTS[2]: np.zeros_like(ids)}
        return self.session.run(None, feed)[0][0]

    def decide(self, transcript: str) -> er.EmbedDecision:
        start = time.perf_counter()
        z = self.logits(transcript)
        scores = {label: round(float(v), 4) for label, v in zip(LABELS, z)}
        return er.EmbedDecision(intents_for(z), scores, time.perf_counter() - start)

    def route(self, transcript: str) -> router.Route:
        return er.embed_route(self.decide(transcript))


def write_parity(r: MobileBertRouter, path: Path = PARITY) -> None:
    """Every training and held-out sentence: its token ids, the model's logits and the label, for the Kotlin port."""
    texts = [t for k in ("SAVE", "ACTION", "NONE") for t in json.loads((er.ROUTER_DIR / "routes.json").read_text())[k]]
    texts += [c["text"] for c in json.loads((er.ROUTER_DIR / "fit.json").read_text())]
    for name in ("train.jsonl", "heldout.jsonl"):
        texts += [json.loads(line)["text"] for line in (er.ROUTER_DIR / name).read_text().splitlines() if line.strip()]
    cases = []
    for text in dict.fromkeys(texts):
        z = r.logits(text)
        cases.append({"text": text, "ids": r.ids(text), "logits": [round(float(v), 5) for v in z],
                      "intents": intents_for(z)})
    path.write_text(json.dumps({"model": Path(r.model).name, "labels": list(LABELS), "max_tokens": MAX_TOKENS,
                                "cases": cases}, separators=(",", ":")) + "\n")
    print(f"wrote {path} ({path.stat().st_size // 1024} KB): {len(cases)} cases")


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("text", nargs="*")
    parser.add_argument("--model", default=str(DEFAULT_MODEL))
    parser.add_argument("--parity", action="store_true", help=f"write {PARITY.relative_to(REPO)}")
    args = parser.parse_args(argv)
    r = MobileBertRouter(Path(args.model))
    if args.parity:
        write_parity(r)
    for text in args.text:
        d = r.decide(text)
        print(f"{d.intent:12} {d.seconds * 1000:5.1f} ms  {d.scores}  {text!r}")


if __name__ == "__main__":
    main()
