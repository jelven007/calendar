from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import json


ROOT = Path(__file__).resolve().parent.parent


class LicenseAssemblyTest(unittest.TestCase):
    def test_unknown_license_preserves_the_previous_notice_file(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            pom_dir = directory / "cache" / "example" / "unknown" / "1" / "hash"
            pom_dir.mkdir(parents=True)
            (pom_dir / "unknown.pom").write_text(
                '<project xmlns="http://maven.apache.org/POM/4.0.0">'
                '<licenses><license><name>Unreviewed-License</name></license></licenses></project>'
            )
            inventory = directory / "inventory.json"
            inventory.write_text(json.dumps([{"coordinate": "example:unknown:1"}]))
            output = directory / "notices.txt"
            output.write_text("previous notices")
            result = subprocess.run(
                [sys.executable, str(ROOT / "scripts" / "assemble_licenses.py"),
                 "--inventory", str(inventory), "--cache", str(directory / "cache"),
                 "--output", str(output)], capture_output=True, text=True,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Unreviewed-License", result.stderr)
            self.assertEqual(output.read_text(), "previous notices")


if __name__ == "__main__":
    unittest.main()
