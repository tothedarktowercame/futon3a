#!/usr/bin/env python3
"""verify_g_discharge.py — REAL solution, Phase D: tension-DISCHARGE over real per-commit code structure.

G(pi) = expected tension-discharge across scales. Stack-tension = negative Ollivier-Ricci curvature (kappa) on
the namespace dependency graph (Phase A, from git — the real per-commit structure substrate-2 lacks). DISCHARGE
= kappa RISES (less negative) at a mission's touched namespaces from BEFORE to AFTER its commit window.

THE test (population, attribution is noisy per-mission): do ALIVE / high-Salingaros missions discharge tension at
their target namespaces MORE than MESS missions? vs a label-shuffle null.

Reuses Phase-A extraction (git, read-only) + the rung-3 Ollivier-Ricci core (pattern_curvature). kappa per
(repo, grid-time) is computed lazily and memoized. clj/cljc only (py missions excluded in v1).

Read-only. Run: cd ~/code && python3 futon3a/holes/labs/M-memes-arrows/verify_g_discharge.py
"""
import subprocess, re, json, random, statistics as st
from pathlib import Path
from collections import defaultdict
import sys
sys.path.insert(0, str(Path("/home/joe/code/futon3a/holes/labs/M-memes-arrows")))
from pattern_curvature import curvature   # rung-3 Ollivier-Ricci core
from extract_code_graph_asof import code_graph_asof   # proven Phase-A per-commit extractor (per-file git show)

ROOT = Path("/home/joe/code")
GRID = ["2026-02-01", "2026-03-01", "2026-04-01", "2026-05-01", "2026-06-01", "2026-07-01"]
REPOS = ["futon3c", "futon2", "futon3", "futon5", "futon3a", "futon1a", "futon5a", "futon4"]

NS_RE = re.compile(r"\(ns\s+([a-zA-Z0-9.\-]+)")
REQ_NS = re.compile(r"\[?\s*([a-zA-Z][a-zA-Z0-9.\-]+)")

def git(repo, *args, **kw):
    return subprocess.run(["git", "-C", str(ROOT/repo), *args], capture_output=True, text=True, **kw).stdout

def commit_asof(repo, date):
    return git(repo, "rev-list", "-1", f"--before={date}", "HEAD").strip()

def graph_asof(repo, commit):
    """Batched: {ns->set(in-repo reqs)} and {repo/path -> ns}, at `commit`. One cat-file --batch subprocess."""
    files = [f for f in git(repo, "ls-tree", "-r", "--name-only", commit).splitlines()
             if f.endswith((".clj", ".cljc"))]
    if not files:
        return {}, {}
    spec = "".join(f"{commit}:{f}\n" for f in files)
    p = subprocess.run(["git", "-C", str(ROOT/repo), "cat-file", "--batch"],
                       input=spec, capture_output=True, text=True)
    out = p.stdout; ns_reqs = {}; file2ns = {}; i = 0; fidx = 0
    lines = out.split("\n")
    # parse the --batch stream: header "<sha> blob <size>" then <size> bytes; simpler: re-request per-miss? No —
    # use a robust scan: split on headers.
    buf = out
    pos = 0
    for f in files:
        hdr_end = buf.find("\n", pos)
        if hdr_end < 0: break
        hdr = buf[pos:hdr_end]
        parts = hdr.split()
        if len(parts) >= 3 and parts[1] == "blob":
            size = int(parts[2])
            content = buf[hdr_end+1: hdr_end+1+size]
            pos = hdr_end + 1 + size + 1   # skip content + trailing newline
            m = NS_RE.search(content)
            if m:
                ns = m.group(1)
                head = content[:content.find("(defn") if "(defn" in content else len(content)]
                reqs = {c for c in REQ_NS.findall(head) if "." in c and not c.startswith(("clojure.", "java."))}
                ns_reqs[ns] = reqs
                file2ns[f"{repo}/{f}"] = ns
        else:
            pos = hdr_end + 1
    present = set(ns_reqs)
    graph = {ns: (reqs & present) for ns, reqs in ns_reqs.items()}
    return graph, file2ns

_KCACHE = {}
def kappa_asof(repo, date):
    """node_kappa {ns->kappa} at (repo, grid date), memoized. Also returns file2ns for that snapshot."""
    key = (repo, date)
    if key in _KCACHE:
        return _KCACHE[key]
    graph, file2ns, commit = code_graph_asof(repo, date)
    if not graph:
        _KCACHE[key] = ({}, {}, {}); return _KCACHE[key]
    # make undirected for OR
    und = defaultdict(dict)
    for n, nbrs in graph.items():
        for m in nbrs:
            und[n][m] = 1; und[m][n] = 1
    node_kappa = {}
    if und:
        _, node_kappa, _ = curvature(und)
    _KCACHE[key] = (node_kappa, file2ns, {k: dict(v) for k, v in und.items()})
    return _KCACHE[key]

def grid_before(date):  # latest grid time <= date
    g = [t for t in GRID if t <= date]; return g[-1] if g else None
def grid_after(date):   # earliest grid time >= date
    g = [t for t in GRID if t >= date]; return g[0] if g else None

def mission_window(repo_files):
    """(start,end) author-dates spanning commits that touched the mission's files (per repo)."""
    dates = []
    for repo, files in repo_files.items():
        out = git(repo, "log", "--format=%cI", "--", *files)
        dates += [d[:10] for d in out.splitlines() if d.strip()]
    if not dates: return None, None
    return min(dates), max(dates)

def main():
    CLASS = dict(re.findall(r':mission "M-([^"]+)" :class :(\w+)',
                            (ROOT/"futon6/data/mission-wholeness.edn").read_text()))
    mfe = json.load(open(ROOT/"futon5a/holes/tech-notes/mission-file-edges.json"))
    # mission-name -> {repo: [path-without-repo]}
    miss = {}
    for key, files in mfe.items():
        name = Path(key).stem[2:] if Path(key).stem.startswith("M-") else Path(key).stem
        rf = defaultdict(list)
        for f in files:
            if f.endswith((".clj", ".cljc")):
                repo = f.split("/", 1)[0]
                if repo in REPOS:
                    rf[repo].append(f.split("/", 1)[1])
        if rf:
            miss[name] = (dict(rf), [f for f in files if f.endswith((".clj", ".cljc"))])

    results = {"alive": [], "mess": []}
    detail = []
    for name, (rf, allfiles) in miss.items():
        cls = CLASS.get(name)
        if cls not in ("alive", "mess"):
            continue
        ws, we = mission_window(rf)
        if not ws:
            continue
        tb, ta = grid_before(ws), grid_after(we)
        if not tb or not ta or tb == ta:
            continue
        # collect per-repo dkappa at the mission's namespaces
        dks = []
        for repo in rf:
            nk_b, f2n_b, _g_b = kappa_asof(repo, tb)
            nk_a, f2n_a, g_a = kappa_asof(repo, ta)
            # mission's namespaces at ta (its touched files -> ns)
            mns = {f2n_a.get(p) for p in allfiles if p.startswith(repo + "/")} - {None}
            # landing zone = mission ns + their graph neighbours at ta (the structure it attaches to)
            region = set(mns)
            for n in mns:
                region |= set(g_a.get(n, {}))
            # DRIFT CONTROL: subtract the repo-wide mean dkappa over the same window, so we measure LOCAL
            # discharge, not global repo growth (the long-window confound). common = ns present at tb AND ta.
            common = set(nk_a) & set(nk_b)
            repo_drift = st.mean(nk_a[n] - nk_b[n] for n in common) if common else 0.0
            # DISCHARGE over the PRE-EXISTING region members, drift-corrected (works for new-code missions too).
            for r in region:
                if r in nk_a and r in nk_b:
                    dks.append((nk_a[r] - nk_b[r]) - repo_drift)   # >0 = MORE discharge than repo-wide drift
        if dks:
            mdk = st.mean(dks)
            results[cls].append(mdk)
            detail.append((cls, name, round(mdk, 4), len(dks), tb, ta))

    def auc(pos, neg):
        return sum((p > n)+0.5*(p == n) for p in pos for n in neg)/(len(pos)*len(neg)) if pos and neg else float("nan")

    A, M = results["alive"], results["mess"]
    print(f"=== Phase D: tension-DISCHARGE at mission namespaces (real per-commit code graph) ===")
    print(f"  evaluable missions: alive={len(A)} mess={len(M)}  (kappa node-cache: {len(_KCACHE)} repo-snapshots)\n")
    if not A or not M:
        print("  insufficient evaluable missions — check windows/grid coverage.");
        for d in detail[:20]: print("   ", d)
        return
    a_mean, m_mean = st.mean(A), st.mean(M)
    a_disc = sum(x > 0 for x in A)/len(A); m_disc = sum(x > 0 for x in M)/len(M)
    aucv = auc(A, M)
    rng = random.Random(20260623); pool = A + M; nulls = []
    for _ in range(2000):
        rng.shuffle(pool); nulls.append(auc(pool[:len(A)], pool[len(A):]))
    mu, sd = st.mean(nulls), st.pstdev(nulls); z = (aucv-mu)/sd if sd else float("nan")
    print(f"  mean dkappa:  alive={a_mean:+.4f}  mess={m_mean:+.4f}   (>0 = tension discharged)")
    print(f"  frac discharging (dkappa>0):  alive={a_disc:.0%}  mess={m_disc:.0%}")
    print(f"  AUC(alive>mess) = {aucv:.3f}   shuffle-null={mu:.3f}±{sd:.3f}   z={z:+.2f}  "
          f"{'** SIGNAL' if abs(z)>2 else 'no clear signal'}")
    print("\n  top alive (most discharge):")
    for c,n,dk,k,tb,ta in sorted([d for d in detail if d[0]=='alive'], key=lambda x:-x[2])[:6]:
        print(f"    {dk:+.3f}  {n}  ({k} ns, {tb[:7]}->{ta[:7]})")
    print("  top mess:")
    for c,n,dk,k,tb,ta in sorted([d for d in detail if d[0]=='mess'], key=lambda x:-x[2])[:4]:
        print(f"    {dk:+.3f}  {n}  ({k} ns)")
    print("\n  CAVEATS: per-commit code graph is REAL (not co-edit proxy); attribution noisy (overlapping windows")
    print("  -> population signal only); windows from commit author-dates; clj/cljc only (py missions excluded).")

if __name__ == "__main__":
    main()
