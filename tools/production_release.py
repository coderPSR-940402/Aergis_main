#!/usr/bin/env python3
"""Build provenance and isolated APK signing; never executes app/Gradle code with secrets."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
from verify_apk import verify_metadata, verify_model


def production_version(run_number):
    if not 1 <= run_number <= 100_000_000:
        raise ValueError('Invalid production workflow run number')
    return 2_000_000_000 + run_number


def validate_request(source, mode, compatible):
    if not re.fullmatch(r'[0-9a-f]{40}', source):
        raise ValueError('Source must be an immutable full commit SHA')
    if mode not in ('release', 'rollback') or (mode == 'rollback' and not compatible):
        raise ValueError('Rollback requires an explicit saved-data compatibility review')


def verify_certificate(report, expected, preview):
    if not re.fullmatch(r'[0-9a-f]{64}', expected) or expected == preview:
        raise ValueError('A pinned private production certificate is required')
    actual = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$', report, re.M)
    if actual != [expected]:
        raise ValueError('Production APK signer mismatch or multiple signers')


def checked_output(args):
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout


def request():
    source = os.environ['SOURCE_SHA']
    mode = os.environ['RELEASE_MODE']
    compatible = os.environ.get('DATA_COMPATIBLE') == 'true'
    validate_request(source, mode, compatible)
    return dict(source_commit=source, kind=mode, data_compatibility_reviewed=compatible,
                version_code=production_version(int(os.environ['GITHUB_RUN_NUMBER'])),
                workflow_commit=os.environ['GITHUB_SHA'], run_id=os.environ['GITHUB_RUN_ID'],
                run_attempt=os.environ['GITHUB_RUN_ATTEMPT'])


def prepare():
    metadata = request()
    # Only already-integrated history can be built; PR merge refs/fork commits fail closed.
    subprocess.run(['git', 'merge-base', '--is-ancestor', metadata['source_commit'], 'HEAD'], check=True)
    subprocess.run(['git', 'worktree', 'add', '--detach', 'source', metadata['source_commit']], check=True)
    gradle = Path('source/app/build.gradle.kts').read_text()
    versions = re.findall(r'^\s*versionName = "([^"\n]+)"$', gradle, re.M)
    if len(versions) != 1 or not re.fullmatch(r'[0-9A-Za-z.+-]+', versions[0]):
        raise ValueError('Source must declare one supported version name')
    metadata['version_name'] = versions[0].removesuffix('-preview')
    Path('release-request.json').write_text(json.dumps(metadata, indent=2)+'\n')
    with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
        output.write(f"version_code={metadata['version_code']}\nversion_name={metadata['version_name']}\n")


def sign():
    metadata = json.loads(Path('candidate/release-request.json').read_text())
    expected_request = request()
    for key, expected in expected_request.items():
        if metadata.get(key) != expected:
            raise ValueError('Candidate provenance does not match this workflow request')
    expected_cert = os.environ['PRODUCTION_CERT_SHA256'].lower().replace(':', '')
    preview = Path('ci/preview-certificate.sha256').read_text().strip()
    # Reject invalid/public pins before loading any key.
    verify_certificate('Signer #1 certificate SHA-256 digest: '+expected_cert, expected_cert, preview)
    apk = Path('candidate/app-release-unsigned.apk')
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
    badging = checked_output([str(tools/'aapt'), 'dump', 'badging', str(apk)])
    verify_metadata(badging, metadata['version_code'], metadata['version_name'])
    verify_model(apk)
    subprocess.run([str(tools/'zipalign'), '-c', '4', str(apk)], check=True)
    output = Path('production'); output.mkdir(exist_ok=True)
    signed = output/'Aergis-production.apk'
    # Keys exist only inside this job's temporary directory, never Gradle or artifacts.
    with tempfile.TemporaryDirectory(prefix='aergis-sign-', dir=os.environ['RUNNER_TEMP']) as directory:
        key = Path(directory)/'release.jks'
        raw = base64.b64decode(os.environ['PRODUCTION_KEYSTORE_BASE64'], validate=True)
        if not 100 <= len(raw) <= 10_000_000:
            raise ValueError('Invalid production keystore size')
        key.write_bytes(raw); key.chmod(0o600)
        command = [str(tools/'apksigner'), 'sign', '--ks', str(key), '--ks-key-alias', os.environ['PRODUCTION_KEY_ALIAS'],
                   '--ks-pass', 'env:PRODUCTION_STORE_PASSWORD', '--key-pass', 'env:PRODUCTION_KEY_PASSWORD',
                   '--out', str(signed), str(apk)]
        result = subprocess.run(command, capture_output=True)
        if result.returncode:
            raise ValueError('Production signing failed; no release published')
    report = checked_output([str(tools/'apksigner'), 'verify', '--verbose', '--print-certs', '--min-sdk-version', '26', str(signed)])
    verify_certificate(report, expected_cert, preview)
    verify_metadata(checked_output([str(tools/'aapt'), 'dump', 'badging', str(signed)]), metadata['version_code'], metadata['version_name'])
    verify_model(signed)
    with signed.open('rb') as apk_stream:
        digest = hashlib.file_digest(apk_stream, 'sha256').hexdigest()
    metadata.update(package='com.airgesture.control', certificate_sha256=expected_cert,
                    apk_sha256=digest,
                    run_url=f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}")
    (output/'release-manifest.json').write_text(json.dumps(metadata, indent=2)+'\n')
    (output/'apk-signing.txt').write_text(report)
    (output/'sha256.txt').write_text(f"{metadata['apk_sha256']}  {signed.name}\n")
    (output/'release-notes.md').write_text(
        f"Production {metadata['kind']} candidate. Source: `{metadata['source_commit']}`. "
        f"Version code: {metadata['version_code']}.\n\n"
        'Keep this draft until exact-APK physical acceptance. Android input completion does not prove target activation. '
        'Rollback rebuilds older source with a higher version code and the same private signer. '
        'Review stored-data compatibility before installation. No automatic baseline promotion.\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=['prepare', 'sign'])
    args = parser.parse_args()
    try:
        (prepare if args.operation == 'prepare' else sign)()
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError):
        # Tool output/exception details can contain signing-provider information.
        raise SystemExit('Production request/build/signature validation failed; no release published') from None


if __name__ == '__main__': main()
