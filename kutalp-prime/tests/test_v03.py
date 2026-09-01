import os
import tempfile
import unittest

from kutalp import db
from kutalp.realtime import session_config
from kutalp.tools import execute_tool


class KutalpV03Tests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.NamedTemporaryFile(suffix=".db", delete=False)
        self.tmp.close()
        os.environ["KUTALP_DB"] = self.tmp.name
        db.init_db()

    def tearDown(self):
        try:
            os.unlink(self.tmp.name)
        except FileNotFoundError:
            pass

    def test_write_tool_requires_approval(self):
        result = execute_tool(
            "save_memory",
            {"text": "Only save with approval", "kind": "rule"},
            approved=False,
        )
        self.assertFalse(result["ok"])
        self.assertTrue(result["approval_required"])
        self.assertEqual(db.list_memories(), [])

    def test_approved_memory_tool(self):
        result = execute_tool(
            "save_memory",
            {"text": "Scientific decisions", "kind": "rule"},
            approved=True,
        )
        self.assertTrue(result["ok"])
        self.assertEqual(db.list_memories()[0].text, "Scientific decisions")

    def test_task_roundtrip(self):
        tid = db.add_task("Build KUTALP voice", "WebRTC", None)
        self.assertEqual(db.list_tasks("open")[0].id, tid)
        self.assertTrue(db.complete_task(tid))
        self.assertEqual(db.list_tasks("done")[0].status, "done")

    def test_prediction_brier_score(self):
        pid = db.add_prediction("Test succeeds", 0.8, None)
        result = db.resolve_prediction(pid, True)
        self.assertAlmostEqual(result["brier_score"], 0.04, places=8)
        metrics = db.prediction_metrics()
        self.assertEqual(metrics["resolved_predictions"], 1)
        self.assertAlmostEqual(metrics["average_brier_score"], 0.04, places=8)

    def test_realtime_config_has_tools_without_secrets(self):
        cfg = session_config()
        self.assertEqual(cfg["type"], "realtime")
        self.assertIn("audio", cfg)
        self.assertGreaterEqual(len(cfg["tools"]), 5)
        rendered = str(cfg)
        self.assertNotIn("OPENAI_API_KEY", rendered)


if __name__ == "__main__":
    unittest.main()
