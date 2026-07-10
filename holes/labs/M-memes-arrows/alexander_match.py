#!/usr/bin/env python3
"""alexander_match.py — the Alexandrian (non-circular) cascade→solution test.

Instead of building the wiring and back-tagging it with patterns (what we did, circular),
do it Alexander's way:
  1. restate the SORRY as IF/HOWEVER *tensions* (the problem statement);
  2. match those tensions against each cascade pattern's REAL context(IF) + HOWEVER
     (by embedding meaning, not by fiat);
  3. read back the THEN lines of the patterns whose tension matches.
The output (which patterns the tensions retrieve, and their THENs) is then driven by the
data — the sorry, and the flexiargs — not by a hand-assigned rule table.

Run: cd ~/code/futon3a && .venv/bin/python3 holes/labs/M-memes-arrows/alexander_match.py
"""
import json, re, glob, sys
from pathlib import Path

LAB = Path("/home/joe/code/futon3a/holes/labs/M-memes-arrows")
LIB = Path("/home/joe/code/futon3/library")

# 1) the sorry, restated as IF/HOWEVER tensions (the legitimate problem statement;
#    everything downstream is data-driven from here).
SORRY_TENSIONS = {
 "compose-not-manual":
   "A cascade of patterns expresses how to fill a hole, however composing them into a "
   "runnable construction is manual judgment with no executable procedure or stopping rule.",
 "select-and-order":
   "Many candidate patterns could each contribute to the construction, however there is no "
   "principled way to decide which ones apply and in what order to combine them.",
 "verify-and-terminate":
   "The construction must be checkable and must terminate, however some required decisions "
   "(thresholds, policy choices) cannot be derived from the patterns and must be surfaced honestly.",
}

def find_flexiarg(pid):
    stem = pid.split("/")[-1]
    hits = glob.glob(str(LIB / "**" / f"{stem}.flexiarg"), recursive=True)
    return hits[0] if hits else None

def block(text, start, ends):
    """Extract text from a `+ start:` marker up to the first of `ends` markers."""
    m = re.search(r"(?im)^\s*\+?\s*" + re.escape(start) + r"\s*:?", text)
    if not m:
        return ""
    rest = text[m.end():]
    cut = len(rest)
    for e in ends:
        em = re.search(r"(?im)^\s*\+?\s*" + re.escape(e) + r"\s*:?", rest)
        if em:
            cut = min(cut, em.start())
    return re.sub(r"\s+", " ", rest[:cut]).strip()

def parse_pattern(pid):
    f = find_flexiarg(pid)
    if not f:
        return None
    t = Path(f).read_text()
    ctx = block(t, "context", ["HOWEVER", "THEN"])
    however = block(t, "HOWEVER", ["THEN", "QUALITY-CRITERIA"])
    then = block(t, "THEN", ["QUALITY-CRITERIA", "context"])
    concl = ""
    cm = re.search(r"(?is)!\s*conclusion\s*:(.*?)(?:\+\s*context|$)", t)
    if cm:
        concl = re.sub(r"\s+", " ", cm.group(1)).strip()
    return {"id": pid, "tension": (ctx + " " + however).strip(), "then": then, "conclusion": concl}

def main():
    cascade = [row[0] for row in json.load(open(LAB / "E-fold-engine-stage1.json"))["cascade"]]
    pats = [p for p in (parse_pattern(pid) for pid in cascade) if p and p["tension"]]
    print(f"parsed {len(pats)}/{len(cascade)} cascade patterns with a readable IF+HOWEVER\n")

    from sentence_transformers import SentenceTransformer
    import numpy as np
    model = SentenceTransformer("all-MiniLM-L6-v2")
    def emb(xs):
        v = model.encode(xs, normalize_embeddings=True)
        return np.array(v)
    tkeys = list(SORRY_TENSIONS)
    T = emb([SORRY_TENSIONS[k] for k in tkeys])          # sorry tensions
    P = emb([p["tension"] for p in pats])                # pattern IF+HOWEVER
    sim = T @ P.T                                         # (tensions x patterns)

    # per-tension: which pattern's tension matches, and its THEN
    print("=== Alexander match: sorry-tension → best-matching pattern tension → its THEN ===")
    for i, k in enumerate(tkeys):
        order = np.argsort(-sim[i])
        print(f"\n[tension: {k}]  \"{SORRY_TENSIONS[k][:80]}...\"")
        for j in order[:2]:
            p = pats[j]
            print(f"   {sim[i][j]:.3f}  {p['id']}")
            print(f"          THEN→ {p['then'][:150] or p['conclusion'][:150]}")

    # the tension-matched cascade ranking vs the topical cascade_construct order
    best = [(pats[j]["id"], float(sim[:, j].max())) for j in range(len(pats))]
    best.sort(key=lambda x: -x[1])
    print("\n=== tension-matched ranking (max sim to ANY sorry-tension) ===")
    for pid, s in best:
        print(f"   {s:.3f}  {pid}")
    print("\n=== vs cascade_construct topical order (the original cascade) ===")
    for pid in cascade:
        print(f"        {pid}")

    print("\nNOTE: if the tension-ranking reorders/demotes patterns vs the topical cascade,")
    print("that is the finding — topical relevance ≠ tension-match (the Alexandrian point).")

if __name__ == "__main__":
    main()
