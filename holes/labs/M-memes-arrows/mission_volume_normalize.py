#!/usr/bin/env python3
"""mission_volume_normalize.py — SLICE-2a MAP cut (b): is the management-pattern skew VOLUME or COMPOSITION?

Cut (a) ruled out DOMAIN (math is only 9/194). The live confound is VOLUME: stack-alive applies 4.8 patterns,
stack-mess 2.3, and every alive-enriched pattern has M=0 — consistent with "mess is just pattern-sparse" rather
than "mess avoids management patterns." Cut (b) controls for volume two ways:
  1. Restrict to missions applying >= K patterns (comparable volume) and re-check the management skew.
  2. Volume-NORMALIZED measure: the FRACTION of a mission's applied patterns that are management-type.
The decisive question: among PATTERN-RICH mess missions (|applied|>=K), is the mgmt rate STILL ~0? If yes, the
signal is COMPOSITION (even rich mess missions avoid mgmt). If those missions DO carry mgmt, it was volume.

This is still QUANTITATIVE (bag-of-patterns). Joe (2026-06-23): the real target is STRUCTURAL (how patterns
compose) for recreating cascades — that is the next phase, over mission-phylogeny.edn's co-application edges.

Read-only. Run: cd ~/code/futon3a && python3 holes/labs/M-memes-arrows/mission_volume_normalize.py
"""
import re
from pathlib import Path
from collections import defaultdict

DATA = Path("/home/joe/code/futon6/data")
CLASS = dict(re.findall(r':mission "M-([^"]+)" :class :(\w+)', (DATA/"mission-wholeness.edn").read_text()))
APPLIED = {m: re.findall(r'"([^"]+)"', body) for m, body in
          re.findall(r':mission "M-([^"]+)" :applied \[([^\]]*)\]', (DATA/"mission-pattern-scopes.edn").read_text())}

MGMT = {"expected-free-energy-scorecard","structured-observation-vector","candidate-pattern-action-space",
        "single-source-of-truth","par-as-obligation","unresolved-tensions-at-closure",
        "what-problem-is-this-actually-solving","whose-question-is-this","world-is-hypergraph",
        "task-shape-validation","mission-interface-signature","negative-space-duality",
        "deep-storage-to-active-graph","term-to-channel-traceability","evidence-precision-registry",
        "policy-precision-commitment-temperature","interest-event-vocabulary","all-or-nothing",
        "aif-as-environment-not-instruction"}

alive = [m for m in CLASS if CLASS[m]=="alive" and m in APPLIED]
mess  = [m for m in CLASS if CLASS[m]=="mess"  and m in APPLIED]

print("=== (b1) management presence at matched volume (restrict to |applied| >= K) ===")
print(f"  {'K':>2}  {'alive n':>8} {'mgmt%':>6}   {'mess n':>7} {'mgmt%':>6}   {'mess |app|':>10}")
for K in (1,2,3,4,5):
    aK = [m for m in alive if len(set(APPLIED[m]))>=K]
    mK = [m for m in mess  if len(set(APPLIED[m]))>=K]
    if not aK or not mK:
        print(f"  {K:>2}  (a class empty at this K)"); continue
    am = sum(1 for m in aK if MGMT & set(APPLIED[m]))/len(aK)
    mm = sum(1 for m in mK if MGMT & set(APPLIED[m]))/len(mK)
    mavg = sum(len(set(APPLIED[m])) for m in mK)/len(mK)
    print(f"  {K:>2}  {len(aK):>8} {am:>6.0%}   {len(mK):>7} {mm:>6.0%}   {mavg:>10.1f}")
print("  KEY: if mess mgmt% stays ~0 even as K rises (mess missions that ARE pattern-rich still lack mgmt),")
print("       the signal is COMPOSITION, not volume.")

print("\n=== (b2) volume-normalized: FRACTION of applied patterns that are management-type ===")
def frac(grp):
    vals = [len(MGMT & set(APPLIED[m]))/len(set(APPLIED[m])) for m in grp if APPLIED[m]]
    return sum(vals)/len(vals) if vals else 0.0
print(f"  alive: mgmt-fraction of applied = {frac(alive):.0%}")
print(f"  mess : mgmt-fraction of applied = {frac(mess):.0%}")
print("  (controls for volume directly: share of the bag that is management, not the count.)")

print("\n=== (b3) the clean-negative sample: mess AND pattern-rich AND no management pattern ===")
rich_mess_nomgmt = sorted(m for m in mess if len(set(APPLIED[m]))>=3 and not (MGMT & set(APPLIED[m])))
rich_mess_total  = [m for m in mess if len(set(APPLIED[m]))>=3]
print(f"  mess missions with |applied|>=3: {len(rich_mess_total)}")
print(f"  of those, with NO management pattern (the 'lost-in-detail' negatives): {len(rich_mess_nomgmt)}")
for m in rich_mess_nomgmt:
    aps = sorted(set(APPLIED[m]))
    print(f"    M-{m}  ({len(aps)}): {', '.join(aps)}")

print("\n=== clean positives for comparison: alive AND pattern-rich AND >=1 management pattern ===")
rich_alive_mgmt = [m for m in alive if len(set(APPLIED[m]))>=3 and (MGMT & set(APPLIED[m]))]
print(f"  alive |applied|>=3 with >=1 mgmt: {len(rich_alive_mgmt)} (clean positives)")
