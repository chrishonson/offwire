#!/bin/bash
# Sets up a cloud session (no Mac, no phone) to run the release evals against Gemma 4 E2B on llama.cpp.
# About 15 minutes on 4 cores and 15 GB of RAM; needs about 25 GB of free disk while converting.
#
# Sources: Gemma 4 weights and the MiniLM tokenizer from Google's public Vertex Model Garden bucket (no key),
# llama.cpp from GitHub, the router's MiniLM ONNX from this repo's LFS assets. Nick approved this route on Nov 14, 2026.
#
# Usage: scripts/eval/cloud/setup.sh [E2B|E4B]   then   scripts/eval/cloud/serve.sh
# E4B's 16 GB safetensors may not fit next to the output; convert_remote.py reads it by range requests instead.
set -euo pipefail
SIZE=${1:-E2B}
WORK=${WORK:-/home/claude}
REPO=$(cd "$(dirname "$0")/../../.." && pwd)
MODELS=$WORK/models
LLAMA=$WORK/llama.cpp
VENV=$WORK/venv
NAME=gemma-4-$SIZE-it
mkdir -p "$MODELS/$NAME"

[ -x "$LLAMA/build/bin/llama-server" ] || {
  [ -d "$LLAMA" ] || git clone -q --depth 1 https://github.com/ggml-org/llama.cpp "$LLAMA"
  cmake -S "$LLAMA" -B "$LLAMA/build" -G Ninja -DCMAKE_BUILD_TYPE=Release -DLLAMA_CURL=OFF > "$WORK/build.log"
  cmake --build "$LLAMA/build" --target llama-server llama-quantize -j "$(nproc)" >> "$WORK/build.log"
}

[ -x "$VENV/bin/python" ] || python3 -m venv "$VENV"
# The CUDA build of torch from PyPI (the CPU index is not reachable here); conversion only uses the CPU.
"$VENV/bin/pip" install -q torch numpy safetensors sentencepiece protobuf transformers onnxruntime tokenizers pytest

GGUF=$MODELS/$NAME-Q4_K_M.gguf
if [ ! -f "$GGUF" ]; then
  gsutil -m -q cp "gs://vertex-model-garden-public-us/gemma4/$NAME/*" "$MODELS/$NAME/"
  # Row-chunked quantizer: the 2.35B-parameter per-layer embedding does not fit in 15 GB otherwise.
  LLAMA_CPP=$LLAMA "$VENV/bin/python" "$REPO/scripts/eval/cloud/convert_lowmem.py" "$MODELS/$NAME" \
    --outtype q8_0 --outfile "$MODELS/$NAME-Q8_0.gguf"
  rm -f "$MODELS/$NAME/model.safetensors"
  # Q4_K_M is the closest llama.cpp quant to the app's int4 .litertlm.
  "$LLAMA/build/bin/llama-quantize" --allow-requantize "$MODELS/$NAME-Q8_0.gguf" "$GGUF" Q4_K_M "$(nproc)"
fi

"$REPO/scripts/fetch-router-model.sh"
echo "ready: $GGUF"
