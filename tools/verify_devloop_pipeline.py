#!/usr/bin/env python3
"""Verify the Android observer -> redacted log -> collector -> replay artifact contract."""
import argparse
import json
from pathlib import Path
import tempfile
try:
    from .aergis_live_collector import Collector, regression_fixture
except ImportError:
    from aergis_live_collector import Collector, regression_fixture


def validate(lines):
    with tempfile.TemporaryDirectory() as temp:
        collector = Collector(Path(temp))
        for text in lines:
            collector.accept(text)
        session = collector.view('manifest')
        trace = collector.view('replay')
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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('log', type=Path)
    parser.add_argument('--fixture', type=Path, required=True)
    args = parser.parse_args()
    result, fixture = validate(args.log.read_text().splitlines())
    args.fixture.parent.mkdir(parents=True, exist_ok=True)
    args.fixture.write_text(json.dumps(fixture, indent=2))
    print(json.dumps(result))


if __name__ == '__main__':
    main()
