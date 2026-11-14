import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parent.parent
SCRIPT = ROOT / "scripts" / "build_release.py"
KEYTOOL = ROOT / ".toolchains" / "jdk-17" / "Contents" / "Home" / "bin" / "keytool"


class ReleaseSigningTest(unittest.TestCase):
    def prepare(self, directory):
        environment = {key: value for key, value in os.environ.items()
                       if not key.startswith("TIANDILI_")}
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--prepare-only", "--signing-dir", str(directory)],
            env=environment, capture_output=True, text=True,
        )

    def test_generated_key_is_valid_private_and_reused_without_rotation(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary) / "signing"
            first = self.prepare(directory)
            self.assertEqual(first.returncode, 0, first.stderr)
            credentials = json.loads((directory / "signing.json").read_text())
            keystore = directory / "tiandili-release.p12"
            original_digest = hashlib.sha256(keystore.read_bytes()).hexdigest()
            verification = subprocess.run(
                [str(KEYTOOL), "-list", "-keystore", str(keystore),
                 "-storepass:env", "TEST_SIGNING_PASSWORD", "-alias", credentials["alias"]],
                env={**os.environ, "TEST_SIGNING_PASSWORD": credentials["storePassword"]},
                capture_output=True, text=True,
            )
            self.assertEqual(verification.returncode, 0, verification.stderr)
            second = self.prepare(directory)
            self.assertEqual(second.returncode, 0, second.stderr)
            self.assertEqual(hashlib.sha256(keystore.read_bytes()).hexdigest(), original_digest)
            self.assertEqual(directory.stat().st_mode & 0o777, 0o700)
            self.assertEqual(keystore.stat().st_mode & 0o777, 0o600)
            self.assertEqual((directory / "signing.json").stat().st_mode & 0o777, 0o600)
            self.assertTrue(credentials["storePassword"] not in first.stdout + first.stderr
                            + second.stdout + second.stderr)

    def test_missing_credentials_never_replace_an_existing_signing_key(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            keystore = directory / "tiandili-release.p12"
            keystore.write_bytes(b"existing-signing-key")
            result = self.prepare(directory)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("incomplete", result.stderr.lower())
            self.assertEqual(keystore.read_bytes(), b"existing-signing-key")
            self.assertFalse((directory / "signing.json").exists())

    def test_corrupt_key_is_reported_without_leaking_or_replacing_credentials(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            keystore = directory / "tiandili-release.p12"
            keystore.write_bytes(b"invalid-keystore")
            credentials = {"alias": "tiandili", "storePassword": "sensitive-test-password",
                           "keyPassword": "sensitive-test-password"}
            credential_file = directory / "signing.json"
            credential_file.write_text(json.dumps(credentials))
            result = self.prepare(directory)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("verify", result.stderr.lower())
            self.assertTrue(credentials["storePassword"] not in result.stdout + result.stderr)
            self.assertEqual(json.loads(credential_file.read_text()), credentials)
            self.assertEqual(keystore.read_bytes(), b"invalid-keystore")


if __name__ == "__main__":
    unittest.main()
