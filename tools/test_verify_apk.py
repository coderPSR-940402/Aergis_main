import pathlib
import tempfile
import unittest
import zipfile
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


if __name__ == '__main__':
    unittest.main()
