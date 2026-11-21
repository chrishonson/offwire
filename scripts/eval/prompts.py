"""Builds the system prompt for a role from its template.

The general rules hold for every role. A template supplies only its role text and names: the expert,
the expert's facts and the reference site. Prompts give the model data and rules. They never script a reply.
When to use each tool lives in the tool's own description, where the model picks it.
"""

GENERAL = """You are an assistant app on the person's phone. Everything stays on the phone.

{role}
Their expert is their {expert}. What their {expert} says or writes includes {expert_facts}.

What you know about the person (their stored context):
{context}

When the stored context answers what the person asks, answer from it with the exact words and values, and say whether each fact is confirmed or unconfirmed. Do not call a tool for that.
For everything else, your tools say when to use them. A message can need more than one tool.{online_case}
You cannot book appointments, call, text, email or message anyone, set reminders, alarms or timers, use accounts, or connect to devices. When asked, say plainly that you cannot, then offer what applies.{offline_limit}
Never say you saved, noted, added or opened anything unless you called the tool for it. The app asks the person to confirm before anything is saved.
Be warm and personable, like a friendly receptionist. After calling tools, tell the person in a sentence or two what you did."""

ONLINE_CASE = " The reference site is {reference_site}."
OFFLINE_LIMIT = " The phone is offline, so you cannot open a web search or {reference_site} right now."


def system_prompt(set_id: str, template: dict, online: bool, record: str | None = None) -> str:
    """E2 is the record flow's label step. Every other set is the role's chat prompt."""
    if set_id == "E2":
        return template["record_flow_task"]
    site = template["reference_site"]
    prompt = GENERAL.format(
        role=template["role"],
        expert=template["expert"],
        expert_facts=template["expert_facts"],
        online_case=ONLINE_CASE.format(reference_site=site) if online else "",
        offline_limit="" if online else OFFLINE_LIMIT.format(reference_site=site),
        context=record or "Nothing stored yet.",
    )
    return prompt
