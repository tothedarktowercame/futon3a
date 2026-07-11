"""wiring_corpus.py — R1: wireability prior mined from the 21-deposit fold corpus.

Parses ALL deposits' wiring (boxes' :fits-pattern + hyperedges) to extract:
  - per-pair POSITIVE counts {seq, copar}: pairs of patterns that were wired
    together (connected by a hyperedge in the wiring :hyperedges block)
  - per-pair NEGATIVE counts: pairs co-proposed in a deposit's :pattern-ids
    where at least one member ended as a non-contribution (no :fits-pattern
    box) or overlap policy-hole rather than a wired box

The parsing reuses the shape of render_cascades.py's parse_deposit but is a
clean function, not a copy.

Output: wiring-corpus.json + loader.  Runs from any cwd.

Run: python3 wiring_corpus.py
"""
from __future__ import annotations
import json, re, sys
from pathlib import Path
from itertools import combinations

DEPOSITS_DIR = Path("/home/joe/code/futon6/data/fold-turns")
OUTPUT = Path(__file__).parent / "wiring-corpus.json"

# All deposits with wiring (glob the fold-turns directory)
DEPOSIT_STEMS = None  # populated at runtime from glob


def parse_deposit(path: Path) -> dict:
    """Parse a fold-turn deposit's wiring structure.

    Returns:
      {stem, proposal_hash, pattern_ids, wired_patterns, edges, non_contributors}
    """
    t = path.read_text()

    # proposal hash
    hash_m = re.search(r':proposal/hash\s+"([0-9a-f]+)"', t)
    proposal_hash = hash_m.group(1) if hash_m else None

    # pattern-ids (the proposal's full pattern set)
    pid_section = re.search(r':pattern-ids\s*\n?\s*\[([^\]]+)\]', t, re.DOTALL)
    pattern_ids = re.findall(r'"([^"]+)"', pid_section.group(1)) if pid_section else []

    # :fits-pattern values (patterns that actually got boxes = wired patterns)
    wired_patterns = re.findall(r':fits-pattern\s+"([^"]+)"', t)

    # hyperedges from the first :hyperedges block (wiring :nodes section)
    wiring = t.split(":hyperedges")[1] if ":hyperedges" in t else ""
    wiring = wiring.split(":terminals")[0] if ":terminals" in wiring else wiring.split("\n :pins")[0] if ":pins" in wiring else wiring
    edges = []
    for m in re.finditer(
        r'\{[^{}]*?:from \[([^\]]*)\][^{}]*?:to \[([^\]]*)\][^{}]*?:connective :(\w+)',
        wiring,
    ):
        frm = re.findall(r':(b\d+)', m.group(1))
        to = re.findall(r':(b\d+)', m.group(2))
        edges.append({"from": frm, "to": to, "connective": m.group(3)})

    # Map box-id -> fits-pattern (first occurrence wins)
    box_to_pattern = {}
    for chunk in re.split(r'\{:id :', t)[1:]:
        bid = re.match(r'(b\d+)', chunk)
        fp = re.search(r':fits-pattern\s+"([^"]+)"', chunk[:2000])
        if bid and fp and bid.group(1) not in box_to_pattern:
            box_to_pattern[bid.group(1)] = fp.group(1)

    # non-contributors: patterns in pattern_ids but NOT in wired_patterns
    wired_set = set(wired_patterns)
    non_contributors = set(pattern_ids) - wired_set

    return {
        "stem": path.stem,
        "proposal_hash": proposal_hash,
        "pattern_ids": pattern_ids,
        "wired_patterns": wired_patterns,
        "wired_set": wired_set,
        "edges": edges,
        "box_to_pattern": box_to_pattern,
        "non_contributors": non_contributors,
    }


def extract_wiring_pairs(deposit: dict) -> list[dict]:
    """Extract positive wiring pairs from a deposit's hyperedges.

    Each edge connects box-ids; we resolve to pattern-ids and record the
    pair + connective type.
    """
    b2p = deposit["box_to_pattern"]
    pairs = []
    for edge in deposit["edges"]:
        for src in edge["from"]:
            for dst in edge["to"]:
                p_src = b2p.get(src)
                p_dst = b2p.get(dst)
                if p_src and p_dst and p_src != p_dst:
                    pair = tuple(sorted([p_src, p_dst]))
                    pairs.append({
                        "pair": pair,
                        "connective": edge["connective"],
                    })
    return pairs


def extract_negative_pairs(deposit: dict) -> list[tuple[str, str]]:
    """Extract negative (anti-wiring) pairs from a deposit.

    A negative pair = two patterns co-proposed in :pattern-ids where at least
    one member ended as a non-contribution (no box) rather than a wired box.
    These are pairs the proposer put together but the folder did not wire.
    """
    pid = deposit["pattern_ids"]
    nc = deposit["non_contributors"]
    wired = deposit["wired_set"]
    negatives = []
    for a, b in combinations(sorted(set(pid)), 2):
        # negative if at least one is a non-contributor
        if a in nc or b in nc:
            # but only if BOTH were in the proposal (they were, by construction)
            # and the pair was NOT wired (no edge between them)
            negatives.append(tuple(sorted([a, b])))
    return negatives


def build_corpus(deposits_dir: Path = DEPOSITS_DIR) -> dict:
    """Build the wiring corpus from all deposits."""
    pos_counts = {}   # pair -> {seq: int, copar: int, tensor: int}
    neg_counts = {}   # pair -> int
    deposit_summaries = []

    # Glob all ft-*.edn deposits
    paths = sorted(deposits_dir.glob("ft-*.edn"))
    for path in paths:
        stem = path.stem
        if not path.exists():
            print(f"WARNING: {stem} not found, skipping", file=sys.stderr)
            continue

        dep = parse_deposit(path)

        # positive pairs
        wired_pairs = extract_wiring_pairs(dep)
        for wp in wired_pairs:
            pair = wp["pair"]
            conn = wp["connective"]
            if pair not in pos_counts:
                pos_counts[pair] = {"seq": 0, "copar": 0, "tensor": 0}
            if conn in pos_counts[pair]:
                pos_counts[pair][conn] += 1
            else:
                # unknown connective, count as copar
                pos_counts[pair]["copar"] += 1

        # negative pairs
        neg_pairs = extract_negative_pairs(dep)
        for np in neg_pairs:
            # only count as negative if the pair was NOT positively wired
            if np not in pos_counts:
                neg_counts[np] = neg_counts.get(np, 0) + 1

        deposit_summaries.append({
            "stem": stem,
            "proposal_hash": dep["proposal_hash"],
            "n_pattern_ids": len(dep["pattern_ids"]),
            "n_wired": len(dep["wired_patterns"]),
            "n_non_contributors": len(dep["non_contributors"]),
            "n_edges": len(dep["edges"]),
            "n_wired_pairs": len(wired_pairs),
            "n_negative_pairs": len(neg_pairs),
        })

    corpus = {
        "deposits": deposit_summaries,
        "positive_pairs": {
            f"{a} | {b}": counts
            for (a, b), counts in sorted(pos_counts.items())
        },
        "negative_pairs": {
            f"{a} | {b}": count
            for (a, b), count in sorted(neg_counts.items())
        },
        "stats": {
            "n_deposits": len(deposit_summaries),
            "n_positive_pairs": len(pos_counts),
            "n_negative_pairs": len(neg_counts),
            "total_positive_edges": sum(
                sum(c.values()) for c in pos_counts.values()
            ),
            "total_negative_edges": sum(neg_counts.values()),
        },
    }
    return corpus


def load_corpus(path: Path = OUTPUT) -> dict:
    """Load the wiring corpus from JSON."""
    return json.loads(path.read_text())


def pair_affinity(pair: tuple[str, str], corpus: dict,
                  laplace_prior: float = 1.0) -> float:
    """Laplace-smoothed wiring affinity for a pattern pair.

    Returns:
      > 0: pair has been positively wired (more seq/copar evidence)
      = 0: pair never seen (neutral — Laplace prior cancels)
      < 0: pair has negative evidence (co-proposed but not wired)

    Formula: (pos_count + laplace_prior) / (pos_count + neg_count + 2*laplace_prior) - 0.5
    This gives:
      - unseen pair: (0 + 1) / (0 + 0 + 2) - 0.5 = 0.0 (neutral)
      - only positive: (n + 1) / (n + 2) - 0.5 > 0
      - only negative: 1 / (n + 2) - 0.5 < 0
    """
    key = " | ".join(sorted(pair))
    pos = corpus.get("positive_pairs", {})
    neg = corpus.get("negative_pairs", {})

    pos_count = sum(pos.get(key, {}).values()) if key in pos else 0
    neg_count = neg.get(key, 0)

    return (pos_count + laplace_prior) / (pos_count + neg_count + 2 * laplace_prior) - 0.5


def main():
    corpus = build_corpus()
    OUTPUT.write_text(json.dumps(corpus, indent=2))
    print(f"Wrote {OUTPUT}")
    print(f"Stats: {json.dumps(corpus['stats'], indent=2)}")

    # Print top positive pairs
    pos = corpus["positive_pairs"]
    print(f"\nTop positive pairs (by total count):")
    sorted_pos = sorted(pos.items(), key=lambda kv: sum(kv[1].values()), reverse=True)
    for key, counts in sorted_pos[:10]:
        print(f"  {key}: {counts} (total={sum(counts.values())})")

    # Print some negative pairs
    neg = corpus["negative_pairs"]
    print(f"\nTop negative pairs (by count):")
    sorted_neg = sorted(neg.items(), key=lambda kv: kv[1], reverse=True)
    for key, count in sorted_neg[:10]:
        print(f"  {key}: {count}")


if __name__ == "__main__":
    main()
