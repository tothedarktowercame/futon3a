#!/usr/bin/env python3
"""extract_code_graph_asof.py — REAL solution, Phase A: per-commit code-structure extraction from git.

Reconstructs the namespace dependency graph (Clojure ns -> :require edges) AS IT WAS at any historical date,
read-only from git (`git rev-list --before`, `git ls-tree`, `git show` — NO checkout, never mutates the working
tree). This is the real per-commit code structure substrate-2 fails to keep (code/v05/edits is HEAD-snapshot);
git is the backfill source of truth, NOT a co-edit proxy.

Output (per (repo, date)): a dependency graph {ns -> set(required ns)} restricted to in-repo namespaces, plus a
file->ns map. Downstream (Phase D) runs Ollivier-Ricci kappa on the graph as-of T_before vs T_after a mission.

Read-only. Run: cd ~/code && python3 futon3a/holes/labs/M-memes-arrows/extract_code_graph_asof.py
"""
import subprocess, re
from pathlib import Path
from collections import defaultdict

ROOT = Path("/home/joe/code")

def git(repo, *args):
    return subprocess.run(["git", "-C", str(ROOT/repo), *args],
                          capture_output=True, text=True).stdout

def commit_asof(repo, date):
    return git(repo, "rev-list", "-1", f"--before={date}", "HEAD").strip()

def clj_files_at(repo, commit):
    out = git(repo, "ls-tree", "-r", "--name-only", commit)
    return [f for f in out.splitlines() if f.endswith((".clj", ".cljc", ".cljs"))]

NS_RE = re.compile(r"\(ns\s+([a-zA-Z0-9.\-]+)")
# :require forms: capture required ns symbols (bare or in vectors), within (:require ...) / (require ...)
REQ_BLOCK = re.compile(r"\(:?require\b(.*?)(?=\)\s*\(:|\)\s*\)|\Z)", re.S)
REQ_NS = re.compile(r"\[?\s*([a-zA-Z][a-zA-Z0-9.\-]+)")

def file_ns_and_reqs(repo, commit, path):
    src = git(repo, "show", f"{commit}:{path}")
    m = NS_RE.search(src)
    if not m:
        return None, set()
    ns = m.group(1)
    reqs = set()
    # scan the ns form's :require / use of clojure require — coarse but consistent
    for blk in REQ_BLOCK.findall(src[:src.find("(defn") if "(defn" in src else len(src)]):
        for cand in REQ_NS.findall(blk):
            if "." in cand and not cand.startswith(("clojure.", "java.")):
                reqs.add(cand)
    return ns, reqs

def code_graph_asof(repo, date):
    """({ns -> set(in-repo reqs)}, {repo/path -> ns}, commit) as of `date`. Edges restricted to ns present then."""
    commit = commit_asof(repo, date)
    if not commit:
        return None, {}, commit
    files = clj_files_at(repo, commit)
    ns_reqs = {}; file2ns = {}
    for f in files:
        ns, reqs = file_ns_and_reqs(repo, commit, f)
        if ns:
            ns_reqs[ns] = reqs
            file2ns[f"{repo}/{f}"] = ns
    present = set(ns_reqs)
    graph = {ns: (reqs & present) for ns, reqs in ns_reqs.items()}   # in-repo edges only
    return graph, file2ns, commit

def stats(graph):
    n = len(graph); e = sum(len(v) for v in graph.values())
    return n, e

if __name__ == "__main__":
    print("Phase A self-check: namespace dependency graph as-of historical dates (read-only git)\n")
    for repo in ("futon3c", "futon2"):
        print(f"=== {repo} ===")
        prev = None
        for date in ("2026-02-15", "2026-04-15", "2026-06-15"):
            g, _f2n, c = code_graph_asof(repo, date)
            if g is None:
                print(f"  {date}: no commit"); continue
            n, e = stats(g)
            churn = ""
            if prev is not None:
                added = len(set(g) - set(prev)); removed = len(set(prev) - set(g))
                churn = f"  (+{added} -{removed} ns vs prev snapshot)"
            print(f"  {date} @ {c[:8]}: {n} namespaces, {e} in-repo require-edges{churn}")
            prev = g
    print("\nOK if namespace/edge counts EVOLVE across snapshots -> real per-commit structure (not HEAD).")
