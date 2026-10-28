import unittest
from production_release import production_version, validate_request, verify_certificate

class ProductionReleaseTests(unittest.TestCase):
    def test_forward_versions_increase_across_rollback(self):
        self.assertGreater(production_version(2), production_version(1))
        for number in (0, -1, 100000001):
            with self.assertRaises(ValueError): production_version(number)
    def test_requires_full_commit_and_reviewed_rollback(self):
        validate_request('a' * 40, 'release', False)
        validate_request('a' * 40, 'rollback', True)
        for source, mode, reviewed in [('main','release',False), ('a'*40,'rollback',False), ('a'*40,'oops',True)]:
            with self.assertRaises(ValueError): validate_request(source,mode,reviewed)
    def test_rejects_preview_wrong_and_multiple_signers(self):
        preview = 'a'*64; production = 'b'*64
        verify_certificate('Signer #1 certificate SHA-256 digest: '+production, production, preview)
        for report, expected in [('Signer #1 certificate SHA-256 digest: '+preview,preview),
                                 ('Signer #1 certificate SHA-256 digest: '+preview,production),
                                 ('Signer #1 certificate SHA-256 digest: '+production+'\nSigner #2 certificate SHA-256 digest: '+production,production)]:
            with self.assertRaises(ValueError): verify_certificate(report, expected, preview)

if __name__ == '__main__': unittest.main()

class IsolatedSigningTests(unittest.TestCase):
    def test_signing_failure_removes_key_and_never_places_passwords_in_arguments(self):
        import base64
        import json
        import os
        from pathlib import Path
        import subprocess
        import tempfile
        from unittest.mock import patch
        import production_release as release
        env = dict(SOURCE_SHA='a'*40, RELEASE_MODE='release', DATA_COMPATIBLE='false',
                   GITHUB_RUN_NUMBER='1', GITHUB_SHA='c'*40, GITHUB_RUN_ID='123', GITHUB_RUN_ATTEMPT='1',
                   GITHUB_REPOSITORY='owner/repo', PRODUCTION_CERT_SHA256='b'*64,
                   PRODUCTION_KEYSTORE_BASE64=base64.b64encode(b'fixture-keystore'*20).decode(),
                   PRODUCTION_KEY_ALIAS='test-alias', PRODUCTION_STORE_PASSWORD='fixture-store-pass',
                   PRODUCTION_KEY_PASSWORD='fixture-key-pass', ANDROID_HOME='/fake-sdk')
        cwd = Path.cwd()
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, env):
            os.environ['RUNNER_TEMP'] = directory
            try:
                os.chdir(directory)
                Path('ci').mkdir(); Path('ci/preview-certificate.sha256').write_text('d'*64)
                Path('candidate').mkdir()
                manifest = release.request(); manifest['version_name'] = '1.0'
                Path('candidate/release-request.json').write_text(json.dumps(manifest))
                def command(args, **kwargs):
                    if 'sign' in args:
                        key = Path(args[args.index('--ks')+1])
                        self.assertTrue(key.is_file())
                        self.assertEqual(0o600,key.stat().st_mode & 0o777)
                        self.assertNotIn('fixture-store-pass',args)
                        self.assertNotIn('fixture-key-pass',args)
                        return subprocess.CompletedProcess(args,1)
                    return subprocess.CompletedProcess(args,0)
                with patch.object(release,'checked_output',return_value='badging'), patch.object(release,'verify_metadata'), patch.object(release,'verify_model'), patch.object(release.subprocess,'run',side_effect=command):
                    with self.assertRaises(ValueError): release.sign()
                self.assertEqual([],list(Path(directory).glob('aergis-sign-*')))
                self.assertEqual([],list(Path('production').glob('*')))
            finally:
                os.chdir(cwd)
