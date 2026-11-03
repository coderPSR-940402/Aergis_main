#!/usr/bin/env python3
"""Verify the Android observer -> redacted log -> collector -> replay artifact contract."""
import argparse
import json
from pathlib import Path
import tempfile
import base64
import re
import shlex
import subprocess
from urllib.parse import urlsplit, parse_qs
from unittest.mock import patch
try:
    from .aergis_live_collector import Collector, regression_fixture, sync_device
except ImportError:
    from aergis_live_collector import Collector, regression_fixture, sync_device


def validate(lines):
    with tempfile.TemporaryDirectory() as temp:
        collector = Collector(Path(temp))
        for text in lines:
            collector.accept(text)
        session = collector.view('manifest')
        trace = collector.replay()
        records = trace.get('records', [])
        opens = {r['request_id'] for r in records if r.get('step') == 'open'}
        terminals = [r for r in records if r.get('step') == 'terminal']
        failures = collector.view('history')
        if not (session.get('clean') is True and session.get('click_success') == 1 and
                session.get('action_failures') == 1 and len(opens) == 2 and
                {r.get('outcome') for r in terminals} == {'SUCCESS', 'FAILED'} and
                all(r['request_id'] in opens for r in terminals) and
                len(failures) == 1 and failures[0].get('event_kind') == 'ACTION_FAILED' and
                failures[0].get('request_id') in {r['request_id'] for r in terminals if r['outcome'] == 'FAILED'}):
            raise ValueError('Production runtime evidence lacks the expected completed and rejected transaction path')
        fixture = regression_fixture(trace)
        fixture['provenance'] = 'simulated_android_runtime'
        return dict(pipeline='passed', build_id=session['build_id'], session_id=session['session_id'],
                    success=1, correlated_failure=1, clean=True), fixture


def validate_snapshot(lines):
    """Feed actual Android provider pages through the production shell parser and resynchronizer."""
    pages = {}
    for line in lines:
        match = re.search(r'payload=([A-Za-z0-9+/=]+)', line)
        if match:
            data = json.loads(base64.b64decode(match[1], validate=True))
            pages[data.get('offset', 0)] = (data, line)
    if not pages:
        raise ValueError('Missing Android durable snapshot pages')
    first = pages[0][0]
    def shell(argv, **_):
        tokens = shlex.split(argv[-1])
        if tokens[:3] != ['content', 'query', '--uri']:
            raise ValueError('Unexpected durable evidence command')
        params = parse_qs(urlsplit(tokens[3]).query)
        data, output = pages[int(params.get('offset', ['0'])[0])]
        if params.get('session_id', [first['manifest']['session_id']])[0] != data['manifest']['session_id']:
            raise ValueError('Unexpected session in simulated provider export')
        return subprocess.CompletedProcess(argv, 0, output, '')
    with tempfile.TemporaryDirectory() as temp:
        collector = Collector(Path(temp))
        with patch('subprocess.run', side_effect=shell):
            sync_device(collector, 'simulated-rish')
        recovered = ['AERGISTEL|' + json.dumps(collector.view('session'))]
        recovered += ['AERGISTEL|' + json.dumps(r) for r in collector.replay().get('records', [])]
        return validate(recovered)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path)
    parser.add_argument('--fixture', type=Path, required=True)
    parser.add_argument('--snapshot', type=Path)
    args = parser.parse_args()
    result, fixture = validate(args.log.read_text().splitlines())
    if args.snapshot:
        durable_result, durable_fixture = validate_snapshot(args.snapshot.read_text().splitlines())
        if durable_result != result or durable_fixture['expected'] != fixture['expected']:
            raise ValueError('Durable evidence and log transport disagree')
        fixture = durable_fixture
        result['durable_recovery'] = 'passed_without_logcat'
    args.fixture.parent.mkdir(parents=True, exist_ok=True)
    args.fixture.write_text(json.dumps(fixture, indent=2))
    print(json.dumps(result))


if __name__ == '__main__':
    main()
