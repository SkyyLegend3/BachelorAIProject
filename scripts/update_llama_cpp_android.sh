#!/usr/bin/env bash
set -euo pipefail

# Pull a fresh upstream llama.cpp checkout used by the Android native build.
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_DIR="$ROOT_DIR/third_party/llama.cpp"
REPO_URL="https://github.com/ggerganov/llama.cpp.git"

mkdir -p "$ROOT_DIR/third_party"

if [ -d "$TARGET_DIR/.git" ]; then
  echo "[llama.cpp] Update existing checkout in $TARGET_DIR"
  git -C "$TARGET_DIR" fetch --depth 1 origin master
  git -C "$TARGET_DIR" reset --hard FETCH_HEAD
else
  echo "[llama.cpp] Clone upstream into $TARGET_DIR"
  git clone --depth 1 "$REPO_URL" "$TARGET_DIR"
fi

echo "[llama.cpp] Done. Current commit:"
git -C "$TARGET_DIR" rev-parse --short HEAD

