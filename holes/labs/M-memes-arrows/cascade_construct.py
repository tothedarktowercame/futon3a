#!/usr/bin/env python3
"""cascade_construct.py — the cascade-construction CALLABLE (claude-4, post-verify, 2026-06-09).

Locked design (M-wm-policies, after claude-3's monotone-C verify):
  construction = COHERENCE-GREEDY ordering + COVERAGE-SATURATION stop (the too-little floor).
  C (claude-3) ranks wholeness, monotone, does NOT bound size. The too-MUCH ceiling is a
  parsimony/BUDGET on the live-judge side — NOT applied here (claude-1 sets it from data).

construct_cascade(psi_query, epsilon) -> {
  :cascade  ordered [(pattern-id, relevance, marginal-coverage), ...]   (coverage-saturated)
  :size     k
  :C        real-Salingaros C = T (intensity = sum rel) × H (coherent-harmony, mean 4·s·(1-s))
  :H, :T
  :trajectory   the marginal-coverage curve (so you can SEE where saturation bites)
}

ORDER: greedy pick p maximizing marginal coverage  m(p) = rel(p|psi) · (1 - max_sim(p, chosen)) —
  prefers relevant + coherently-distinct members (the coherence-greedy choice).
STOP:  when m(best) < epsilon  ("the argument is now expressed" — coverage saturated).
NO budget ceiling here by design.

Run (examples on real missions):  cd ~/code/futon3a &&
  .venv/bin/python3 holes/labs/M-memes-arrows/cascade_construct.py
"""
import json, math
from pathlib import Path

ROOT = Path("/home/joe/code/futon3a")
EMB = {r["id"]: r["vector"] for r in json.load(open(ROOT/"resources/notions/minilm_pattern_embeddings.json"))}
DEFAULT_POSTERIORS = ROOT/"resources/notions/pattern_posteriors.self_graded.json"

def cos(a, b):
    d = sum(x*y for x, y in zip(a, b)); na = math.sqrt(sum(x*x for x in a)); nb = math.sqrt(sum(y*y for y in b))
    return d/(na*nb) if na and nb else 0.0

_MODEL = None
def _embed(text):
    global _MODEL
    if _MODEL is None:
        from sentence_transformers import SentenceTransformer
        _MODEL = SentenceTransformer("sentence-transformers/all-MiniLM-L6-v2")
    return [float(x) for x in _MODEL.encode([text], normalize_embeddings=True)[0]]

def load_posteriors(path=DEFAULT_POSTERIORS):
    """Load self-graded pattern posteriors. Missing file = no posterior signal."""
    p = Path(path)
    if not p.exists():
        return {"label": "self-graded", "patterns": {}}
    return json.load(open(p))

def posterior_mean(pid, posterior_table):
    row = (posterior_table or {}).get("patterns", {}).get(pid)
    return float(row.get("mean", 0.5)) if row else 0.5

def posterior_multiplier(pid, posterior_table, posterior_weight):
    """Compose posterior trust with marginal coverage; never replace m(p)."""
    if not posterior_weight:
        return 1.0
    centered = posterior_mean(pid, posterior_table) - 0.5
    return max(0.0, 1.0 + float(posterior_weight) * centered)

def ranked_candidates(psi_query, pool=40, posterior_table=None):
    """Embedding-ranked pool plus posterior rank; used for the A/B disagreement surface."""
    qv = _embed(psi_query)
    ranked = sorted(((cos(qv, v), pid) for pid, v in EMB.items()), reverse=True)[:pool]
    rows = [
        {
            "pattern_id": pid,
            "embedding_rank": i + 1,
            "relevance": rel,
            "posterior_mean": posterior_mean(pid, posterior_table),
            "posterior_n": int(((posterior_table or {}).get("patterns", {}).get(pid) or {}).get("n", 0)),
            "label": (posterior_table or {}).get("label", "self-graded"),
        }
        for i, (rel, pid) in enumerate(ranked)
    ]
    posterior_order = {
        row["pattern_id"]: i + 1
        for i, row in enumerate(sorted(rows, key=lambda r: (-r["posterior_mean"], r["embedding_rank"])))
    }
    for row in rows:
        row["posterior_rank"] = posterior_order[row["pattern_id"]]
    return rows

def construct_cascade(psi_query, epsilon=0.15, pool=40, posterior_weight=0.0, posterior_table=None):
    """|psi> = a mission/scope (query text). Returns the coverage-saturated coherence-greedy cascade."""
    posterior_table = posterior_table or {"label": "self-graded", "patterns": {}}
    cand = [(row["pattern_id"], row["relevance"]) for row in ranked_candidates(psi_query, pool, posterior_table)]
    chosen, traj = [], []
    while cand:
        # coherence-greedy: marginal coverage = rel * (1 - max similarity to already-chosen)
        def marg(pr):
            pid, rel = pr
            mx = max([cos(EMB[pid], EMB[c]) for c, _ in chosen] or [0.0])
            base = rel * (1.0 - mx)
            return base * posterior_multiplier(pid, posterior_table, posterior_weight)
        best = max(cand, key=marg); m = marg(best)
        if chosen and m < epsilon:          # coverage saturated -> stop (too-little floor)
            break
        chosen.append(best); cand.remove(best); traj.append((len(chosen), best[0], round(m, 3)))
    ids = [c for c, _ in chosen]
    T = sum(rel for _, rel in chosen)                                   # intensity
    pairs = [(i, j) for i in range(len(ids)) for j in range(i+1, len(ids))]
    H = (sum(4*cos(EMB[ids[i]], EMB[ids[j]])*(1-cos(EMB[ids[i]], EMB[ids[j]])) for i, j in pairs)/len(pairs)) if pairs else 1.0
    return {"cascade": [(c, round(r, 3), mc) for (c, r), (_, _, mc) in zip(chosen, traj)],
            "size": len(chosen), "C": round(T*H, 3), "H": round(H, 3), "T": round(T, 3),
            "trajectory": traj,
            "posterior_weight": float(posterior_weight),
            "posterior_label": posterior_table.get("label", "self-graded")}

if __name__ == "__main__":
    # A couple of REAL missions of differing character — so claude-1 can eyeball coverage-saturation sizes.
    MISSIONS = {
        "BROAD: interim-director proxy-metrics":
            "proxy metric inventory value-generating arms ingest retrieve process evaluate report external uptake internal mastery pipeline health observation channels scaffold business evidence",
        "FOCUSED: AIF/EFE policy selection":
            "active inference expected free energy policy selection belief update observation vector precision action candidate ranking",
        "TECHNICAL: substrate ground-metric":
            "ground metric ollivier ricci curvature wasserstein fisher rao latent distance substrate tension field differentiable",
    }
    print("\n=== coverage-saturated coherence-greedy cascades (epsilon=0.15; NO budget ceiling) ===\n")
    for name, q in MISSIONS.items():
        r = construct_cascade(q, epsilon=0.15)
        print(f"[{name}]  size={r['size']}  C={r['C']} (T={r['T']} × H={r['H']})")
        for k, pid, mc in r["trajectory"]:
            print(f"    {k}. {pid:<46} marginal-coverage={mc}")
        print()
    print("NOTE: size is set by coverage-saturation alone (the too-little floor). The too-much")
    print("ceiling (parsimony budget) is claude-1's live-judge layer, set from these observed sizes.")
