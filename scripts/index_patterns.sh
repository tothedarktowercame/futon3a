#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/index_patterns.sh [--futon3-root PATH] [--out-dir PATH] [--glove PATH]
                                 [--fasttext PATH] [--minilm] [--minilm-model NAME]
                                 [--include-missions]
                                 [--missions-roots PATH[,PATH...]]

Build a local notions index from Futon3 library patterns.

Options:
  --futon3-root PATH  Path to Futon3 repo (default: ../futon3).
  --out-dir PATH      Output directory (default: resources/notions).
  --glove PATH        Optional path to GloVe vectors; generates neighbors report.
  --fasttext PATH     Optional path to fastText .vec or .bin vectors; writes embeddings.
  --minilm            Use default MiniLM model (all-MiniLM-L6-v2).
  --minilm-model NAME Optional SentenceTransformer model name/path for MiniLM embeddings.
  --include-missions  Refresh mission_records + mission embeddings from substrate-2/cache.
  --missions-roots X  Deprecated compatibility alias; mission roots are no longer used.
EOF
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FUTON3A_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DEFAULT_FUTON3_ROOT="$(cd "${SCRIPT_DIR}/../../futon3" && pwd)"
FUTON3_ROOT="${FUTON3_ROOT:-$DEFAULT_FUTON3_ROOT}"
OUT_DIR="${OUT_DIR:-${SCRIPT_DIR}/../resources/notions}"
GLOVE_PATH=""
FASTTEXT_PATH=""
MINILM_MODEL=""
INCLUDE_MISSIONS=0
MISSIONS_ROOTS=""
minilm_pattern_tmp=""
minilm_mission_tmp=""

cleanup() {
  [[ -z "$minilm_pattern_tmp" ]] || rm -f "$minilm_pattern_tmp"
  [[ -z "$minilm_mission_tmp" ]] || rm -f "$minilm_mission_tmp"
}
trap cleanup EXIT

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
    --include-missions)
      INCLUDE_MISSIONS=1
      shift 1
      ;;
    --missions-roots)
      INCLUDE_MISSIONS=1
      MISSIONS_ROOTS="$2"
      shift 2
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

if [[ -n "${CLJ_CMD:-}" ]]; then
  if ! command -v "$CLJ_CMD" >/dev/null 2>&1; then
    echo "Configured CLJ_CMD is not runnable: $CLJ_CMD" >&2
    exit 1
  fi
elif command -v clj >/dev/null 2>&1; then
  CLJ_CMD="clj"
elif command -v clojure >/dev/null 2>&1; then
  CLJ_CMD="clojure"
else
  echo "Missing clj/clojure on PATH." >&2
  exit 1
fi

find_venv_python() {
  local dir="$FUTON3A_ROOT"
  local depth=0
  while [[ "$depth" -le 6 && -n "$dir" && "$dir" != "/" ]]; do
    if [[ -x "$dir/.venv/bin/python3" ]]; then
      echo "$dir/.venv/bin/python3"
      return 0
    fi
    dir="$(dirname "$dir")"
    depth=$((depth + 1))
  done
  return 1
}

if [[ -n "${NOTIONS_PYTHON:-}" ]]; then
  PYTHON_BIN="$NOTIONS_PYTHON"
elif [[ -n "${PYTHON_BIN:-}" ]]; then
  PYTHON_BIN="$PYTHON_BIN"
else
  PYTHON_BIN="$(find_venv_python || true)"
  PYTHON_BIN="${PYTHON_BIN:-python3}"
fi

if [[ ! -x "$PYTHON_BIN" ]] && ! command -v "$PYTHON_BIN" >/dev/null 2>&1; then
  echo "Missing Python executable: $PYTHON_BIN" >&2
  exit 1
fi

require_minilm_python() {
  "$PYTHON_BIN" - <<'PY'
try:
    import sentence_transformers
except Exception as exc:
    raise SystemExit(f"sentence-transformers unavailable for MiniLM generation: {exc}")
print(f"sentence-transformers {sentence_transformers.__version__}")
PY
}

json_array_count() {
  "$PYTHON_BIN" - <<'PY' "$1"
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
payload = json.loads(path.read_text())
if not isinstance(payload, list):
    raise SystemExit(f"{path} is not a JSON array")
print(len(payload))
PY
}

assert_nonempty_json_array() {
  local path="$1"
  local label="$2"
  if [[ ! -s "$path" ]]; then
    echo "$label is empty or missing: $path" >&2
    exit 1
  fi
  local count
  count="$(json_array_count "$path")"
  if [[ "$count" -le 0 ]]; then
    echo "$label has zero JSON entries: $path" >&2
    exit 1
  fi
  echo "$label count: $count"
}

assert_embedding_parity() {
  local records="$1"
  local embeddings="$2"
  local label="$3"
  assert_nonempty_json_array "$records" "$label records"
  assert_nonempty_json_array "$embeddings" "$label embeddings"
  local record_count embedding_count
  record_count="$(json_array_count "$records")"
  embedding_count="$(json_array_count "$embeddings")"
  if [[ "$record_count" -ne "$embedding_count" ]]; then
    echo "$label count mismatch: records=$record_count embeddings=$embedding_count" >&2
    exit 1
  fi
}

copy_if_distinct() {
  local src="$1"
  local dst="$2"
  local src_real dst_real
  src_real="$(readlink -f "$src")"
  dst_real="$(readlink -f "$dst" 2>/dev/null || true)"
  if [[ -n "$dst_real" && "$src_real" == "$dst_real" ]]; then
    return 0
  fi
  mkdir -p "$(dirname "$dst")"
  cp "$src" "$dst"
}

mkdir -p "$OUT_DIR"

if [[ -n "$MISSIONS_ROOTS" ]]; then
  echo "WARN: --missions-roots is deprecated and ignored; mission indexing now reads substrate-2/cache." >&2
fi

(
  cd "$FUTON3A_ROOT"
  "$CLJ_CMD" -M -m futon.flexiarg.projection \
    --futon3-root "$FUTON3_ROOT" \
    --out "$OUT_DIR/pattern-projections.edn" \
    --embed-json-out "$OUT_DIR/pattern-embedding-records.json"
  if [[ "$INCLUDE_MISSIONS" -eq 1 ]]; then
    "$CLJ_CMD" -M -m futon.missions index \
      --out-dir "$OUT_DIR"
  fi
)

(
  cd "$FUTON3_ROOT"
  "$CLJ_CMD" -M -m scripts.build-pattern-index
  if [[ -n "$GLOVE_PATH" ]]; then
    "$PYTHON_BIN" scripts/embed_patterns_glove.py --glove "$GLOVE_PATH"
  fi
  if [[ -n "$FASTTEXT_PATH" ]]; then
    "$PYTHON_BIN" scripts/embed_patterns_fasttext.py --fasttext "$FASTTEXT_PATH"
  fi
)

copy_if_distinct "$FUTON3_ROOT/resources/sigils/patterns-index.tsv" "$OUT_DIR/patterns-index.tsv"
copy_if_distinct "$FUTON3_ROOT/resources/sigils/rationale-examples.edn" "$OUT_DIR/rationale-examples.edn"
copy_if_distinct "$FUTON3_ROOT/resources/sigils/index.edn" "$OUT_DIR/sigil-index.edn"

if [[ -f "$FUTON3_ROOT/resources/embeddings/glove_pattern_neighbors.json" ]]; then
  copy_if_distinct "$FUTON3_ROOT/resources/embeddings/glove_pattern_neighbors.json" \
    "$OUT_DIR/glove_pattern_neighbors.json"
fi
if [[ -f "$FUTON3_ROOT/data/glove_pattern_embeddings.json" ]]; then
  copy_if_distinct "$FUTON3_ROOT/data/glove_pattern_embeddings.json" \
    "$OUT_DIR/glove_pattern_embeddings.json"
fi
if [[ -f "$FUTON3_ROOT/resources/embeddings/fasttext_pattern_embeddings.json" ]]; then
  copy_if_distinct "$FUTON3_ROOT/resources/embeddings/fasttext_pattern_embeddings.json" \
    "$OUT_DIR/fasttext_pattern_embeddings.json"
fi

if [[ -n "$MINILM_MODEL" ]]; then
  require_minilm_python
  minilm_pattern_tmp="$(mktemp "$OUT_DIR/.minilm_pattern_embeddings.XXXXXX.json")"
  (
    cd "$FUTON3A_ROOT"
    "$PYTHON_BIN" scripts/embed_text.py --json --model "$MINILM_MODEL" \
      < "$OUT_DIR/pattern-embedding-records.json" \
      > "$minilm_pattern_tmp"
  )
  assert_embedding_parity "$OUT_DIR/pattern-embedding-records.json" "$minilm_pattern_tmp" "MiniLM pattern"
  mv "$minilm_pattern_tmp" "$OUT_DIR/minilm_pattern_embeddings.json"
  minilm_pattern_tmp=""
  mkdir -p "$FUTON3_ROOT/resources/embeddings"
  copy_if_distinct "$OUT_DIR/minilm_pattern_embeddings.json" \
    "$FUTON3_ROOT/resources/embeddings/minilm_pattern_embeddings.json"
fi

if [[ "$INCLUDE_MISSIONS" -eq 1 && -n "$MINILM_MODEL" && -f "$OUT_DIR/mission_records.json" ]]; then
  minilm_mission_tmp="$(mktemp "$OUT_DIR/.minilm_mission_embeddings.XXXXXX.json")"
  (
    cd "$FUTON3A_ROOT"
    "$PYTHON_BIN" scripts/embed_text.py --json --model "$MINILM_MODEL" \
      < "$OUT_DIR/mission_records.json" \
      > "$minilm_mission_tmp"
  )
  assert_embedding_parity "$OUT_DIR/mission_records.json" "$minilm_mission_tmp" "MiniLM mission"
  mv "$minilm_mission_tmp" "$OUT_DIR/minilm_mission_embeddings.json"
  minilm_mission_tmp=""
fi

if [[ -f "$OUT_DIR/minilm_pattern_embeddings.json" || -f "$OUT_DIR/minilm_mission_embeddings.json" ]]; then
  "$PYTHON_BIN" - <<'PY' "$OUT_DIR"
import json
import sys
from pathlib import Path

out_dir = Path(sys.argv[1])
entries = []

def load(path, default_type):
    if not path.exists():
        return
    payload = json.loads(path.read_text())
    for item in payload:
        item = dict(item)
        item.setdefault("type", default_type)
        entries.append(item)

load(out_dir / "minilm_pattern_embeddings.json", "pattern")
load(out_dir / "minilm_mission_embeddings.json", "mission")

if not entries:
    raise SystemExit("refusing to write empty minilm_corpus_embeddings.json")

(out_dir / "minilm_corpus_embeddings.json").write_text(json.dumps(entries))
PY
  assert_nonempty_json_array "$OUT_DIR/minilm_corpus_embeddings.json" "MiniLM corpus embeddings"
fi

echo "Notions index written to $OUT_DIR"
