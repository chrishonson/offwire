#!/usr/bin/env bash
# Puts the intent router's MiniLM model in place: core/src/androidMain/assets/router/all-MiniLM-L6-v2.onnx (an LFS
# file, like the other .onnx assets) and the evals' copy in ~/models/all-MiniLM-L6-v2/onnx/model.onnx.
# Since Nov 27 that model is all-MiniLM-L6-v2 fine-tuned on the router's training sentences (Nov 11,
# finetune_router.py on gemma-voice's claude/embed-router-app branch), not the stock Chroma export, so it comes
# from Git LFS only. Checks SHA-256, so the app and the evals use exactly the model the router was fitted with.
# The tokenizer, vocab and config are the stock model's; docs/spikes/2026-11-10-tool-calling-eval.md gets those.
#
#   scripts/fetch-router-model.sh [PATH/TO/all-MiniLM-L6-v2-router.onnx]
#
# With a path, or with the file in ~/Downloads or ~/models/all-MiniLM-L6-v2-router, it copies that file into the
# app's assets first (for committing it to LFS); otherwise it pulls it from LFS.
set -euo pipefail

MODEL_SHA256="5b59593bd292b855b2d0d909cab5805d4e7fdd4a4fa78ab468afe665aef675df"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
REL="core/src/androidMain/assets/router/all-MiniLM-L6-v2.onnx"
DEST="$REPO/$REL"
CACHE="${EMBED_MODEL_DIR:-$HOME/models/all-MiniLM-L6-v2/onnx}/model.onnx"

sha() { if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }

if [ ! -f "$DEST" ] || [ "$(sha "$DEST")" != "$MODEL_SHA256" ]; then
  for src in "${1:-}" "$HOME/Downloads/all-MiniLM-L6-v2-router.onnx" "$HOME/models/all-MiniLM-L6-v2-router/model.onnx"; do
    if [ -n "$src" ] && [ -f "$src" ] && [ "$(sha "$src")" = "$MODEL_SHA256" ]; then
      cp "$src" "$DEST"
      break
    fi
  done
fi
if [ ! -f "$DEST" ] || [ "$(sha "$DEST")" != "$MODEL_SHA256" ]; then
  git -C "$REPO" lfs pull --include="$REL"
fi
[ "$(sha "$DEST")" = "$MODEL_SHA256" ] || { echo "model SHA-256 mismatch: $DEST" >&2; exit 1; }
echo "in place: $DEST"
if [ ! -f "$CACHE" ] || [ "$(sha "$CACHE")" != "$MODEL_SHA256" ]; then
  mkdir -p "$(dirname "$CACHE")"
  cp "$DEST" "$CACHE"
  echo "copied to the evals' model: $CACHE"
fi
