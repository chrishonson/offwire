# test-import-check: skip (hosted is scripts/eval/hosted.py, imported from the pytest rootdir like the sibling tests)
import json
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

import pytest

import hosted
import tool_eval as te


def sse(*events):
    return [f"data: {json.dumps(e)}\n".encode() for e in events] + [b"data: [DONE]\n"]


def text_event(text):
    return {"choices": [{"delta": {"content": text}}]}


def call_event(index, name=None, args=None, call_id=None):
    fn = {}
    if name:
        fn["name"] = name
    if args is not None:
        fn["arguments"] = args
    d = {"index": index, "function": fn}
    if call_id:
        d["id"] = call_id
    return {"choices": [{"delta": {"tool_calls": [d]}}]}


def scripted_post(*responses):
    """Stands in for post_stream: returns one canned SSE body per request and keeps every request body."""
    queue = list(responses)
    bodies = []

    def post(url, body, key):
        bodies.append(json.loads(json.dumps(body)))
        return iter(queue.pop(0))
    post.bodies = bodies
    return post


def test_joins_argument_fragments_and_answers_the_call_by_id():
    post = scripted_post(
        sse(call_event(0, "save_note", '{"text": "the vet', "c1"), call_event(0, args=' said dental"}')),
        sse(text_event("Saved: "), text_event("the vet said dental.")))
    open_send = hosted.openai_open_send("http://local/v1", "m", post=post)
    send = open_send("sys", [], te.tool_specs("shipped"), 0)
    box = te.ToolBox("shipped", [])
    passes = te.run_turn(send, "Remember the vet said dental", box)

    assert box.calls == [{"name": "save_note", "args": {"text": "the vet said dental"}}]
    assert [p.text for p in passes] == ["", "Saved: the vet said dental."]
    second = post.bodies[1]["messages"]
    assert second[-2]["tool_calls"][0]["id"] == "c1"
    assert second[-1] == {"role": "tool", "tool_call_id": "c1", "name": "save_note", "content": te.SAVE_NOTE_RESULT}


def test_sends_the_apps_sampling_and_drops_top_k_for_gemini():
    post = scripted_post(sse(text_event("hi")), sse(text_event("hi")))
    hosted.openai_open_send("http://local/v1", "m", post=post)("s", [], [], 2)(
        {"role": "user", "content": [{"type": "text", "text": "hi"}]}).__next__()
    assert post.bodies[0]["top_k"] == 64 and post.bodies[0]["temperature"] == 1.0 and post.bodies[0]["seed"] == 2
    assert "tools" not in post.bodies[0]
    assert not hosted.accepts_top_k(hosted.GEMINI_OPENAI_BASE)


def test_a_missing_key_stops_before_any_request(monkeypatch):
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    with pytest.raises(SystemExit, match="GEMINI_API_KEY"):
        hosted.openai_open_send(hosted.GEMINI_OPENAI_BASE, "m")


def test_broken_arguments_become_a_parse_error_row_not_a_crash():
    post = scripted_post(sse(call_event(0, "save_note", '{"text": dental}', "c1")))
    rows = te.run_cases([{"id": "s", "category": "save", "user": "Remember dental", "expect": [{"name": "save_note"}]}],
                        "shipped", hosted.openai_open_send("http://local/v1", "m", post=post), 1)
    assert rows[0]["parse_error"].startswith("could not parse tool call arguments")


def test_history_and_list_content_become_plain_openai_messages():
    assert hosted.to_openai_message({"role": "user", "content": [{"type": "text", "text": "a"}, {"type": "text", "text": "b"}]}, []) == [
        {"role": "user", "content": "ab"}]
    post = scripted_post(sse(text_event("ok")))
    send = hosted.openai_open_send("http://local/v1", "m", post=post)("sys", [{"role": "assistant", "text": "earlier"}], [], 0)
    list(send({"role": "user", "content": [{"type": "text", "text": "now"}]}))
    assert [m["role"] for m in post.bodies[0]["messages"]] == ["system", "assistant", "user"]


def test_retry_delay_honors_retry_after_then_backs_off():
    assert hosted.retry_delay("7", 0) == 7.0
    assert hosted.retry_delay(None, 2) == 20.0


class FakeServer(BaseHTTPRequestHandler):
    """A real HTTP server streaming SSE: no-call cases get chat, anything mentioning 'remember' gets a save."""
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        last = body["messages"][-1]
        if last["role"] == "tool":
            events = [text_event("Saved that.")]
        elif "remember" in last["content"].lower() and body.get("tools"):
            events = [call_event(0, "save_note", json.dumps({"text": last["content"]}), "c1")]
        else:
            events = [text_event("Sure, "), text_event("happy to talk.")]
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.end_headers()
        for line in sse(*events):
            self.wfile.write(line + b"\n")

    def log_message(self, *_):
        pass


def test_end_to_end_over_http_scores_like_the_local_runner():
    server = HTTPServer(("127.0.0.1", 0), FakeServer)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        open_send = hosted.openai_open_send(f"http://127.0.0.1:{server.server_port}/v1", "fake")
        cases = [{"id": "s", "category": "save", "user": "Remember the vet said dental",
                  "expect": [{"name": "save_note", "args": {"text": "dental"}}]},
                 {"id": "c", "category": "no_call", "user": "What's a good name for a dog?", "expect": []}]
        rows = te.run_cases(cases, "shipped", open_send, 1)
    finally:
        server.shutdown()
    assert [r["failures"] for r in rows] == [[], []]
    assert rows[0]["called"] == ["save_note"] and rows[1]["called"] == []
