"""convert_lowmem.py, reading the safetensors file by HTTP range requests instead of from disk.

The E4B weights are 16 GB, more than a cloud session's disk allowance holds next to the converted file. Usage:
  convert_remote.py <URL of model.safetensors> <local dir with config.json and tokenizer files> [convert args]
Google's public bucket answers range requests at https://storage.googleapis.com/<bucket>/<path>.
"""
import os
import runpy
import sys
import time

LLAMA = os.environ.get("LLAMA_CPP", "/home/claude/llama.cpp")
sys.path[:0] = [LLAMA + "/gguf-py", LLAMA, os.path.dirname(os.path.abspath(__file__))]
os.environ.setdefault("REQUESTS_CA_BUNDLE", os.environ.get("SSL_CERT_FILE") or "/root/.ccr/ca-bundle.crt")

import huggingface_hub  # noqa: E402
from gguf.utility import SafetensorRemote  # noqa: E402
import convert_lowmem  # noqa: E402,F401  (installs the in-place quantizer; its runpy is guarded)

source_url, config_dir = sys.argv[1], sys.argv[2]
SafetensorRemote.get_list_tensors_hf_model = classmethod(lambda cls, model_id: cls.get_list_tensors(source_url))
huggingface_hub.snapshot_download = lambda **kw: config_dir
_get = SafetensorRemote.get_data_by_range.__func__


def get_with_retries(cls, url, start, size=-1):
    for attempt in range(6):
        try:
            return _get(cls, url, start, size)
        except Exception:
            if attempt == 5:
                raise
            time.sleep(5 * 2 ** attempt)


SafetensorRemote.get_data_by_range = classmethod(get_with_retries)

# The per-layer embedding table (E4B: 262,144 x 10,752 bf16, 5.6 GB) would be read whole and widened to float32,
# more than 15 GB of RAM. Stream it instead: a few thousand rows per range request, quantized as they are written.
ROWS_PER_CHUNK = 4096


def stream_bf16_rows(name: str):
    import numpy as np
    from gguf.lazy import LazyChunkedTensor

    remote = SafetensorRemote.get_list_tensors(source_url)
    key = next(k for k in remote if k.endswith(name) or name.endswith(k))
    meta = remote[key]
    assert meta.dtype == "BF16" and len(meta.shape) == 2, (key, meta.dtype, meta.shape)
    rows, cols = meta.shape

    def chunk(start: int, count: int):
        def load():
            raw = SafetensorRemote.get_data_by_range(source_url, meta.offset_start + start * cols * 2, count * cols * 2)
            half = np.frombuffer(raw, dtype=np.uint16).astype(np.uint32) << 16
            return half.view(np.float32).reshape(count, cols)
        return load

    chunks = [chunk(s, min(ROWS_PER_CHUNK, rows - s)) for s in range(0, rows, ROWS_PER_CHUNK)]
    return LazyChunkedTensor(chunks, shape=(rows, cols), dtype=np.float32)


from conversion import gemma  # noqa: E402

_modify = gemma.Gemma4Model.modify_tensors


def modify_tensors(self, data_torch, name, bid):
    if name.endswith("embed_tokens_per_layer.weight"):
        assert self.hparams.get("vocab_size_per_layer_input", 0) <= self.hparams.get("vocab_size", 0)
        yield self.map_tensor_name(name), stream_bf16_rows(name)
        return
    yield from _modify(self, data_torch, name, bid)


gemma.Gemma4Model.modify_tensors = modify_tensors
sys.argv = [LLAMA + "/convert_hf_to_gguf.py", "--remote", config_dir] + sys.argv[3:]
runpy.run_path(LLAMA + "/convert_hf_to_gguf.py", run_name="__main__")
