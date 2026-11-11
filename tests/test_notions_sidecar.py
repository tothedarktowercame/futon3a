"""Reload test for notions_search.py --resident --sidecar (packet 3).

A temp library + temp sidecar built with --embed-stub; a resident started on
it in stub mode; add one pattern file, rebuild, and the next query returns
the new id WITHOUT restarting the resident.

Run: .venv/bin/python3 -m unittest discover -s tests -v
"""
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FUTON3 = Path.home() / "code/futon3"
PY = ROOT / ".venv/bin/python3"
FIXTURES = ["library/cascade-construction/read-what-exists-first.flexiarg",
            "library/agent/handoff-preserves-context.flexiarg"]
NEW = "library/proof-search/typed-hole-as-frontier.flexiarg"


class ResidentSidecarTest(unittest.TestCase):
    def setUp(self):
        self.td = Path(tempfile.mkdtemp(prefix="notions-sidecar-test-"))
        self.lib = self.td / "futon3"
        for rel in FIXTURES:
            dst = self.lib / rel
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(FUTON3 / rel, dst)
        self.db = str(self.td / "sidecar.sqlite")
        self.proc = None

    def tearDown(self):
        if self.proc:
            self.proc.kill()
            self.proc.wait()
        shutil.rmtree(self.td, ignore_errors=True)

    def build(self):
        subprocess.run(
            [str(PY), str(ROOT / "scripts/pattern_index.py"), "build",
             "--futon3-root", str(self.lib), "--db", self.db, "--embed-stub"],
            cwd=str(ROOT), check=True, capture_output=True, text=True)

    def query(self, text, top=10):
        self.proc.stdin.write(json.dumps({"query": text, "top": top}) + "\n")
        self.proc.stdin.flush()
        line = self.proc.stdout.readline()
        return json.loads(line)

    def test_reload_on_checkpoint_move(self):
        self.build()
        self.proc = subprocess.Popen(
            [str(PY), "-u", str(ROOT / "scripts/notions_search.py"),
             "--resident", "--sidecar", self.db, "--embed-stub"],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
        self.assertEqual(json.loads(self.proc.stdout.readline()),
                         {"resident": "ready"})
        before = {r["id"] for r in self.query("anything")}
        self.assertNotIn("proof-search/typed-hole-as-frontier", before)
        # add a pattern file and rebuild; the resident must see it by itself
        dst = self.lib / NEW
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(FUTON3 / NEW, dst)
        self.build()
        after = {r["id"] for r in self.query("anything")}
        self.assertIn("proof-search/typed-hole-as-frontier", after)


if __name__ == "__main__":
    unittest.main()
