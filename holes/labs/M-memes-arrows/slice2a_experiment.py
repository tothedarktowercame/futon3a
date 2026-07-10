#!/usr/bin/env python3
"""slice2a_experiment.py — SLICE 2a eval (claude-1 build / claude-2 review).

Spec: futon2/holes/M-G-over-cascades.md "Slice-2a — re-scoped build spec". Reconstruct held-out missions'
:applied sets via a discharge-trained, management-anchored, want-directed BEAM over the stack semilattice
(constellations + co_app/descent), and check it (i) beats slice-1's 25% recovery, (ii) reproduces the
alive anchor-signature, (iii) beats two null controls (label-shuffle-trained prior, random prior).

DISCIPLINE: hyperparameters are fixed A PRIORI (below) and NOT tuned on the held-out folds. The
discharge-trained prior is trained ONLY on each fold's TRAIN missions. k-fold so every labelled mission is
held out once. Discharge label = mission-wholeness :class (alive/mess), computed over scope-tree structure
(non-circular w.r.t. patterns). EDN is read via babashka (no python edn lib); phylogeny is already JSON.

Run: cd ~/code/futon3a && .venv/bin/python3 holes/labs/M-memes-arrows/slice2a_experiment.py
"""
import subprocess, json, random, statistics
from pathlib import Path
from cascade_semilattice import (slug, membership_map, move_graph, anchor, management_anchored,
                                 train_logodds, beam_rollout, MGMT_CONSTELLATIONS)

ROOT = Path("/home/joe/code")
# --- fixed a-priori hyperparameters (NOT tuned on held-out) ---
W_COS, W_TRAIN, W_ANCHOR = 1.0, 1.0, 0.5
N_SEEDS, BEAM_B, BEAM_K, ALPHA = 4, 12, 5, 0.5
N_FOLDS, RANDOM_REPS, SEED = 5, 20, 20260623
SLICE1_RECALL = 0.25


def load_edn(path):
    """EDN -> python via babashka (cheshire). Read-only over the source artifacts."""
    code = ('(require (quote [cheshire.core :as json])) '
            f'(print (json/generate-string (clojure.edn/read-string (slurp "{path}"))))')
    out = subprocess.run(["bb", "-e", code], capture_output=True, text=True, check=True)
    return json.loads(out.stdout)


def build():
    scopes = load_edn(ROOT / "futon6/data/mission-pattern-scopes.edn")["missions"]
    whole = load_edn(ROOT / "futon6/data/mission-wholeness.edn")["missions"]
    semi = load_edn(ROOT / "futon3c/holes/excursions/pipeline-semilattice-clusters.edn")
    phylo = json.load(open(ROOT / "futon6/data/pattern-phylogeny-edges.json"))
    s2c, coll = membership_map(semi["pattern-membership"])
    graph = move_graph(phylo)
    nodes = sorted(graph)
    label = {m["mission"]: m["class"] for m in whole}
    # join: only missions with a label and a non-empty :applied are evaluable
    missions = []
    for m in scopes:
        cls = label.get(m["mission"])
        applied = [slug(p) for p in m["applied"]]
        cand = {slug(c["pattern"]): float(c["cos"]) for c in m["try-candidates"]}
        if cls and applied:
            missions.append({"id": m["mission"], "class": cls, "applied": applied, "cos": cand})
    return missions, s2c, coll, graph, nodes


def score_fn_for(cosd, logodds, s2c):
    """Want-directed move-prior over a sub-path: base cos (entry retrieval) + discharge-trained log-odds
    + a management-anchor bias on the whole path (reward sub-paths anchored in the stack-meta region)."""
    def sf(path):
        s = sum(W_COS * cosd.get(p, 0.0) + W_TRAIN * logodds.get(p, 0.0) for p in path)
        if management_anchored(path, s2c):
            s += W_ANCHOR
        return s
    return sf


def seeds_for(m, graph):
    """Retrieval entry points: the mission's top-cos try-candidates that exist in the move graph."""
    ranked = sorted(m["cos"].items(), key=lambda kv: -kv[1])
    return [p for p, _ in ranked if p in graph][:N_SEEDS]


def recall(R, applied):
    return len(set(R) & set(applied)) / len(applied) if applied else 0.0


def recover(m, graph, logodds, s2c):
    seeds = seeds_for(m, graph)
    if not seeds:
        return []
    return beam_rollout(seeds, graph, score_fn_for(m["cos"], logodds, s2c), B=BEAM_B, K=BEAM_K)


def folds(missions, k):
    ms = sorted(missions, key=lambda m: m["id"])
    return [[m for j, m in enumerate(ms) if j % k == i] for i in range(k)]


def kfold_eval(missions, graph, nodes, s2c, prior_builder, rng):
    """Run k-fold; prior_builder(train_missions)->logodds dict. Returns per-mission recalls + anchor info."""
    fs = folds(missions, N_FOLDS)
    recalls, perpat, rnd, anchors, fails = [], [], [], [], 0
    for i in range(N_FOLDS):
        test = fs[i]
        train = [m for j, f in enumerate(fs) if j != i for m in f]
        logodds = prior_builder(train)
        for m in test:
            R = recover(m, graph, logodds, s2c)
            if not R:
                fails += 1
            recalls.append(recall(R, m["applied"]))
            # per-pattern baseline (slice-1 style): top-|applied| try-candidates by cos, no graph/training
            pp = [p for p, _ in sorted(m["cos"].items(), key=lambda kv: -kv[1])][:len(m["applied"])]
            perpat.append(recall(pp, m["applied"]))
            # random baseline: |R| random graph nodes, averaged
            k = max(1, len(R))
            rnd.append(statistics.mean(recall(rng.sample(nodes, k), m["applied"]) for _ in range(RANDOM_REPS)))
            anchors.append((m["class"], management_anchored(R, s2c) if R else False))
    return {"recall": recalls, "perpat": perpat, "random": rnd, "anchors": anchors, "fails": fails}


def loo_diagnostic(missions, graph, nodes, s2c, rng):
    """Leave-one-out WITHIN :applied (claude-1 addition, flagged). Bypasses the disjoint try-candidates by
    seeding from the mission's OWN applied patterns: given k-1 applied, can the beam recover the held-out
    k-th via the cascade's internal co_app structure + the trained prior? Isolates whether a cascade is a
    coherent structured object with recoverable members — separate from the broken retrieval seed.
    NB trained on ALL missions: this is a STRUCTURE probe, not the held-out generalisation claim."""
    logodds = trained_builder(missions)
    hits = tot = 0
    rnd = []
    for m in missions:
        ap = m["applied"]
        if len(ap) < 2:
            continue
        for held in ap:
            seeds = [p for p in ap if p != held and p in graph]
            if not seeds:
                continue
            R = beam_rollout(seeds, graph, score_fn_for({}, logodds, s2c), B=BEAM_B, K=BEAM_K)
            tot += 1
            hits += (held in set(R))
            k = max(1, len(R))
            rnd.append(statistics.mean((held in set(rng.sample(nodes, k))) for _ in range(RANDOM_REPS)))
    return (hits / tot if tot else 0.0), (statistics.mean(rnd) if rnd else 0.0), tot


def trained_builder(train):
    alive = [m["applied"] for m in train if m["class"] == "alive"]
    mess = [m["applied"] for m in train if m["class"] == "mess"]
    return train_logodds(alive, mess, ALPHA)


def shuffled_builder(rng):
    def b(train):
        labels = [m["class"] for m in train]
        rng.shuffle(labels)
        alive = [m["applied"] for m, c in zip(train, labels) if c == "alive"]
        mess = [m["applied"] for m, c in zip(train, labels) if c == "mess"]
        return train_logodds(alive, mess, ALPHA)
    return b


def random_prior_builder(rng):
    # a prior with NO discharge signal: random per-pattern weights (same scale as log-odds)
    def b(train):
        return {p: rng.gauss(0, 1) for m in train for p in m["applied"]}
    return b


def main():
    missions, s2c, coll, graph, nodes = build()
    cls = {}
    for m in missions:
        cls[m["class"]] = cls.get(m["class"], 0) + 1
    print(f"evaluable missions (labelled ∧ |applied|≥1): {len(missions)}  by class {cls}")
    print(f"move graph: {len(nodes)} nodes | bare-name collisions handled: {len(coll)} {list(coll)}")
    print(f"a-priori params: w_cos={W_COS} w_train={W_TRAIN} w_anchor={W_ANCHOR} "
          f"seeds={N_SEEDS} B={BEAM_B} K={BEAM_K} folds={N_FOLDS} (NOT tuned on held-out)\n")

    rng = random.Random(SEED)
    trained = kfold_eval(missions, graph, nodes, s2c, trained_builder, rng)
    shuffled = kfold_eval(missions, graph, nodes, s2c, shuffled_builder(random.Random(SEED + 1)), rng)
    rnd_prior = kfold_eval(missions, graph, nodes, s2c, random_prior_builder(random.Random(SEED + 2)), rng)

    def stat(xs):
        return statistics.mean(xs), (statistics.stdev(xs) if len(xs) > 1 else 0.0)

    n = len(trained["recall"])
    mr, sr = stat(trained["recall"]); mp, sp = stat(trained["perpat"]); mrand, _ = stat(trained["random"])
    msh, _ = stat(shuffled["recall"]); mrp, _ = stat(rnd_prior["recall"])
    print(f"=== (i) HELD-OUT RECOVERY (k-fold, n={n} held-out missions) ===")
    print(f"  trained beam   recall = {mr:.2%}  (sd {sr:.2f})   [slice-1 ref = {SLICE1_RECALL:.0%}]")
    print(f"  per-pattern    recall = {mp:.2%}  (sd {sp:.2f})   <- slice-1-style cos top-|U|, no graph/train")
    print(f"  random         recall = {mrand:.3%}")
    print(f"  trained beam unreachable (no seed/empty): {trained['fails']}/{n}")
    print(f"\n=== (iii) NULL CONTROLS ===")
    print(f"  null label-shuffle prior recall = {msh:.2%}   (trained-minus-shuffle = {mr-msh:+.2%})")
    print(f"  null random      prior recall = {mrp:.2%}   (trained-minus-random  = {mr-mrp:+.2%})")

    print(f"\n=== (ii) ANCHOR-SIGNATURE reproduction (recovered sub-paths) ===")
    for c in ("alive", "mess"):
        rows = [ok for cl, ok in trained["anchors"] if cl == c]
        rate = (sum(rows) / len(rows)) if rows else 0.0
        print(f"  {c:5}: recovered sub-path management-anchored {sum(rows)}/{len(rows)} = {rate:.0%}")

    loo, loo_rnd, loo_n = loo_diagnostic(missions, graph, nodes, s2c, random.Random(SEED + 3))
    print(f"\n=== LOO STRUCTURE PROBE (claude-1 addition: seed from the mission's OWN applied, recover a held-out member) ===")
    print(f"  leave-one-out recall = {loo:.2%} vs random {loo_rnd:.2%}  (n={loo_n} held-out members)")
    print(f"  -> isolates whether a cascade's internal co_app structure predicts its own members "
          f"(bypasses the disjoint retrieval seed).")

    print(f"\n=== VERDICT (slice 2a) ===")
    beats_s1 = mr > SLICE1_RECALL
    beats_nulls = (mr > msh) and (mr > mrp)
    print(f"  beats slice-1 25%: {beats_s1} ({mr:.0%}) | beats BOTH nulls: {beats_nulls} "
          f"(shuffle {msh:.0%}, random {mrp:.0%})")
    a_rows = [ok for cl, ok in trained["anchors"] if cl == "alive"]
    m_rows = [ok for cl, ok in trained["anchors"] if cl == "mess"]
    ar = sum(a_rows)/len(a_rows) if a_rows else 0; mm = sum(m_rows)/len(m_rows) if m_rows else 0
    print(f"  alive recovered paths management-anchored ({ar:.0%}) vs mess ({mm:.0%}): "
          f"{'reproduced' if ar > mm else 'NOT reproduced'}")
    print("  honest edges: see sd, unreachable count, and per-class anchor n above. label = Salingaros")
    print("  alive/mess (structural proxy for 'went well', not ground-truth success). No held-out tuning.")


if __name__ == "__main__":
    main()
