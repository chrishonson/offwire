import json

import numpy as np
import pytest

import mobilebert_router as mr


def test_the_top_class_is_the_route():
    assert mr.intents_for(np.array([5.0, -1.0, 2.0, 1.0])) == ["SAVE"]
    assert mr.intents_for(np.array([-1.0, 4.0, 2.0, 1.0])) == ["ACTION"]
    assert mr.intents_for(np.array([1.0, 1.0, 9.0, 1.0])) == []


def test_save_and_action_puts_the_higher_scoring_tool_first():
    assert mr.intents_for(np.array([-4.0, 4.7, -3.8, 6.3])) == ["ACTION", "SAVE"]
    assert mr.intents_for(np.array([3.0, 1.0, 0.0, 6.0])) == ["SAVE", "ACTION"]


@pytest.mark.skipif(not mr.DEFAULT_MODEL.is_file() or mr.DEFAULT_MODEL.stat().st_size < 1_000_000,
                    reason="the MobileBERT model is an LFS pointer here (scripts/fetch-router-model.sh)")
def test_the_model_matches_the_parity_file():
    r = mr.MobileBertRouter()
    cases = json.loads(mr.PARITY.read_text())["cases"][:50]
    for case in cases:
        assert r.ids(case["text"]) == case["ids"]
        assert mr.intents_for(r.logits(case["text"])) == case["intents"]
        assert np.allclose(r.logits(case["text"]), case["logits"], atol=1e-3)
    assert r.decide("save a note that the garage door is open").intent == "SAVE"
