# test-import-check: skip, because tools and two_pass are this repo's scripts/eval/tools.py and two_pass.py,
# imported by bare name since scripts/eval is on sys.path for this suite, as in test_run.py.
import tools
import two_pass


def scripted(route_reply: str, seen: list, schemas: list | None = None):
    def send(system, user, history, image, seed, schema=None):
        seen.append(system)
        if schemas is not None:
            schemas.append(schema)
        return route_reply if len(seen) == 1 else "Done, warmly."
    return send


def test_router_calls_run_and_the_reply_is_told_what_happened():
    functions, calls = tools.build(online=False)
    seen = []
    reply = two_pass.ask(scripted('{"save_question": [{"question": "q"}]}', seen),
                         "SYSTEM", "u", None, None, 0, functions, "mechanic")
    assert reply == "Done, warmly."
    assert calls == [{"name": "save_question", "args": {"question": "q"}}]
    assert "save_question" in seen[1] and "nothing" not in seen[1]


def test_unknown_tools_bad_args_and_bad_json_are_skipped():
    functions, calls = tools.build(online=False)
    seen = []
    two_pass.ask(scripted('{"open_web_search": [{"query": "x"}], "save_question": [{"q": 1}]}', seen),
                 "SYSTEM", "u", None, None, 0, functions, "mechanic")
    assert calls == [] and "What the app did for this message: nothing" in seen[1]
    assert two_pass.run_calls("not json", functions) == []


def test_router_sees_every_tool_and_the_expert_in_its_examples():
    functions, _ = tools.build(online=True)
    seen = []
    two_pass.ask(scripted("{}", seen), "SYSTEM", "u", None, None, 0, functions, "mechanic")
    assert all(f.__name__ in seen[0] for f in functions)
    assert "my mechanic gave me" in seen[0]


def test_router_is_constrained_to_one_required_list_per_offered_tool():
    functions, _ = tools.build(online=False)
    schemas = []
    two_pass.ask(scripted("{}", [], schemas), "SYSTEM", "u", None, None, 0, functions, "mechanic")
    route_schema, reply_schema = schemas
    assert route_schema["required"] == [f.__name__ for f in functions]
    assert route_schema["properties"]["note_personal_fact"]["items"]["required"] == ["kind", "text"]
    assert reply_schema is None


def test_offline_examples_never_show_web_tools():
    functions, _ = tools.build(online=False)
    assert "open_web_search" not in two_pass.examples(functions, "mechanic")
