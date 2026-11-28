"""Fine-tunes the router's MiniLM encoder on the training pool, so its vectors separate SAVE, ACTION and NONE.

embed_router.py keeps the encoder frozen and trains only the small layer on top (about 25,000 weights). This
script also trains the encoder's own 22 million weights, starting from the same all-MiniLM-L6-v2 ONNX the app
ships (the weights are read out of the ONNX file, so no other download is needed), then writes a new ONNX file
with the same inputs and output. The app runs it exactly as before; only the model file and the layer change.

How it is trained and scored, with nothing from the eval fixtures or eval/router/heldout.jsonl:
  1. The single-request sentences of the training pool (routes.json, fit.json, train.jsonl) are embedded the way
     the router embeds them (whole message averaged with its first head_words words, normalized) and scored by a
     64-unit layer like the app's. The encoder and that layer learn together, by cross-entropy, for a few epochs.
  2. The app's layer is then refit on the new vectors with embed_router.train_mlp, as `embed_router.py fit` does.
  3. Thresholds come from out-of-fold scores: the pool is cut in five, and for each fifth the encoder is
     fine-tuned again on the other four, so every sentence is scored by an encoder that never saw it. The same
     out-of-fold cost picks the learning rate and number of epochs.

Usage (from scripts/eval, with torch, transformers, onnx, onnxruntime, tokenizers and numpy installed):
  python finetune_router.py check  [--base DIR]                 the rebuilt encoder matches the ONNX model's output
  python finetune_router.py cv     [--base DIR] [--grid 1e-4:4,2e-4:4]
                                   out-of-fold cost per setting (lr:epochs), five fine-tunes each
  python finetune_router.py train  --lr 2e-4 --epochs 4 --out DIR [--thresholds SAVE,ACTION]
                                   fine-tunes on the whole pool, writes DIR/model.onnx, config.json, tokenizer.json
                                   and vocab.txt, and eval/router/embed-<name>-mlp.json with the out-of-fold
                                   thresholds (--thresholds reuses an earlier cv run's instead of cross-fitting again)
--base defaults to ~/models/all-MiniLM-L6-v2/onnx (scripts/fetch-router-model.sh). It also takes a Hugging Face id
of a BERT-style encoder, such as sentence-transformers/all-MiniLM-L12-v2 or BAAI/bge-small-en-v1.5, on a machine
that can reach the hub; the cloud sessions cannot, so only MiniLM-L6 has been tried.

What the app ships (Nov 27): `train --lr 3e-4 --epochs 4 --thresholds 1.0,1.3` from all-MiniLM-L6-v2 on the
1284-sentence pool, as core/src/androidMain/assets/router/all-MiniLM-L6-v2-router.onnx with
embed-all-MiniLM-L6-v2-router-mlp.json. The cv runs (`cv --grid 1e-4:4,2e-4:4,2e-4:8,3e-4:4`): costs 133, 129,
113 and 110; the frozen encoder's mlp costs 193. About four minutes per setting on a 10-core Mac.
"""
import argparse
import json
import sys
import time
from pathlib import Path

import numpy as np
import onnx
import torch
import torch.nn.functional as F
from onnx import numpy_helper
from tokenizers import Tokenizer
from transformers import BertConfig, BertModel

import embed_router as er
import router

HEAD_WORDS = 3     # what the frozen sweep picked; kept so the only change is the encoder
HEAD_L2 = 0.01
BATCH = 32
HEAD_LR = 1e-3
SEED = 0
MATMULS_PER_LAYER = ("attention.self.query", "attention.self.key", "attention.self.value",
                     "attention.output.dense", "intermediate.dense", "output.dense")


def bert_from_onnx(model_dir: Path, model_file: str = "model.onnx") -> BertModel:
    """A BertModel holding the ONNX file's weights. The exporter renamed each Linear's weight to onnx::MatMul_<n>
    (transposed); they come in layer order, six per layer, in MATMULS_PER_LAYER order. check_against_onnx()
    confirms the result matches the ONNX model's output."""
    graph = onnx.load(str(model_dir / model_file)).graph
    arrays = {i.name: numpy_helper.to_array(i) for i in graph.initializer}
    config = BertConfig(**{k: v for k, v in json.loads((model_dir / "config.json").read_text()).items()
                           if k in BertConfig().to_dict() and not k.startswith("_")})
    model = BertModel(config, add_pooling_layer=False)
    state = {k: torch.tensor(v) for k, v in arrays.items() if not k.startswith("onnx::")}
    matmuls = sorted((k for k in arrays if k.startswith("onnx::MatMul_")), key=lambda k: int(k.rsplit("_", 1)[1]))
    if len(matmuls) != len(MATMULS_PER_LAYER) * config.num_hidden_layers:
        raise SystemExit(f"expected {len(MATMULS_PER_LAYER) * config.num_hidden_layers} MatMul weights, "
                         f"found {len(matmuls)}")
    for i, name in enumerate(matmuls):
        layer, part = divmod(i, len(MATMULS_PER_LAYER))
        state[f"encoder.layer.{layer}.{MATMULS_PER_LAYER[part]}.weight"] = torch.tensor(arrays[name].T.copy())
    state.pop("embeddings.position_ids", None)
    missing, unexpected = model.load_state_dict(state, strict=False)
    missing = [m for m in missing if not m.endswith("position_ids")]
    if missing or unexpected:
        raise SystemExit(f"weights did not line up: missing {missing}, unexpected {unexpected}")
    return model.eval()


class TorchEncoder:
    """The same vectors as embed_router.OnnxEncoder (mean-pooled, L2-normalized), from a torch BertModel."""

    def __init__(self, model: BertModel, tokenizer: Tokenizer, name: str):
        self.model, self.tokenizer, self.name = model, tokenizer, name

    def pooled(self, texts: list[str]) -> torch.Tensor:
        encs = self.tokenizer.encode_batch(texts)
        width = max(len(e.ids) for e in encs)
        ids = torch.tensor([e.ids + [0] * (width - len(e.ids)) for e in encs])
        mask = torch.tensor([[1] * len(e.ids) + [0] * (width - len(e.ids)) for e in encs])
        hidden = self.model(input_ids=ids, attention_mask=mask, token_type_ids=torch.zeros_like(ids)).last_hidden_state
        pooled = (hidden * mask[..., None]).sum(1) / mask.sum(1, keepdim=True)
        return F.normalize(pooled, dim=1)

    def encode(self, texts: list[str]) -> np.ndarray:
        self.model.eval()
        with torch.no_grad():
            return np.concatenate([self.pooled(texts[i:i + 64]).numpy() for i in range(0, len(texts), 64)])


def tokenizer_for(model_dir: Path) -> Tokenizer:
    tokenizer = Tokenizer.from_file(str(model_dir / "tokenizer.json"))
    tokenizer.enable_truncation(max_length=128)
    tokenizer.no_padding()
    return tokenizer


def load_base(base: str) -> tuple[BertModel, Tokenizer]:
    """A fresh copy of the starting encoder. base is a directory holding model.onnx, config.json and tokenizer.json
    (the app's MiniLM), or a Hugging Face model id or directory of a BERT-style sentence encoder (on a machine
    that can reach the hub), such as sentence-transformers/all-MiniLM-L12-v2 or BAAI/bge-small-en-v1.5."""
    path = Path(base).expanduser()
    if (path / "model.onnx").is_file():
        return bert_from_onnx(path), tokenizer_for(path)
    model = BertModel.from_pretrained(base, add_pooling_layer=False).eval()
    if (path / "tokenizer.json").is_file():
        return model, tokenizer_for(path)
    from huggingface_hub import hf_hub_download
    return model, tokenizer_for(Path(hf_hub_download(base, "tokenizer.json")).parent)


def router_vectors(encoder: TorchEncoder, texts: list[str]) -> torch.Tensor:
    """EmbeddingRouter.embed, with gradients."""
    texts = [er.normalize(t) for t in texts]
    full = encoder.pooled(texts)
    head = encoder.pooled([" ".join(t.split()[:HEAD_WORDS]) for t in texts])
    return F.normalize(full + head, dim=1)


def fine_tune(base: str, items: list[dict], lr: float, epochs: int, seed: int = SEED, log=print) -> TorchEncoder:
    """Fine-tunes a fresh copy of the ONNX model's encoder, with a 64-unit layer like the app's on top, on the
    single-request items. The layer starts from embed_router.train_mlp on the frozen vectors."""
    torch.manual_seed(seed)
    rng = np.random.default_rng(seed)
    encoder = TorchEncoder(*load_base(base), "")
    single = [i for i in items if i["intent"] in er.LABELS]
    texts = [i["text"] for i in single]
    y = torch.tensor([er.LABELS.index(i["intent"]) for i in single])
    start = er.EmbeddingRouter(encoder, er.routes_from(single), "mlp", head_words=HEAD_WORDS, l2=HEAD_L2, split=False)
    head = [torch.nn.Parameter(torch.tensor(np.asarray(p), dtype=torch.float32)) for p in start.weights]
    optimizer = torch.optim.AdamW([{"params": encoder.model.parameters(), "lr": lr, "weight_decay": 0.01},
                                   {"params": head, "lr": HEAD_LR, "weight_decay": 0.0}])
    steps = epochs * int(np.ceil(len(texts) / BATCH))
    schedule = torch.optim.lr_scheduler.LambdaLR(optimizer, lambda s: min(1.0, (s + 1) / (0.1 * steps)) *
                                                 max(0.0, (steps - s) / steps))
    for epoch in range(epochs):
        encoder.model.train()
        order, total = rng.permutation(len(texts)), 0.0
        for b in range(0, len(texts), BATCH):
            idx = order[b:b + BATCH]
            x = router_vectors(encoder, [texts[i] for i in idx])
            w1, b1, w2, b2 = head
            logits = F.relu(er.LINEAR_SCALE * x @ w1 + b1) @ w2 + b2
            loss = F.cross_entropy(logits, y[idx])
            optimizer.zero_grad()
            loss.backward()
            optimizer.step()
            schedule.step()
            total += float(loss.detach()) * len(idx)
        log(f"    epoch {epoch + 1}: loss {total / len(texts):.4f}")
    encoder.model.eval()
    return encoder


def cross_fit(base: str, items: list[dict], lr: float, epochs: int, folds: int = er.FOLDS, log=print):
    """Out-of-fold (thresholds, cost, right) as embed_router.cross_fit, but each fold also fine-tunes the encoder,
    so no sentence is scored by an encoder or a layer that learned from it."""
    fold = np.arange(len(items)) % folds
    np.random.default_rng(0).shuffle(fold)
    margins = {r: np.zeros(len(items)) for r in er.TOOL_INTENTS}
    two = np.zeros(len(items), dtype=bool)
    for f in range(folds):
        test = np.flatnonzero(fold == f)
        train = [items[i] for i in np.flatnonzero(fold != f)]
        log(f"  fold {f + 1}/{folds}")
        encoder = fine_tune(base, train, lr, epochs, log=log)
        embedder = er.EmbeddingRouter(encoder, er.routes_from(train), "mlp", head_words=HEAD_WORDS, l2=HEAD_L2)
        _, m, t = embedder.split_margins([items[i]["text"] for i in test])
        for r in er.TOOL_INTENTS:
            margins[r][test] = m[r]
        two[test] = t
    gold = np.array([i["intent"] for i in items])
    thresholds, cost = er.best_thresholds(margins, two, gold, er.GRIDS["mlp"])
    return thresholds, cost, int((er.predict(margins, thresholds, two) == gold).sum())


class LastHidden(torch.nn.Module):
    """BertModel with plain positional inputs and one output, for the ONNX exporter."""

    def __init__(self, model: BertModel):
        super().__init__()
        self.model = model

    def forward(self, input_ids, attention_mask, token_type_ids):
        return self.model(input_ids=input_ids, attention_mask=attention_mask,
                          token_type_ids=token_type_ids).last_hidden_state


def export_onnx(encoder: TorchEncoder, out_dir: Path, name: str) -> Path:
    """Writes out_dir/model.onnx with the original's inputs and output, its config.json, tokenizer.json and
    vocab.txt (what the app's WordPiece tokenizer reads)."""
    out_dir.mkdir(parents=True, exist_ok=True)
    ids = torch.tensor([encoder.tokenizer.encode("set a timer for ten minutes").ids])
    axes = {0: "batch", 1: "sequence"}
    path = out_dir / "model.onnx"
    torch.onnx.export(LastHidden(encoder.model).eval(), (ids, torch.ones_like(ids), torch.zeros_like(ids)), str(path),
                      input_names=["input_ids", "attention_mask", "token_type_ids"],
                      output_names=["last_hidden_state"], opset_version=14, dynamo=False,
                      dynamic_axes={"input_ids": axes, "attention_mask": axes, "token_type_ids": axes,
                                    "last_hidden_state": axes})
    encoder.tokenizer.save(str(out_dir / "tokenizer.json"))
    vocab = encoder.tokenizer.get_vocab()
    (out_dir / "vocab.txt").write_text("".join(t + "\n" for t, _ in sorted(vocab.items(), key=lambda kv: kv[1])))
    config = encoder.model.config.to_dict()
    config["_name_or_path"] = name
    (out_dir / "config.json").write_text(json.dumps(config, indent=2) + "\n")
    return path


def check_against_onnx(encoder: TorchEncoder, model_dir: Path, model_file: str = "model.onnx") -> float:
    """Largest difference between this encoder's vectors and the ONNX model's, over a few sentences."""
    texts = ["set a timer for ten minutes", "remember that the wifi password is on the fridge",
             "what's the capital of france", "navigate home"]
    onnx_vectors = er.OnnxEncoder(model_dir, model_file).encode(texts)
    return float(np.abs(onnx_vectors - encoder.encode(texts)).max())


def parse_grid(text: str) -> list[tuple[float, int]]:
    return [(float(lr), int(epochs)) for lr, epochs in (pair.split(":") for pair in text.split(","))]


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["check", "cv", "train"])
    parser.add_argument("--base", default=str(er.STOCK_MODEL_DIR),
                        help="starting encoder: a model.onnx directory (default: the app's MiniLM) or a hub id")
    parser.add_argument("--grid", default="1e-4:4,2e-4:4")
    parser.add_argument("--lr", type=float, default=3e-5)
    parser.add_argument("--epochs", type=int, default=4)
    parser.add_argument("--thresholds", help="train: SAVE,ACTION thresholds from an earlier cv run (skips it)")
    parser.add_argument("--out", help="train: output directory for the fine-tuned model")
    parser.add_argument("--name", default="all-MiniLM-L6-v2-router", help="train: model name for the config file")
    parser.add_argument("--threads", type=int, default=0, help="torch threads (0: torch's default)")
    args = parser.parse_args(argv)
    if args.threads:
        torch.set_num_threads(args.threads)
    items = er.load_pool()

    if args.command == "check":
        encoder = TorchEncoder(*load_base(args.base), "")
        print(f"largest difference from the ONNX model's vectors: {check_against_onnx(encoder, Path(args.base)):.2e}")
        return

    if args.command == "cv":
        for lr, epochs in parse_grid(args.grid):
            start = time.time()
            thresholds, cost, right = cross_fit(args.base, items, lr, epochs)
            print(f"lr {lr:g} epochs {epochs}: out-of-fold cost {cost:g}, right {right}/{len(items)}, "
                  f"thresholds {thresholds} ({time.time() - start:.0f} s)", flush=True)
        return

    if not args.out:
        raise SystemExit("train needs --out DIR")
    out_dir = Path(args.out).expanduser()
    if args.thresholds:
        save, action = (float(v) for v in args.thresholds.split(","))
        thresholds = {router.SAVE: save, router.ACTION: action}
    else:
        thresholds, cost, right = cross_fit(args.base, items, args.lr, args.epochs)
        print(f"out-of-fold cost {cost:g}, right {right}/{len(items)}, thresholds {thresholds}", flush=True)
    encoder = fine_tune(args.base, items, args.lr, args.epochs)
    export_onnx(encoder, out_dir, args.name)
    diff = check_against_onnx(encoder, out_dir)
    print(f"exported {out_dir / 'model.onnx'}; largest difference from torch: {diff:.2e}")
    if diff > 1e-4:
        raise SystemExit("the exported model does not match the fine-tuned one")
    onnx_encoder = er.OnnxEncoder(out_dir)
    final = er.EmbeddingRouter(onnx_encoder, er.routes_from(items), "mlp", thresholds, head_words=HEAD_WORDS,
                               l2=HEAD_L2)
    path = er.config_path(onnx_encoder.name, "mlp")
    path.write_text(json.dumps(final.config()) + "\n")
    print(f"wrote {path}")


if __name__ == "__main__":
    sys.exit(main())
