import contextlib
import hashlib
import io
import pathlib
import os
import re
import subprocess
import tempfile
import textwrap
import unittest
import warnings
import zipfile
from unittest.mock import patch
import verify_apk
from verify_apk import MODEL, MODEL_SIZE, verify_metadata, verify_model


class ApkContractTest(unittest.TestCase):
    def test_package_and_versions_must_match_on_package_line(self):
        valid = "package: name='com.airgesture.control' versionCode='1234' versionName='0.10.0-preview'"
        verify_metadata(valid, 1234, '0.10.0-preview')
        for bad in (valid.replace("1234", "1235"), valid.replace("com.airgesture.control", "wrong.package"),
                    valid.replace("0.10.0-preview", "old"), "label: " + valid):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                verify_metadata(bad, 1234, '0.10.0-preview')

    def test_missing_partial_or_wrong_model_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = pathlib.Path(directory) / 'test.apk'
            for content in (None, b'partial', bytes(MODEL_SIZE)):
                with zipfile.ZipFile(apk, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
                    if content is not None:
                        archive.writestr(MODEL, content)
                with self.subTest(size=None if content is None else len(content)), self.assertRaises(ValueError):
                    verify_model(apk)


class BundledModelsTest(unittest.TestCase):
    def setUp(self):
        self.gesture = b'gesture fixture'
        self.face = b'face fixture'
        self.contracts = (
            ('assets/gesture_recognizer.task', len(self.gesture), hashlib.sha256(self.gesture).hexdigest()),
            ('assets/face_landmarker.task', len(self.face), hashlib.sha256(self.face).hexdigest()),
        )

    def write_apk(self, apk, entries):
        with warnings.catch_warnings(), zipfile.ZipFile(apk, 'w') as archive:
            warnings.simplefilter('ignore', UserWarning)  # Deliberately duplicate ZIP entry.
            for name, content in entries:
                archive.writestr(name, content)

    def test_both_models_must_be_present_unique_complete_and_pinned(self):
        valid = [(self.contracts[0][0], self.gesture), (self.contracts[1][0], self.face)]
        with tempfile.TemporaryDirectory() as directory:
            apk = pathlib.Path(directory) / 'fixture.apk'
            with patch.object(verify_apk, 'MODEL_CONTRACTS', self.contracts, create=True):
                self.write_apk(apk, valid)
                verify_apk.verify_models(apk)
                for index in (0, 1):
                    name, content = valid[index]
                    for replacement in ([], [(name, content[:-1])], [(name, b'x' * len(content))],
                                        [(name, content), (name, content)]):
                        entries = valid[:index] + replacement + valid[index + 1:]
                        with self.subTest(model=name, entries=len(replacement)):
                            self.write_apk(apk, entries)
                            with self.assertRaises(ValueError):
                                verify_apk.verify_models(apk)

    def test_cli_checks_face_model_instead_of_only_gesture(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = pathlib.Path(directory) / 'missing-face.apk'
            self.write_apk(apk, [(self.contracts[0][0], self.gesture)])
            badging = "package: name='com.airgesture.control' versionCode='1234' versionName='preview'"
            argv = ['verify_apk.py', str(apk), '--aapt', 'aapt', '--version-code', '1234', '--version-name', 'preview']
            with patch('sys.argv', argv), patch.object(verify_apk, 'MODEL_CONTRACTS', self.contracts, create=True), \
                    patch.object(verify_apk, 'MODEL_SIZE', len(self.gesture)), \
                    patch.object(verify_apk, 'MODEL_SHA256', self.contracts[0][2]), \
                    patch.object(verify_apk.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, badging)), \
                    contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaises(ValueError):
                    verify_apk.main()

    def test_model_contracts_match_gradle_pins(self):
        gradle = (pathlib.Path(__file__).resolve().parents[1] / 'app/build.gradle.kts').read_text()
        self.assertEqual({'assets/gesture_recognizer.task', 'assets/face_landmarker.task'},
                         {name for name, _, _ in verify_apk.MODEL_CONTRACTS})
        for name, size, digest in verify_apk.MODEL_CONTRACTS:
            with self.subTest(model=name):
                self.assertIn(name.removeprefix('assets/'), gradle)
                self.assertIn(f'{size:_}L', gradle)
                self.assertIn(f'"{digest}"', gradle)

    def test_legacy_signing_contract_still_accepts_gesture_only_apk(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = pathlib.Path(directory) / 'legacy.apk'
            self.write_apk(apk, [(MODEL, self.gesture)])
            with patch.object(verify_apk, 'MODEL_SIZE', len(self.gesture)), \
                    patch.object(verify_apk, 'MODEL_SHA256', self.contracts[0][2]):
                verify_apk.verify_model(apk)

    def test_legacy_cli_mode_accepts_pre_gaze_model_contract(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = pathlib.Path(directory) / 'legacy.apk'
            self.write_apk(apk, [(MODEL, self.gesture)])
            badging = "package: name='com.airgesture.control' versionCode='1234' versionName='preview'"
            argv = ['verify_apk.py', str(apk), '--aapt', 'aapt', '--version-code', '1234',
                    '--version-name', 'preview', '--legacy-gesture-only']
            with patch('sys.argv', argv), patch.object(verify_apk, 'MODEL_SIZE', len(self.gesture)), \
                    patch.object(verify_apk, 'MODEL_SHA256', self.contracts[0][2]), \
                    patch.object(verify_apk.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, badging)), \
                    contextlib.redirect_stdout(io.StringIO()):
                verify_apk.main()

    def test_production_uses_current_verifier_and_limits_legacy_to_pre_gaze_source(self):
        workflow = (pathlib.Path(__file__).resolve().parents[1] / '.github/workflows/production-release.yml').read_text()
        match = re.search(r'      - name: Verify unsigned candidate\n.*?        run: \|\n((?:          [^\n]*\n)+)',
                          workflow, re.DOTALL)
        self.assertIsNotNone(match)
        commands = textwrap.dedent(match.group(1))
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            (root / 'source/app').mkdir(parents=True)
            captured = root / 'arguments.txt'
            env = dict(os.environ, CAPTURED_ARGS=str(captured), VERSION_CODE='1234',
                       VERSION_NAME='preview', ANDROID_HOME='/fixture-sdk')
            for declaration, legacy in [('val model = "face_landmarker.task"', False),
                                        ('val model = "gesture_recognizer.task"', True)]:
                with self.subTest(legacy=legacy):
                    (root / 'source/app/build.gradle.kts').write_text(declaration)
                    spy = 'python3() { printf "%s\\n" "$@" > "$CAPTURED_ARGS"; };\n'
                    subprocess.run(['bash', '-c', spy + commands], cwd=root, env=env, check=True)
                    args = captured.read_text().splitlines()
                    self.assertEqual('tools/verify_apk.py', args[0])
                    self.assertEqual(legacy, '--legacy-gesture-only' in args)
                    self.assertIn('1234', args)
                    self.assertIn('preview', args)


if __name__ == '__main__':
    unittest.main()
