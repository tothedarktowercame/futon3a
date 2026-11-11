"""Tests for scripts/pattern_index.py (packet 1: sidecar + incremental builder).

RECORDED fixture: ~20 real futon3 library files copied into a temp root,
including one .multiarg and one of the 81 `@status accepted` files onboarded
in futon3 a59c721.  Embedding is faked deterministically (hash-based), so no
model load; the embedding TEXT still comes from the real projection parser in
the parity/timing runs, not here.

Run: .venv/bin/python3 -m unittest discover -s tests -v
"""
import importlib.util
import json
import shutil
import sqlite3
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location(
    "pattern_index", ROOT / "scripts" / "pattern_index.py")
pi = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pi)

FUTON3 = Path.home() / "code/futon3"
FIXTURE_FILES = [
    "library/cascade-construction/read-what-exists-first.flexiarg",
    "library/cascade-construction/run-it-on-a-real-case.flexiarg",
    "library/cascade-construction/borrow-a-sibling-cascade.flexiarg",
    "library/cascade-construction/add-a-pattern-when-an-item-fits-no-class.flexiarg",
    "library/cascade-construction/lift-when-three-align.flexiarg",
    "library/pattern-interpretation/declare-the-universe.flexiarg",
    "library/agent/handoff-preserves-context.flexiarg",
    "library/agent/evidence-over-assertion.flexiarg",
    "library/process/built-but-not-wired-invisibility.flexiarg",
    "library/proof-search/typed-hole-as-frontier.flexiarg",
    "library/forward-model/the-forward-model-is-a-pattern-cascade.flexiarg",
    "library/translation/bind-to-the-source.flexiarg",
    "library/math-strategy/construction-before-estimates.flexiarg",
    "library/workshop/records-carry-warrant.flexiarg",
    "library/memory/verify-in-the-serving-process.flexiarg",
    "library/cycle-machine/step-machine.flexiarg",
    "library/futon-theory/single-source-of-truth.flexiarg",
    "library/or/or.multiarg",                       # a real .multiarg
    # one of the 81 onboarded @status accepted files (futon3 a59c721):
    "library/writing-coherence/refuse-the-cut-and-dried-answer.flexiarg",
    "library/writing-coherence/hedged-lift.flexiarg",
]


def fake_embed(model, texts):
    """Deterministic stand-in for the model: hash each text to 8 floats."""
    out = []
    for t in texts:
        d = pi.hashlib.sha256(t.encode()).digest()
        out.append([b / 255.0 for b in d[:8]])
    return out


class PatternIndexTest(unittest.TestCase):
    def setUp(self):
        self.td = Path(tempfile.mkdtemp(prefix="pattern-index-test-"))
        self.root = self.td / "futon3"
        for rel in FIXTURE_FILES:
            src = FUTON3 / rel
            dst = self.root / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, dst)
        self.db = str(self.td / "index.sqlite")
        # embedding texts: id -> text (what the projection would emit;
        # here a fixed per-id string — the real projection is exercised by
        # the parity run, and tests key on change, not content)
        inv = pi.scan_library(self.root)
        self.records = {pid: f"text of {pid}" for pid in inv}

    def tearDown(self):
        shutil.rmtree(self.td, ignore_errors=True)

    def build(self, **kw):
        kw.setdefault("embed_fn", fake_embed)
        kw.setdefault("records", self.records)
        return pi.build(self.root, self.db, **kw)

    def ids(self):
        conn = sqlite3.connect(self.db)
        out = {r[0] for r in conn.execute("SELECT id FROM pattern")}
        conn.close()
        return out

    def test_first_build_indexes_all(self):
        stats = self.build()
        expected = len(self.records)  # sections, incl. the .multiarg's
        self.assertEqual(stats["scanned"], expected)
        self.assertEqual(stats["embedded"], expected)
        conn = sqlite3.connect(self.db)
        n_pat = conn.execute("SELECT count(*) FROM pattern").fetchone()[0]
        n_emb = conn.execute("SELECT count(*) FROM embedding").fetchone()[0]
        drift = json.loads(conn.execute(
            "SELECT drift FROM checkpoint ORDER BY seq DESC LIMIT 1").fetchone()[0])
        # a .multiarg section landed, with lowercase + if:/+ however: parsed
        or_row = conn.execute(
            "SELECT if_ IS NOT NULL AND however_ IS NOT NULL"
            " FROM pattern WHERE id='or/license-laddering'").fetchone()
        acc = conn.execute(
            "SELECT status FROM pattern"
            " WHERE id='writing-coherence/refuse-the-cut-and-dried-answer'"
        ).fetchone()[0]
        conn.close()
        self.assertEqual(n_pat, expected)
        self.assertEqual(n_emb, expected)
        self.assertEqual(drift, {"missing": [], "stale": []})
        self.assertEqual(or_row, (1,))
        self.assertTrue(acc.startswith("accepted"))

    def test_no_change_reembeds_zero(self):
        self.build()
        stats = self.build()
        self.assertEqual(stats["changed"], 0)
        self.assertEqual(stats["embedded"], 0)

    def test_edit_reembeds_exactly_one(self):
        self.build()
        target = self.root / FIXTURE_FILES[0]
        text = target.read_text()
        assert "+ THEN:" in text
        target.write_text(text.replace("+ THEN:", "+ THEN:\n    Amended.", 1))
        stats = self.build()
        self.assertEqual(stats["changed"], 1)
        self.assertEqual(stats["embedded"], 1)

    def test_delete_drops_exactly_one(self):
        self.build()
        (self.root / FIXTURE_FILES[-1]).unlink()
        stats = self.build()
        self.assertEqual(stats["deleted"], 1)
        self.assertEqual(len(self.ids()), len(self.records) - 1)
        conn = sqlite3.connect(self.db)
        n_emb = conn.execute("SELECT count(*) FROM embedding").fetchone()[0]
        conn.close()
        self.assertEqual(n_emb, len(self.records) - 1)

    def test_status_live_drift(self):
        self.build()
        st = pi.status(self.db, self.root)
        self.assertEqual(st["live-drift"], {"missing": [], "stale": []})
        # one extra file in the library after the checkpoint
        extra = self.root / "library/extra/a-new-pattern.flexiarg"
        extra.parent.mkdir(parents=True)
        extra.write_text("@flexiarg extra/a-new-pattern\n! conclusion: x\n")
        st = pi.status(self.db, self.root)
        self.assertEqual(st["live-drift"]["missing"], ["extra/a-new-pattern"])

    def test_failed_build_is_atomic(self):
        """A failure at the checkpoint write (inside the transaction) rolls
        the whole build back: no pattern rows, no embeddings, no checkpoint.
        With the checkpoint written outside the transaction (plant b), the
        pattern rows would survive with no checkpoint — a half-built state."""
        # monkeypatch: library_head returns an unbindable type, so the
        # checkpoint INSERT raises inside the transaction.
        orig = pi.library_head
        pi.library_head = lambda root: {"not": "bindable"}
        try:
            with self.assertRaises(Exception):
                self.build()
        finally:
            pi.library_head = orig
        conn = sqlite3.connect(self.db)
        n_pat = conn.execute("SELECT count(*) FROM pattern").fetchone()[0]
        n_emb = conn.execute("SELECT count(*) FROM embedding").fetchone()[0]
        n_ckpt = conn.execute("SELECT count(*) FROM checkpoint").fetchone()[0]
        conn.close()
        self.assertEqual(n_pat, 0)
        self.assertEqual(n_emb, 0)
        self.assertEqual(n_ckpt, 0)

    def test_failed_embed_leaves_prior_state(self):
        """A build whose embed step dies before the transaction leaves the
        previous build's rows and checkpoints exactly as they were."""
        self.build()
        conn = sqlite3.connect(self.db)
        before_patterns = conn.execute(
            "SELECT id, file_sha256 FROM pattern ORDER BY id").fetchall()
        n_ckpt = conn.execute("SELECT count(*) FROM checkpoint").fetchone()[0]
        conn.close()
        target = self.root / FIXTURE_FILES[0]
        target.write_text(target.read_text() + "\n@keywords changed\n")

        def boom(model, texts):
            raise RuntimeError("embed exploded")

        with self.assertRaises(RuntimeError):
            self.build(embed_fn=boom)
        conn = sqlite3.connect(self.db)
        after_patterns = conn.execute(
            "SELECT id, file_sha256 FROM pattern ORDER BY id").fetchall()
        n_ckpt_after = conn.execute(
            "SELECT count(*) FROM checkpoint").fetchone()[0]
        conn.close()
        self.assertEqual(before_patterns, after_patterns)
        self.assertEqual(n_ckpt, n_ckpt_after)


if __name__ == "__main__":
    unittest.main()
