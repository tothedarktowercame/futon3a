#!/usr/bin/env python3
"""mission_status_triangulation.py — SLICE-2a MAP cut (d): triangulate Salingaros class against mission Status.

Joe's test (2026-06-23): the domain skew (math missions -> mess) is partly a real MECHANISM — in math domains he
leans on math patterns and DROPS the general project/research-management patterns that keep a mission on the
rails, so it gets lost in detail. If that's right, MESS missions should ALSO be disproportionately UNFINISHED
(by Status) — that's the triangulation that turns "alive" into a credible proxy for "went well." And the
mechanism predicts: mess missions carry MATH patterns but LACK the MANAGEMENT patterns that alive missions have.

Read-only. Run: cd ~/code/futon3a && python3 holes/labs/M-memes-arrows/mission_status_triangulation.py
"""
import re, glob
from pathlib import Path
from collections import defaultdict, Counter

CODE = Path("/home/joe/code")
DATA = CODE / "futon6/data"
WHOLE = (DATA / "mission-wholeness.edn").read_text()
SCOPES = (DATA / "mission-pattern-scopes.edn").read_text()

CLASS = dict(re.findall(r':mission "M-([^"]+)" :class :(\w+)', WHOLE))
APPLIED = {m: re.findall(r'"([^"]+)"', body)
           for m, body in re.findall(r':mission "M-([^"]+)" :applied \[([^\]]*)\]', SCOPES)}

# --- index mission files by name (basename minus .md minus leading M-) ---
FILES = {}
for p in glob.glob(str(CODE / "*/holes/missions/M-*.md")) + glob.glob(str(CODE / "*/holes/M-*.md")):
    name = Path(p).stem[2:]  # drop "M-"
    FILES.setdefault(name, p)

def status_of(name):
    p = FILES.get(name)
    if not p:
        return None
    for line in Path(p).read_text(errors="ignore").splitlines():
        if re.match(r'\*{0,2}status', line, re.I):
            return line
    return None

def bucket(line):
    if line is None:
        return "no-file" if True else "no-status"
    s = line.lower()
    if re.search(r'park|blocked|deferred|nonstarter|on hold|held', s): return "parked/blocked"
    if re.search(r'archived', s):                                        return "archived"
    if re.search(r'complete|closed|\bdone\b|✅|finished', s):            return "done"
    if re.search(r'identify|\bmap\b|derive|argue|verify|instantiate|head|active|\bopen\b|draft|diagnosis', s):
        return "open/in-progress"
    return "other"

# ---- class x status crosstab ----
rows = defaultdict(Counter)
missing = Counter()
for m, cls in CLASS.items():
    line = status_of(m)
    b = bucket(line)
    if line is None:
        missing[cls] += 1
    rows[cls][b] += 1

cls_order = ["alive", "mess", "pipeline", "stub"]
buckets = ["done", "archived", "open/in-progress", "parked/blocked", "other", "no-file"]
print("=== class x Status crosstab (counts) ===")
print(f"  {'class':9} " + " ".join(f"{b:>16}" for b in buckets) + "   N")
for cls in cls_order:
    r = rows[cls]
    n = sum(r.values())
    print(f"  {cls:9} " + " ".join(f"{r.get(b,0):>16}" for b in buckets) + f"   {n}")

print("\n=== Joe's test: are MESS missions less FINISHED than ALIVE? ===")
def finished_rate(cls):
    r = rows[cls]; n = sum(r.values())
    fin = r.get("done", 0) + r.get("archived", 0)
    havefile = n - r.get("no-file", 0)
    return fin, n, havefile
for cls in ("alive", "mess", "pipeline"):
    fin, n, hf = finished_rate(cls)
    base = hf if hf else 1
    print(f"  {cls:9}: finished(done+archived)={fin}/{n}  = {fin/n:.0%} of all, {fin/base:.0%} of those-with-a-file")
print("  prediction: alive finished-rate >> mess finished-rate (if so, 'alive' tracks 'went well').")

# ---- mechanism: math-present-but-management-absent in mess ----
MGMT = ["expected-free-energy-scorecard","structured-observation-vector","candidate-pattern-action-space",
        "single-source-of-truth","par-as-obligation","unresolved-tensions-at-closure",
        "what-problem-is-this-actually-solving","whose-question-is-this","world-is-hypergraph",
        "task-shape-validation","mission-interface-signature"]
MATH = ["local-to-global","unfold-the-definition","work-examples-first","construct-an-explicit-witness",
        "reduce-to-known-result","argue-by-contradiction","split-into-cases","try-a-simpler-case",
        "transport-across-isomorphism","non-circularity-check","reduce-to-kernel"]
mset, hset = set(MGMT), set(MATH)
print("\n=== mechanism check: does each class carry MANAGEMENT vs MATH patterns? ===")
print("  (MGMT = top alive-enriched; MATH = top mess-enriched, from the contrast)")
for cls in ("alive", "mess", "pipeline"):
    grp = [m for m in CLASS if CLASS[m] == cls and m in APPLIED]
    if not grp: continue
    has_mgmt = sum(1 for m in grp if mset & set(APPLIED[m])) / len(grp)
    has_math = sum(1 for m in grp if hset & set(APPLIED[m])) / len(grp)
    print(f"  {cls:9}: applies >=1 MGMT pattern in {has_mgmt:.0%} of missions | >=1 MATH pattern in {has_math:.0%}")
print("  mechanism (Joe): mess = MATH present + MGMT absent ('lost in detail, drops the management patterns').")
