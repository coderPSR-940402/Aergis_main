"""Verify the committed preview identity survives fresh checkouts; no production signing keys."""
import hashlib
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
KEY = ROOT / 'ci' / 'aergis-preview.keystore'

class PreviewSigningTest(unittest.TestCase):
    def test_clean_builds_share_one_valid_signing_identity(self):
        self.assertTrue(KEY.is_file(), 'No permanent preview signing key; clean CI runners generate incompatible APK identities')
        with tempfile.TemporaryDirectory() as directory:
            certificates = []
            for build in ('first', 'second'):
                destination = pathlib.Path(directory) / build / 'preview.keystore'
                destination.parent.mkdir()
                shutil.copyfile(KEY, destination)
                result = subprocess.run(['keytool', '-exportcert', '-keystore', str(destination),
                    '-storepass', 'android', '-alias', 'androiddebugkey'], capture_output=True, check=True)
                self.assertGreater(len(result.stdout), 500)
                self.assertEqual(hashlib.sha256(result.stdout).hexdigest(),
                    (ROOT / 'ci' / 'preview-certificate.sha256').read_text().strip())
                # Import requires the private entry and its password, unlike a certificate-only export.
                subprocess.run(['keytool', '-importkeystore', '-srckeystore', str(destination),
                    '-srcstorepass', 'android', '-srckeypass', 'android', '-srcalias', 'androiddebugkey',
                    '-destkeystore', str(destination.with_suffix('.p12')), '-deststoretype', 'PKCS12',
                    '-deststorepass', 'android', '-destkeypass', 'android', '-noprompt'],
                    capture_output=True, check=True)
                certificates.append(result.stdout)
            self.assertEqual(certificates[0], certificates[1])

if __name__ == '__main__':
    unittest.main()
