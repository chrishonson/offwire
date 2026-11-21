# test-import-check: skip (embed_router, router and router_eval are scripts/eval modules, imported from the pytest rootdir like the sibling tests)
"""Model-free tests for embed_router.py: a hashed bag-of-words encoder stands in for MiniLM."""
import json
import re
import zlib

import numpy as np
import pytest

import embed_router as er
import router as r
import router_eval as re_
import tool_eval as te

CASES = {c["id"]: c for c in json.loads(te.FIXTURE.read_text())}
HOLDOUT = json.loads((te.FIXTURE.parent / "tool-eval-holdout.json").read_text())
SAVE_DECISION = json.loads((te.FIXTURE.parent / "save-decision.json").read_text())


class BagEncoder:
    """Each word hashes to a dimension. Shared words make texts similar, which is enough to exercise the router."""
    name = "bag"

    def __init__(self, dims: int = 256):
        self.dims, self.calls = dims, 0

    def encode(self, texts):
        self.calls += 1
        out = np.zeros((len(texts), self.dims))
        for i, text in enumerate(texts):
            for word in re.findall(r"[a-z']+", text.lower()):
                out[i, zlib.crc32(word.encode()) % self.dims] += 1.0
            out[i, -1] += 0.01  # keeps empty text from dividing by zero
        return out / np.linalg.norm(out, axis=1, keepdims=True)


ROUTES = {
    r.SAVE: ["remember that the code is 12", "save a note about the car", "write down the plumber number",
             "jot this down for later"],
    r.ACTION: ["set a timer for ten minutes", "remind me in five minutes", "directions to the store",
               "what's on my calendar today"],
    r.NONE: ["tell me a joke", "how are you", "what is the capital of france", "explain how magnets work"],
}


@pytest.fixture
def knn():
    return er.EmbeddingRouter(BagEncoder(), ROUTES, "knn", {r.SAVE: 0.1, r.ACTION: 0.1}, head_words=0, top_k=1)


def test_normalize_drops_the_wake_word_and_politeness():
    assert er.normalize("Hey Computer, could you please set a timer") == "set a timer"
    assert er.normalize("  Thanks! ") == "thanks!"
    assert er.normalize("Computer") == "computer"  # nothing left after stripping: keep the original


def test_knn_routes_by_the_closest_examples_and_falls_back_to_none(knn):
    assert knn.decide("please save a note about the boat").intent == r.SAVE
    assert knn.decide("set a timer for two minutes").intent == r.ACTION
    assert knn.decide("tell me a joke about cats").intent == r.NONE
    assert knn.decide("zebra quantum lettuce").intent == r.NONE  # near nothing: no route beats NONE by 0.1
    route = knn.route("remind me in an hour")
    assert (route.intent, route.source, route.tool, route.args) == (r.ACTION, "embed", "do_action", None)


def test_decide_reports_scores_and_its_own_time():
    ticks = iter([1.0, 1.25])
    knn = er.EmbeddingRouter(BagEncoder(), ROUTES, "knn", head_words=0, top_k=1, clock=lambda: next(ticks))
    d = knn.decide("set a timer")
    assert d.seconds == 0.25 and set(d.scores) == {r.SAVE, r.ACTION, r.NONE}


def test_head_words_mixes_the_start_of_the_message_back_in():
    enc = BagEncoder()
    knn = er.EmbeddingRouter(enc, ROUTES, "knn", head_words=3, top_k=1)
    long = "write down the plumber number " + "pizza " * 30
    whole = er.EmbeddingRouter(enc, ROUTES, "knn", head_words=0, top_k=1)
    assert knn.margins([long])[1][r.SAVE][0] > whole.margins([long])[1][r.SAVE][0]
    assert np.allclose(np.linalg.norm(knn.embed(["a b c d e"]), axis=1), 1.0)


def test_both_tools_fire_only_when_the_message_joins_two_requests():
    th = {r.SAVE: 0.0, r.ACTION: 0.0}
    assert er.fire({r.SAVE: 0.3, r.ACTION: 0.2}, th, two=False) == [r.SAVE]
    assert er.fire({r.SAVE: 0.1, r.ACTION: 0.2}, th, two=True) == [r.ACTION, r.SAVE]
    assert er.EmbedDecision([r.ACTION, r.SAVE], {}).intent == "SAVE+ACTION"
    assert er.embed_route(er.EmbedDecision([r.ACTION, r.SAVE], {})).tool == "save_note+do_action"
    assert er.fire({r.SAVE: -0.1, r.ACTION: -0.2}, th, True) == []


def test_vectorized_predict_matches_fire_one_message_at_a_time():
    rng = np.random.default_rng(0)
    margins = {r.SAVE: rng.normal(0, 0.2, 100), r.ACTION: rng.normal(0, 0.2, 100)}
    th = {r.SAVE: 0.05, r.ACTION: -0.02}
    two = np.array([True, False] * 50)
    slow = [er.EmbedDecision(er.fire({k: v[i] for k, v in margins.items()}, th, two[i]), {}).intent for i in range(100)]
    assert list(er.predict(margins, th, two)) == slow


def test_segments_split_sentences_and_joined_requests_but_not_plain_and():
    assert er.segments("Hey computer, we landed and it's raining. Get me to the hotel.") == [
        "we landed and it's raining", "get me to the hotel."]
    assert er.segments("note the code and then remind me in ten") == ["note the code", "remind me in ten"]
    assert er.segments("save the code, oh and set a timer") == ["save the code", "set a timer"]
    assert er.segments("set a timer and write down that the tea is green") == ["set a timer", "write down that the tea is green"]
    assert er.segments("add eggs and bread to the list") == []
    assert er.segments("Hi. Save the code") == []  # one-word pieces drop out, and one piece is no split


def test_a_request_at_the_end_of_a_story_fires_from_its_own_sentence():
    story = "pizza pizza pizza pizza pizza pizza pizza pizza. directions to the store"
    whole = er.EmbeddingRouter(BagEncoder(), ROUTES, "knn", {r.SAVE: 0.05, r.ACTION: 0.05}, head_words=0, top_k=1,
                               split=False)
    split = er.EmbeddingRouter(BagEncoder(), ROUTES, "knn", {r.SAVE: 0.05, r.ACTION: 0.05}, head_words=0, top_k=1)
    assert split.decide(story).intent == r.ACTION
    assert split.split_margins([story])[1][r.ACTION][0] > whole.split_margins([story])[1][r.ACTION][0]


def test_two_pieces_winning_different_tools_are_two_requests():
    knn = er.EmbeddingRouter(BagEncoder(), ROUTES, "knn", {r.SAVE: 0.2, r.ACTION: 0.2}, head_words=0, top_k=1)
    _, _, two = knn.split_margins(["write down the plumber number. set a timer for ten minutes",
                                   "write down the plumber number", "tell me a joke. how are you"])
    assert list(two) == [True, False, False]
    assert knn.decide("write down the plumber number. set a timer for ten minutes").intent == "SAVE+ACTION"


def test_cross_fit_scores_each_item_with_a_router_that_never_saw_it():
    items = [{"text": t, "intent": label} for label, texts in ROUTES.items() for t in texts] * 3
    thresholds, cost, acc = er.cross_fit(BagEncoder(), "knn", items, folds=3, head_words=0, top_k=1)
    assert set(thresholds) == {r.SAVE, r.ACTION} and cost >= 0 and 0 <= acc <= 1
    assert er.routes_from(items + [{"text": "x", "intent": "SAVE+ACTION"}])[r.SAVE] == ROUTES[r.SAVE] * 3


def test_fitting_finds_thresholds_that_separate_the_fit_items(knn):
    items = [{"text": "save a note about the dentist", "intent": r.SAVE},
             {"text": "write down the gate code", "intent": r.SAVE},
             {"text": "set a timer for an hour", "intent": r.ACTION},
             {"text": "directions to the library", "intent": r.ACTION},
             {"text": "tell me a story", "intent": r.NONE},
             {"text": "what is the capital of peru", "intent": r.NONE}]
    knn.thresholds, acc = er.fit_thresholds(knn, items)
    assert acc == 1.0
    assert [knn.decide(i["text"]).intent for i in items] == [i["intent"] for i in items]


def test_linear_head_trains_deterministically_and_round_trips_through_its_config(tmp_path, monkeypatch):
    enc = BagEncoder()
    a = er.EmbeddingRouter(enc, ROUTES, "linear", head_words=0, l2=0.01)
    b = er.EmbeddingRouter(enc, ROUTES, "linear", head_words=0, l2=0.01)
    assert np.array_equal(a.weights[0], b.weights[0])
    assert a.decide("save a note about the car").intent == r.SAVE
    assert a.decide("set a timer for ten minutes").intent == r.ACTION
    assert a.decide("tell me a joke").intent == r.NONE

    monkeypatch.setattr(er, "ROUTER_DIR", tmp_path)
    monkeypatch.setattr(er, "load_pool", lambda: [{"text": t, "intent": k} for k, ts in ROUTES.items() for t in ts])
    er.config_path("bag", "linear").write_text(json.dumps(a.config()))
    loaded = er.load_router(tmp_path, scorer="linear", encoder=enc)
    texts = ["remember the code", "remind me in an hour", "how are you", "what's on my calendar"]
    assert [loaded.decide(t).scores for t in texts] == [a.decide(t).scores for t in texts]
    with pytest.raises(ValueError):
        er.EmbeddingRouter(enc, ROUTES, "svm")


def test_mlp_head_trains_deterministically_and_round_trips_through_its_config(tmp_path, monkeypatch):
    enc = BagEncoder()
    a = er.EmbeddingRouter(enc, ROUTES, "mlp", head_words=0, l2=0.01)
    b = er.EmbeddingRouter(enc, ROUTES, "mlp", head_words=0, l2=0.01)
    assert all(np.array_equal(x, y) for x, y in zip(a.weights, b.weights))
    assert [a.decide(t).intent for t in ("save a note about the car", "set a timer for ten minutes", "tell me a joke")] \
        == [r.SAVE, r.ACTION, r.NONE]
    monkeypatch.setattr(er, "ROUTER_DIR", tmp_path)
    monkeypatch.setattr(er, "load_pool", lambda: [{"text": t, "intent": k} for k, ts in ROUTES.items() for t in ts])
    er.config_path("bag", "mlp").write_text(json.dumps(a.config()))
    loaded = er.load_router(tmp_path, scorer="mlp", encoder=enc)
    for t in ["remember the code", "remind me in an hour", "how are you"]:
        assert loaded.decide(t).scores == pytest.approx(a.decide(t).scores, abs=1e-3)


def test_cached_encoder_embeds_each_text_once():
    inner = BagEncoder()
    cached = er.CachedEncoder(inner)
    first = cached.encode(["a b", "c d"])
    again = cached.encode(["c d", "a b"])
    assert inner.calls == 1 and np.array_equal(first[0], again[1])


def test_rules_win_on_statements_and_the_embedding_vetoes_questions():
    say = lambda intents: type("R", (), {"decide": lambda self, t: er.EmbedDecision(intents, {})})()  # noqa: E731
    # A statement rule hit runs as a rule even when the embedding says NONE: no model call.
    hit = er.route_with_rules(say([]), "Remind me in 20 minutes to check the oven")
    assert (hit.source, hit.args) == ("rule", {"action": "remind_me", "arg": "20 min: check the oven"})
    # A question that hits a rule goes where the embedding says.
    vetoed = er.route_with_rules(say([]), "Remember that movie with the talking dog? What was it called?")
    assert (vetoed.intent, vetoed.source) == (r.NONE, "embed")
    # ...and keeps the rule when the embedding agrees.
    kept = er.route_with_rules(say([r.ACTION]), "What's on my calendar today?")
    assert (kept.source, kept.args["action"]) == ("rule", "whats_on")
    # No rule hit: the embedding's route, with no arguments.
    free = er.route_with_rules(say([r.SAVE]), "Can you hang onto this, the code is 4512")
    assert (free.intent, free.source, free.tool, free.args) == (r.SAVE, "embed", "save_note", None)


# ---- The data: route examples and the fit set stay out of both fixtures -------------------------------------

def _key(text: str) -> str:
    return re.sub(r"[^a-z0-9 ]", "", er.normalize(text)).strip()


def test_training_sentences_are_not_eval_cases_or_held_out_sentences():
    routes, fit, train = er.load_routes(), er.load_fit(), er.load_train()
    examples = {_key(t) for ts in routes.values() for t in ts}
    fit_texts = {_key(i["text"]) for i in fit}
    pool = {_key(i["text"]) for i in er.load_pool()}
    cases = {_key(c["user"]) for c in list(CASES.values()) + HOLDOUT + SAVE_DECISION}
    heldout = {_key(json.loads(line)["text"]) for line in (er.ROUTER_DIR / "heldout.jsonl").read_text().splitlines()}
    assert not pool & cases and not heldout & cases
    # Three held-out sentences match training sentences once spoken lead-ins ("uh", "okay", "um can you") are dropped.
    # They were near-duplicates from the start; kept so the held-out counts stay comparable across releases.
    assert pool & heldout == {"hello", "got it", "send my notes to my email address"}
    assert not examples & fit_texts
    assert set(routes) == {r.SAVE, r.ACTION, r.NONE} and min(len(v) for v in routes.values()) >= 30
    assert {i["intent"] for i in fit} == {r.SAVE, r.ACTION, r.NONE} and len(fit) >= 100
    assert {i["intent"] for i in train} == {r.SAVE, r.ACTION, r.NONE, "SAVE+ACTION"} and len(train) >= 600


def test_committed_configs_are_complete():
    for scorer in er.SCORERS:
        data = json.loads(er.config_path("all-MiniLM-L6-v2", scorer).read_text())
        assert set(data["thresholds"]) == {r.SAVE, r.ACTION} and data["head_words"] in er.SWEEP["head_words"]
        assert data["split"] is True
        if scorer == "linear":
            assert np.array(data["weights"]).shape == (384, 3) and len(data["bias"]) == 3
        if scorer == "mlp":
            assert np.array(data["hidden_weights"]).shape == (384, er.MLP_HIDDEN)
            assert np.array(data["weights"]).shape == (er.MLP_HIDDEN, 3) and len(data["bias"]) == 3
            assert data["labels"] == list(er.LABELS)


# ---- router_eval with an embed route ---------------------------------------------------------------------------

def scripted(*passes):
    queue, opened = list(passes), []

    def open_send(system, history, specs, seed):
        opened.append({"system": system, "specs": [s["function"]["name"] for s in specs]})
        return lambda message: iter(queue.pop(0) if queue else [])
    open_send.opened = opened
    return open_send


def fixed(intents):
    embedder = type("E", (), {"decide": lambda self, t: er.EmbedDecision(intents, {"SAVE": 0.1}, 0.004)})()
    return {"knn": embedder, "linear": embedder, "mlp": embedder}


def test_embed_none_shows_gemma_no_tools_and_logs_the_scores():
    send = scripted([("A whole note lasts twice as long.", [])])
    row = re_.run_routed(CASES["trap-music-note"], "embed_knn", 1, send, None,
                         route_fn=re_.embed_route_fn("embed_knn", fixed([])))
    assert send.opened[0]["specs"] == [] and row["route_source"] == "embed" and row["failures"] == []
    assert row["embed"] == {"intent": r.NONE, "scores": {"SAVE": 0.1}, "embed_s": 0.004}


def test_embed_save_shows_gemma_only_save_note_and_two_intents_show_both():
    send = scripted([("", [("save_note", {"text": "the vet said Max needs a dental cleaning"})])], [("Saved.", [])])
    row = re_.run_routed(CASES["save-vet"], "embed_linear", 1, send, None,
                         route_fn=re_.embed_route_fn("embed_linear", fixed([r.SAVE])))
    assert send.opened[0]["specs"] == ["save_note"] and row["route_intent"] == r.SAVE

    send = scripted([("", [])])
    row = re_.run_routed(CASES["multi-save-and-timer"], "embed_linear", 1, send, None,
                         route_fn=re_.embed_route_fn("embed_linear", fixed([r.SAVE, r.ACTION])))
    assert sorted(send.opened[0]["specs"]) == ["do_action", "save_note"] and row["route_intent"] == "SAVE+ACTION"
    assert "You have two tools, save_note and do_action." in send.opened[0]["system"]


def test_embed_rules_runs_a_statement_rule_with_no_model_call():
    send = scripted()
    row = re_.run_routed(CASES["act-timer-oven"], "embed_rules", 1, send, None,
                         route_fn=re_.embed_route_fn("embed_rules", fixed([r.ACTION])))
    assert send.opened == [] and row["route_source"] == "rule" and row["guarded"] == "Timer set for 20 minutes."


def test_app_tools_gives_an_action_route_no_tools_and_keeps_save_note_beside_it():
    assert re_.app_tool("do_action") is None and re_.app_tool(None) is None
    assert re_.app_tool("save_note+do_action") == "save_note" and re_.app_tool("save_note") == "save_note"
    send = scripted([("A whole note lasts twice as long.", [])])
    re_.run_routed(CASES["trap-music-note"], "embed_mlp", 1, send, None,
                   route_fn=re_.embed_route_fn("embed_mlp", fixed([r.ACTION])), app_tools=True)
    assert send.opened[0]["specs"] == [] and re_.NO_TOOL_LINE in send.opened[0]["system"]


def test_app_cases_drops_cases_that_expect_do_action():
    kept = {c["id"] for c in re_.app_cases([CASES["save-vet"], CASES["act-timer-oven"], CASES["trap-music-note"]])}
    assert kept == {"save-vet", "trap-music-note"}


def test_only_e4b_chat_turns_get_the_shorter_on_ask_sentence():
    try:
        re_.is_e4b("gemma-4-E2B-it-Q4_K_M")
        assert re_.SHORTER_ON_ASK not in re_.routed_prompt([], None)
        re_.is_e4b("/models/gemma-4-E4B-it.litertlm")
        chat = re_.routed_prompt([], None)
        assert "from your last reply. " + re_.NO_FLATTERY + " When the user asks for shorter answers, answer in one or two sentences. " in chat
        assert chat.count(re_.REACH_BACK) == 1
        assert re_.SHORTER_ON_ASK not in re_.routed_prompt([], "save_note")
        assert re_.REACH_BACK not in re_.routed_prompt([], "save_note")
        assert re_.NO_FLATTERY not in re_.routed_prompt([], "save_note")
        re_.is_e4b("gemma-4-E2B-it-Q4_K_M")
        assert re_.REACH_BACK not in re_.routed_prompt([], None)
        assert re_.NO_FLATTERY not in re_.routed_prompt([], None)
    finally:
        re_.is_e4b("")
