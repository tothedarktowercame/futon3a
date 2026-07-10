"""obligation_accuracy.py — Slice 1.5: the accuracy limb, moved from lexical identity
toward obligation unification WITHOUT giving up directionality.

Diagnosis (TN-gflownets-fable-review + per-record miss analysis, 2026-07-10): the
v3 gate's accuracy = |THEN-tokens ∩ want-tokens| / |want-tokens| scores known
dischargers (closure-folds :success true) at median 0. Miss classes:
  M1 missing flexiarg (instrumentation)   — e.g. dsc/evidence-situated-log
  M2 morphology (matured vs maturity)     — lexical identity too strict
  M3 synonymy (detector vs tripwire; redundant-ways vs projections)
  M4 interface-size variance (THEN token sets range 7..220)

Fix principle (the density/anti-gaming rope): relax LEXICAL IDENTITY only; never
directionality (want atoms must be satisfied by produces atoms — asymmetric) and
never executability (admissible_step chaining untouched). Unification tiers:
  T1 exact      — same token (v3 behavior)
  T2 stem       — same deterministic morphological stem
  T3 semantic   — cosine(atom_w, atom_p) >= TAU per-ATOM (MiniLM), TAU a priori

TAU = 0.60 chosen before seeing results; report 0.55/0.65 sensitivity. Per-atom
strict-threshold matching is NOT document-level embedding coverage (the corpus
gate showed that collapses into relevance): each want atom must be individually
claimed by some production atom, and coverage stays a fraction of the WANT.

Missingness: a pattern with no flexiarg contributes no moves; a record whose
:used yields NO moves at all gets coverage None (MISSING), never 0.
"""
from __future__ import annotations
from functools import lru_cache

_SUFFIXES = ("ations", "ation", "ities", "ility", "ically", "ingly",
             "ings", "ing", "ies", "ied", "ely", "ers", "er", "ed",
             "es", "ly", "ity", "s")


def stem(w: str) -> str:
    """Deterministic light stemmer (no deps): longest-suffix strip + y->i."""
    for suf in _SUFFIXES:
        if w.endswith(suf) and len(w) - len(suf) >= 3:
            w = w[: -len(suf)]
            break
    return w[:-1] + "i" if w.endswith("y") else w


class AtomMatcher:
    """Unifies want atoms against production atoms at tiers T1/T2/T3."""

    def __init__(self, use_semantic: bool = True, tau: float = 0.60):
        self.tau = tau
        self.model = None
        if use_semantic:
            from sentence_transformers import SentenceTransformer
            self.model = SentenceTransformer("all-MiniLM-L6-v2")
        self._emb_cache: dict[str, object] = {}

    def _embed(self, tokens):
        missing = [t for t in tokens if t not in self._emb_cache]
        if missing and self.model is not None:
            vecs = self.model.encode(missing, normalize_embeddings=True)
            for t, v in zip(missing, vecs):
                self._emb_cache[t] = v
        return {t: self._emb_cache[t] for t in tokens if t in self._emb_cache}

    def match_report(self, want: set, produces: set, tau: float | None = None):
        """For each want atom, the best tier at which some production atom claims it.

        Returns {want_atom: ("T1"|"T2"|"T3", witness_atom)} for matched atoms only.
        """
        tau = self.tau if tau is None else tau
        report = {}
        prod_stems = {}
        for p in produces:
            prod_stems.setdefault(stem(p), p)
        for w in sorted(want):
            if w in produces:
                report[w] = ("T1", w)
                continue
            sw = stem(w)
            if sw in prod_stems:
                report[w] = ("T2", prod_stems[sw])
        if self.model is not None:
            unmatched = [w for w in sorted(want) if w not in report]
            if unmatched and produces:
                w_emb = self._embed(unmatched)
                p_emb = self._embed(sorted(produces))
                for w in unmatched:
                    best, best_s = None, tau
                    for p, pv in p_emb.items():
                        s = float(w_emb[w] @ pv)
                        if s >= best_s:
                            best, best_s = p, s
                    if best is not None:
                        report[w] = ("T3", best)
        return report

    def coverage(self, want: set, produces: set, tiers=("T1", "T2", "T3"),
                 tau: float | None = None) -> float:
        """Directional obligation coverage: matched-want-atoms / |want|, counting
        only matches at the allowed tiers."""
        if not want:
            return 0.0
        rep = self.match_report(want, produces, tau=tau)
        n = sum(1 for tier, _ in rep.values() if tier in tiers)
        return n / len(want)
