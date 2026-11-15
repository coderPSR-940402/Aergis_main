#!/usr/bin/env python3
"""Verify a Gradle candidate against fresh official repository artifact downloads.

This never writes trusted metadata, uses no Gradle cache, and does not establish
publisher identity or scan vulnerabilities. Repository bytes can change; review
this report and the metadata diff before accepting new trusted checksums.
"""
import argparse
import concurrent.futures
import hashlib
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

REPOSITORIES = (
    'https://dl.google.com/dl/android/maven2/',
    'https://repo.maven.apache.org/maven2/',
    'https://plugins.gradle.org/m2/',
)
# Explicit hosts only, including official plugin artifact CDN and Central redirect.
OFFICIAL_HOSTS = frozenset((
    'dl.google.com', 'dl.googleusercontent.com', 'repo.maven.apache.org',
    'plugins.gradle.org', 'plugins-artifacts.gradle.org', 'repo.gradle.org',
))
NAMESPACE = 'https://schema.gradle.org/dependency-verification'
MAX_METADATA_BYTES = 16 * 1024 * 1024
MAX_ARTIFACT_BYTES = 256 * 1024 * 1024
EXPECTED_SETTINGS = '''pluginManagement {
 repositories { google() mavenCentral() gradlePluginPortal() }
}
dependencyResolutionManagement {
 repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
 repositories { google() mavenCentral() }
}
rootProject.name = "Aergis"
include(":app")'''


@dataclass(frozen=True)
class Entry:
    path: str
    sha256: str


def validate_settings(settings):
    # Deliberately fail closed if the checked-in repository configuration changes.
    # This is a small pinned Kotlin settings file, not a general Kotlin parser.
    def normalize(text):
        text = re.sub(r'/\*.*?\*/|//[^\n]*', '', text, flags=re.S)
        return re.sub(r'[\s;]+', '', text)
    if normalize(settings) != normalize(EXPECTED_SETTINGS):
        raise ValueError('settings.gradle.kts changed: review and update the official repository policy')


def parse_metadata(path):
    data = Path(path).read_bytes()
    if len(data) > MAX_METADATA_BYTES or b'<!' in data:
        raise ValueError('metadata too large or contains forbidden declarations')
    root = ET.fromstring(data)
    def tag(element):
        prefix = '{' + NAMESPACE + '}'
        if not element.tag.startswith(prefix):
            raise ValueError('unexpected metadata namespace')
        return element.tag[len(prefix):]
    def structure(element, name, attributes=()):
        if tag(element) != name or set(element.attrib) - set(attributes):
            raise ValueError('unexpected metadata element or attributes: ' + name)
        if element.text and element.text.strip():
            raise ValueError('unexpected metadata text: ' + name)
    structure(root, 'verification-metadata', ('{http://www.w3.org/2001/XMLSchema-instance}schemaLocation',))
    if [tag(child) for child in root] != ['configuration', 'components']:
        raise ValueError('expected configuration and components only')
    config, components = root
    structure(config, 'configuration')
    if [tag(child) for child in config] != ['verify-metadata', 'verify-signatures']:
        raise ValueError('trusted/ignored artifacts, keys, or unknown configuration are forbidden')
    for child, expected in zip(config, ('true', 'false')):
        if child.attrib or len(child) or (child.text or '').strip() != expected:
            raise ValueError('require verify-metadata=true and verify-signatures=false')
    structure(components, 'components')
    entries = []
    seen_components = set()
    seen_paths = set()
    safe = re.compile(r'[A-Za-z0-9_+.-]+\Z')
    def segment(value):
        if not value or not safe.fullmatch(value) or '..' in value or value == '.':
            raise ValueError('unsafe Maven coordinate or artifact filename')
        return value
    for component in components:
        structure(component, 'component', ('group', 'name', 'version'))
        if set(component.attrib) != {'group', 'name', 'version'} or not len(component):
            raise ValueError('incomplete component')
        group = component.attrib['group']
        group_path = '/'.join(segment(part) for part in group.split('.'))
        name = segment(component.attrib['name'])
        version = segment(component.attrib['version'])
        key = (group, name, version)
        if key in seen_components:
            raise ValueError('duplicate component')
        seen_components.add(key)
        for artifact in component:
            structure(artifact, 'artifact', ('name',))
            filename = segment(artifact.attrib.get('name'))
            if len(artifact) != 1:
                raise ValueError('require exactly one SHA256 per artifact')
            checksum = artifact[0]
            structure(checksum, 'sha256', ('value', 'origin'))
            digest = checksum.attrib.get('value', '')
            if len(checksum) or not re.fullmatch(r'[0-9a-fA-F]{64}', digest):
                raise ValueError('require exact SHA256 without alternate checksums')
            artifact_path = '/'.join((group_path, name, version, filename))
            if artifact_path in seen_paths:
                raise ValueError('duplicate artifact')
            seen_paths.add(artifact_path)
            entries.append(Entry(artifact_path, digest.lower()))
    if not entries:
        raise ValueError('candidate contains no artifacts')
    return entries


def validate_url(url):
    parsed = urllib.parse.urlsplit(url)
    if (parsed.scheme != 'https' or parsed.hostname not in OFFICIAL_HOSTS
            or parsed.username is not None or parsed.password is not None
            or parsed.port not in (None, 443) or parsed.query or parsed.fragment):
        raise ValueError('URL is outside the official HTTPS repository allowlist')


class OfficialRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        validate_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def download_hash(url, max_bytes=MAX_ARTIFACT_BYTES):
    validate_url(url)
    # No ambient proxy credentials, netrc, cookies, or cache are used.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), OfficialRedirectHandler())
    for attempt in range(3):
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'dependency-bootstrap-verifier/1', 'Accept-Encoding': 'identity'})
            with opener.open(request, timeout=30) as response:
                length = response.headers.get('Content-Length')
                if length and int(length) > max_bytes:
                    raise ValueError('artifact exceeds download limit')
                digest = hashlib.sha256()
                total = 0
                deadline = time.monotonic() + 120
                while True:
                    chunk = response.read(min(128 * 1024, max_bytes + 1 - total))
                    if not chunk:
                        break
                    total += len(chunk)
                    if total > max_bytes:
                        raise ValueError('artifact exceeds download limit')
                    if time.monotonic() > deadline:
                        raise TimeoutError('artifact download exceeded time limit')
                    digest.update(chunk)
                return digest.hexdigest()
        except urllib.error.HTTPError as error:
            if error.code not in (408, 429, 500, 502, 503, 504) or attempt == 2:
                raise
        except (urllib.error.URLError, TimeoutError, OSError):
            if attempt == 2:
                raise
        except ValueError as error:
            raise OSError(str(error)) from error
        time.sleep(attempt + 1)


def verify_entry(entry, fetch=download_hash):
    errors = []
    for repository in REPOSITORIES:
        url = repository + entry.path
        try:
            actual = fetch(url)
        except (OSError, ValueError) as error:
            # Avoid copying arbitrary response bodies, credentials or URLs into reports.
            code = getattr(error, 'code', None)
            errors.append({'repository': repository, 'error': 'HTTP ' + str(code) if code else type(error).__name__})
            continue
        return {'artifact': entry.path, 'expected_sha256': entry.sha256,
                'actual_sha256': actual, 'repository': repository,
                'method': 'download-sha256',
                'status': 'verified' if actual == entry.sha256 else 'mismatch'}
    return {'artifact': entry.path, 'expected_sha256': entry.sha256,
            'status': 'unavailable', 'attempts': errors}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('candidate', type=Path)
    parser.add_argument('--settings', type=Path, default=Path('settings.gradle.kts'))
    parser.add_argument('--report', type=Path, default=Path('dependency-verification-report.json'))
    parser.add_argument('--workers', type=int, choices=range(1, 17), default=8)
    args = parser.parse_args(argv)
    report = {'policy': 'fresh official repository downloads; no cache or trusted metadata writes'}
    try:
        validate_settings(args.settings.read_text())
        entries = parse_metadata(args.candidate)
        with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
            results = list(executor.map(verify_entry, entries))
        counts = Counter(result['status'] for result in results)
        report.update(candidate_sha256=hashlib.sha256(args.candidate.read_bytes()).hexdigest(),
                      summary={status: counts[status] for status in ('verified', 'mismatch', 'unavailable')},
                      artifacts=results)
        passed = counts['verified'] == len(entries)
    except (OSError, ValueError, ET.ParseError) as error:
        report['error'] = str(error)
        passed = False
    args.report.write_text(json.dumps(report, indent=2, sort_keys=True) + '\n')
    print(json.dumps(report.get('summary', {'error': report.get('error')}), sort_keys=True))
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
