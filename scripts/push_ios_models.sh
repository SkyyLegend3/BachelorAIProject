#!/usr/bin/env bash
set -euo pipefail

# Pushes local whisper + llama models into an iOS Simulator app container.
# Intended for re-seeding models after app uninstall/reinstall on simulator.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_PROPERTIES="$ROOT_DIR/local.properties"

DEFAULT_BUNDLE_ID="com.joshuavogt.bacheloraiproject"
DEFAULT_SRC_LLAMA=""
DEFAULT_SRC_WHISPER=""
DEFAULT_DEST_LLAMA_NAME="model.gguf"
DEFAULT_DEST_WHISPER_NAME="whisper-base.bin"
DEFAULT_SIM_TARGET="booted"

read_local_prop() {
  local key="$1"
  local file="$2"
  if [[ ! -f "$file" ]]; then
    return 0
  fi
  local raw
  raw="$(grep -E "^${key}=" "$file" | tail -n 1 | cut -d'=' -f2- || true)"
  raw="${raw//$'\r'/}"
  raw="${raw//$'\n'/}"
  echo "$raw" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//'
}

is_android_sandbox_path() {
  local p="$1"
  [[ "$p" == /data/user/0/* || "$p" == /data/data/* ]]
}

# Defaults from local.properties (if present)
DEFAULT_SRC_LLAMA="$(read_local_prop "llama.model.push.src" "$LOCAL_PROPERTIES")"
DEFAULT_SRC_WHISPER="$(read_local_prop "whisper.model.push.src" "$LOCAL_PROPERTIES")"

# Compatibility fallback: use llama.model.path / whisper.model.path as source
# only if they look like local host paths (not Android sandbox paths).
fallback_llama="$(read_local_prop "llama.model.path" "$LOCAL_PROPERTIES")"
fallback_whisper="$(read_local_prop "whisper.model.path" "$LOCAL_PROPERTIES")"

if [[ -z "$DEFAULT_SRC_LLAMA" && -n "$fallback_llama" ]] && ! is_android_sandbox_path "$fallback_llama"; then
  if [[ -f "$fallback_llama" ]]; then
    DEFAULT_SRC_LLAMA="$fallback_llama"
  fi
fi

if [[ -z "$DEFAULT_SRC_WHISPER" && -n "$fallback_whisper" ]] && ! is_android_sandbox_path "$fallback_whisper"; then
  if [[ -f "$fallback_whisper" ]]; then
    DEFAULT_SRC_WHISPER="$fallback_whisper"
  fi
fi

BUNDLE_ID="$DEFAULT_BUNDLE_ID"
SIM_TARGET="$DEFAULT_SIM_TARGET"
LLAMA_SRC="$DEFAULT_SRC_LLAMA"
WHISPER_SRC="$DEFAULT_SRC_WHISPER"
DEST_LLAMA_NAME="$DEFAULT_DEST_LLAMA_NAME"
DEST_WHISPER_NAME="$DEFAULT_DEST_WHISPER_NAME"

usage() {
  cat <<'EOF'
Usage:
  scripts/push_ios_models.sh \
    [--bundle-id com.example.bachelor_ai_project] \
    [--simulator booted|<UDID>|"iPhone 17 Pro Max"] \
    [--llama-src /abs/path/model.gguf] \
    [--whisper-src /abs/path/whisper-base.bin] \
    [--llama-name model.gguf] \
    [--whisper-name whisper-base.bin]

Notes:
- Source paths can come from local.properties:
  - llama.model.push.src
  - whisper.model.push.src
- CLI arguments override local.properties.
- Models are copied to:
  <AppDataContainer>/Documents/models/<name>
- App must be installed in the selected simulator target at least once.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --bundle-id)
      BUNDLE_ID="$2"
      shift 2
      ;;
    --simulator)
      SIM_TARGET="$2"
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
    --llama-name)
      DEST_LLAMA_NAME="$2"
      shift 2
      ;;
    --whisper-name)
      DEST_WHISPER_NAME="$2"
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

if ! command -v xcrun >/dev/null 2>&1; then
  echo "Fehler: xcrun nicht gefunden (Xcode Command Line Tools fehlen?)." >&2
  exit 1
fi

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

echo "[1/5] Ermittle App-Data-Container"
if ! APP_DATA_DIR="$(xcrun simctl get_app_container "$SIM_TARGET" "$BUNDLE_ID" data 2>/dev/null)"; then
  echo "Fehler: Konnte App-Container nicht finden." >&2
  echo "Hinweis: Stelle sicher, dass die App '$BUNDLE_ID' im Simulator '$SIM_TARGET' installiert ist und starte sie einmal." >&2
  echo "Optional: --simulator booted verwenden und Simulator vorher booten." >&2
  exit 1
fi

echo "[2/5] Zielordner anlegen"
DEST_DIR="$APP_DATA_DIR/Documents/models"
mkdir -p "$DEST_DIR"

echo "[3/5] Llama kopieren"
cp "$LLAMA_SRC" "$DEST_DIR/$DEST_LLAMA_NAME"

echo "[4/5] Whisper kopieren"
cp "$WHISPER_SRC" "$DEST_DIR/$DEST_WHISPER_NAME"

echo "[5/5] Verifizieren"
ls -lh "$DEST_DIR/$DEST_LLAMA_NAME" "$DEST_DIR/$DEST_WHISPER_NAME"

echo "Fertig."
echo "Llama (iOS Simulator): $DEST_DIR/$DEST_LLAMA_NAME"
echo "Whisper (iOS Simulator): $DEST_DIR/$DEST_WHISPER_NAME"

