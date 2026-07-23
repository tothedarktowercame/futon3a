#!/usr/bin/env python3
"""offramp_cascade.py — the outer->inner offramp's cascade stage.
Mint the have->want MAGNET from a mission's IDENTIFY prose (not the thin WM
banner), pick the right-sized cascade by argmax-F (Bayesian-Occam; F>0 gate),
and emit the cascade + its semilattice (descent=BV.seq, co_app=BV.copar) as JSON
for the Clojure semilattice-fold. Usage: offramp_cascade.py <mission-doc.md> [out.json]"""
import sys, re, collections
sys.path.insert(0, "/home/joe/code/futon3a/holes/labs/M-memes-arrows")
from cascade_construct import construct_cascade, chosen_semi_lattice, load_phylogeny, pattern_stem

EPS = [0.02, 0.08, 0.12, 0.15, 0.18, 0.22, 0.30, 0.45]

def identify_psi(path, cap=1200):
    """The magnet: prefer the '## 1. IDENTIFY' section body (the actual sorry),
    falling back to the doc's leading prose. Strip headers/metadata/path-bullets."""
    txt = open(path, encoding="utf-8").read()
    m = re.search(r"(?ims)^##\s+(?:\d+\.\s*)?IDENTIFY\b(.*?)(?=^##\s|\Z)", txt)
    body = m.group(1) if m else txt
    lines = []
    for ln in body.splitlines():
        s = ln.strip()
        if not s or s.startswith("#") or s.startswith("**") or s.startswith("|"):
            continue
        if re.match(r"^[-*]\s", s) and ("/" in s or ".md" in s):
            continue
        if re.match(r"^(Status|Date|Parent|Cross-refs?|Owner)\b", s):
            continue
        lines.append(s)
        if sum(len(x) for x in lines) > cap:
            break
    return " ".join(lines)[:cap]

def main():
    doc = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else "/tmp/offramp-cascade.json"
    psi = identify_psi(doc)
    phy = load_phylogeny()
    best = None
    for e in EPS:
        r = construct_cascade(psi, epsilon=e, phylogeny=phy)
        if best is None or r["cascade-score"] > best[1]:
            best = (e, r["cascade-score"], r)
    eps, F, r = best
    ids = [p for (p, _rl, _mc) in r["cascade"]]
    sl = chosen_semi_lattice(ids, phy)
    def ns(pid): return pattern_stem(pid).split("/")[0]
    xns = sum(1 for a, b, _ in sl["co_app"] if ns(a) != ns(b))
    payload = {
        "psi_chars": len(psi),
        "epsilon_star": eps,
        "F": round(F, 4),
        "size": r["size"],
        "wholeness": r["wholeness"],
        "shown": [pattern_stem(p) for p in ids],
        "semilattice": {"descent": [[pattern_stem(a), pattern_stem(b)] for a, b in sl["descent"]],
                        "co_app": [[pattern_stem(a), pattern_stem(b), w] for a, b, w in sl["co_app"]]},
        "cross_ns_meets": f"{xns}/{len(sl['co_app'])}",
        "gate": "F>0 PASS" if F > 0 else "F<=0 WEAK-MAGNET (abstain)",
    }
    def edn(x):
        if isinstance(x, bool): return "true" if x else "false"
        if isinstance(x, str): return '"' + x.replace('\\', '\\\\').replace('"', '\\"') + '"'
        if isinstance(x, (list, tuple)): return "[" + " ".join(edn(v) for v in x) + "]"
        if isinstance(x, dict): return "{" + " ".join(f":{k} {edn(v)}" for k, v in x.items()) + "}"
        return str(x)
    open(out, "w").write(edn(payload) + "\n")
    print(f"[offramp-cascade] F={payload['F']} size={payload['size']} "
          f"wholeness={payload['wholeness']} seq={len(payload['semilattice']['descent'])} "
          f"copar={len(payload['semilattice']['co_app'])} gate=({payload['gate']}) -> {out}")

if __name__ == "__main__":
    main()
