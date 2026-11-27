"""Embedding intent router: a small sentence-embedding model picks SAVE, ACTION or NONE from the transcript.

The second router to compare with router.py's phrase rules, in the style of aurelio semantic-router:
  1. The training pool: example utterances for SAVE, ACTION and NONE in eval/router/routes.json, the labeled
     eval/router/fit.json and eval/router/train.jsonl (sentences from writers who never saw any test set, a few
     with two requests, SAVE+ACTION). Nothing in it is from either eval fixture or eval/router/heldout.jsonl.
  2. A transcript is embedded (MiniLM, the whole message averaged with its first few words) and scored per route.
     Two scorers:
       knn     the mean cosine similarity of the route's top_k closest examples (semantic-router's way)
       linear  a 384x3 softmax layer trained on the single-request examples; the score is the route's logit
       mlp     the same with one hidden layer of 64 (ReLU) between; what the app ships (APP_SCORER)
     A longer message is also split into its sentences and clauses (segments()), each scored the same way, and each
     route keeps its best score: a request at the end of a story still counts.
  3. SAVE and ACTION each fire when their score beats NONE's by at least that route's threshold. Both fire only
     when the message holds two requests ("remember X, and remind me in 10 minutes"). Nothing firing is NONE:
     plain chat with no tool schemas.
  4. Thresholds are fitted on out-of-fold scores: the pool is cut in five, and each fifth is scored by a router
     trained on the other four, as a new sentence would be. The same cross-fit picks head_words and top_k (knn) or
     l2 (linear). No eval fixture or held-out sentence is used to choose anything.
  The result is saved as eval/router/embed-<model>-<scorer>.json, with the linear weights, so the app loads
  the same numbers. scripts/eval/router_heldout.py scores it on the held-out set.

The model is all-MiniLM-L6-v2 as ONNX (90 MB fp32, 22M parameters, 384 dims; about 23 MB as int8), which the app
could run on its existing ONNX Runtime dependency. The encoder is injected, so the tests run with a fake one.

Usage (from scripts/eval, with onnxruntime, tokenizers and numpy installed):
  python embed_router.py fit      [--model-dir DIR] [--model-file F] [--scorers mlp]   fit, write the configs
  python embed_router.py score    [--model-dir DIR] [--model-file F] [--fixture PATH ...] [--verbose]
                                  routing-only intent accuracy beside the rules, no Gemma
  python embed_router.py quantize [--model-dir DIR]                    writes DIR/model_int8.onnx
  python embed_router.py overlap  [--model-dir DIR]                    closest example to each fixture case
"""
import argparse
import json
import os
import re
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Protocol

import numpy as np

import router

REPO = Path(__file__).resolve().parents[2]
ROUTER_DIR = REPO / "eval" / "router"
ROUTES = ROUTER_DIR / "routes.json"
FIT = ROUTER_DIR / "fit.json"
TRAIN = ROUTER_DIR / "train.jsonl"
STORIES = ROUTER_DIR / "train-stories.jsonl"
STORY_PIECES = 3  # NONE pieces taken from each story (load_story_pieces)
FIXTURES = [REPO / "eval" / "fixtures" / "tool-eval.json", REPO / "eval" / "fixtures" / "tool-eval-holdout.json"]
DEFAULT_MODEL_DIR = Path(os.environ.get("EMBED_MODEL_DIR", str(Path.home() / "models" / "all-MiniLM-L6-v2" / "onnx")))
LABELS = (router.SAVE, router.ACTION, router.NONE)
TOOL_INTENTS = (router.SAVE, router.ACTION)
SCORERS = ("knn", "linear", "mlp")
GRIDS = {"knn": np.round(np.arange(-0.30, 0.401, 0.005), 3), "linear": np.round(np.arange(-8.0, 8.01, 0.1), 2),
         "mlp": np.round(np.arange(-8.0, 8.01, 0.1), 2)}
SWEEP = {"head_words": (0, 3, 4), "knn": {"top_k": (1, 3, 5)}, "linear": {"l2": (0.01, 0.03, 0.1, 0.3)},
         "mlp": {"l2": (0.01, 0.03)}}
MLP_HIDDEN, MLP_STEPS = 64, 300
APP_SCORER = "mlp"  # what the app ships (core/src/androidMain/assets/router/)
FOLDS = 5
NONE_COST = 2.0  # a tool shown on a message that wanted none costs this much; any other miss costs 1
LINEAR_SCALE = 10.0  # cosine-sized inputs, so the logits need a scale to reach confident probabilities


class Encoder(Protocol):
    name: str

    def encode(self, texts: list[str]) -> np.ndarray:
        """One L2-normalized row per text."""


# Where one spoken message holds more than one thought: sentence ends, a comma before a joining word, and joining
# phrases that start a new request. Plain "and" splits only before a request verb ("set a timer and write down that
# ..."), not elsewhere ("eggs and bread").
SEGMENT = re.compile(r"[.!?;]+\s+|,\s*(?=(?:and|then|also|plus|oh|so|but)\b)"
                     r"|\s+(?:and then|and also|oh and|then|also)\s+"
                     r"|\s+and\s+(?=(?:save|jot|write|note|make|take|keep|add|put|set|start|remind|navigate|get|show|text|email|send|tell|give|find|directions)\b)",
                     re.IGNORECASE | re.ASCII)  # ASCII: Java's \s and \b, so the app's port splits the same


def segments(transcript: str) -> list[str]:
    """The message's sentences and clauses of two words or more, when there are at least two; otherwise none.

    A request is often buried: "We landed, it's raining, the kids are cranky. Get me to the hotel." The whole message
    reads as a story; its last sentence reads as a request.
    """
    parts = [p.strip(" ,") for p in SEGMENT.split(normalize(transcript))]
    parts = [p for p in parts if len(p.split()) >= 2]
    return parts if len(parts) > 1 else []


def normalize(transcript: str) -> str:
    """Drops the wake word and 'can you', as the rules do, so 'Hey computer' does not pull messages together."""
    return router._PREFIX.sub("", transcript.strip().lower(), count=1).strip() or transcript.strip().lower()


class OnnxEncoder:
    """all-MiniLM-L6-v2 style: WordPiece ids in, token vectors out, mean-pooled over the mask, L2-normalized."""

    def __init__(self, model_dir: Path | str = DEFAULT_MODEL_DIR, model_file: str = "model.onnx", threads: int = 1):
        import onnxruntime as ort
        from tokenizers import Tokenizer
        model_dir = Path(model_dir).expanduser()
        path = model_dir / model_file
        if not path.is_file():
            raise SystemExit(f"embedding model not found: {path} (see docs/spikes/2026-11-10-tool-calling-eval.md)")
        self.tokenizer = Tokenizer.from_file(str(model_dir / "tokenizer.json"))
        self.tokenizer.enable_truncation(max_length=128)
        options = ort.SessionOptions()
        options.intra_op_num_threads = threads  # one thread: closer to one phone core than the Mac's whole CPU
        self.session = ort.InferenceSession(str(path), options, providers=["CPUExecutionProvider"])
        self.inputs = {i.name for i in self.session.get_inputs()}
        config = model_dir / "config.json"
        base = json.loads(config.read_text()).get("_name_or_path", model_dir.name) if config.is_file() else model_dir.name
        suffix = Path(model_file).stem.removeprefix("model").strip("_-")
        self.name = base.split("/")[-1] + (f"-{suffix}" if suffix else "")  # all-MiniLM-L6-v2, all-MiniLM-L6-v2-int8

    def encode(self, texts: list[str]) -> np.ndarray:
        """One text at a time, unpadded, as the app would run it."""
        rows = []
        for text in texts:
            enc = self.tokenizer.encode(text)
            ids = np.array([enc.ids], dtype=np.int64)
            mask = np.array([enc.attention_mask], dtype=np.int64)
            feed = {"input_ids": ids, "attention_mask": mask, "token_type_ids": np.zeros_like(ids)}
            hidden = self.session.run(None, {k: v for k, v in feed.items() if k in self.inputs})[0][0]
            pooled = (hidden * mask[0][:, None]).sum(axis=0) / max(int(mask.sum()), 1)
            rows.append(pooled / np.linalg.norm(pooled))
        return np.array(rows)


class CachedEncoder:
    """Remembers vectors by text, so a hyperparameter sweep embeds each sentence once."""

    def __init__(self, inner: Encoder):
        self.inner, self.name, self._cache = inner, inner.name, {}

    def encode(self, texts: list[str]) -> np.ndarray:
        missing = [t for t in dict.fromkeys(texts) if t not in self._cache]
        if missing:
            self._cache.update(zip(missing, self.inner.encode(missing)))
        return np.array([self._cache[t] for t in texts])


def load_routes(path: Path = ROUTES) -> dict[str, list[str]]:
    data = json.loads(Path(path).read_text())
    return {k: data[k] for k in LABELS}


def load_fit(path: Path = FIT) -> list[dict]:
    return json.loads(Path(path).read_text())


def load_train(path: Path = TRAIN) -> list[dict]:
    return [json.loads(line) for line in Path(path).read_text().splitlines() if line.strip()]


def load_story_pieces(path: Path = STORIES, per_story: int = STORY_PIECES) -> list[dict]:
    """NONE examples cut from long spoken messages that ask for nothing (train-stories.jsonl: stories, venting,
    thinking aloud), split with segments() as a message is at routing time, at most [per_story] from each.

    A long message is scored whole and piece by piece, and any piece can fire a tool. Trained only on short
    sentences, the layer took a plain clause of a story ("the inspector said the outlets are two prong") for
    something to save, so long chats got save_note. Each of these pieces is, by construction, not a request. All
    665 pieces tipped the layer toward missing real saves; three per story (chosen with a fixed seed) kept those
    level on the held-out dev half while cutting saves on long chats.
    """
    pieces = []
    for i, item in enumerate(load_train(path)):
        parts = segments(item["text"])
        if len(parts) > per_story:
            parts = [parts[j] for j in sorted(np.random.default_rng(i).choice(len(parts), per_story, replace=False))]
        pieces += [{"text": p, "intent": router.NONE} for p in parts]
    return pieces


def load_pool() -> list[dict]:
    """Every labeled training sentence: the route examples, fit.json, train.jsonl and the story pieces. Nothing from
    eval/fixtures, heldout.jsonl or long.jsonl is in it (test_embed_router checks)."""
    pool = [{"text": t, "intent": label} for label, texts in load_routes().items() for t in texts]
    return pool + load_fit() + load_train() + load_story_pieces()


def routes_from(items: list[dict]) -> dict[str, list[str]]:
    """The single-intent items as route examples. SAVE+ACTION items are only used to fit thresholds."""
    routes = {label: [] for label in LABELS}
    for item in items:
        if item["intent"] in routes:
            routes[item["intent"]].append(item["text"])
    return routes


def config_path(encoder_name: str, scorer: str) -> Path:
    return ROUTER_DIR / f"embed-{encoder_name}-{scorer}.json"


def train_linear(x: np.ndarray, labels: list[str], l2: float, steps: int = 3000, lr: float = 0.5):
    """Softmax regression by full-batch gradient descent from zeros. Deterministic: same vectors, same weights."""
    y = np.eye(len(LABELS))[[LABELS.index(label) for label in labels]]
    w, b = np.zeros((x.shape[1], len(LABELS))), np.zeros(len(LABELS))
    for _ in range(steps):
        z = LINEAR_SCALE * x @ w + b
        p = np.exp(z - z.max(axis=1, keepdims=True))
        p /= p.sum(axis=1, keepdims=True)
        g = (p - y) / len(x)
        w -= lr * (LINEAR_SCALE * x.T @ g + l2 * w)
        b -= lr * g.sum(axis=0)
    return w, b


def train_mlp(x: np.ndarray, labels: list[str], l2: float, hidden: int = MLP_HIDDEN, steps: int = MLP_STEPS,
              seed: int = 0):
    """One hidden ReLU layer (384 -> 64 -> 3) by full-batch Adam from a seeded start. Deterministic per seed.

    A straight line through MiniLM's space mixes up topic and request: "Navigate to Target" scored as shopping chat.
    One small hidden layer can learn "a request to go somewhere, whatever the place". 24,800 weights, a few seconds.
    """
    y = np.eye(len(LABELS))[[LABELS.index(label) for label in labels]]
    rng = np.random.default_rng(seed)
    params = [rng.normal(0, 1 / np.sqrt(x.shape[1]), (x.shape[1], hidden)), np.zeros(hidden),
              rng.normal(0, 1 / np.sqrt(hidden), (hidden, len(LABELS))), np.zeros(len(LABELS))]
    m, v = [np.zeros_like(p) for p in params], [np.zeros_like(p) for p in params]
    for t in range(1, steps + 1):
        w1, b1, w2, b2 = params
        h = np.maximum(0.0, LINEAR_SCALE * x @ w1 + b1)
        z = h @ w2 + b2
        p = np.exp(z - z.max(axis=1, keepdims=True))
        p /= p.sum(axis=1, keepdims=True)
        g = (p - y) / len(x)
        gh = (g @ w2.T) * (h > 0)
        grads = [LINEAR_SCALE * x.T @ gh + l2 * w1, gh.sum(axis=0), h.T @ g + l2 * w2, g.sum(axis=0)]
        for i, (param, grad) in enumerate(zip(params, grads)):
            m[i] = 0.9 * m[i] + 0.1 * grad
            v[i] = 0.999 * v[i] + 0.001 * grad * grad
            param -= 0.01 * (m[i] / (1 - 0.9 ** t)) / (np.sqrt(v[i] / (1 - 0.999 ** t)) + 1e-8)
    return tuple(params)


@dataclass
class EmbedDecision:
    intents: list[str]                 # tool intents that fired, best first; empty is NONE
    scores: dict[str, float]           # per route: knn similarity or linear logit
    seconds: float = 0.0               # embedding plus scoring, for this transcript

    @property
    def intent(self) -> str:
        return "+".join(sorted(self.intents, key=TOOL_INTENTS.index)) or router.NONE


@dataclass
class EmbeddingRouter:
    encoder: Encoder
    routes: dict[str, list[str]]
    scorer: str = "linear"
    thresholds: dict[str, float] = field(default_factory=lambda: {router.SAVE: 0.0, router.ACTION: 0.0})
    head_words: int = 4
    top_k: int = 3
    l2: float = 0.01
    weights: tuple | None = None       # linear (W 384x3, b 3), mlp (W1, b1, W2, b2); trained from the routes when None
    split: bool = True                 # also score each sentence or clause of a longer message (segments())
    clock: Callable[[], float] = time.perf_counter

    def __post_init__(self):
        if self.scorer not in SCORERS:
            raise ValueError(f"scorer must be one of {SCORERS}")
        self._labels = [label for label in LABELS for _ in self.routes[label]]
        if self.scorer == "knn" or self.weights is None:  # a saved linear router needs no route vectors
            self._matrix = self.embed([t for label in LABELS for t in self.routes[label]])
        if self.scorer == "linear" and self.weights is None:
            self.weights = train_linear(self._matrix, self._labels, self.l2)
        if self.scorer == "mlp" and self.weights is None:
            self.weights = train_mlp(self._matrix, self._labels, self.l2)

    def embed(self, transcripts: list[str]) -> np.ndarray:
        """The whole message averaged with its first head_words words.

        MiniLM vectors follow the topic, so "Save this idea: a voice app that reads recipes" lands near recipes. The
        request is nearly always in the first few words ("save this idea", "remind me in", "how do timers"), so the
        head pulls the vector back toward what is asked. head_words 0 uses the whole message only.
        """
        texts = [normalize(t) for t in transcripts]
        full = self.encoder.encode(texts)
        if not self.head_words:
            return full
        mixed = full + self.encoder.encode([" ".join(t.split()[: self.head_words]) for t in texts])
        return mixed / np.linalg.norm(mixed, axis=1, keepdims=True)

    def route_scores(self, vectors: np.ndarray) -> dict[str, np.ndarray]:
        if self.scorer == "linear":
            w, b = self.weights
            z = LINEAR_SCALE * vectors @ np.asarray(w) + np.asarray(b)
            return {label: z[:, i] for i, label in enumerate(LABELS)}
        if self.scorer == "mlp":
            w1, b1, w2, b2 = (np.asarray(p) for p in self.weights)
            z = np.maximum(0.0, LINEAR_SCALE * vectors @ w1 + b1) @ w2 + b2
            return {label: z[:, i] for i, label in enumerate(LABELS)}
        sims = vectors @ self._matrix.T
        labels = np.array(self._labels)
        out = {}
        for label in LABELS:
            block = sims[:, labels == label]
            k = min(self.top_k, block.shape[1])
            out[label] = np.sort(block, axis=1)[:, -k:].mean(axis=1)
        return out

    def margins(self, transcripts: list[str]) -> tuple[dict[str, np.ndarray], dict[str, np.ndarray]]:
        """(scores, margins) of the whole message: a tool route's margin is its score minus NONE's."""
        scores = self.route_scores(self.embed(transcripts))
        return scores, {r: scores[r] - scores[router.NONE] for r in TOOL_INTENTS}

    def split_margins(self, transcripts: list[str]) -> tuple[dict[str, np.ndarray], dict[str, np.ndarray], np.ndarray]:
        """(scores, margins, two) with segments: each tool margin is the best of the whole message and its segments.

        two: the message holds two requests, either joined by a word ("and", "then") or with SAVE and ACTION each
        winning in a different piece. scores stay the whole message's, for logs.
        """
        scores, margins = self.margins(transcripts)
        margins = {r: m.copy() for r, m in margins.items()}
        best_piece = {r: np.zeros(len(transcripts), dtype=int) for r in TOOL_INTENTS}  # 0 is the whole message
        pieces = [segments(t) if self.split else [] for t in transcripts]
        flat = [p for ps in pieces for p in ps]
        if flat:
            _, piece_margins = self.margins(flat)
            j = 0
            for i, ps in enumerate(pieces):
                for k in range(len(ps)):
                    for r in TOOL_INTENTS:
                        if piece_margins[r][j] > margins[r][i]:
                            margins[r][i], best_piece[r][i] = piece_margins[r][j], k + 1
                    j += 1
        two = np.array([bool(TWO_REQUESTS.search(t)) or best_piece[router.SAVE][i] != best_piece[router.ACTION][i]
                        for i, t in enumerate(transcripts)])
        return scores, margins, two

    def decide(self, transcript: str) -> EmbedDecision:
        start = self.clock()
        scores, margins, two = self.split_margins([transcript])
        fired = fire({r: float(m[0]) for r, m in margins.items()}, self.thresholds, bool(two[0]))
        return EmbedDecision(fired, {r: round(float(s[0]), 4) for r, s in scores.items()}, self.clock() - start)

    def route(self, transcript: str) -> router.Route:
        """A router.Route with an intent and no arguments: Gemma sees the routed tool or tools and fills them in."""
        return embed_route(self.decide(transcript))

    def config(self) -> dict:
        data = {"model": self.encoder.name, "scorer": self.scorer, "thresholds": self.thresholds,
                "head_words": self.head_words, "split": self.split}
        if self.scorer == "knn":
            data["top_k"] = self.top_k
            return data
        data.update(l2=self.l2, scale=LINEAR_SCALE, labels=list(LABELS))
        if self.scorer == "mlp":
            w1, b1 = self.weights[:2]
            data.update(hidden_weights=np.round(np.asarray(w1), 6).tolist(),
                        hidden_bias=np.round(np.asarray(b1), 6).tolist())
        w, b = self.weights[-2:]
        data.update(weights=np.round(np.asarray(w), 6).tolist(), bias=np.round(np.asarray(b), 6).tolist())
        return data


def embed_route(d: EmbedDecision) -> router.Route:
    tools = {router.SAVE: "save_note", router.ACTION: "do_action"}
    return router.Route(d.intent, "embed", "+".join(tools[i] for i in d.intent.split("+") if i in tools) or None, None)


TWO_REQUESTS = re.compile(r"\b(?:and|then|also|plus)\b", re.IGNORECASE)


def fire(margins: dict[str, float], thresholds: dict[str, float], two: bool) -> list[str]:
    """Tool intents over their thresholds, best first. Both only when the message holds two requests ([two]).

    A single request often scores over both thresholds ("Remind me in 20 minutes to check the oven" sits near the
    SAVE examples too), and a second tool there is an over-call.
    """
    fired = sorted((r for r in TOOL_INTENTS if margins[r] >= thresholds[r]), key=lambda r: -margins[r])
    return fired if len(fired) < 2 or two else fired[:1]


QUESTION = re.compile(r"\?\s*$|^(?:what|when|where|who|whom|why|how|which|is|are|was|were|do|does|did|can|could|"
                      r"should|would|will|have|has)\b", re.IGNORECASE)


def route_with_rules(embedder: EmbeddingRouter, transcript: str, decision: EmbedDecision | None = None) -> router.Route:
    """Rules first, with the embedding in place of the gate, and a veto on questions.

    - A rule hit on a statement ("Remind me in 20 minutes to check the oven") runs as in the rules setup: no model.
    - A rule hit on a question ("Remember that movie with the talking dog?") stands only when the embedding agrees;
      otherwise the embedding's route wins, so that one goes to plain chat.
    - No rule hit: the embedding's route, with no arguments.
    """
    d = decision or embedder.decide(transcript)
    hit = router.apply_rules(transcript)
    if hit and (hit.intent == d.intent or not QUESTION.search(normalize(transcript))):
        return hit
    return embed_route(d)


# ---- Fitting --------------------------------------------------------------------------------------------------

def predict(margins: dict[str, np.ndarray], thresholds: dict[str, float], two: np.ndarray) -> np.ndarray:
    """fire() for many messages at once. [two]: whether each message joins two requests."""
    ms, ma = margins[router.SAVE], margins[router.ACTION]
    s, a = ms >= thresholds[router.SAVE], ma >= thresholds[router.ACTION]
    both = s & a & ~two
    s, a = s & ~(both & (ma > ms)), a & ~(both & (ms >= ma))
    return np.where(s & a, f"{router.SAVE}+{router.ACTION}", np.where(s, router.SAVE, np.where(a, router.ACTION, router.NONE)))


def best_thresholds(margins: dict[str, np.ndarray], two: np.ndarray, gold: np.ndarray, grid,
                    none_cost: float = NONE_COST) -> tuple[dict[str, float], float]:
    """Joint grid search over the SAVE and ACTION thresholds for the lowest cost.

    A wrong tool on a NONE item costs none_cost, any other miss 1, since calling a tool by mistake hurts more than
    missing one. Ties go to the median of the tied thresholds, so one example does not set the edge.
    """
    weight = np.where(gold == router.NONE, none_cost, 1.0)
    best, pairs = None, []
    for ts in grid:
        for ta in grid:
            cost = float((weight * (predict(margins, {router.SAVE: ts, router.ACTION: ta}, two) != gold)).sum())
            if best is None or cost < best - 1e-9:
                best, pairs = cost, [(ts, ta)]
            elif abs(cost - best) < 1e-9:
                pairs.append((ts, ta))
    return ({router.SAVE: round(float(np.median([p[0] for p in pairs])), 3),
             router.ACTION: round(float(np.median([p[1] for p in pairs])), 3)}, best)


def fit_thresholds(embedder: EmbeddingRouter, items: list[dict], grid=None) -> tuple[dict[str, float], float]:
    """Thresholds fitted on [items] as this router scores them, and its accuracy there."""
    _, margins, two = embedder.split_margins([i["text"] for i in items])
    gold = np.array([i["intent"] for i in items])
    thresholds, _ = best_thresholds(margins, two, gold, GRIDS[embedder.scorer] if grid is None else grid)
    return thresholds, round(float((predict(margins, thresholds, two) == gold).mean()), 3)


def cross_fit(encoder: Encoder, scorer: str, items: list[dict], folds: int = FOLDS, **params):
    """Out-of-fold margins: each item scored by a router trained on the other folds, as a new sentence would be.

    Fitting thresholds on these, not on the training sentences themselves, keeps the cutoffs honest: a router is
    always surer of the sentences it learned from. Returns (thresholds, cost, accuracy) on the out-of-fold margins.
    """
    fold = np.arange(len(items)) % folds
    np.random.default_rng(0).shuffle(fold)
    margins = {r: np.zeros(len(items)) for r in TOOL_INTENTS}
    two = np.zeros(len(items), dtype=bool)
    for f in range(folds):
        test = np.flatnonzero(fold == f)
        train = [items[i] for i in np.flatnonzero(fold != f)]
        embedder = EmbeddingRouter(encoder, routes_from(train), scorer, **params)
        _, m, t = embedder.split_margins([items[i]["text"] for i in test])
        for r in TOOL_INTENTS:
            margins[r][test] = m[r]
        two[test] = t
    gold = np.array([i["intent"] for i in items])
    thresholds, cost = best_thresholds(margins, two, gold, GRIDS[scorer])
    return thresholds, cost, round(float((predict(margins, thresholds, two) == gold).mean()), 3)


def sweep(encoder: Encoder, scorer: str, items: list[dict], log=print) -> EmbeddingRouter:
    """Every head_words x (top_k or l2), scored by cross-fitting on the training pool alone. The lowest cost wins;
    the router is then trained on the whole pool with that setting and its out-of-fold thresholds."""
    best, best_cost = None, None
    (param, values), = SWEEP[scorer].items()
    for head_words in SWEEP["head_words"]:
        for value in values:
            params = {"head_words": head_words, param: value}
            thresholds, cost, acc = cross_fit(encoder, scorer, items, **params)
            log(f"  {scorer} head_words={head_words} {param}={value}: out-of-fold cost {cost:g}, right {acc}, "
                f"thresholds {thresholds}")
            if best_cost is None or cost < best_cost:
                best, best_cost = (params, thresholds), cost
    params, thresholds = best
    return EmbeddingRouter(encoder, routes_from(items), scorer, thresholds, **params)


def load_router(model_dir: Path, model_file: str = "model.onnx", scorer: str = "linear",
                encoder: Encoder | None = None) -> EmbeddingRouter:
    encoder = encoder or OnnxEncoder(model_dir, model_file)
    path = config_path(encoder.name, scorer)
    if not path.is_file():
        raise SystemExit(f"no router config at {path}: run `python embed_router.py fit` first")
    data = json.loads(path.read_text())
    weights = None
    if scorer != "knn":
        weights = (np.array(data["weights"]), np.array(data["bias"]))
        if scorer == "mlp":
            weights = (np.array(data["hidden_weights"]), np.array(data["hidden_bias"])) + weights
    return EmbeddingRouter(encoder, routes_from(load_pool()), scorer, data["thresholds"], data["head_words"],
                           data.get("top_k", 3), data.get("l2", 0.01), weights, data.get("split", False))


# ---- Routing-only scoring -------------------------------------------------------------------------------------

def expected_intent(case: dict) -> str:
    import router_eval  # late: router_eval imports tool_eval, which fitting does not need
    return router_eval.expected_intent(case)


def score_cases(cases: list[dict], decide: Callable[[str], str], expected=expected_intent) -> dict:
    rows = [{"id": c["id"], "user": c["user"], "expected": expected(c), "routed": decide(c["user"])} for c in cases]
    no_call = [r for r in rows if r["expected"] == router.NONE]
    calls = [r for r in rows if r["expected"] != router.NONE]
    return {"n": len(rows), "intent_match": sum(r["expected"] == r["routed"] for r in rows),
            "over_call": sum(r["routed"] != router.NONE for r in no_call), "no_call_n": len(no_call),
            "missed": sum(r["routed"] == router.NONE for r in calls), "call_n": len(calls),
            "misses": [r for r in rows if r["expected"] != r["routed"]]}


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["fit", "score", "quantize", "overlap"])
    parser.add_argument("--model-dir", default=str(DEFAULT_MODEL_DIR))
    parser.add_argument("--model-file", default="model.onnx", help="model.onnx, or model_int8.onnx after quantize")
    parser.add_argument("--fixture", action="append", help="default: both tool-eval fixtures")
    parser.add_argument("--verbose", action="store_true")
    parser.add_argument("--scorers", default=",".join(SCORERS), help="fit: which scorers to fit (default: all)")
    args = parser.parse_args(argv)
    model_dir = Path(args.model_dir).expanduser()

    if args.command == "quantize":
        from onnxruntime.quantization import QuantType, quantize_dynamic
        out = model_dir / "model_int8.onnx"
        quantize_dynamic(str(model_dir / "model.onnx"), str(out), weight_type=QuantType.QInt8)
        print(f"wrote {out} ({out.stat().st_size / 1e6:.1f} MB)")
        return

    encoder = CachedEncoder(OnnxEncoder(model_dir, args.model_file))
    if args.command == "fit":
        items = load_pool()
        for scorer in args.scorers.split(","):
            best = sweep(encoder, scorer, items)
            out = config_path(encoder.name, scorer)
            out.write_text(json.dumps(best.config()) + "\n")
            print(f"{scorer}: picked head_words={best.head_words} "
                  f"{'top_k=' + str(best.top_k) if scorer == 'knn' else 'l2=' + str(best.l2)}, "
                  f"thresholds {best.thresholds}; wrote {out.name}")
        return

    fixtures = [Path(f) for f in (args.fixture or FIXTURES)]
    if args.command == "overlap":
        texts = [i["text"] for i in load_pool()]
        bank = encoder.encode([normalize(t) for t in texts])
        for fixture in fixtures:
            cases = json.loads(fixture.read_text())
            sims = encoder.encode([normalize(c["user"]) for c in cases]) @ bank.T
            print(f"\n{fixture.name}: closest route or fit example to each case (cosine, whole message)")
            for c, row in sorted(zip(cases, sims), key=lambda x: -x[1].max()):
                print(f"  {row.max():.2f}  {c['user'][:60]!r:64} ~ {texts[int(row.argmax())][:50]!r}")
        return

    timing = OnnxEncoder(model_dir, args.model_file)  # uncached, for honest per-transcript times
    routers = {s: load_router(model_dir, args.model_file, s, encoder) for s in SCORERS}
    timed = load_router(model_dir, args.model_file, APP_SCORER, timing)
    setups = {"rules": lambda t: router.route(t).intent,
              "embed_knn": lambda t: routers["knn"].decide(t).intent,
              "embed_linear": lambda t: routers["linear"].decide(t).intent,
              "embed_mlp": lambda t: routers["mlp"].decide(t).intent,
              "embed_rules": lambda t: route_with_rules(routers[APP_SCORER], t).intent}
    print(f"model {encoder.name}; " + "; ".join(
        f"{s}: head_words {r.head_words}, thresholds {r.thresholds}" for s, r in routers.items()))
    for fixture in fixtures:
        cases = json.loads(fixture.read_text())
        print(f"\n{fixture.name} ({len(cases)} cases)")
        for name, decide in setups.items():
            s = score_cases(cases, decide)
            print(f"  {name:13} intent {s['intent_match']:>2}/{s['n']}  over-call {s['over_call']}/{s['no_call_n']}  "
                  f"missed call {s['missed']}/{s['call_n']}")
            if args.verbose or name != "rules":
                for m in s["misses"]:
                    print(f"      {m['id']:28} expected {m['expected']:12} got {m['routed']:12} {m['user'][:56]!r}")
    times = [timed.decide(c["user"]).seconds for f in fixtures for c in json.loads(f.read_text())]
    print(f"\nembed_{APP_SCORER} time per transcript (two encoder runs per piece, one thread): median "
          f"{1000 * float(np.median(times)):.1f} ms, max {1000 * max(times):.1f} ms")


if __name__ == "__main__":
    sys.exit(main())
