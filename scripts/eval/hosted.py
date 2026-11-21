"""Runs tool_eval's turns against an OpenAI-compatible chat endpoint instead of LiteRT-LM on the Mac.

Any server that speaks POST {base}/chat/completions with streamed tool calls works: the Gemini API's OpenAI
endpoint, llama-server --jinja, Ollama, LM Studio, vLLM. Standard library only, so a cloud session needs no
installs. The key, if the endpoint needs one, is read from an environment variable and never written anywhere.

What differs from the phone, so read hosted numbers as approximate:
- Weights: the server's (often bf16 or its own quant), not the app's int4 .litertlm.
- Tool format: the server's chat template turns the OpenAI tool schema into Gemma's tool tokens and parses calls
  back. LiteRT-LM does its own version of that, so malformed-call and parse-error counts can differ.
- Sampling: temperature and top_p match the app; top_k is sent only where the server accepts it.
- Timing: TTFT includes the network and the server's queue, and decode speed is not measured.
"""
import json
import os
import ssl
import time
import urllib.error
import urllib.request
from typing import Callable, Iterable, Iterator

from sampler import SAMPLER

GEMINI_OPENAI_BASE = "https://generativelanguage.googleapis.com/v1beta/openai"
TIMEOUT_S = 120
RETRIES = 5
RETRY_CODES = {429, 500, 502, 503}


def default_key_env(base_url: str) -> str | None:
    return "GEMINI_API_KEY" if "generativelanguage.googleapis.com" in base_url else None


def accepts_top_k(base_url: str) -> bool:
    """The Gemini OpenAI endpoint rejects fields outside the OpenAI schema; local servers take top_k."""
    return "generativelanguage.googleapis.com" not in base_url


def to_openai_message(message: dict, pending_ids: list[str]) -> list[dict]:
    """Turns one of run_turn's LiteRT-style messages into OpenAI messages.

    A tool message holds one tool_response per call of the previous pass, in order; each is paired with that
    call's id, which is what OpenAI-style servers need to match a result to its call.
    """
    if message["role"] == "tool":
        out = []
        for i, part in enumerate(message["content"]):
            call_id = pending_ids[i] if i < len(pending_ids) else f"call_{i}"
            out.append({"role": "tool", "tool_call_id": call_id, "name": part["name"], "content": str(part["response"])})
        return out
    content = message["content"]
    if isinstance(content, list):
        content = "".join(p.get("text", "") for p in content if p.get("type") == "text")
    return [{"role": message["role"], "content": content}]


def sse_events(lines: Iterable[bytes]) -> Iterator[dict]:
    """Parses a text/event-stream body into JSON events, stopping at [DONE]."""
    for raw in lines:
        line = raw.decode("utf-8").strip()
        if not line.startswith("data:"):
            continue
        data = line[5:].strip()
        if data == "[DONE]":
            return
        if data:
            yield json.loads(data)


class CallBuffer:
    """Streamed tool calls arrive as fragments keyed by index; arguments are a JSON string split across chunks."""

    def __init__(self):
        self.calls: dict[int, dict] = {}

    def add(self, deltas: list[dict]) -> None:
        for d in deltas:
            slot = self.calls.setdefault(d.get("index", len(self.calls)), {"id": None, "name": "", "arguments": ""})
            slot["id"] = d.get("id") or slot["id"]
            fn = d.get("function") or {}
            slot["name"] += fn.get("name") or ""
            args = fn.get("arguments")
            if isinstance(args, dict):  # some servers send parsed arguments
                slot["arguments"] = json.dumps(args)
            elif args:
                slot["arguments"] += args

    def finish(self) -> list[dict]:
        """Calls in index order with parsed arguments. Unparseable arguments raise RuntimeError, which tool_eval
        records as a parse_error row, the same way it records a LiteRT-LM parse failure."""
        out = []
        for i, (_, slot) in enumerate(sorted(self.calls.items())):
            text = slot["arguments"].strip() or "{}"
            try:
                args = json.loads(text)
            except json.JSONDecodeError as error:
                raise RuntimeError(f"could not parse tool call arguments for {slot['name']}: {text[:200]}") from error
            out.append({"id": slot["id"] or f"call_{i}", "name": slot["name"], "arguments": args if isinstance(args, dict) else {}})
        return out


def post_stream(url: str, body: dict, api_key: str | None) -> Iterator[bytes]:
    headers = {"Content-Type": "application/json", "Accept": "text/event-stream"}
    if api_key:
        headers["Authorization"] = f"Bearer {api_key}"
    request = urllib.request.Request(url, data=json.dumps(body).encode(), headers=headers, method="POST")
    context = ssl.create_default_context(cafile=os.environ.get("SSL_CERT_FILE") or None)
    for attempt in range(RETRIES + 1):
        try:
            response = urllib.request.urlopen(request, timeout=TIMEOUT_S, context=context)
        except urllib.error.HTTPError as error:
            detail = error.read().decode("utf-8", "replace")[:400]
            if error.code in RETRY_CODES and attempt < RETRIES:
                time.sleep(retry_delay(error.headers.get("Retry-After"), attempt))
                continue
            # A bad key or model name ends the run rather than failing every case the same way.
            raise SystemExit(f"{url} answered HTTP {error.code}: {detail}") from error
        with response:
            yield from response
        return


def retry_delay(retry_after: str | None, attempt: int) -> float:
    """Free tiers rate-limit by the minute: honor Retry-After, else back off 5, 10, 20, 40, 80 seconds."""
    try:
        return max(1.0, float(retry_after))
    except (TypeError, ValueError):
        return 5.0 * 2 ** attempt


def openai_open_send(base_url: str, model: str, api_key_env: str | None = None,
                     post: Callable[[str, dict, str | None], Iterable[bytes]] = post_stream):
    """Returns tool_eval's open_send for an OpenAI-compatible endpoint.

    Each conversation keeps its own message list: after a pass with calls, the assistant message carrying those
    calls goes into the history so the next tool message can answer them by id.
    """
    url = base_url.rstrip("/") + "/chat/completions"
    key_env = api_key_env or default_key_env(base_url)
    api_key = os.environ.get(key_env) if key_env else None
    if key_env and not api_key:
        raise SystemExit(f"set {key_env} in the environment (as a secret) to call {base_url}")

    def open_send(system: str, history: list[dict], specs: list[dict], seed: int):
        messages = [{"role": "system", "content": system}]
        messages += [{"role": t["role"], "content": t["text"]} for t in history]
        pending_ids: list[str] = []

        def send(message: dict) -> Iterator[tuple[str, list]]:
            nonlocal pending_ids
            messages.extend(to_openai_message(message, pending_ids))
            body = {"model": model, "messages": list(messages), "stream": True,
                    "temperature": SAMPLER["temperature"], "top_p": SAMPLER["top_p"], "seed": seed}
            if accepts_top_k(base_url):
                body["top_k"] = SAMPLER["top_k"]
            if specs:
                body["tools"] = specs
            text = ""
            buffer = CallBuffer()
            for event in sse_events(post(url, body, api_key)):
                for choice in event.get("choices") or []:
                    delta = choice.get("delta") or {}
                    chunk = delta.get("content") or ""
                    if delta.get("tool_calls"):
                        buffer.add(delta["tool_calls"])
                    if chunk:
                        text += chunk
                        yield chunk, []
            calls = buffer.finish()
            assistant = {"role": "assistant", "content": text or None}
            if calls:
                assistant["tool_calls"] = [{"id": c["id"], "type": "function",
                                            "function": {"name": c["name"], "arguments": json.dumps(c["arguments"])}}
                                           for c in calls]
            messages.append(assistant)
            pending_ids = [c["id"] for c in calls]
            if calls:
                yield "", [(c["name"], c["arguments"]) for c in calls]
        return send
    return open_send
