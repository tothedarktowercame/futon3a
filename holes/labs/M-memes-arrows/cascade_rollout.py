#!/usr/bin/env python3
"""cascade_rollout.py — SLICE 1 of the cascade-rollout fold (claude-1, author; claude-2 reviews).

The level-shift (Joe / F-wm-piloted:362): G is over CASCADES, not patterns. So the unit of account is
a cascade pi = an ordered SET of pattern-moves, and the genuinely-new piece is INCREMENTAL
TERMINAL-CHAINING admissibility — the legal-move filter a cascade-search needs.

This module is the PURE, model-free core (no sentence-transformers, no I/O) so the legal-move filter is
unit-testable deterministically. The experiment (cascade_recovery_experiment.py) wires it to real data.

MOVE INTERFACE (honest PROXY, g-grain = token-level, NOT a real type system):
  consumes(p) = salient tokens of the pattern's IF+HOWEVER   (what it needs to attach to)
  produces(p) = salient tokens of the pattern's THEN          (what it emits onto the frontier)
A move admits iff its consumes chain onto the partial cascade's FRONTIER (have ∪ ⋃produces). A move
disconnected from the frontier is type-broken and rejected. This is terminals-match (meme.gates) in
proxy form; it is NOT real type-unification — slice 2 wires the JVM gate for that.
"""
import re

# compact stoplist; domain-frequent tokens are additionally DF-dropped by the experiment (drop=...).
STOP = frozenset("""
a an the this that these those of for to in on at by with from into onto over under as is are was were
be been being do does did doing have has had having it its it's they them their there here then than
and or but not no nor so if however because thus hence which who whom whose what when where why how
one two each every all any some many much more most less few both either neither own same other another
we you our your i me my he she his her them up down out off about across after before between among
can could may might must shall should will would per via etc eg ie vs you're we're
""".split())


def salient(text, drop=frozenset()):
    """Content tokens: lowercase [a-z]+, len>2, minus stopwords and the supplied DF-drop set."""
    return {w for w in re.findall(r"[a-z]+", (text or "").lower())
            if len(w) > 2 and w not in STOP and w not in drop}


def move_interface(pid, ifhow, then, drop=frozenset()):
    """Build a pattern's move interface {:id :consumes :produces} from its IF+HOWEVER and THEN."""
    return {"id": pid, "consumes": salient(ifhow, drop), "produces": salient(then, drop)}


def frontier_of(have, cascade):
    """The available tokens after a partial cascade: the sorry's HAVE plus everything produced so far."""
    f = set(have)
    for m in cascade:
        f |= m["produces"]
    return f


def admissible_step(have, cascade, move):
    """LEGAL-MOVE FILTER (the genuinely-new piece): does `move` chain onto the partial cascade's frontier?

    True iff the move consumes at least one token already available (from HAVE or an earlier move's
    produces). A move whose consumes are disjoint from the frontier is type-broken and rejected. This is
    INCREMENTAL: a move blocked at the empty cascade can become admissible once an earlier move produces
    the token it needs (see the incremental test)."""
    return len(move["consumes"] & frontier_of(have, cascade)) > 0


def want_coverage(cascade, want):
    """Proxy DISCHARGE signal: fraction of the want-signature the cascade's produces actually cover.

    The proxy form of meme.gates' terminals-match: does the cascade EMIT toward the want? A cascade
    retrieval ranks high but whose produces miss the want (the cosine artifact) scores ~0 here — which
    is exactly why retrieval cannot commit and discharge can."""
    if not want:
        return 0.0
    prod = set()
    for m in cascade:
        prod |= m["produces"]
    return len(prod & set(want)) / len(set(want))


def rollout(have, want, moves, prior, phylo_neighbors, K=3, floor=0.0, lam=0.05, cover_stop=0.6):
    """Greedy state-conditioned cascade rollout (slice-1; beam/full-G(pi) deferred to slice 2).

    moves            : list of interfaces {:id :consumes :produces}
    prior            : dict id -> retrieval move-prior score (cosine(problem, pattern))
    phylo_neighbors  : dict tail-slug -> set(neighbor tail-slugs)  (the structural P(next|current))
    The prior is STATE-CONDITIONED via a phylogeny bonus for candidates adjacent to already-chosen moves
    — so a later pick depends on earlier picks (the cascade is the unit, not independent top-K patterns).
    Returns the chosen cascade (list of interfaces)."""
    cascade, chosen = [], set()
    for _ in range(K):
        cand = []
        for m in moves:
            if m["id"] in chosen or not admissible_step(have, cascade, m):
                continue
            tail = m["id"].split("/")[-1]
            bonus = lam * sum(1 for c in cascade
                              if tail in phylo_neighbors.get(c["id"].split("/")[-1], ()))
            cand.append((prior.get(m["id"], 0.0) + bonus, m))
        if not cand:
            break
        cand.sort(key=lambda x: -x[0])
        best_s, best = cand[0]
        if best_s < floor:
            break
        cascade.append(best)
        chosen.add(best["id"])
        if want and want_coverage(cascade, want) >= cover_stop:
            break
    return cascade
