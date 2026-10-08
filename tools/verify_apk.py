#!/usr/bin/env python3
"""Verify install metadata and the exact inference model in debug/release APKs."""
import argparse
import hashlib
import pathlib
import re
import subprocess
import zipfile

MODEL = 'assets/gesture_recognizer.task'
MODEL_SHA256 = '97952348cf6a6a4915c2ea1496b4b37ebabc50cbbf80571435643c455f2b0482'
MODEL_SIZE = 8_373_440


def verify_metadata(badging, version_code, version_name):
    package = re.search(r'^package: (.*)$', badging, re.MULTILINE)
    if not package:
        raise ValueError('APK package metadata is missing')
    values = dict(re.findall(r"(\w+)='([^']*)'", package.group(1)))
    expected = dict(name='com.airgesture.control', versionCode=str(version_code), versionName=version_name)
    for key, value in expected.items():
        if values.get(key) != value:
            raise ValueError(f'APK {key} mismatch: expected {value}, got {values.get(key)}')


def verify_model(apk):
    with zipfile.ZipFile(apk) as archive:
        models = [entry for entry in archive.infolist() if entry.filename == MODEL]
        if len(models) != 1 or models[0].file_size != MODEL_SIZE:
            raise ValueError('APK must contain one complete gesture model')
        with archive.open(models[0]) as model:
            digest = hashlib.file_digest(model, 'sha256').hexdigest()
        if digest != MODEL_SHA256:
            raise ValueError('APK gesture model SHA-256 mismatch')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=pathlib.Path)
    parser.add_argument('--aapt', required=True)
    parser.add_argument('--version-code', required=True, type=int)
    parser.add_argument('--version-name', default='0.10.0-preview')
    args = parser.parse_args()
    badging = subprocess.run([args.aapt, 'dump', 'badging', str(args.apk)],
                             check=True, capture_output=True, text=True).stdout
    verify_metadata(badging, args.version_code, args.version_name)
    verify_model(args.apk)
    print(f'{args.apk}: package, version and inference model verified')


if __name__ == '__main__':
    main()
