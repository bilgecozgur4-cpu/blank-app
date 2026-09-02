import os
import tempfile
import unittest

from kutalp import agent, db


class MetehanV05Tests(unittest.TestCase):
    def setUp(self):
        self.old_metehan = os.environ.get("METEHAN_DB")
        self.old_kutalp = os.environ.get("KUTALP_DB")
        self.old_key = os.environ.get("OPENAI_API_KEY")
        self.tmp = tempfile.NamedTemporaryFile(suffix=".db", delete=False)
        self.tmp.close()
        os.environ["METEHAN_DB"] = self.tmp.name
        os.environ.pop("KUTALP_DB", None)
        os.environ.pop("OPENAI_API_KEY", None)
        db.init_db()

    def tearDown(self):
        if self.old_metehan is None:
            os.environ.pop("METEHAN_DB", None)
        else:
            os.environ["METEHAN_DB"] = self.old_metehan
        if self.old_kutalp is None:
            os.environ.pop("KUTALP_DB", None)
        else:
            os.environ["KUTALP_DB"] = self.old_kutalp
        if self.old_key is None:
            os.environ.pop("OPENAI_API_KEY", None)
        else:
            os.environ["OPENAI_API_KEY"] = self.old_key
        try:
            os.unlink(self.tmp.name)
        except FileNotFoundError:
            pass

    def test_metehan_db_is_authoritative(self):
        self.assertEqual(str(db._db_path()), self.tmp.name)

    def test_memory_retrieval_finds_relevant_record(self):
        db.add_memory("Buzağı takip cihazı pil ömrü uzun olmalı", "project")
        db.add_memory("Borsa sistemi günlük sinyal üretiyor", "project")
        hits = db.relevant_memories("buzağı cihazı pil", limit=2)
        self.assertTrue(hits)
        self.assertIn("Buzağı", hits[0].text)

    def test_offline_agent_never_proposes_action(self):
        plan = agent.plan_command("Wi-Fi ayarını aç", {"battery_percent": 80}, [])
        self.assertEqual(plan["action"]["type"], "none")
        self.assertFalse(plan["needs_confirmation"])

    def test_normalizer_forces_confirmation_for_actions(self):
        plan = agent._normalize_plan(
            {
                "reply": "Wi-Fi ayarlarını açabilirim.",
                "confidence": 0.9,
                "needs_confirmation": False,
                "action": {"type": "open_settings", "label": "Wi-Fi", "target": "wifi"},
            }
        )
        self.assertTrue(plan["needs_confirmation"])
        self.assertEqual(plan["action"]["type"], "open_settings")


if __name__ == "__main__":
    unittest.main()
