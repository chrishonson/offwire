import json

import fluff


def test_each_kind_of_filler_is_counted_once_per_reply():
    texts = ["Sure! The capital is Canberra.", "I'm sorry, I don't know.", "What a great idea. Try a shelf.",
             "Two ideas: a shelf or a pegboard. Does that sound good?", "Let me know if you want more.",
             "Canberra."]
    s = fluff.score(texts)
    assert (s["opener"], s["apology"], s["praise"], s["checkin"], s["offer"], s["exclaim"]) == (1, 1, 1, 1, 1, 1)
    assert s["with_filler"] == 5 and s["replies"] == 6


def test_lead_in_words_count_only_a_first_sentence_that_is_filler():
    assert fluff.lead_in_words("That sounds like a great plan. Start with the shelf.") == 6
    assert fluff.lead_in_words("Start with the shelf. That sounds like a great plan.") == 0


def test_replies_reads_router_rows_and_demo_turns(tmp_path):
    p = tmp_path / "r.jsonl"
    p.write_text(json.dumps({"guarded": "One."}) + "\n" + json.dumps({"turns": [{"guarded": "Two."}, {}]}) + "\n")
    assert fluff.replies(str(p)) == ["One.", "Two."]
