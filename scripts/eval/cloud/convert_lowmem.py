"""Runs llama.cpp's convert_hf_to_gguf.py with a row-group quantizer that writes into the output in place,
so the 2.35B-parameter per-layer embedding fits in 15 GB of RAM."""
import runpy, sys
import numpy as np
LLAMA = __import__("os").environ.get("LLAMA_CPP", "/home/claude/llama.cpp")
sys.path[:0] = [LLAMA + "/gguf-py", LLAMA]
import gguf.quants as q

def apply_in_place(func, arr, otype, oshape):
    rows = arr.reshape((-1, arr.shape[-1]))
    osize = int(np.prod(oshape))
    out = np.empty(osize, dtype=otype)
    pos = 0
    step = 4096
    for start in range(0, rows.shape[0], step):
        chunk = func(rows[start:start + step]).ravel()
        out[pos:pos + chunk.size] = chunk
        pos += chunk.size
    assert pos == osize
    return out.reshape(oshape)

q._apply_over_grouped_rows = apply_in_place
if __name__ == "__main__":
    sys.argv = [LLAMA + "/convert_hf_to_gguf.py"] + sys.argv[1:]
    runpy.run_path(LLAMA + "/convert_hf_to_gguf.py", run_name="__main__")
