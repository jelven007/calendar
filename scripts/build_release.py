#!/usr/bin/env python3
"""Build a signed APK using a persistent local key or supplied signing variables."""

import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys


ROOT = Path(__file__).resolve().parent.parent
KEYTOOL = ROOT / ".toolchains" / "jdk-17" / "Contents" / "Home" / "bin" / "keytool"
SIGNING_VARIABLES = (
    "TIANDILI_KEYSTORE_PATH", "TIANDILI_KEY_ALIAS",
    "TIANDILI_STORE_PASSWORD", "TIANDILI_KEY_PASSWORD",
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--signing-dir", type=Path, default=ROOT / ".signing")
    parser.add_argument("--prepare-only", action="store_true")
    arguments = parser.parse_args()
    environment = os.environ.copy()
    supplied = [bool(environment.get(key)) for key in SIGNING_VARIABLES]
    if any(supplied) and not all(supplied):
        raise ValueError("Incomplete signing environment; provide all four TIANDILI variables.")

    if not any(supplied):
        directory = arguments.signing_dir.resolve()
        directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        directory.chmod(0o700)
        keystore = directory / "tiandili-release.p12"
        credential_file = directory / "signing.json"
        if keystore.exists() != credential_file.exists():
            raise ValueError("Incomplete local signing files; restore the matching key and credentials.")
        if not keystore.exists():
            password = secrets.token_urlsafe(36)
            credentials = {"alias": "tiandili", "storePassword": password, "keyPassword": password}
            key_environment = {**environment, "TIANDILI_STORE_PASSWORD": password,
                               "TIANDILI_KEY_PASSWORD": password}
            result = subprocess.run(
                [str(KEYTOOL), "-genkeypair", "-keystore", str(keystore),
                 "-storetype", "PKCS12", "-alias", credentials["alias"],
                 "-keyalg", "RSA", "-keysize", "3072", "-validity", "10950",
                 "-dname", "CN=Tiandili Local Release, O=Tiandili, C=CN",
                 "-storepass:env", "TIANDILI_STORE_PASSWORD",
                 "-keypass:env", "TIANDILI_KEY_PASSWORD", "-noprompt"],
                env=key_environment, capture_output=True,
            )
            if result.returncode:
                raise ValueError("Could not generate the local signing key.")
            keystore.chmod(0o600)
            with credential_file.open("x") as output:
                credential_file.chmod(0o600)
                json.dump(credentials, output)
                output.flush()
                os.fsync(output.fileno())
        credentials = json.loads(credential_file.read_text())
        if not all(isinstance(credentials.get(key), str) and credentials[key]
                   for key in ("alias", "storePassword", "keyPassword")):
            raise ValueError("Could not verify the signing credentials.")
        keystore.chmod(0o600)
        credential_file.chmod(0o600)
        environment.update(dict(zip(SIGNING_VARIABLES, (
            str(keystore), credentials["alias"], credentials["storePassword"],
            credentials["keyPassword"],
        ))))

    verification = subprocess.run(
        [str(KEYTOOL), "-list", "-keystore", environment["TIANDILI_KEYSTORE_PATH"],
         "-alias", environment["TIANDILI_KEY_ALIAS"],
         "-storepass:env", "TIANDILI_STORE_PASSWORD"],
        env=environment, capture_output=True,
    )
    if verification.returncode:
        raise ValueError("Could not verify the signing key; existing files were preserved.")
    print("Signing key verified. Keep the key and credentials for future upgrades.", flush=True)
    if arguments.prepare_only:
        return 0
    return subprocess.run(
        [str(ROOT / "scripts" / "gradlew-local.sh"), ":app:assembleRelease", ":app:lintRelease",
         "--console=plain"], cwd=ROOT, env=environment,
    ).returncode


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, KeyError, OSError):
        # Do not include credential values or tool output in diagnostics.
        error = sys.exc_info()[1]
        message = str(error) if type(error) is ValueError and not isinstance(error, json.JSONDecodeError) \
            else "Could not verify local signing files; restore matching key and credentials."
        print(message, file=sys.stderr)
        sys.exit(1)
