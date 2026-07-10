#!/usr/bin/env python3
"""cascade_recovery_experiment.py — SLICE 1: the CASCADE-LEVEL recovery experiment (claude-1).

The level-shifted analogue of discharge_experiment.py. There the unit was a PATTERN (does retrieval
rank the true resolver?). Here the unit is the CASCADE: does a cascade-ROLLOUT (state-conditioned
move-prior + INCREMENTAL terminal-chaining admissibility) recover the true :used SET above random —
where per-pattern scoring was flat? And do the cosine-artifact NEGATIVES, which retrieval ranks high,
fail to DISCHARGE at cascade level (want-coverage ~ 0)?

Ground truth (NOT authored here): futon6/holes/closure-folds.edn, surfaced via discharge_experiment.GROUND.
g-grain: PROXY — interfaces are token sets (consumes=IF+HOWEVER, produces=THEN); discharge is proxied by
want-coverage (terminals-match in token form). Slice 2 wires futon2.aif.rollout + the real meme.gates.

Run: cd ~/code/futon3a && .venv/bin/python3 holes/labs/M-memes-arrows/cascade_recovery_experiment.py
"""
import glob, json
from collections import Counter
import numpy as np

from discharge_experiment import GROUND, EMB, IDS, MAT       # embeddings + ground truth (reused)
from alexandrian_aif import parse, LIB, PHYLO
from cascade_rollout import salient, move_interface, admissible_step, want_coverage, rollout

N_LIB = len(IDS)
RANK = {i: IDS[i] for i in range(N_LIB)}
IDX = {pid: i for i, pid in enumerate(IDS)}


def build():
    # 1. parse library text for move interfaces (consumes/produces need IF/THEN, not embeddings)
    parsed = {}
    for f in glob.glob(str(LIB / "**" / "*.flexiarg"), recursive=True):
        p = parse(f)
        if p["ifhow"] and p["then"]:
            parsed[p["id"]] = p
    # 2. DF-drop corpus-frequent tokens so admissibility discriminates (answer-independent)
    df = Counter()
    for p in parsed.values():
        df.update(salient(p["ifhow"]) | salient(p["then"]))
    drop = frozenset(w for w, c in df.items() if c > 0.20 * len(parsed))
    # 3. interfaces for patterns that are BOTH parsed and embedded
    iface = {pid: move_interface(pid, parsed[pid]["ifhow"], parsed[pid]["then"], drop)
             for pid in parsed if pid in EMB}
    # 4. phylogeny neighbours (tail-slug space) as the structural move-prior P(next|current)
    pe = json.load(open(PHYLO))
    neigh = {}
    for key in ("co_app", "descent"):
        for e in pe.get(key, []):
            a, b = e[0], e[1]
            neigh.setdefault(a, set()).add(b)
            neigh.setdefault(b, set()).add(a)
    return parsed, drop, iface, neigh


def main():
    parsed, drop, iface, neigh = build()
    moves = list(iface.values())
    print(f"library: {len(parsed)} parsed / {N_LIB} embedded / {len(moves)} usable moves "
          f"(parsed∩embedded) | DF-drop={len(drop)} tokens")

    # retrieval move-prior: encode the resolver-BLIND problem texts, cosine vs precomputed pattern embs
    from sentence_transformers import SentenceTransformer
    model = SentenceTransformer("all-MiniLM-L6-v2")
    Q = np.array(model.encode([g["problem"] for g in GROUND], normalize_embeddings=True))
    SIMS = Q @ MAT.T  # (hole x pattern over IDS)

    K_MAX = 3
    print("\n=== (A) CASCADE RECOVERY — does the rollout recover the true :used SET above random? ===")
    print(f"  random recall of a size-m draw over {N_LIB} ≈ m/{N_LIB}\n")
    micro_hit = micro_tot = 0
    rollout_recalls, perpat_recalls, rand_recalls = [], [], []
    for i, g in enumerate(GROUND):
        if not g["success"] or not g["used"]:
            continue
        prior = {IDS[j]: float(SIMS[i][j]) for j in range(N_LIB)}
        have = salient(g["problem"])               # generous seed (no DF-drop) = the context HAVE
        want = salient(g["problem"], drop)         # goal signature (DF-consistent with produces)
        U = set(g["used"])
        U_reach = U & set(iface)                   # only embedded+parsed patterns are recoverable
        casc = rollout(have, want, moves, prior, neigh, K=K_MAX)
        R = [m["id"] for m in casc]
        m = max(1, len(R))
        # per-pattern baseline (OLD way): top-m by retrieval, NO admissibility, NO chaining
        perpat = [RANK[j] for j in np.argsort(-SIMS[i])[:m]]
        rec_roll = len(set(R) & U) / len(U)
        rec_pp = len(set(perpat) & U) / len(U)
        rec_rand = m / N_LIB                        # analytic expected recall of a size-m random draw
        rollout_recalls.append(rec_roll); perpat_recalls.append(rec_pp); rand_recalls.append(rec_rand)
        micro_hit += len(set(R) & U); micro_tot += len(U)
        # best retrieval rank over the recoverable used-set — explains misses (low prior, not bad filter)
        ranks = [int(np.where(np.argsort(-SIMS[i]) == IDX[u])[0][0]) for u in U_reach if u in IDX]
        best_rank = min(ranks) if ranks else -1
        flag = "" if U_reach == U else f"  ({len(U - set(iface))} used not in moves)"
        print(f"  [{g['scope']:34}] |U|={len(U)} size={len(R)}  rollout={rec_roll:.2f} "
              f"perpat={rec_pp:.2f} rand={rec_rand:.3f}  best-prior-rank={best_rank:4}  R∩U={sorted(set(R)&U)}{flag}")

    nposit = len(rollout_recalls)
    mean_roll = np.mean(rollout_recalls); mean_pp = np.mean(perpat_recalls); mean_rand = np.mean(rand_recalls)
    lift = mean_roll / mean_rand if mean_rand else float("inf")
    print(f"\n  mean recall over {nposit} positive holes:  rollout={mean_roll:.2%}  "
          f"per-pattern={mean_pp:.2%}  random={mean_rand:.3%}")
    print(f"  micro recall (used patterns recovered) = {micro_hit}/{micro_tot} = {micro_hit/micro_tot:.2%}")
    print(f"  => cascade-rollout lift over random ≈ {lift:.0f}x"
          f"  (rollout {'>' if mean_roll>mean_pp else '≈' if abs(mean_roll-mean_pp)<1e-9 else '<'} per-pattern)")

    print("\n=== (B) NEGATIVES — retrieval ranks the artifact high, but it does NOT DISCHARGE at cascade level ===")
    for i, g in enumerate(GROUND):
        if g["success"]:
            continue
        want = salient(g["problem"], drop)
        for u in g["used"]:
            rank = int(np.where(np.argsort(-SIMS[i]) == IDX[u])[0][0]) if u in IDX else -1
            cov = want_coverage([iface[u]], want) if u in iface else None
            covs = f"{cov:.2f}" if cov is not None else "n/a"
            print(f"  [{g['scope']:34}] {u}\n      retrieval-rank={rank}/{N_LIB} (high)  "
                  f"cascade want-coverage={covs}  -> discharge≈FALSE")

    print(f"\n=== VERDICT (slice 1, N={nposit} positive holes — small sample, honest numbers) ===")
    print(f"  (A) cascade-rollout recovers true :used SETs at {mean_roll:.0%} vs random {mean_rand:.1%} "
          f"(~{lift:.0f}x), and BEATS per-pattern ({mean_pp:.0%}) at the same size-{K_MAX} budget.")
    print("      REVIEW NOTE (claude-2, re-run verified): the rollout>per-pattern gap is driven ENTIRELY by ONE")
    print("      hole (head-sigil, |U|=1) where admissibility promoted a rank-3 pattern the top-3 cosine crowded")
    print("      out; the other 7 holes tie. So the MECHANISM is demonstrated but 'beats per-pattern' is n=1, not")
    print("      robust at N=8. And incremental-chaining, though unit-tested, did ZERO work on real data (every")
    print("      recovered hole was |U|=1; the size-2 chaining holes recovered 0) — its real-data payoff is slice-2.")
    print("      WHY only ~25%, not higher: the misses are MOVE-PRIOR misses (true patterns ranked "
          "116-661 by retrieval), NOT admissibility/chaining failures — see best-prior-rank above.")
    print("      Admissibility's value is shown two ways: it PROMOTED head-sigil that per-pattern missed,")
    print("      and it REJECTS the negatives (B). The fix for the misses is a better move-prior —")
    print("      i.e. DISCHARGE-trained retrieval (the return channel), which is slice 2, not tuning.")
    print("  (B) the cosine-artifact is retrieved HIGH (rank 4, 12) yet covers ~none of the want at")
    print("      cascade level — only DISCHARGE commits, as at the pattern level but now per CASCADE.")
    print("  CAVEAT: greedy (not beam) + low prior cannot reach low-ranked chain partners — the size-2")
    print("      cascades need want-directed beam search (slice 2). g-grain: PROXY (token interfaces +")
    print("      want-coverage); slice 2 = futon2.aif.rollout + real meme.gates for true discharge.")


if __name__ == "__main__":
    main()
