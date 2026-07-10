"""aliveness_v3 gate v2 — DENSE semantic accuracy, CORPUS-scaled.

Two upgrades over aliveness_v3_gate.py (operator, 2026-07-10):
  1. DENSER accuracy: the token-overlap want_coverage was median-0 (would collapse
     a GFN to complexity-minimization). Replace with SEMANTIC THEN-coverage —
     embedding similarity between the mission's want and the cascade's THEN-produces
     (still commitment-grounded: what the patterns PRODUCE, not IF-side relevance).
  2. CORPUS-scaled: every mission has an IDENTIFY (the have->want gap), so want-text
     comes from `identify_psi(mission_doc)` — lifting N from closure-folds' 10 to the
     full labelled alive/mess corpus (properly powered null test).

aliveness_v3(S|m) = accuracy(S, want_m) - lambda*complexity(S),  accuracy = semantic
THEN-coverage, complexity = sum -log base-rate prior (cascade_construct).
"""
from __future__ import annotations
import glob, math, random, sys
from pathlib import Path
LAB = "/home/joe/code/futon3a/holes/labs/M-memes-arrows"
sys.path.insert(0, LAB)
import re
import numpy as np

from offramp_cascade import identify_psi
import rollout_execute as rx
from alexandrian_aif import parse
from cascade_construct import load_phylogeny, base_rate_prior, pattern_stem

def parse_wholeness(path="/home/joe/code/futon6/data/mission-wholeness.edn"):
    text = Path(path).read_text(); out = {}
    for m in re.finditer(r':mission\s+"(M-[^"]+)"\s+:class\s+:(\w+)\s+:L\s+([0-9.]+)', text):
        out[m.group(1).split("@")[0]] = {"class": m.group(2), "L": float(m.group(3))}
    return out

def mission_applied(path="/home/joe/code/futon6/data/mission-pattern-scopes.edn"):
    text = Path(path).read_text(); out = {}
    for m in re.finditer(r':mission\s+"(M-[^"]+)".*?:applied\s+\[([^\]]*)\]', text, re.DOTALL):
        out[m.group(1).split("@")[0]] = [p.split("/")[-1] for p in re.findall(r'"([^"]+)"', m.group(2))]
    return out

MISSION_APPLIED = mission_applied()

LAMBDA = 0.03
SEED = 20260710

from sentence_transformers import SentenceTransformer
_M = SentenceTransformer("sentence-transformers/all-MiniLM-L6-v2")
def embed(texts):
    return np.asarray(_M.encode(list(texts), normalize_embeddings=True))

PRIOR, PRIOR_DEFAULT = base_rate_prior(load_phylogeny())
_THEN_CACHE = {}
def then_text(stem):
    if stem not in _THEN_CACHE:
        f = rx.find_flexiarg(stem)
        _THEN_CACHE[stem] = (parse(f)["then"] if f else None)
    return _THEN_CACHE[stem]

def locate_doc(mission):
    for pat in (f"/home/joe/code/*/holes/missions/{mission}.md",
                f"/home/joe/code/*/holes/{mission}.md",
                f"/home/joe/code/*/holes/**/{mission}.md"):
        hits = glob.glob(pat, recursive=True)
        if hits:
            return hits[0]
    return None

def complexity(patterns):
    return sum(-math.log(PRIOR.get(pattern_stem(p), PRIOR_DEFAULT)) for p in patterns)

def build_rows():
    whole = parse_wholeness()
    rows, no_doc = [], 0
    for m, rec in whole.items():
        if rec["class"] not in ("alive", "mess"):
            continue
        applied = [pattern_stem(p) for p in MISSION_APPLIED.get(m.split("@")[0], [])]
        if not applied:
            continue
        doc = locate_doc(m)
        if not doc:
            no_doc += 1; continue
        want = identify_psi(doc)
        thens = [(p, then_text(p)) for p in applied]
        thens = [(p, t) for p, t in thens if t]
        if not want or not thens:
            continue
        rows.append({"mission": m, "y": 1.0 if rec["class"] == "alive" else 0.0,
                     "L": rec["L"], "applied": applied, "want": want,
                     "then_pats": [p for p, _ in thens], "thens": [t for _, t in thens]})
    return rows, no_doc

def score(rows):
    want_e = embed([r["want"] for r in rows])
    for r, we in zip(rows, want_e):
        te = embed(r["thens"])                       # (n, d)
        r["acc_mean"] = float(we @ te.mean(0))       # cosine(want, mean THEN)
        r["acc_max"] = float(np.max(te @ we))        # best single covering pattern
        r["complexity"] = complexity(r["applied"])
        r["aliveness"] = r["acc_mean"] - LAMBDA * r["complexity"]
    return rows

def auc(scores, labels):
    pos = [s for s, y in zip(scores, labels) if y]; neg = [s for s, y in zip(scores, labels) if not y]
    if not pos or not neg:
        return float("nan")
    return sum((a > b) + 0.5 * (a == b) for a in pos for b in neg) / (len(pos) * len(neg))

def null_auc(scores, y, n=500):
    rng = random.Random(SEED); nulls = []
    for _ in range(n):
        yy = y[:]; rng.shuffle(yy); nulls.append(auc(scores, yy))
    nulls.sort(); return nulls[int(0.95 * len(nulls))]

def main():
    rows, no_doc = build_rows()
    rows = score(rows)
    y = [r["y"] for r in rows]
    print(f"labelled missions with doc+patterns+THEN: {len(rows)}  "
          f"(alive {int(sum(y))} / mess {int(len(y)-sum(y))}; {no_doc} skipped no-doc)\n")
    for key in ("acc_mean", "acc_max", "aliveness"):
        s = [r[key] for r in rows]
        a = auc(s, y); nm = null_auc(s, y)
        print(f"[{key:10}] AUC(alive>mess)={a:.3f}  null-95pct={nm:.3f}  "
              f"{'PASS' if a > nm else 'weak'}")
    accs = sorted(r["acc_mean"] for r in rows)
    print(f"\naccuracy(acc_mean) spread: min={accs[0]:.3f} med={accs[len(accs)//2]:.3f} "
          f"max={accs[-1]:.3f}  (token gate was median 0.00)")

    # anti-1=1 carried over: substantive vs trivial vs bloated (semantic)
    print("\n=== ANTI-1=1 CONTROL (semantic) ===")
    sub = max((r for r in rows if r["y"] == 1.0), key=lambda r: r["aliveness"])
    triv_want = "prove that x equals x, a trivially true statement"
    we = embed([triv_want])[0]; te = embed([sub["thens"][0]])
    t_acc = float(we @ te.mean(0)); t_cx = complexity([sub["then_pats"][0]])
    t_alv = t_acc - LAMBDA * t_cx
    bloat = list(dict.fromkeys(p for r in rows for p in r["applied"]))[:20]
    bthen = [t for t in (then_text(p) for p in bloat) if t]
    bwe = embed([sub["want"]])[0]; bte = embed(bthen)
    b_acc = float(bwe @ bte.mean(0)); b_cx = complexity(bloat); b_alv = b_acc - LAMBDA * b_cx
    print(f"  substantive [{sub['mission']}] aliveness={sub['aliveness']:+.3f} (acc={sub['acc_mean']:.3f} cx={sub['complexity']:.2f})")
    print(f"  trivial-want              aliveness={t_alv:+.3f} (acc={t_acc:.3f} cx={t_cx:.2f})")
    print(f"  bloated-shell ({len(bloat)} pat)     aliveness={b_alv:+.3f} (acc={b_acc:.3f} cx={b_cx:.2f})")
    print(f"  substantive > trivial : {'PASS' if sub['aliveness'] > t_alv else 'FAIL'}")
    print(f"  substantive > bloated : {'PASS' if sub['aliveness'] > b_alv else 'FAIL'}")

if __name__ == "__main__":
    main()
