"""Trains the MobileBERT router (mobilebert_router.py) and exports it as the app's ONNX file.

Base: MediaPipe Model Maker's MobileBERT-tiny text-classifier base (Model Maker's "BERT classifier", 18.2M parameters,
Apache 2.0), the SavedModel Model Maker itself downloads from storage.googleapis.com/mediapipe-assets. Every weight
is fine-tuned, with a new 4-way head: SAVE, ACTION, NONE, SAVE+ACTION. One change from Model Maker's recipe: the
head mean-pools encoder block 22 of 24 and layer-normalizes it. MobileBERT-tiny's last two blocks give nearly the
same vector for every sentence here (cosine ~1.0, values up to ~7e5), so nothing trains from its pooled output.

Data: the router's training pool only (routes.json + fit.json + train.jsonl, 1,284 sentences). The learning rate
and epoch are picked on the held-out dev half; the test half is never looked at. Adam, clipnorm 1.0, batch 32,
seed 0. Then tf2onnx (opset 17, dynamic sequence length) and onnxruntime's dynamic-range int8 quantization, checked
label by label against the Keras model.

The shipped file came from the Nov 27 run of this recipe on one cloud CPU (the router-classifier-compare scripts it
was trimmed from): learning rate 1e-4, epoch 8, dev 278 of 297 (batched, padded). Seed 1 picked 1e-4 and epoch 6,
also dev 278, so seeds give close but different weights; the shipped file is seed 0.

Needs: tensorflow, tf_keras, tensorflow_hub (with setuptools<81), tokenizers, tf2onnx, onnx, onnxruntime.
Usage (from the repo root):
  TF_USE_LEGACY_KERAS=1 python scripts/eval/train_mobilebert_router.py --work ~/models/mobilebert-router
writes core/src/androidMain/assets/router/mobilebert-router.onnx
"""
import argparse
import hashlib
import json
import os
import tarfile
import urllib.request
from pathlib import Path

os.environ.setdefault("TF_USE_LEGACY_KERAS", "1")
import numpy as np

import mobilebert_router as mr

BASE_URL = "https://storage.googleapis.com/mediapipe-assets/mobilebert_tiny.tar.gz"
BASE_SHA256 = "d7292110392dfe9fa5ea845c0f35a6d305d53387b2075388d6e9f64705681267"
ROUTER = mr.er.ROUTER_DIR
LRS = (5e-5, 1e-4)
EPOCHS = 12
BATCH = 32
POOL_BLOCK = 22


def load():
    routes = json.loads((ROUTER / "routes.json").read_text())
    pool = [{"text": t, "intent": k} for k in ("SAVE", "ACTION", "NONE") for t in routes[k]]
    pool += json.loads((ROUTER / "fit.json").read_text())
    pool += [json.loads(line) for line in (ROUTER / "train.jsonl").read_text().splitlines() if line.strip()]
    held = [json.loads(line) for line in (ROUTER / "heldout.jsonl").read_text().splitlines() if line.strip()]
    return pool, [h for h in held if h["split"] == "dev"]


def base_model(work: Path) -> Path:
    path = work / "mobilebert_tiny"
    if not (path / "mobilebert_tiny" / "saved_model.pb").exists():
        work.mkdir(parents=True, exist_ok=True)
        tgz = work / "mobilebert_tiny.tar.gz"
        urllib.request.urlretrieve(BASE_URL, tgz)
        if hashlib.sha256(tgz.read_bytes()).hexdigest() != BASE_SHA256:
            raise SystemExit(f"SHA-256 mismatch: {tgz}")
        with tarfile.open(tgz) as t:
            t.extractall(path, filter="data")
    return path / "mobilebert_tiny"


def features(tok, texts):
    encs = [tok.encode(t).ids for t in texts]
    n = max(len(e) for e in encs)
    ids = np.zeros((len(texts), n), np.int32)
    mask = np.zeros_like(ids)
    for i, e in enumerate(encs):
        ids[i, :len(e)], mask[i, :len(e)] = e, 1
    return {"input_word_ids": ids, "input_mask": mask, "input_type_ids": np.zeros_like(ids)}


def build(base: Path):
    import tensorflow as tf
    import tensorflow_hub as hub
    import tf_keras as keras
    enc = hub.KerasLayer(str(base), trainable=True)
    inp = {k: keras.Input((None,), dtype=tf.int32, name=k) for k in mr.INPUTS}
    out = enc(inp)
    mask = tf.cast(inp["input_mask"], tf.float32)[..., None]
    x = tf.reduce_sum(out["encoder_outputs"][POOL_BLOCK] * mask, 1) / tf.reduce_sum(mask, 1)
    x = keras.layers.LayerNormalization()(x)
    x = keras.layers.Dropout(0.1)(x)
    return keras.Model(inp, keras.layers.Dense(len(mr.LABELS))(x))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--work", required=True, help="folder for the base model, the Keras model and the fp32 ONNX")
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--out", default=str(mr.DEFAULT_MODEL))
    a = ap.parse_args()
    import tensorflow as tf
    import tf_keras as keras
    from tokenizers import BertWordPieceTokenizer
    work = Path(a.work).expanduser()
    base = base_model(work)
    tok = BertWordPieceTokenizer(str(mr.VOCAB), lowercase=True)
    tok.enable_truncation(max_length=mr.MAX_TOKENS)
    pool, dev = load()
    texts = [p["text"] for p in pool]
    y = np.array([mr.LABELS.index(p["intent"]) for p in pool])
    rng = np.random.default_rng(a.seed)

    def dev_right(model):
        z = np.concatenate([model(features(tok, [r["text"] for r in dev[i:i + 64]]), training=False).numpy()
                            for i in range(0, len(dev), 64)])
        return sum(mr.LABELS[int(j)] == r["intent"] for j, r in zip(z.argmax(1), dev))

    best = None
    for lr in LRS:
        tf.keras.utils.set_random_seed(a.seed)
        model = build(base)
        model.compile(keras.optimizers.Adam(lr, clipnorm=1.0),
                      keras.losses.SparseCategoricalCrossentropy(from_logits=True))
        for epoch in range(1, EPOCHS + 1):
            order = rng.permutation(len(pool))
            for i in range(0, len(order), BATCH):
                b = order[i:i + BATCH]
                model.train_on_batch(features(tok, [texts[j] for j in b]), y[b])
            d = dev_right(model)
            print(f"lr {lr} epoch {epoch}: dev {d} of {len(dev)}", flush=True)
            if best is None or d > best[0]:
                best = (d, lr, epoch, model.get_weights())
    d, lr, epoch, weights = best
    print(f"picked lr {lr} epoch {epoch}: dev {d} of {len(dev)}")
    model = build(base)
    model.set_weights(weights)
    export(model, work, Path(a.out), tok, dev)


def export(model, work: Path, out: Path, tok, rows):
    import onnxruntime as ort
    import tensorflow as tf
    import tf2onnx
    from onnxruntime.quantization import QuantType, quantize_dynamic
    fp32 = work / "mobilebert-router-fp32.onnx"
    spec = {k: tf.TensorSpec((1, None), tf.int32, name=k) for k in mr.INPUTS}
    tf2onnx.convert.from_function(tf.function(lambda d: {"logits": model(d, training=False)}),
                                  input_signature=[spec], opset=17, output_path=str(fp32))
    quantize_dynamic(str(fp32), str(out), weight_type=QuantType.QInt8)
    session = ort.InferenceSession(str(out), providers=["CPUExecutionProvider"])
    same = 0
    for r in rows:
        feed = features(tok, [r["text"]])
        same += int(np.argmax(session.run(None, feed)[0][0]) == np.argmax(model(feed, training=False).numpy()[0]))
    print(f"wrote {out} ({out.stat().st_size / 1e6:.1f} MB): int8 label matches Keras on {same} of {len(rows)} dev")


if __name__ == "__main__":
    main()
