#!/usr/bin/env bash
set -euo pipefail

ROOT=/home/joe/code
FUTON3A="$ROOT/futon3a"
FUTON3="$ROOT/futon3"
NOTIONS="$ROOT/data/notions"
HYPEREDGES_URL="${FUTON1A_URL:-http://localhost:7073}/api/alpha/hyperedges?type=code%2Fv05%2Fmission-doc&limit=500"
response_tmp=""

cleanup() {
  [[ -z "$response_tmp" ]] || rm -f "$response_tmp"
}
trap cleanup EXIT

response_tmp="$(mktemp "${TMPDIR:-/tmp}/notions-mission-docs.XXXXXX.edn")"
curl --fail --silent --show-error --max-time 90 "$HYPEREDGES_URL" > "$response_tmp"

read -r shelf_patterns embedded_patterns embedded_missions < <(
  python3 - "$FUTON3" "$NOTIONS" <<'PY'
import json
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
notions = Path(sys.argv[2])
header = re.compile(r"^\s*@(arg|flexiarg|multiarg)\s+(\S+)")
pattern_count = 0
for source_root in (root / "library", root / "holes"):
    for path in source_root.rglob("*"):
        if path.suffix.lower() not in {".flexiarg", ".multiarg"}:
            continue
        for line in path.read_text(errors="replace").splitlines():
            match = header.match(line)
            if match:
                pattern_count += 1

def ids(path, field):
    payload = json.loads(path.read_text())
    return [str(item[field]) for item in payload if item.get(field)]

embedded_patterns = ids(notions / "minilm_pattern_embeddings.json", "id")
embedded_missions = ids(notions / "bge_mission_embeddings.json", "basename")
print(pattern_count, len(embedded_patterns), len(embedded_missions))
PY
)

substrate_missions="$({
  cd "$FUTON3A"
  RESPONSE_TMP="$response_tmp" clojure -M -e \
    '(require (quote clojure.edn)) (println (count (:hyperedges (clojure.edn/read-string (slurp (System/getenv "RESPONSE_TMP"))))))'
} | tail -n 1)"

pattern_delta=$((shelf_patterns - embedded_patterns))
mission_delta=$((substrate_missions - embedded_missions))
echo "[notions-reembed] drift pattern_delta=$pattern_delta shelf_pattern_ids=$shelf_patterns embedded_pattern_ids=$embedded_patterns mission_delta=$mission_delta substrate_mission_docs=$substrate_missions embedded_mission_basenames=$embedded_missions"

CLJ_CMD=clojure FUTON1A_URL="${FUTON1A_URL:-http://localhost:7073}" \
  "$FUTON3A/scripts/index_patterns.sh" \
  --minilm --include-missions --out-dir "$NOTIONS"

"$ROOT/futon6/scripts/daily_reembed.sh"

echo "[notions-reembed] pipeline complete"
