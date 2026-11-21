import demo_eval as de


def fake_ask(replies, saves_at):
    def ask(case, run):
        step = case["id"].removeprefix("demo-")
        calls = [{"name": "save_note", "args": {"text": t}} for t in saves_at.get(step, [])]
        return {"guarded": replies.get(step, "Sure."), "calls": calls, "false_claim": False,
                "double_confirm_guarded": False, "history_seen": case["history"], "notes_seen": case["notes"]}
    return ask


GOOD = {"3-can-save": "Yes, I can save notes.", "3-tool": "Yes, I do.",
        "5-jot": "Saved.", "6-recall": "Three garage ideas, and you want to try the second one Saturday."}


def test_a_clean_run_passes_every_check_and_chains_history_and_notes():
    turns = de.run_script(1, fake_ask(GOOD, {"5-jot": ["Try the second idea Saturday"]}))
    assert all(de.score_run(turns).values())
    assert len(turns[-1]["history_seen"]) == 2 * (len(de.STEPS) - 1)
    assert turns[-1]["notes_seen"] == ["Try the second idea Saturday"]
    assert turns[0]["notes_seen"] == [] and turns[0]["history_seen"] == []


def test_an_early_save_a_denial_and_a_missing_saturday_each_fail_their_check():
    replies = {**GOOD, "3-tool": "I don't have a tool for that.", "6-recall": "A workbench."}
    turns = de.run_script(1, fake_ask(replies, {"2-second": ["tools"], "5-jot": ["second idea"]}))
    checks = de.score_run(turns)
    assert not checks["no_early_save"] and not checks["says_yes"] and not checks["one_save_saturday"]
    assert not checks["recall_saturday"] and not checks["one_note"]


def test_a_note_that_only_points_back_does_not_name_the_idea():
    def jot(text):
        return [{"step": "5-jot", "calls": [{"name": "save_note", "args": {"text": text}}]}]
    assert not de.names_idea(jot("want to try the second idea Saturday"))
    assert de.names_idea(jot("Want to try building a workbench on Saturday."))
    assert not de.names_idea([{"step": "5-jot", "calls": []}])
