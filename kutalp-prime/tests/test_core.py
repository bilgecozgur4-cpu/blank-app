import os
import tempfile
import unittest

from kutalp import db


class KutalpCoreTests(unittest.TestCase):
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

    def test_memory_roundtrip(self):
        mid = db.add_memory("Bilimsel kararları tercih et", "rule")
        memories = db.list_memories()
        self.assertEqual(memories[0].id, mid)
        self.assertIn("Bilimsel", memories[0].text)

    def test_retrieval(self):
        db.add_memory("KUTALP projesinde Red Team zorunlu olsun", "project")
        db.add_memory("Başka bir bilgi", "general")
        result = db.relevant_memories("Red Team nasıl çalışacak?")
        self.assertTrue(result)
        self.assertIn("Red Team", result[0].text)


if __name__ == "__main__":
    unittest.main()
