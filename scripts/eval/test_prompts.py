import json
from pathlib import Path

from prompts import system_prompt

TEMPLATE = json.loads((Path(__file__).parents[2] / "eval" / "health-template.json").read_text())
MECHANIC = {
    "role": "You help the person keep track of their car between shop visits.",
    "expert": "mechanic",
    "expert_facts": "an estimate, a repair or an inspection finding",
    "reference_site": "the owner's manual",
    "record_flow_task": "Name the part the estimate is for.",
}


def test_role_text_and_names_come_from_the_template():
    prompt = system_prompt("E4", MECHANIC, online=True)
    assert "their car" in prompt and "mechanic" in prompt and "owner's manual" in prompt


def test_general_rules_carry_no_medical_words():
    prompt = system_prompt("E4", MECHANIC, online=True).lower()
    assert not any(word in prompt for word in ("doctor", "medical", "prescription", "medlineplus", "health"))


def test_offline_prompt_says_the_web_is_unavailable():
    assert "offline" not in system_prompt("E4", TEMPLATE, online=True)
    assert "offline" in system_prompt("E4", TEMPLATE, online=False)


def test_stored_context_is_in_every_chat_prompt():
    assert "LDL 162" in system_prompt("E1", TEMPLATE, online=False, record="LDL 162 (confirmed)")
    assert "Nothing stored yet." in system_prompt("E1", TEMPLATE, online=False)


def test_record_flow_step_uses_its_own_task():
    assert system_prompt("E2", TEMPLATE, online=False) == TEMPLATE["record_flow_task"]
