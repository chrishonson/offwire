#!/usr/bin/env bash
# Puts the intent router's MiniLM model where the app ships it: core/src/androidMain/assets/router/all-MiniLM-L6-v2.onnx
# (an LFS file, like the other .onnx assets). Uses ~/models/all-MiniLM-L6-v2/onnx/model.onnx when it is already
# there (docs/spikes/2026-11-10-tool-calling-eval.md), otherwise downloads the Chroma ONNX export. Checks SHA-256
# both ways, so the app ships exactly the model the router was fitted and scored with.
set -euo pipefail

TARBALL_URL="https://chroma-onnx-models.s3.amazonaws.com/all-MiniLM-L6-v2/onnx.tar.gz"
TARBALL_SHA256="913d7300ceae3b2dbc2c50d1de4baacab4be7b9380491c27fab7418616a16ec3"
MODEL_SHA256="4f148ba8ae9c2c7fbee4af2b132db8d06c6a6545b47fc83bbb98c3d22b8393e6"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$REPO/core/src/androidMain/assets/router/all-MiniLM-L6-v2.onnx"
CACHE="${EMBED_MODEL_DIR:-$HOME/models/all-MiniLM-L6-v2/onnx}/model.onnx"

sha() { if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }

if [ -f "$DEST" ] && [ "$(sha "$DEST")" = "$MODEL_SHA256" ]; then
  echo "already in place: $DEST"
  exit 0
fi
if [ ! -f "$CACHE" ] || [ "$(sha "$CACHE")" != "$MODEL_SHA256" ]; then
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  curl -fsSL -o "$tmp/onnx.tar.gz" "$TARBALL_URL"
  [ "$(sha "$tmp/onnx.tar.gz")" = "$TARBALL_SHA256" ] || { echo "tarball SHA-256 mismatch" >&2; exit 1; }
  tar xzf "$tmp/onnx.tar.gz" -C "$tmp"
  CACHE="$tmp/onnx/model.onnx"
fi
[ "$(sha "$CACHE")" = "$MODEL_SHA256" ] || { echo "model SHA-256 mismatch: $CACHE" >&2; exit 1; }
cp "$CACHE" "$DEST"
echo "copied $(du -h "$DEST" | cut -f1) to $DEST"
