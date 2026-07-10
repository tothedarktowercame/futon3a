#!/usr/bin/env python3
"""rollout_execute.py — the SEMILATTICE-ROLLOUT executor for the offramp witness.

The offramp predictor (futon2.aif.fold-semilattice/semilattice-fold) treats the
cascade CORRECTLY as a semilattice: one box per pattern, wired by the cascade's
own descent (BV.seq) + co_app (BV.copar) edges. The strict rule-table fold
(fold_engine.clj) is the WRONG executor for it — it is a linear pipeline that
only folds patterns pre-listed in its RULES table, so it abstains (boxes=0) on
any mission whose patterns aren't rule-encoded (e.g. M-learning-loop).

This executor reproduces the construction the semilattice way, INDEPENDENTLY of
the predictor: each pattern's move interface is consumes=tokens(IF+HOWEVER),
produces=tokens(THEN) (cascade_rollout.py, slice-1, 8/8 tested); a move admits
iff it chains onto the frontier; discharge = want_coverage of the mission's
have->want magnet. Because this chains on the patterns' OWN TEXT (not the
phylogeny edges the predictor used), realized != expected by construction — no
tautology (enact.clj's honesty bar, at the cascade grain).

SCOPE (honest): this is slice-1's want_coverage PROXY discharge. It is NOT
slice-2a's discharge-trained move-prior nor slice-2b's live meme.gates commit
(M-G-over-cascades.md §5). It makes the witness semilattice-shaped and
ungameable; it does not claim the slice-2a recovery result.

Usage: rollout_execute.py <mission-doc> <cascade-edn> [out.json]
Emits JSON: {enacted-wiring{boxes,wires,policy-holes}, discharge, folded-count,
             unfolded-count, want-signature}
"""
import sys, json, re, glob
from collections import Counter
sys.path.insert(0, "/home/joe/code/futon3a/holes/labs/M-memes-arrows")
from alexandrian_aif import parse, LIB
from cascade_rollout import salient, move_interface, want_coverage, admissible_step, frontier_of
from offramp_cascade import identify_psi

def read_shown_and_semilattice(cascade_edn_path):
    """Pull :shown stems and the semilattice's bare-name edges out of the EDN the
    offramp cascade stage wrote (light regex read — the EDN is machine-emitted)."""
    txt = open(cascade_edn_path, encoding="utf-8").read()
    shown = re.findall(r'"([^"]+)"', re.search(r':shown \[([^\]]*)\]', txt).group(1))
    edges = []
    for key in ("descent", "co_app"):
        m = re.search(r':%s \[(.*?)\]\s*(?::co_app|\})' % key, txt, re.S)
        if m:
            for pair in re.findall(r'\[([^\]]*)\]', m.group(1)):
                names = re.findall(r'"([^"]+)"', pair)
                if len(names) >= 2:
                    edges.append((names[0], names[1]))
    return shown, edges

def corpus_df_drop():
    """DF-drop corpus-frequent tokens so admissibility discriminates (mirrors
    cascade_recovery_experiment.build step 2, answer-independent)."""
    parsed = []
    for f in glob.glob(str(LIB / "**" / "*.flexiarg"), recursive=True):
        p = parse(f)
        if p["ifhow"] and p["then"]:
            parsed.append(p)
    df = Counter()
    for p in parsed:
        df.update(salient(p["ifhow"]) | salient(p["then"]))
    return frozenset(w for w, c in df.items() if c > 0.20 * len(parsed)), parsed

def find_flexiarg(stem):
    hits = glob.glob(str(LIB / "**" / (stem + ".flexiarg")), recursive=True)
    return hits[0] if hits else None

def main():
    doc, cascade_edn = sys.argv[1], sys.argv[2]
    out = sys.argv[3] if len(sys.argv) > 3 else "/tmp/rollout-execute.json"
    shown, edges = read_shown_and_semilattice(cascade_edn)
    psi = identify_psi(doc)
    drop, _corpus = corpus_df_drop()
    want = salient(psi, drop)          # goal signature (DF-consistent with produces)
    have = salient(psi)                # generous seed = the context HAVE (magnet)

    # move interfaces for the cascade's OWN patterns (the semilattice's boxes)
    moves, missing = [], []
    for stem in shown:
        f = find_flexiarg(stem)
        if not f:
            missing.append(stem); continue
        p = parse(f)
        moves.append(move_interface(p["id"], p["ifhow"], p["then"], drop))
    by_stem = {m["id"].split("/")[-1]: m for m in moves}

    # ENACT: greedily admit patterns that chain onto the frontier, ordered along
    # the semilattice's descent/co_app edges (producers before the nodes they feed).
    order = []
    for a, b in edges:
        for s in (a, b):
            if s in by_stem and s not in order:
                order.append(s)
    for s in shown:                    # any not touched by an edge, appended (will test as isolated)
        if s in by_stem and s not in order:
            order.append(s)

    enacted, holes = [], []
    for s in order:
        m = by_stem[s]
        if admissible_step(have, enacted, m):
            enacted.append(m)
        else:
            holes.append(s)
    enacted_stems = [m["id"].split("/")[-1] for m in enacted]
    discharge = want_coverage(enacted, want)

    boxes = [{"id": s, "pattern": by_stem[s]["id"], "produces": s + "-out"} for s in enacted_stems]
    wires = [{"from": a, "to": b, "type": "wire/seq"}
             for a, b in edges if a in enacted_stems and b in enacted_stems]
    policy_holes = ([{"unfolded-pattern": s} for s in holes]
                    + [{"missing-flexiarg": s} for s in missing])
    wiring = {"boxes": boxes, "wires": wires, "policy-holes": policy_holes,
              "generated-by": "rollout_execute.py (semilattice rollout; consumes=IF+HOWEVER, produces=THEN; want_coverage proxy discharge)",
              "want-signature": "MissionState -> {Wiring, PolicyHoles}"}
    print(json.dumps({
        "wiring": wiring,
        "discharge": round(discharge, 4),
        "folded-count": len(boxes),
        "unfolded-count": len(policy_holes),
        "want-signature": "MissionState -> {Wiring, PolicyHoles}",
        "want-tokens": len(want),
    }))
    open(out, "w").write(json.dumps({"wiring": wiring, "discharge": discharge}) + "\n")

if __name__ == "__main__":
    main()
