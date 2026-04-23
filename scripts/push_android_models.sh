#!/usr/bin/env bash
set -euo pipefail

# Pushes local whisper + llama models to an installed Android app sandbox via adb.
# Works after app reinstall (debug build, run-as required).

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_PROPERTIES="$ROOT_DIR/local.properties"
DEFAULT_PACKAGE="com.example.bachelor_ai_project"
DEFAULT_SRC_LLAMA=""
DEFAULT_SRC_WHISPER=""
DEFAULT_REMOTE_LLAMA=""
DEFAULT_REMOTE_WHISPER=""

read_local_prop() {
  local key="$1"
  local file="$2"
  if [[ ! -f "$file" ]]; then
    return 0
  fi
  local raw
  raw="$(grep -E "^${key}=" "$file" | tail -n 1 | cut -d'=' -f2- || true)"
  # trim CR/LF and surrounding spaces
  raw="${raw//$'\r'/}"
  raw="${raw//$'\n'/}"
  echo "$raw" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//'
}

DEFAULT_REMOTE_LLAMA="$(read_local_prop "llama.model.path" "$LOCAL_PROPERTIES")"
DEFAULT_REMOTE_WHISPER="$(read_local_prop "whisper.model.path" "$LOCAL_PROPERTIES")"
DEFAULT_SRC_LLAMA="$(read_local_prop "llama.model.push.src" "$LOCAL_PROPERTIES")"
DEFAULT_SRC_WHISPER="$(read_local_prop "whisper.model.push.src" "$LOCAL_PROPERTIES")"

is_android_sandbox_path() {
  local p="$1"
  [[ "$p" == /data/user/0/* || "$p" == /data/data/* ]]
}

# Falls llama.model.path / whisper.model.path lokale Dateien sind (statt /data/...)
# koennen sie als Source-Default verwendet werden.
if [[ -z "$DEFAULT_SRC_LLAMA" && -n "$DEFAULT_REMOTE_LLAMA" ]] && ! is_android_sandbox_path "$DEFAULT_REMOTE_LLAMA"; then
  if [[ -f "$DEFAULT_REMOTE_LLAMA" ]]; then
    DEFAULT_SRC_LLAMA="$DEFAULT_REMOTE_LLAMA"
    DEFAULT_REMOTE_LLAMA=""
  fi
fi

if [[ -z "$DEFAULT_SRC_WHISPER" && -n "$DEFAULT_REMOTE_WHISPER" ]] && ! is_android_sandbox_path "$DEFAULT_REMOTE_WHISPER"; then
  if [[ -f "$DEFAULT_REMOTE_WHISPER" ]]; then
    DEFAULT_SRC_WHISPER="$DEFAULT_REMOTE_WHISPER"
    DEFAULT_REMOTE_WHISPER=""
  fi
fi

PACKAGE_NAME="$DEFAULT_PACKAGE"
DEVICE_ID=""
LLAMA_SRC="$DEFAULT_SRC_LLAMA"
WHISPER_SRC="$DEFAULT_SRC_WHISPER"
REMOTE_LLAMA="$DEFAULT_REMOTE_LLAMA"
REMOTE_WHISPER="$DEFAULT_REMOTE_WHISPER"

usage() {
  cat <<'EOF'
Usage:
  scripts/push_android_models.sh \
    [--llama-src /abs/path/model.gguf] \
    [--whisper-src /abs/path/whisper-base.bin] \
    [--package com.example.bachelor_ai_project] \
    [--device emulator-5554] \
    [--remote-llama /data/user/0/<pkg>/files/models/model.gguf] \
    [--remote-whisper /data/user/0/<pkg>/files/models/whisper-base.bin]

Notes:
- Source-Pfade koennen aus local.properties gelesen werden:
  - llama.model.push.src
  - whisper.model.push.src
- If --remote-* are omitted, values from local.properties are used:
  - llama.model.path
  - whisper.model.path
- CLI-Parameter haben Vorrang vor local.properties.
- If local.properties values are empty, fallback is:
  /data/user/0/<package>/files/models/model.gguf
  /data/user/0/<package>/files/models/whisper-base.bin
- App must be installed and debuggable (run-as must work).
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --package)
      PACKAGE_NAME="$2"
      shift 2
      ;;
    --device)
      DEVICE_ID="$2"
      shift 2
      ;;
    --llama-src)
      LLAMA_SRC="$2"
      shift 2
      ;;
    --whisper-src)
      WHISPER_SRC="$2"
      shift 2
      ;;
    --remote-llama)
      REMOTE_LLAMA="$2"
      shift 2
      ;;
    --remote-whisper)
      REMOTE_WHISPER="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unbekanntes Argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$LLAMA_SRC" || -z "$WHISPER_SRC" ]]; then
  echo "Fehler: Quellpfade fehlen. Setze --llama-src/--whisper-src oder local.properties (llama.model.push.src, whisper.model.push.src)." >&2
  usage
  exit 1
fi

if [[ ! -f "$LLAMA_SRC" ]]; then
  echo "Fehler: Llama-Datei nicht gefunden: $LLAMA_SRC" >&2
  exit 1
fi

if [[ ! -f "$WHISPER_SRC" ]]; then
  echo "Fehler: Whisper-Datei nicht gefunden: $WHISPER_SRC" >&2
  exit 1
fi

if [[ -z "$REMOTE_LLAMA" ]]; then
  REMOTE_LLAMA="/data/user/0/${PACKAGE_NAME}/files/models/model.gguf"
fi

if [[ -z "$REMOTE_WHISPER" ]]; then
  REMOTE_WHISPER="/data/user/0/${PACKAGE_NAME}/files/models/whisper-base.bin"
fi

adb_cmd=(adb)
if [[ -n "$DEVICE_ID" ]]; then
  adb_cmd+=( -s "$DEVICE_ID" )
fi

if ! command -v adb >/dev/null 2>&1; then
  echo "Fehler: adb nicht gefunden." >&2
  exit 1
fi

echo "[1/7] Prüfe App-Installation: $PACKAGE_NAME"
if ! "${adb_cmd[@]}" shell pm path "$PACKAGE_NAME" >/dev/null 2>&1; then
  echo "Fehler: App '$PACKAGE_NAME' ist auf dem Zielgerät nicht installiert." >&2
  exit 1
fi

echo "[2/7] Prüfe run-as Berechtigung"
if ! "${adb_cmd[@]}" shell run-as "$PACKAGE_NAME" id >/dev/null 2>&1; then
  echo "Fehler: run-as für '$PACKAGE_NAME' fehlgeschlagen (Debug-Build nötig)." >&2
  exit 1
fi

TMP_DIR="/data/local/tmp/.bachelor_models"
TMP_LLAMA="$TMP_DIR/$(basename "$LLAMA_SRC")"
TMP_WHISPER="$TMP_DIR/$(basename "$WHISPER_SRC")"

echo "[3/7] Lege temporären Push-Ordner an: $TMP_DIR"
"${adb_cmd[@]}" shell mkdir -p "$TMP_DIR" >/dev/null

echo "[4/7] Push Llama -> $TMP_LLAMA"
"${adb_cmd[@]}" push "$LLAMA_SRC" "$TMP_LLAMA" >/dev/null

echo "[5/7] Push Whisper -> $TMP_WHISPER"
"${adb_cmd[@]}" push "$WHISPER_SRC" "$TMP_WHISPER" >/dev/null

rel_path_for_run_as() {
  local abs="$1"
  local p1="/data/user/0/${PACKAGE_NAME}/"
  local p2="/data/data/${PACKAGE_NAME}/"
  if [[ "$abs" == "$p1"* ]]; then
    echo "${abs#$p1}"
    return 0
  fi
  if [[ "$abs" == "$p2"* ]]; then
    echo "${abs#$p2}"
    return 0
  fi
  # fallback: nutze files/models/... innerhalb app sandbox
  echo ""
}

LLAMA_REL="$(rel_path_for_run_as "$REMOTE_LLAMA")"
WHISPER_REL="$(rel_path_for_run_as "$REMOTE_WHISPER")"

if [[ -z "$LLAMA_REL" ]]; then
  LLAMA_REL="files/models/$(basename "$REMOTE_LLAMA")"
  echo "Hinweis: --remote-llama liegt außerhalb app sandbox, fallback auf $LLAMA_REL"
fi

if [[ -z "$WHISPER_REL" ]]; then
  WHISPER_REL="files/models/$(basename "$REMOTE_WHISPER")"
  echo "Hinweis: --remote-whisper liegt außerhalb app sandbox, fallback auf $WHISPER_REL"
fi

LLAMA_DIR_REL="${LLAMA_REL%/*}"
WHISPER_DIR_REL="${WHISPER_REL%/*}"

if [[ -z "$LLAMA_DIR_REL" || "$LLAMA_DIR_REL" == "$LLAMA_REL" ]]; then
  LLAMA_DIR_REL="files/models"
fi

if [[ -z "$WHISPER_DIR_REL" || "$WHISPER_DIR_REL" == "$WHISPER_REL" ]]; then
  WHISPER_DIR_REL="files/models"
fi

echo "Resolved target (run-as):"
echo "  LLAMA_REL=$LLAMA_REL"
echo "  WHISPER_REL=$WHISPER_REL"
echo "  LLAMA_DIR_REL=$LLAMA_DIR_REL"
echo "  WHISPER_DIR_REL=$WHISPER_DIR_REL"

echo "[6/7] Kopiere Dateien in App-Sandbox"
"${adb_cmd[@]}" shell run-as "$PACKAGE_NAME" mkdir -p "$LLAMA_DIR_REL" "$WHISPER_DIR_REL"
"${adb_cmd[@]}" shell run-as "$PACKAGE_NAME" cp "$TMP_LLAMA" "$LLAMA_REL"
"${adb_cmd[@]}" shell run-as "$PACKAGE_NAME" cp "$TMP_WHISPER" "$WHISPER_REL"

echo "[7/7] Verifiziere Dateigrößen"
"${adb_cmd[@]}" shell run-as "$PACKAGE_NAME" ls -lh "$LLAMA_REL" "$WHISPER_REL"

echo "Cleanup temp files"
"${adb_cmd[@]}" shell rm -f "$TMP_LLAMA" "$TMP_WHISPER" >/dev/null || true

echo "Fertig."
echo "Llama (Sandbox): /data/user/0/${PACKAGE_NAME}/${LLAMA_REL}"
echo "Whisper (Sandbox): /data/user/0/${PACKAGE_NAME}/${WHISPER_REL}"



