#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/index_patterns.sh [--futon3-root PATH] [--out-dir PATH] [--glove PATH]
                                 [--fasttext PATH] [--minilm] [--minilm-model NAME]

Build a local notions index from Futon3 library patterns.

Options:
  --futon3-root PATH  Path to Futon3 repo (default: ../futon3).
  --out-dir PATH      Output directory (default: resources/notions).
  --glove PATH        Optional path to GloVe vectors; generates neighbors report.
  --fasttext PATH     Optional path to fastText .vec or .bin vectors; writes embeddings.
  --minilm            Use default MiniLM model (all-MiniLM-L6-v2).
  --minilm-model NAME Optional SentenceTransformer model name/path for MiniLM embeddings.
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_FUTON3_ROOT="$(cd "${SCRIPT_DIR}/../../futon3" && pwd)"
FUTON3_ROOT="${FUTON3_ROOT:-$DEFAULT_FUTON3_ROOT}"
OUT_DIR="${OUT_DIR:-${SCRIPT_DIR}/../resources/notions}"
GLOVE_PATH=""
FASTTEXT_PATH=""
MINILM_MODEL=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --futon3-root)
      FUTON3_ROOT="$2"
      shift 2
      ;;
    --out-dir)
      OUT_DIR="$2"
      shift 2
      ;;
    --glove)
      GLOVE_PATH="$2"
      shift 2
      ;;
    --fasttext)
      FASTTEXT_PATH="$2"
      shift 2
      ;;
    --minilm-model)
      MINILM_MODEL="$2"
      shift 2
      ;;
    --minilm)
      MINILM_MODEL="sentence-transformers/all-MiniLM-L6-v2"
      shift 1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! -d "$FUTON3_ROOT" ]]; then
  echo "Futon3 root not found: $FUTON3_ROOT" >&2
  exit 1
fi

if command -v clj >/dev/null 2>&1; then
  CLJ_CMD="clj"
elif command -v clojure >/dev/null 2>&1; then
  CLJ_CMD="clojure"
else
  echo "Missing clj/clojure on PATH." >&2
  exit 1
fi

PYTHON_BIN="${PYTHON_BIN:-python3}"
if ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  echo "Missing $PYTHON_BIN on PATH." >&2
  exit 1
fi

(
  cd "$FUTON3_ROOT"
  "$CLJ_CMD" -M -m scripts.build-pattern-index
  if [[ -n "$GLOVE_PATH" ]]; then
    "$PYTHON_BIN" scripts/embed_patterns_glove.py --glove "$GLOVE_PATH"
  fi
  if [[ -n "$FASTTEXT_PATH" ]]; then
    "$PYTHON_BIN" scripts/embed_patterns_fasttext.py --fasttext "$FASTTEXT_PATH"
  fi
  if [[ -n "$MINILM_MODEL" ]]; then
    "$PYTHON_BIN" scripts/embed_patterns_minilm.py --model "$MINILM_MODEL"
  fi
)

mkdir -p "$OUT_DIR"
cp "$FUTON3_ROOT/resources/sigils/patterns-index.tsv" "$OUT_DIR/"
cp "$FUTON3_ROOT/resources/sigils/rationale-examples.edn" "$OUT_DIR/"
cp "$FUTON3_ROOT/resources/sigils/index.edn" "$OUT_DIR/sigil-index.edn"

if [[ -f "$FUTON3_ROOT/resources/embeddings/glove_pattern_neighbors.json" ]]; then
  cp "$FUTON3_ROOT/resources/embeddings/glove_pattern_neighbors.json" "$OUT_DIR/"
fi
if [[ -f "$FUTON3_ROOT/data/glove_pattern_embeddings.json" ]]; then
  cp "$FUTON3_ROOT/data/glove_pattern_embeddings.json" "$OUT_DIR/"
fi
if [[ -f "$FUTON3_ROOT/resources/embeddings/fasttext_pattern_embeddings.json" ]]; then
  cp "$FUTON3_ROOT/resources/embeddings/fasttext_pattern_embeddings.json" "$OUT_DIR/"
fi
if [[ -f "$FUTON3_ROOT/resources/embeddings/minilm_pattern_embeddings.json" ]]; then
  cp "$FUTON3_ROOT/resources/embeddings/minilm_pattern_embeddings.json" "$OUT_DIR/"
fi

echo "Notions index written to $OUT_DIR"
