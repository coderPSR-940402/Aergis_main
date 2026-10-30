#!/usr/bin/env python3
"""Exercise production signing with a disposable fixture, never production credentials."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile


def checked(args, env):
    result = subprocess.run([str(arg) for arg in args], env=env, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(f'{Path(args[0]).name} failed: {result.stderr[-4000:]}')
    return result


def smoke(sdk, model):
    repo = Path(__file__).resolve().parent.parent
    tools = sdk/'build-tools/36.0.0'
    for executable in ('aapt2', 'zipalign', 'apksigner'):
        if not (tools/executable).is_file():
            raise ValueError(f'Missing SDK tool: {executable}')
    # Scope every fixture/key/output to one temporary tree outside release artifacts.
    with tempfile.TemporaryDirectory(prefix='aergis-signing-smoke-', dir=os.environ.get('RUNNER_TEMP')) as temporary:
        root = Path(temporary)
        env = {key: value for key, value in os.environ.items() if not key.startswith('PRODUCTION_')}
        env['AERGIS_SMOKE_PASSWORD'] = secrets.token_urlsafe(24)
        keystore = root/'fixture.p12'
        checked(['keytool', '-genkeypair', '-alias', 'fixture', '-keystore', keystore,
                 '-storetype', 'PKCS12', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '2',
                 '-dname', 'CN=Aergis ephemeral signing fixture', '-noprompt',
                 '-storepass:env', 'AERGIS_SMOKE_PASSWORD', '-keypass:env', 'AERGIS_SMOKE_PASSWORD'], env)
        certificate = root/'fixture.der'
        checked(['keytool', '-exportcert', '-alias', 'fixture', '-keystore', keystore,
                 '-storepass:env', 'AERGIS_SMOKE_PASSWORD', '-file', certificate], env)
        fingerprint = hashlib.sha256(certificate.read_bytes()).hexdigest()
        assets = root/'assets'; assets.mkdir()
        shutil.copyfile(model, assets/'gesture_recognizer.task')
        manifest = root/'AndroidManifest.xml'
        manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
 package="com.airgesture.control" android:versionCode="2000000001" android:versionName="1.0-signing-smoke">
 <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>
 <application android:hasCode="false" android:label="Ephemeral signing test"/>
</manifest>''')
        unaligned = root/'unaligned.apk'
        checked([tools/'aapt2', 'link', '-o', unaligned, '--manifest', manifest,
                 '-I', sdk/'platforms/android-36/android.jar', '-A', assets], env)
        aligned = root/'fixture.apk'
        checked([tools/'zipalign', '-f', '4', unaligned, aligned], env)
        source = env.get('GITHUB_SHA', 'a'*40)
        env.update(SOURCE_SHA=source, RELEASE_MODE='release', DATA_COMPATIBLE='false',
                   GITHUB_SHA=source, GITHUB_RUN_NUMBER='1', GITHUB_RUN_ID='signing-smoke',
                   GITHUB_RUN_ATTEMPT='1', GITHUB_REPOSITORY='fixture/signing-smoke',
                   ANDROID_HOME=str(sdk), RUNNER_TEMP=str(root),
                   PRODUCTION_KEYSTORE_BASE64=base64.b64encode(keystore.read_bytes()).decode(),
                   PRODUCTION_STORE_PASSWORD=env['AERGIS_SMOKE_PASSWORD'],
                   PRODUCTION_KEY_PASSWORD=env['AERGIS_SMOKE_PASSWORD'],
                   PRODUCTION_KEY_ALIAS='fixture', PRODUCTION_CERT_SHA256=fingerprint)
        request = dict(source_commit=source, kind='release', data_compatibility_reviewed=False,
                       version_code=2_000_000_001, version_name='1.0-signing-smoke', workflow_commit=source,
                       run_id='signing-smoke', run_attempt='1')
        def invoke(name, pin=fingerprint, corrupt_source=False):
            directory = root/name; directory.mkdir()
            (directory/'ci').mkdir(); (directory/'candidate').mkdir()
            shutil.copyfile(repo/'ci/preview-certificate.sha256', directory/'ci/preview-certificate.sha256')
            shutil.copyfile(aligned, directory/'candidate/app-release-unsigned.apk')
            data = dict(request)
            if corrupt_source:
                data['source_commit'] = ('0' if source[0] != '0' else '1') + source[1:]
            (directory/'candidate/release-request.json').write_text(json.dumps(data))
            signing_env = dict(env, PRODUCTION_CERT_SHA256=pin)
            result = subprocess.run(['python3', str(repo/'tools/production_release.py'), 'sign'],
                                    cwd=directory, env=signing_env, capture_output=True, text=True)
            if list(root.glob('aergis-sign-*')):
                raise AssertionError('Signing helper left temporary key material')
            return directory, result
        valid, result = invoke('valid')
        if result.returncode:
            raise RuntimeError(f'Valid signing fixture failed: {result.stderr}')
        output = valid/'production'
        signed = output/'Aergis-production.apk'
        report = json.loads((output/'release-manifest.json').read_text())
        if (report['certificate_sha256'], report['version_code'], report['kind'], report['package']) != (
                fingerprint, 2_000_000_001, 'release', 'com.airgesture.control'):
            raise AssertionError('Signed fixture manifest has incorrect identity')
        with signed.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        if report['apk_sha256'] != digest or (output/'sha256.txt').read_text() != f'{digest}  Aergis-production.apk\n':
            raise AssertionError('Signed fixture checksum mismatch')
        checked([tools/'apksigner', 'verify', '--min-sdk-version', '26', signed], env)
        wrong_pin = ('0' if fingerprint[0] != '0' else '1') + fingerprint[1:]
        preview_pin = (repo/'ci/preview-certificate.sha256').read_text().strip()
        for name, pin, corrupt in [('wrong-key', wrong_pin, False), ('public-preview', preview_pin, False),
                                   ('wrong-source', fingerprint, True)]:
            directory, failure = invoke(name, pin, corrupt)
            if failure.returncode == 0 or (directory/'production/release-manifest.json').exists():
                raise AssertionError(f'{name} fixture was incorrectly accepted')
        print('Ephemeral SDK signing verified: success, checksum, wrong signer, public preview, provenance and key cleanup')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--model', type=Path, required=True)
    args = parser.parse_args()
    smoke(args.sdk.resolve(), args.model.resolve())


if __name__ == '__main__': main()
