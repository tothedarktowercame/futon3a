"""aliveness_v3 corpus gate v2 (Task B) — the LOCKED Slice-1.5 measure at corpus scale.

Expanded discharge ground truth: mission-grain records from
  futon6/data/mission-wholeness.edn      (:class alive|mess|pipeline|stub — labels)
  futon6/data/mission-pattern-scopes.edn (:applied — the patterns actually used)
Success = :alive, fail = :mess (pipeline/stub excluded: in-progress / too small).
The mess-negatives are SELECTION-REALISTIC: their :applied patterns were chosen
by the live process and the mission still went to mess — the cosine-artifact
class at process grain (chosen-but-didn't-discharge), which is what R2 needs.

Measure = locked Slice 1.5 (findings/slice1_5_validity_findings.md):
  accuracy  = directional want-atom coverage, produces = salient(parse()["action"])
              (THEN + conclusion), tiers T1 exact / T2 stem / T3 per-atom MiniLM
              cosine >= 0.60 (a priori);
  MISSING   = no flexiarg text at all (reported, never scored 0);
  aliveness = accuracy - LAMBDA * complexity (base-rate prior, cascade_construct).

Outputs:
  1. findings/ground_truth_mission_grain.json — the expanded ground-truth artifact
     (mission, want, applied, label, provenance) for reuse by later slices;
  2. discrimination test: AUC(alive>mess) vs 200-shuffle null, spread stats,
     R1-frequency (interface-underspecified), MISSING-frequency.

Caveat recorded, not hidden: want-text = identify_psi(mission doc). Mission docs
are written pattern-aware, so some laundering of pattern vocabulary into wants is
possible; the fold-grain GROUND gate (aliveness_v3_gate2) stays the validity
anchor because its problem texts are resolver-blind by construction.

Run: cd ~/code/futon3a && .venv/bin/python3 holes/labs/M-memes-arrows/aliveness_v3_corpus_gate2.py
"""
from __future__ import annotations
import glob, json, math, random, re, sys
from pathlib import Path

LAB = "/home/joe/code/futon3a/holes/labs/M-memes-arrows"
sys.path.insert(0, LAB)

from cascade_rollout import salient
from alexandrian_aif import parse
from cascade_construct import load_phylogeny, base_rate_prior, pattern_stem
from offramp_cascade import identify_psi
import rollout_execute as rx
from obligation_accuracy import AtomMatcher

LAMBDA = 0.05
SEED = 20260710
TAU = 0.60
OUT = Path("/home/joe/code/futon2/holes/labs/slush-demo/findings/ground_truth_mission_grain.json")

PRIOR, PRIOR_DEFAULT = base_rate_prior(load_phylogeny())
DROP, _ = rx.corpus_df_drop()
MATCHER = AtomMatcher(use_semantic=True, tau=TAU)


def parse_wholeness(path="/home/joe/code/futon6/data/mission-wholeness.edn"):
    text = Path(path).read_text()
    return {m.group(1).split("@")[0]: m.group(2)
            for m in re.finditer(r':mission\s+"(M-[^"]+)"\s+:class\s+:(\w+)', text)}


def mission_applied(path="/home/joe/code/futon6/data/mission-pattern-scopes.edn"):
    text = Path(path).read_text()
    out = {}
    for m in re.finditer(r':mission\s+"(M-[^"]+)".*?:applied\s+\[([^\]]*)\]', text, re.DOTALL):
        out[m.group(1).split("@")[0]] = re.findall(r'"([^"]+)"', m.group(2))
    return out


def locate_doc(mission):
    for pat in (f"/home/joe/code/*/holes/missions/{mission}.md",
                f"/home/joe/code/*/holes/{mission}.md",
                f"/home/joe/code/*/holes/missions/{mission}*.md"):
        hits = sorted(glob.glob(pat))
        if hits:
            return hits[0]
    return None


def produces_of(patterns):
    prod, missing = set(), []
    for p in patterns:
        f = rx.find_flexiarg(pattern_stem(p))
        if not f:
            missing.append(p)
            continue
        d = parse(f)
        prod |= salient(d["action"], DROP)
    return prod, missing


def complexity(patterns):
    return sum(-math.log(PRIOR.get(pattern_stem(p), PRIOR_DEFAULT)) for p in patterns)


def auc(scores, labels):
    pos = [s for s, y in zip(scores, labels) if y]
    neg = [s for s, y in zip(scores, labels) if not y]
    if not pos or not neg:
        return float("nan")
    return sum((a > b) + 0.5 * (a == b) for a in pos for b in neg) / (len(pos) * len(neg))


def build_records():
    classes = parse_wholeness()
    applied = mission_applied()
    records = []
    for mission, pats in sorted(applied.items()):
        cls = classes.get(mission)
        if cls not in ("alive", "mess") or not pats:
            continue
        doc = locate_doc(mission)
        if not doc:
            records.append({"mission": mission, "label": cls, "applied": pats,
                            "status": "NO_DOC"})
            continue
        want_text = identify_psi(doc)
        want = sorted(salient(want_text, DROP))
        prod, missing = produces_of(pats)
        rec = {"mission": mission, "label": cls, "applied": pats,
               "doc": doc, "want_text": want_text[:500], "n_want_atoms": len(want),
               "missing_flexiargs": missing,
               "provenance": "mission-wholeness.edn x mission-pattern-scopes.edn (2026-06-08) via identify_psi"}
        if not want:
            rec["status"] = "NO_WANT"
        elif not prod:
            rec["status"] = "MISSING"      # all applied patterns uninstrumented (M1/R1)
        else:
            rec["status"] = "ok"
            rec["accuracy"] = MATCHER.coverage(set(want), prod)
            rec["complexity"] = complexity(pats)
            rec["aliveness"] = rec["accuracy"] - LAMBDA * rec["complexity"]
        records.append(rec)
    return records


def main():
    records = build_records()
    OUT.write_text(json.dumps(records, indent=1))
    ok = [r for r in records if r.get("status") == "ok"]
    n_alive = sum(1 for r in ok if r["label"] == "alive")
    n_mess = len(ok) - n_alive
    print(f"records: {len(records)} total | scored {len(ok)} "
          f"(alive {n_alive} / mess {n_mess}) | "
          f"NO_DOC {sum(1 for r in records if r.get('status') == 'NO_DOC')} "
          f"NO_WANT {sum(1 for r in records if r.get('status') == 'NO_WANT')} "
          f"MISSING {sum(1 for r in records if r.get('status') == 'MISSING')}")
    accs = sorted(r["accuracy"] for r in ok)
    med = accs[len(accs) // 2]
    nz = sum(1 for a in accs if a > 0)
    print(f"accuracy spread: min={accs[0]:.3f} med={med:.3f} max={accs[-1]:.3f} "
          f"| nonzero {nz}/{len(accs)}")

    y = [r["label"] == "alive" for r in ok]
    rng = random.Random(SEED)
    for key in ("accuracy", "aliveness"):
        real = auc([r[key] for r in ok], y)
        nulls = sorted(auc([r[key] for r in ok], rng.sample(y, len(y)))
                       for _ in range(200))
        n95 = nulls[int(0.95 * len(nulls))]
        print(f"AUC[{key:9}] (alive>mess) = {real:.3f}   null-95 = {n95:.3f}   "
              f"{'PASS' if real > n95 else 'weak'}")
    print(f"\nwrote {OUT}")


if __name__ == "__main__":
    main()
