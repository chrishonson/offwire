#!/usr/bin/env bash
# Puts the intent router's models in place, checking SHA-256 every way, so the app ships and the evals score exactly
# the model the committed config, parity file and held-out numbers were made with:
#   1. the stock all-MiniLM-L6-v2 (the Chroma ONNX export) in ~/models/all-MiniLM-L6-v2/onnx: the starting point of
#      scripts/eval/finetune_router.py, and the source of the tokenizer files
#   2. the fine-tuned model the app runs, core/src/androidMain/assets/router/all-MiniLM-L6-v2-router.onnx (an LFS
#      file, like the other .onnx assets; pulled from LFS, or copied from PATH or ~/models/all-MiniLM-L6-v2-router)
#   3. the same model with its tokenizer in ~/models/all-MiniLM-L6-v2-router, where the eval scripts look for it
#
#   scripts/fetch-router-model.sh [PATH/TO/all-MiniLM-L6-v2-router.onnx]
#
# Trained by `finetune_router.py train --lr 3e-4 --epochs 4 --thresholds 1.0,1.3`
# (docs/spikes/2026-11-10-tool-calling-eval.md, "The fine-tuned encoder, retrained").
set -euo pipefail

TARBALL_URL="https://chroma-onnx-models.s3.amazonaws.com/all-MiniLM-L6-v2/onnx.tar.gz"
TARBALL_SHA256="913d7300ceae3b2dbc2c50d1de4baacab4be7b9380491c27fab7418616a16ec3"
STOCK_SHA256="4f148ba8ae9c2c7fbee4af2b132db8d06c6a6545b47fc83bbb98c3d22b8393e6"
ROUTER_SHA256="d5c526227774791a77f8f6e9860835e25d880662983b33f3688f97e2a12f7108"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
ASSET_PATH="core/src/androidMain/assets/router/all-MiniLM-L6-v2-router.onnx"
DEST="$REPO/$ASSET_PATH"
STOCK_DIR="${STOCK_MODEL_DIR:-$HOME/models/all-MiniLM-L6-v2/onnx}"
ROUTER_DIR="${EMBED_MODEL_DIR:-$HOME/models/all-MiniLM-L6-v2-router}"

sha() { if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }
has() { [ -f "$1" ] && [ "$(sha "$1")" = "$2" ]; }

# 1. The stock model.
if ! has "$STOCK_DIR/model.onnx" "$STOCK_SHA256" || [ ! -f "$STOCK_DIR/tokenizer.json" ]; then
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  curl -fsSL -o "$tmp/onnx.tar.gz" "$TARBALL_URL"
  has "$tmp/onnx.tar.gz" "$TARBALL_SHA256" || { echo "tarball SHA-256 mismatch" >&2; exit 1; }
  tar xzf "$tmp/onnx.tar.gz" -C "$tmp"
  has "$tmp/onnx/model.onnx" "$STOCK_SHA256" || { echo "stock model SHA-256 mismatch" >&2; exit 1; }
  mkdir -p "$STOCK_DIR"
  cp "$tmp"/onnx/* "$STOCK_DIR"/
  echo "stock model in $STOCK_DIR"
fi

# 2. The fine-tuned model the app ships.
if ! has "$DEST" "$ROUTER_SHA256"; then
  # Not fatal: the model can still come from PATH or $ROUTER_DIR below.
  git -C "$REPO" lfs pull --include="$ASSET_PATH" ||
    echo "could not pull $ASSET_PATH from LFS (is it pushed, and is git-lfs installed?); trying PATH and $ROUTER_DIR" >&2
fi
if ! has "$DEST" "$ROUTER_SHA256"; then
  for candidate in "${1:-}" "$ROUTER_DIR/model.onnx"; do
    if [ -n "$candidate" ] && has "$candidate" "$ROUTER_SHA256"; then
      cp "$candidate" "$DEST"
      break
    fi
  done
fi
has "$DEST" "$ROUTER_SHA256" || { echo "fine-tuned router model not found (SHA-256 $ROUTER_SHA256): pass its path" >&2; exit 1; }
echo "fine-tuned model in $DEST"

# 3. The fine-tuned model where the evals look. Same WordPiece vocab and tokenizer as the stock model, but unpadded
# like the app's (the stock tokenizer.json pads every message to 128 tokens: same vectors, ten times slower).
mkdir -p "$ROUTER_DIR"
has "$ROUTER_DIR/model.onnx" "$ROUTER_SHA256" || cp "$DEST" "$ROUTER_DIR/model.onnx"
[ -f "$ROUTER_DIR/vocab.txt" ] || cp "$STOCK_DIR/vocab.txt" "$ROUTER_DIR"/
[ -f "$ROUTER_DIR/tokenizer.json" ] || python3 -c 'import json, sys; t = json.load(open(sys.argv[1])); t["padding"] = None
json.dump(t, open(sys.argv[2], "w"))' "$STOCK_DIR/tokenizer.json" "$ROUTER_DIR/tokenizer.json"
[ -f "$ROUTER_DIR/config.json" ] || echo '{"_name_or_path": "all-MiniLM-L6-v2-router"}' > "$ROUTER_DIR/config.json"
echo "fine-tuned model in $ROUTER_DIR"
