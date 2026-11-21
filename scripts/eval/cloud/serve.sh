#!/bin/bash
# Serves the converted Gemma like the app: 4,096-token context, thinking off, one request at a time.
# Usage: serve.sh [E2B|E4B], PORT=8081 for a second server. Then run the evals with
#   --endpoint http://127.0.0.1:8080/v1 --endpoint-model gemma-4-E2B-it-Q4_K_M
SIZE=${1:-E2B}
WORK=${WORK:-/home/claude}
exec "$WORK/llama.cpp/build/bin/llama-server" -m "$WORK/models/gemma-4-$SIZE-it-Q4_K_M.gguf" --jinja \
  --chat-template-kwargs '{"enable_thinking":false}' -c 4096 -t "$(nproc)" -np 1 --host 127.0.0.1 --port "${PORT:-8080}"
