"""Writes what the Python embedding router does on every known message, for the app's Kotlin port to match.

The app runs the same router (core/.../router/) on the phone: the same WordPiece tokenizer, the same MiniLM ONNX
model, the same "whole message averaged with its first words" embedding, the same split into sentences and
clauses, and the same fitted linear layer and thresholds. Its JVM tests read this file and check, case by case:
  - the normalized text, head words and pieces (segments) of every message
  - token ids for the whole message and for its head words (the tokenizer port, exactly)
  - the route intents and margins, given this file's embeddings of the message and its pieces (the scoring port)
Cases: both eval fixtures, every training sentence and every held-out sentence. Vectors are written only for the
fixture cases and the first held-out sentences that split, to keep the file small.

Usage (from scripts/eval, with onnxruntime, tokenizers and numpy installed):
  python export_router_parity.py [--model-dir DIR] [--model-file model.onnx] [--scorer mlp]
writes eval/router/parity-<model>-<scorer>.json
"""
import argparse
import json
from pathlib import Path

import numpy as np

import embed_router as er

# Tokenizer edge cases the fixtures do not cover: accents, punctuation runs, digits, emoji, CJK, a word longer
# than WordPiece's 100-character limit, unknown pieces, and a message past the 128-token truncation.
EDGE_TEXTS = [
    "Café résumé naïve: São Paulo, Zürich & Ångström!",
    "Wi-Fi password is hunter2... (don't tell!) $5.99 @ 7:30pm #notes",
    "remember 🎉 the party is at 6 👍",
    "我的笔记 note this 東京",
    "x" * 120 + " short",
    "supercalifragilisticexpialidocious antidisestablishmentarianism",
    "  Hey   computer,\tremember\nthe   spacing ",
    " ".join(["remember the milk and the eggs and the bread"] * 20),
    "ＦＵＬＬＷＩＤＴＨ text and ﬁ ligature",
    "",
]
HELDOUT_VECTORS = 40


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--model-dir", default=str(er.DEFAULT_MODEL_DIR))
    parser.add_argument("--model-file", default="model.onnx")
    parser.add_argument("--scorer", default=er.APP_SCORER, choices=["linear", "mlp"])
    args = parser.parse_args(argv)

    encoder = er.OnnxEncoder(Path(args.model_dir).expanduser(), args.model_file)
    router = er.load_router(Path(args.model_dir).expanduser(), args.model_file, args.scorer, er.CachedEncoder(encoder))
    tokenizer = encoder.tokenizer

    def ids(text: str) -> list[int]:
        """Without the padding tokenizer.json adds; the encoder masks padding out, and the app sends none."""
        enc = tokenizer.encode(text)
        return [i for i, m in zip(enc.ids, enc.attention_mask) if m]

    cases = []
    for fixture in er.FIXTURES:
        for case in json.loads(fixture.read_text()):
            cases.append({"source": fixture.name, "id": case["id"], "text": case["user"], "vector": True})
    cases += [{"source": "training", "id": f"train-{i}", "text": item["text"], "vector": False}
              for i, item in enumerate(er.load_pool())]
    heldout = [json.loads(line) for line in (er.ROUTER_DIR / "heldout.jsonl").read_text().splitlines() if line.strip()]
    split_rows = [row["id"] for row in heldout if er.segments(row["text"])][:HELDOUT_VECTORS]
    cases += [{"source": "heldout.jsonl", "id": row["id"], "text": row["text"], "vector": row["id"] in split_rows}
              for row in heldout]

    out = []
    for case in cases:
        text = case["text"]
        normalized = er.normalize(text)
        head = " ".join(normalized.split()[: router.head_words])
        vector = router.embed([text])
        pieces = er.segments(text)
        _, margins, _ = router.split_margins([text])
        d = router.decide(text)
        row = {"source": case["source"], "id": case["id"], "text": text, "normalized": normalized, "head": head,
               "segments": pieces, "ids": ids(normalized), "head_ids": ids(head),
               "margins": {r: round(float(m[0]), 6) for r, m in margins.items()}, "intent": d.intent}
        if case["vector"]:
            row["vector"] = [round(float(v), 7) for v in vector[0]]
            vectors = router.embed(pieces) if pieces else []
            row["segment_vectors"] = [[round(float(v), 7) for v in vec] for vec in vectors]
        out.append(row)
    edges = [{"text": t, "ids": ids(t)} for t in EDGE_TEXTS]

    path = er.ROUTER_DIR / f"parity-{encoder.name}-{args.scorer}.json"
    data = {"_comment": "Written by scripts/eval/export_router_parity.py; read by the app's router tests.",
            "model": encoder.name, "scorer": args.scorer, "head_words": router.head_words, "split": router.split,
            "thresholds": router.thresholds, "cases": out, "tokenizer_edges": edges}
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n")
    counts = {}
    for row in out:
        counts[row["intent"]] = counts.get(row["intent"], 0) + 1
    print(f"wrote {path} ({path.stat().st_size / 1e3:.0f} KB): {len(out)} cases {counts}, {len(edges)} tokenizer edges")


if __name__ == "__main__":
    main()
