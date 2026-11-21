"""Two passes per turn: the model routes the message to tool calls, code runs them, then the model replies.

The router fills one required list per tool, under a JSON schema the decoder enforces, so it must consider every
tool instead of defaulting to an empty answer. Worked examples show it what a filled form looks like. The reply
pass has no tools and is told exactly what the app did, so it cannot claim an action that did not happen.
"""
import inspect
import json

ROUTE = """Do not reply to the person in this step. For each tool, list the calls this message needs, or [] when it needs none. A message can need several calls. Everything is [] when the stored context answers the message, when it is small talk, or when the person asks for something no tool does.

Tools:
{tools}

Examples:
{examples}"""

REPLY = """Now reply to the person. You have no tools in this step.
What the app did for this message: {done}
Say only what the app did and what the stored context shows. Never claim anything else was saved, noted, added or opened."""

# Worked examples in no one role's vocabulary. {expert} is the template's expert. They teach the form, not replies.
EXAMPLES = [
    ("I sleep about six hours a night and I want to get to eight",
     {"note_personal_fact": [{"kind": "habit", "text": "sleeps about six hours a night"},
                             {"kind": "goal", "text": "get to eight hours of sleep"}]}),
    ("I checked it this morning and it read 42",
     {"note_personal_fact": [{"kind": "reading", "text": "read 42 this morning"}]}),
    ("is it a problem if I skip the step my {expert} gave me",
     {"save_question": [{"question": "Is it a problem if I skip the step you gave me?"}],
      "open_web_search": [{"query": "skipping a step"}], "open_reference": [{"query": "skipping a step"}]}),
    ("what does that word on my paperwork mean",
     {"save_question": [{"question": "What does that word on my paperwork mean?"}],
      "open_web_search": [{"query": "word on paperwork meaning"}],
      "open_reference": [{"query": "word on paperwork meaning"}]}),
    ("my {expert} gave me a new plan today and wrote it on a sheet",
     {"open_record_flow": [{"kind": "new plan"}]}),
    ("my {expert} measured it at 42 and wrote it down",
     {"open_record_flow": [{"kind": "measurement of 42"}]}),
    ("good afternoon", {}),
    ("can you set a timer for me", {}),
]


def describe(function) -> str:
    params = ", ".join(inspect.signature(function).parameters)
    return f"- {function.__name__}({params}): {inspect.getdoc(function)}"


def schema(functions: list) -> dict:
    """One required list per tool, each item holding exactly that tool's string arguments."""
    def calls(function) -> dict:
        params = list(inspect.signature(function).parameters)
        return {"type": "array", "items": {"type": "object", "properties": {p: {"type": "string"} for p in params},
                                           "required": params, "additionalProperties": False}}
    names = [f.__name__ for f in functions]
    return {"type": "object", "properties": {f.__name__: calls(f) for f in functions},
            "required": names, "additionalProperties": False}


def examples(functions: list, expert: str) -> str:
    """Each example filled in for exactly the tools offered this turn, so offline examples never show web tools."""
    names = [f.__name__ for f in functions]
    lines = []
    for message, calls in EXAMPLES:
        form = {name: calls.get(name, []) for name in names}
        lines.append(f"Message: {message.format(expert=expert)}\nCalls: {json.dumps(form)}")
    return "\n".join(lines)


def run_calls(reply: str, functions: list) -> list[str]:
    """Runs each valid call from the router's form and returns what each one did, in plain words."""
    try:
        form = json.loads(reply)
    except json.JSONDecodeError:
        return []
    done = []
    for function in functions:
        for args in form.get(function.__name__) or []:
            if not isinstance(args, dict):
                continue
            try:
                result = function(**args)
            except TypeError:
                continue
            done.append(f"{function.__name__}({json.dumps(args)}) -> {result.split(' Now tell')[0]}")
    return done


def ask(send, system: str, user: str, history, image, seed: int, functions: list, expert: str) -> str:
    """[send] is model.ask without tools: send(system, user, history, image, seed, schema=None) -> text."""
    route_system = system + "\n\n" + ROUTE.format(tools="\n".join(describe(f) for f in functions),
                                                  examples=examples(functions, expert))
    done = run_calls(send(route_system, user, history, image, seed, schema=schema(functions)), functions)
    reply_system = system + "\n\n" + REPLY.format(done="; ".join(done) or "nothing")
    return send(reply_system, user, history, image, seed)
