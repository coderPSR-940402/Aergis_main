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
# Independently reviewed from JetBrains' official v2.4.0 GitHub release API
# on 2026-11-15: asset size 60,351,320 bytes and SHA256 below. This exception
# is one immutable artifact route, not permission to fetch arbitrary GitHub URLs.
REVIEWED_VENDOR_REDIRECTS = {
    'https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/2.4.0/kotlin-compiler-embeddable-2.4.0.jar': {
        'url': 'https://github.com/JetBrains/kotlin/releases/download/v2.4.0/kotlin-compiler-embeddable-2.4.0.jar',
        'sha256': 'c2b25e8c1c93ec416ba4327f5e31eaec0f0c8847241b9353e294b8db9dce564f',
    },
}

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
        # Reject comments and any added syntax instead of attempting to strip
        # Kotlin comments: comment markers inside strings are not comments.
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


class ReviewedVendorDigestMismatch(ValueError):
    def __init__(self, actual_sha256, reviewed_sha256):
        super().__init__('reviewed vendor artifact digest mismatch')
        self.actual_sha256 = actual_sha256
        self.reviewed_sha256 = reviewed_sha256


class URLPolicyError(ValueError):
    def __init__(self, routing=None):
        super().__init__('URL is outside the official HTTPS repository allowlist')
        self.routing = routing or {}


def safe_redirect_routing(source, target):
    """Expose only bounded public domain names and flags, never URL contents."""
    def domain(parsed):
        host = parsed.hostname
        if host is None or not host.isascii() or len(host) > 253:
            return None
        labels = host.split('.')
        if len(labels) < 2 or not any(character.isalpha() for character in labels[-1]):
            return None
        if not all(re.fullmatch(r'[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?', label) for label in labels):
            return None
        return host
    try:
        nondefault_port = target.port not in (None, 443 if target.scheme == 'https' else 80 if target.scheme == 'http' else None)
    except ValueError:
        nondefault_port = True
    return {
        'source_host': domain(source), 'target_host': domain(target),
        'same_path': source.path == target.path,
        'target_scheme': target.scheme if target.scheme in ('https', 'http') else 'other',
        'target_has_userinfo': target.username is not None or target.password is not None,
        'target_has_query': bool(target.query), 'target_has_fragment': bool(target.fragment),
        'target_nondefault_port': nondefault_port,
    }


def validate_url(url):
    parsed = urllib.parse.urlsplit(url)
    if (parsed.scheme != 'https' or parsed.hostname not in OFFICIAL_HOSTS
            or parsed.username is not None or parsed.password is not None
            or parsed.port not in (None, 443) or parsed.query or parsed.fragment):
        raise URLPolicyError()


class OfficialRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        try:
            validate_url(newurl)
        except ValueError:
            # Central serves some large artifacts through this fixed mirror.
            # Accept only a source-bound redirect preserving the exact path;
            # the mirror is deliberately not an allowed starting repository.
            source = urllib.parse.urlsplit(req.full_url if req is not None else '')
            target = urllib.parse.urlsplit(newurl)
            try:
                ports_valid = source.port in (None, 443) and target.port in (None, 443)
            except ValueError:
                ports_valid = False
            valid = (
                source.scheme == target.scheme == 'https'
                and source.hostname == 'repo.maven.apache.org'
                and target.hostname == 'gcp.mirrors.maven.org'
                and ports_valid
                and source.username is None and source.password is None
                and target.username is None and target.password is None
                and not source.query and not source.fragment
                and not target.query and not target.fragment
                and source.path == target.path
            )
            source_url = req.full_url if req is not None else ''
            reviewed = REVIEWED_VENDOR_REDIRECTS.get(source_url)
            vendor_hop = reviewed is not None and newurl == reviewed['url']
            # The exact reviewed vendor asset may issue a signed CDN URL.
            # Query values stay entirely inside urllib and are never reported.
            cdn_hop = (
                source_url in {item['url'] for item in REVIEWED_VENDOR_REDIRECTS.values()}
                and target.scheme == 'https'
                and target.hostname == 'release-assets.githubusercontent.com'
                and ports_valid
                and target.username is None and target.password is None
                and not target.fragment
            )
            if not (valid or vendor_hop or cdn_hop):
                raise URLPolicyError(safe_redirect_routing(source, target)) from None
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def _download(url, max_bytes, collect=False):
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
                chunks = [] if collect else None
                total = 0
                deadline = time.monotonic() + 120
                while True:
                    if time.monotonic() > deadline:
                        raise TimeoutError('artifact download exceeded time limit')
                    # read1 returns after one buffered/raw read. read(size) can
                    # keep receiving slow progress indefinitely before returning.
                    chunk = response.read1(min(128 * 1024, max_bytes + 1 - total))
                    if not chunk:
                        break
                    total += len(chunk)
                    if total > max_bytes:
                        raise ValueError('artifact exceeds download limit')
                    if time.monotonic() > deadline:
                        raise TimeoutError('artifact download exceeded time limit')
                    digest.update(chunk)
                    if collect:
                        chunks.append(chunk)
                actual = digest.hexdigest()
                reviewed = REVIEWED_VENDOR_REDIRECTS.get(url)
                if reviewed is not None and actual != reviewed['sha256']:
                    raise ReviewedVendorDigestMismatch(actual, reviewed['sha256'])
                return b''.join(chunks) if collect else actual
        except urllib.error.HTTPError as error:
            if error.code not in (408, 429, 500, 502, 503, 504) or attempt == 2:
                raise
        except (urllib.error.URLError, TimeoutError, OSError):
            if attempt == 2:
                raise
        except (URLPolicyError, ReviewedVendorDigestMismatch):
            raise
        except ValueError as error:
            raise OSError(str(error)) from error
        time.sleep(attempt + 1)


def download_hash(url, max_bytes=MAX_ARTIFACT_BYTES):
    return _download(url, max_bytes)


def download_module(url):
    return _download(url, 4 * 1024 * 1024, collect=True)


class ModuleLocationError(ValueError):
    """A pinned module is inconsistent or tries to leave its component scope."""


def module_location(entry, repository, pinned, fetch_module=download_module):
    directory, filename = entry.path.rsplit('/', 1)
    component, version = directory.rsplit('/', 1)
    module_name = component.rsplit('/', 1)[1]
    module_path = directory + '/' + module_name + '-' + version + '.module'
    module_entry = pinned.get(module_path)
    if module_entry is None or filename.endswith('.module'):
        return None
    module_url = repository + module_path
    data = fetch_module(module_url)
    if hashlib.sha256(data).hexdigest() != module_entry.sha256:
        raise ModuleLocationError('module-checksum-mismatch')
    try:
        document = json.loads(data)
        if not isinstance(document, dict) or document.get('formatVersion') != '1.1':
            raise ModuleLocationError('unsupported-module-format')
        variants = document.get('variants')
        if not isinstance(variants, list):
            raise ModuleLocationError('invalid-module-document')
        urls = set()
        for variant in variants:
            if not isinstance(variant, dict) or not isinstance(variant.get('files', []), list):
                raise ModuleLocationError('invalid-module-document')
            for file in variant.get('files', []):
                if not isinstance(file, dict):
                    raise ModuleLocationError('invalid-module-document')
                if file.get('name') == filename:
                    if not isinstance(file.get('url'), str):
                        raise ModuleLocationError('unsafe-module-location')
                    urls.add(file['url'])
        if not urls:
            return None
        if len(urls) != 1:
            raise ModuleLocationError('ambiguous-module-location')
        relative = next(iter(urls))
        # Gradle module metadata may rename artifacts or point at a sibling
        # version (e.g. Guava Android). It cannot widen the repository/component.
        if not isinstance(relative, str) or not re.fullmatch(r'[A-Za-z0-9_+./-]+', relative) or relative.startswith('/'):
            raise ModuleLocationError('unsafe-module-location')
        url = urllib.parse.urljoin(module_url, relative)
        prefix = repository + component + '/'
        if not url.startswith(prefix):
            raise ModuleLocationError('module-location-outside-component')
        parts = url[len(prefix):].split('/')
        if len(parts) != 2 or any(not re.fullmatch(r'[A-Za-z0-9_+.-]+', part) or '..' in part or part == '.' for part in parts):
            raise ModuleLocationError('unsafe-module-location')
        validate_url(url)
        return url
    except (KeyError, TypeError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ModuleLocationError('invalid-module-document') from error


def safe_error(error):
    """Return bounded diagnostic categories, never exception bodies or URLs."""
    if isinstance(error, URLPolicyError):
        return {'error': 'url-policy-rejected', **error.routing}
    if isinstance(error, urllib.error.HTTPError):
        code = error.code
        return {'error': 'HTTP ' + str(code) if isinstance(code, int) and 100 <= code <= 599 else 'HTTPError'}
    fixed_messages = {
        'artifact exceeds download limit': 'download-size-limit',
        'reviewed vendor artifact digest mismatch': 'reviewed-vendor-digest-mismatch',
        'URL is outside the official HTTPS repository allowlist': 'url-policy-rejected',
        'artifact download exceeded time limit': 'download-time-limit',
    }
    category = fixed_messages.get(str(error))
    if category:
        return {'error': category}
    if isinstance(error, urllib.error.URLError):
        reason = error.reason
        result = {'error': 'network-error', 'reason_type': type(reason).__name__}
        errno = getattr(reason, 'errno', None)
        if isinstance(errno, int):
            result['errno'] = errno
        return result
    return {'error': type(error).__name__}


def verify_entry(entry, fetch=download_hash, pinned=None, fetch_module=download_module):
    errors = []
    for repository in REPOSITORIES:
        url = repository + entry.path
        try:
            actual = fetch(url)
        except ReviewedVendorDigestMismatch as error:
            return {'artifact': entry.path, 'expected_sha256': entry.sha256,
                    'actual_sha256': error.actual_sha256,
                    'reviewed_release_sha256': error.reviewed_sha256,
                    'repository': repository, 'method': 'download-sha256-reviewed-vendor',
                    'status': 'mismatch'}
        except (OSError, ValueError) as error:
            if getattr(error, 'code', None) == 404 and pinned:
                try:
                    alias = module_location(entry, repository, pinned, fetch_module)
                    if alias is not None:
                        actual = fetch(alias)
                        return {'artifact': entry.path, 'expected_sha256': entry.sha256,
                                'actual_sha256': actual, 'repository': repository,
                                'source_url': alias, 'method': 'download-sha256-pinned-module-location',
                                'status': 'verified' if actual == entry.sha256 else 'mismatch'}
                except ReviewedVendorDigestMismatch as error:
                    return {'artifact': entry.path, 'expected_sha256': entry.sha256,
                            'actual_sha256': error.actual_sha256,
                            'reviewed_release_sha256': error.reviewed_sha256,
                            'repository': repository, 'method': 'download-sha256-reviewed-vendor',
                            'status': 'mismatch'}
                except ModuleLocationError as module_error:
                    # A bad pinned document/location must not be hidden by a
                    # later repository, just like an artifact hash mismatch.
                    return {'artifact': entry.path, 'expected_sha256': entry.sha256,
                            'status': 'unavailable', 'error': str(module_error)}
                except (OSError, ValueError) as alias_error:
                    errors.append({'repository': repository, 'stage': 'module-location-or-artifact',
                                   **safe_error(alias_error)})
            errors.append({'repository': repository, **safe_error(error)})
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
        pinned = {entry.path: entry for entry in entries}
        with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as executor:
            results = list(executor.map(lambda entry: verify_entry(entry, pinned=pinned), entries))
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
