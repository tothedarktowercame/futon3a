#!/usr/bin/env python3
"""cascade_construct.py — the cascade-construction CALLABLE (claude-4, post-verify, 2026-06-09).

Locked design (M-wm-policies, after claude-3's monotone-C verify; phylogeny-grounded 2026-06-10):
  construction = PHYLOGENY-GREEDY ordering + COVERAGE-SATURATION stop (the too-little floor).
  C (claude-3) ranks wholeness, monotone, does NOT bound size. The too-MUCH ceiling is a
  parsimony/BUDGET on the live-judge side — NOT applied here (claude-1 sets it from data).

construct_cascade(psi_query, epsilon) -> {
  :cascade  ordered [(pattern-id, relevance, marginal-coverage), ...]   (coverage-saturated)
  :size     k
  :wholeness  T (intensity = sum rel) × H (coherence, mean 4·s·(1-s)) = Alexander WHOLENESS
              (= Salingaros life L). NB this is NOT Salingaros disorder C = T·(10−H); the old key
              "C" was a misnomer (it returned T·H = L). Renamed 2026-06-24 (C/L are the same
              quantity under two bad names → one descriptive name).
  :H-coherence, :T-intensity, :coverage-reward, :prior-cost, :cascade-score
  :trajectory   the marginal-coverage curve (so you can SEE where saturation bites)
}

ORDER: greedy pick p maximizing marginal coverage  m'(p) = rel(p|psi) · (alpha + connectivity(p, chosen)) —
  prefers relevant members that grow along descent/co-application roads in the pattern phylogeny.
STOP:  when m(best) < epsilon  ("the argument is now expressed" — coverage saturated).
NO budget ceiling here by design.

Run (examples on real missions):  cd ~/code/futon3a &&
  .venv/bin/python3 holes/labs/M-memes-arrows/cascade_construct.py
"""
import json, math, sys
from pathlib import Path

ROOT = Path("/home/joe/code/futon3a")
EMB = {r["id"]: r["vector"] for r in json.load(open(ROOT/"resources/notions/minilm_pattern_embeddings.json"))}
DEFAULT_POSTERIORS = ROOT/"resources/notions/pattern_posteriors.self_graded.json"
DEFAULT_PHYLOGENY = Path("/home/joe/code/futon6/data/pattern-phylogeny-edges.json")
DEFAULT_LEARNED_PHYLOGENY = Path("/home/joe/code/futon6/data/pattern-phylogeny-learned.json")
DEFAULT_GROUNDED_POSTERIORS = Path("/home/joe/code/futon6/data/pattern_posteriors.grounded.json")
DEFAULT_SEEDS = Path("/home/joe/code/futon6/data/pattern-seeds.json")

# F-score (M-wm-policies omission 2, AIF grounding 2026-06-24): grain-2 cascade quality as a
# Engineering model-selection score: coverage reward minus a prior-derived
# inclusion cost. This is not variational free energy or marginal likelihood:
# coverage is a task proxy, not an expected log likelihood.
# of the selected patterns (the report's open-Q3 map: (10-H) "architectural entropy" -> KL of
# the ψ-conditioned selection from the co-application base-rate prior). lambda set from data
# (the rich/thin F-knee), NOT hand-guessed; this default is provisional pending the sweep.
DEFAULT_LAMBDA = 0.25   # data-set rich/thin F=0 knee (canon crosses 0.216, capability 0.287); re-confirm on full live distribution

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
    """Load grounded posteriors when present; otherwise fall back to self-graded."""
    p = DEFAULT_GROUNDED_POSTERIORS if path == DEFAULT_POSTERIORS and DEFAULT_GROUNDED_POSTERIORS.exists() else Path(path)
    if not p.exists():
        return {"label": "self-graded", "patterns": {}}
    data = json.load(open(p))
    if "patterns" in data:
        return data
    return {"label": "grounded-closure-folds", "patterns": data}

def posterior_mean(pid, posterior_table):
    patterns = (posterior_table or {}).get("patterns", {})
    row = patterns.get(pid) or patterns.get(pattern_stem(pid))
    return float(row.get("mean", 0.5)) if row else 0.5

def posterior_multiplier(pid, posterior_table, posterior_weight):
    """Compose posterior trust with marginal coverage; never replace m(p)."""
    if not posterior_weight:
        return 1.0
    centered = posterior_mean(pid, posterior_table) - 0.5
    return max(0.0, 1.0 + float(posterior_weight) * centered)

def pattern_stem(pid):
    """Match library ids like lib/stem to phylogeny ids keyed by stem."""
    return str(pid).split("/")[-1]

def load_phylogeny(path=DEFAULT_PHYLOGENY, learned_path=DEFAULT_LEARNED_PHYLOGENY, include_learned=True):
    p = Path(path)
    if not p.exists():
        return {"patterns": set(), "descent": set(), "co_app": {}}
    raw = json.load(open(p))
    co_app = {}
    for a, b, w in raw.get("co_app", []):
        w = float(w)
        co_app[(a, b)] = w
        co_app[(b, a)] = w
    descent = {tuple(edge[:2]) for edge in raw.get("descent", [])}
    if include_learned and Path(learned_path).exists():
        learned = json.load(open(learned_path))
        for edge in learned.get("descent", []):
            if len(edge) >= 2:
                descent.add(tuple(edge[:2]))
        for edge in learned.get("co_app", []):
            if len(edge) >= 3:
                a, b, w = edge[:3]
                w = float(w)
                co_app[(a, b)] = w
                co_app[(b, a)] = w
    return {
        "patterns": set(raw.get("patterns", [])),
        "descent": descent,
        "co_app": co_app,
    }

def load_seed_stems(path=DEFAULT_SEEDS):
    """Operator-designated seed patterns (E-live-loop-2 2c). Returns the set of
    pattern STEMS. Missing file => no seeds (empty set). A present-but-malformed
    registry raises: silently running unfloored would re-fine the seeds."""
    p = Path(path)
    if not p.exists():
        return frozenset()
    data = json.load(open(p))
    seeds = data["seeds"]
    ids = [s["id"] for s in seeds]
    assert ids and all(isinstance(i, str) and "/" in i for i in ids), f"malformed seed registry {p}: {seeds}"
    return frozenset(pattern_stem(i) for i in ids)

def base_rate_prior(phylogeny, floor_frac=0.1, seed_stems=frozenset()):
    """Per-pattern INCLUSION prior P(include p) from co-application mass — how EXPECTED p is
    (its unconditional usage). A Bernoulli inclusion probability in (0,1), NOT a categorical
    over all patterns: a categorical (mass/total) makes every -log P ≈ log N bits, swamping
    accuracy and collapsing F to size-1 (the degenerate 'no knee' failure). Here
    prior = (mass+f)/(mass+f+K), K = the median positive co-app mass (the typical scale),
    f = floor_frac·K (so unseen patterns are surprising-but-finite). Common patterns → ~1
    (cheap to include); rare/unseen → small (costly). complexity(p) = -log P(include p) = the
    bits to justify including p against the base rate, O(1) and commensurate with coverage.
    Returns (prior-by-stem, default-for-unseen)."""
    mass = {}
    for (a, _b), w in phylogeny.get("co_app", {}).items():
        mass[a] = mass.get(a, 0.0) + float(w)
    for p in phylogeny.get("patterns", set()):
        mass.setdefault(p, 0.0)
    pos = sorted(m for m in mass.values() if m > 0)
    K = pos[len(pos) // 2] if pos else 1.0          # median positive mass = the typical scale
    f = floor_frac * K
    prior = {p: (m + f) / (m + f + K) for p, m in mass.items()}
    if seed_stems:
        # Operator designation IS a prior statement (E-live-loop-2 2c): registered seeds
        # are floored at the MEDIAN pattern's inclusion prior (mass=K), not the unseen
        # default — "starts nearly every cascade" entered as the prior it claims to be.
        # max() makes the floor self-retiring: once learned co-app mass lifts the seed
        # above the median, the designation is inert. Non-seeds are untouched.
        seed_floor = (K + f) / (K + f + K)
        for s in seed_stems:
            prior[s] = max(prior.get(s, f / (f + K)), seed_floor)
    return prior, (f / (f + K))

def phylogeny_connectivity(pid, chosen, phylogeny):
    """Connectivity from candidate p to already chosen c, normalized for co-application."""
    p = pattern_stem(pid)
    score = 0.0
    for cid, _ in chosen:
        c = pattern_stem(cid)
        if (p, c) in phylogeny["descent"]:
            score += 1.0
        score += min(phylogeny["co_app"].get((p, c), 0.0), 5.0) / 5.0
    return score

def chosen_semi_lattice(ids, phylogeny):
    stems = {pattern_stem(pid): pid for pid in ids}
    descent = []
    co_app = []
    for a in sorted(stems):
        for b in sorted(stems):
            if a != b and (a, b) in phylogeny["descent"]:
                descent.append([stems[a], stems[b]])
    for i, a in enumerate(sorted(stems)):
        for b in sorted(stems)[i+1:]:
            w = phylogeny["co_app"].get((a, b), 0.0)
            if w:
                co_app.append([stems[a], stems[b], int(w)])
    return {"descent": descent, "co_app": co_app}

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

def construct_cascade(psi_query, epsilon=0.15, pool=40, posterior_weight=0.0, posterior_table=None,
                      phylogeny=None, alpha=0.3, lam=DEFAULT_LAMBDA):
    """|psi> = a mission/scope (query text). Returns the coverage-saturated phylogeny-greedy cascade."""
    posterior_table = posterior_table or {"label": "self-graded", "patterns": {}}
    phylogeny = phylogeny or load_phylogeny()
    rows = ranked_candidates(psi_query, pool, posterior_table)
    coverage_candidates = [
        {"pattern_id": row["pattern_id"], "stem": pattern_stem(row["pattern_id"]), "relevance": round(row["relevance"], 3)}
        for row in rows
        if pattern_stem(row["pattern_id"]) not in phylogeny["patterns"]
    ]
    cand = [
        (row["pattern_id"], row["relevance"])
        for row in rows
        if pattern_stem(row["pattern_id"]) in phylogeny["patterns"]
    ]
    chosen, traj = [], []
    while cand:
        # phylogeny-greedy: marginal coverage = rel * (alpha + connectivity to already-chosen)
        def marg(pr):
            pid, rel = pr
            base = rel * (float(alpha) + phylogeny_connectivity(pid, chosen, phylogeny))
            return base * posterior_multiplier(pid, posterior_table, posterior_weight)
        best = max(cand, key=marg); m = marg(best)
        if chosen and m < epsilon:          # coverage saturated -> stop (too-little floor)
            break
        chosen.append(best); cand.remove(best); traj.append((len(chosen), best[0], round(m, 3)))
    ids = [c for c, _ in chosen]
    # --- Honest engineering score: coverage reward - lambda * prior cost.
    prior, default_prior = base_rate_prior(phylogeny, seed_stems=load_seed_stems())
    accuracy = sum(m for _, _, m in traj)                               # total ψ-coverage
    complexity = sum(-math.log(prior.get(pattern_stem(c), default_prior)) for c, _ in chosen)
    cascade_score = accuracy - lam * complexity
    intensity = sum(rel for _, rel in chosen)                           # Salingaros T
    pairs = [(i, j) for i in range(len(ids)) for j in range(i+1, len(ids))]
    coherence = (sum(4*cos(EMB[ids[i]], EMB[ids[j]])*(1-cos(EMB[ids[i]], EMB[ids[j]])) for i, j in pairs)/len(pairs)) if pairs else 1.0
    semi_lattice = chosen_semi_lattice(ids, phylogeny)
    edge_count = len(semi_lattice["descent"]) + len(semi_lattice["co_app"])
    possible_edges = (len(ids) * (len(ids) - 1)) / 2
    edge_density = (edge_count / possible_edges) if possible_edges else 0.0
    coverage_gap = len(coverage_candidates) > len(ids)
    low_connectivity = edge_count == 0 or (coverage_gap and edge_density < 0.45)
    return {"cascade": [(c, round(r, 3), mc) for (c, r), (_, _, mc) in zip(chosen, traj)],
            "size": len(chosen), "wholeness": round(intensity*coherence, 3),
            "H-coherence": round(coherence, 3), "T-intensity": round(intensity, 3),
            "coverage-reward": round(accuracy, 3), "prior-cost": round(complexity, 3),
            "cascade-score": round(cascade_score, 3), "lambda": float(lam),
            "trajectory": traj,
            "posterior_weight": float(posterior_weight),
            "posterior_label": posterior_table.get("label", "self-graded"),
            "semi-lattice": semi_lattice,
            "non-phylogeny": sorted({c["stem"] for c in coverage_candidates}),
            "coverage-candidates": coverage_candidates,
            "phylogeny": {"alpha": float(alpha), "edge_count": edge_count,
                          "edge_density": round(edge_density, 3),
                          "coverage_gap": coverage_gap,
                          "low_connectivity": low_connectivity}}

def print_cascade(name, query):
    r = construct_cascade(query, epsilon=0.15)
    print(f"[{name}]  size={r['size']}  wholeness={r['wholeness']} (intensity={r['T-intensity']} x coherence={r['H-coherence']})  phylo-edges={r['phylogeny']['edge_count']}  edge-density={r['phylogeny']['edge_density']}")
    if r["phylogeny"]["low_connectivity"]:
        print("    LOW-CONNECTIVITY/COVERAGE-GAP: sparse selected graph or more embedding hits outside the phylogeny than inside it")
    print("    cascade:")
    for k, pid, mc in r["trajectory"]:
        print(f"      {k}. {pid:<46} marginal-coverage={mc}")
    print(f"    semi-lattice: {json.dumps(r['semi-lattice'], sort_keys=True)}")
    print(f"    non-phylogeny: {json.dumps(r['non-phylogeny'])}")
    print()

def print_learned_comparison():
    query = "model recompute schedule prototype maturity lifecycle cadence hook forward model beta posterior"
    computed = construct_cascade(query, epsilon=0.15, phylogeny=load_phylogeny(include_learned=False))
    learned = construct_cascade(query, epsilon=0.15)
    print("\n=== learned-overlay downstream comparison ===\n")
    print("[kit-cadence query]")
    print("computed-only:")
    print(f"  size={computed['size']} C={computed['C']} edges={computed['phylogeny']['edge_count']}")
    print(f"  cascade={[pid for _, pid, _ in computed['trajectory']]}")
    print(f"  semi-lattice={json.dumps(computed['semi-lattice'], sort_keys=True)}")
    print("computed+learned:")
    print(f"  size={learned['size']} C={learned['C']} edges={learned['phylogeny']['edge_count']}")
    print(f"  cascade={[pid for _, pid, _ in learned['trajectory']]}")
    print(f"  semi-lattice={json.dumps(learned['semi-lattice'], sort_keys=True)}")
    print()

if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "demo":
        DEMOS = {
            "kit-outbox":
                "staged outbox pipeline: daily-scan to interest-network match to cold EOI draft",
            "inv-tripwire":
                "map each aif2 INV invariant to its tripwire detector",
        }
        print("\n=== phylogeny-grounded cascades demo (epsilon=0.15; alpha=0.3) ===\n")
        for name, q in DEMOS.items():
            print_cascade(name, q)
        raise SystemExit(0)
    if len(sys.argv) > 1 and sys.argv[1] == "learned-demo":
        print_learned_comparison()
        raise SystemExit(0)

    # A couple of REAL missions of differing character — so claude-1 can eyeball coverage-saturation sizes.
    MISSIONS = {
        "BROAD: interim-director proxy-metrics":
            "proxy metric inventory value-generating arms ingest retrieve process evaluate report external uptake internal mastery pipeline health observation channels scaffold business evidence",
        "FOCUSED: AIF/EFE policy selection":
            "active inference expected free energy policy selection belief update observation vector precision action candidate ranking",
        "TECHNICAL: substrate ground-metric":
            "ground metric ollivier ricci curvature wasserstein fisher rao latent distance substrate tension field differentiable",
    }
    print("\n=== coverage-saturated phylogeny-greedy cascades (epsilon=0.15; NO budget ceiling) ===\n")
    for name, q in MISSIONS.items():
        print_cascade(name, q)
    print("NOTE: size is set by coverage-saturation alone (the too-little floor). The too-much")
    print("ceiling (parsimony budget) is claude-1's live-judge layer, set from these observed sizes.")
