#!/usr/bin/env python3
"""Verify install metadata and both pinned inference models in current APKs."""
import argparse
import hashlib
import pathlib
import re
import subprocess
import zipfile

MODEL = 'assets/gesture_recognizer.task'
MODEL_SHA256 = '97952348cf6a6a4915c2ea1496b4b37ebabc50cbbf80571435643c455f2b0482'
MODEL_SIZE = 8_373_440
MODEL_CONTRACTS = (
    (MODEL, MODEL_SIZE, MODEL_SHA256),
    ('assets/face_landmarker.task', 3_758_596,
     '64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff'),
)


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
    """Gesture-only contract retained for historical production signing/rollback."""
    _verify_assets(apk, ((MODEL, MODEL_SIZE, MODEL_SHA256),))


def verify_models(apk):
    """Current builds must contain exactly one intact copy of each bundled model."""
    _verify_assets(apk, MODEL_CONTRACTS)


def _verify_assets(apk, contracts):
    with zipfile.ZipFile(apk) as archive:
        for name, size, expected_digest in contracts:
            models = [entry for entry in archive.infolist() if entry.filename == name]
            if len(models) != 1 or models[0].file_size != size:
                raise ValueError(f'APK must contain one complete {name}')
            with archive.open(models[0]) as model:
                digest = hashlib.file_digest(model, 'sha256').hexdigest()
            if digest != expected_digest:
                raise ValueError(f'APK {name} SHA-256 mismatch')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=pathlib.Path)
    parser.add_argument('--aapt', required=True)
    parser.add_argument('--version-code', required=True, type=int)
    parser.add_argument('--version-name', required=True)
    parser.add_argument('--legacy-gesture-only', action='store_true',
                        help='Use only for integrated pre-Gaze sources that do not bundle a face model')
    args = parser.parse_args()
    badging = subprocess.run([args.aapt, 'dump', 'badging', str(args.apk)],
                             check=True, capture_output=True, text=True).stdout
    verify_metadata(badging, args.version_code, args.version_name)
    if args.legacy_gesture_only:
        verify_model(args.apk)
    else:
        verify_models(args.apk)
    models = 'legacy gesture model' if args.legacy_gesture_only else 'both inference models'
    print(f'{args.apk}: package, version and {models} verified')


if __name__ == '__main__':
    main()
