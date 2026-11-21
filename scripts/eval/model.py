"""Runs the phone's Gemma 4 E2B model on the Mac through the LiteRT-LM runtime."""
import os
from pathlib import Path

import litert_lm
from litert_lm._ffi import LiteRtLmConstraintProviderType

DEFAULT_MODEL_PATH = str(Path.home() / "models/gemma4/gemma-4-E2B-it.litertlm")

from sampler import SAMPLER


def load_engine(model_path: str | None = None) -> litert_lm.Engine:
    """Leaves max_num_tokens unset, like the app, so both get the model file's default (about 4,600 tokens)."""
    path = model_path or os.environ.get("GEMMA_MODEL", DEFAULT_MODEL_PATH)
    return litert_lm.Engine(path, max_num_images=1,
                           vision_backend=litert_lm.Backend.CPU())


def results_suffix() -> str:
    """Tags results from a model other than the app's, so they never overwrite its results."""
    path = os.environ.get("GEMMA_MODEL")
    return "" if not path or path == DEFAULT_MODEL_PATH else "-" + Path(path).stem


def ask(engine, system: str, user: str, history: list[dict] | None = None,
        image: Path | None = None, seed: int = 0, tools: list | None = None, schema: dict | None = None) -> str:
    """Send one user turn in a fresh conversation, so cases never share state. Tool calls run automatically.

    A [schema] makes the decoder emit only JSON that matches it.
    """
    messages = [{"role": turn["role"], "content": turn["text"]} for turn in history or []]
    content = [{"type": "text", "text": user}]
    if image is not None:
        content.append({"type": "image", "path": str(image)})
    sampler = litert_lm.SamplerConfig(**SAMPLER, seed=seed)
    constrained = litert_lm.ConstrainedDecodingConfig(provider=LiteRtLmConstraintProviderType.LL_GUIDANCE) if schema else None
    conversation = engine.create_conversation(
        messages=messages, system_message=system, sampler_config=sampler, tools=tools or None,
        constrained_decoding_config=constrained)
    reply = conversation.send_message({"role": "user", "content": content},
                                      response_format=litert_lm.ResponseFormat.json(schema) if schema else None)
    return "".join(part.text for part in reply.contents if isinstance(part, litert_lm.Content.Text))
