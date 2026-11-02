#!/usr/bin/env python3
"""Bounded AERGISTEL collector. Uses Toolbox's existing rish/relay, never a new server."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import sys
import signal
import subprocess
import tempfile

PREFIX = 'AERGISTEL|'
KINDS = {'MISSED_CLICK', 'ACTION_FAILED', 'SELECTION_FAILED', 'HAND_LOST', 'TRACKING_JUMP',
         'CAMERA_DELAY', 'INFERENCE_STALL', 'PROCESS_RESTART'}
STRINGS = {'record', 'session_id', 'build_id', 'state', 'event_kind', 'severity', 'action',
           'reason_code', 'step', 'outcome', 'hand_id', 'contact_state', 'gesture', 'previous_session_id'}
NUMBERS = {'seq', 'request_id', 'uptime_ms', 'event_uptime_ms', 'wall_clock_ms', 'session_epoch',
           'input_epoch', 'target_x', 'target_y', 'pointer_x', 'pointer_y', 'raw_x', 'raw_y',
           'frame_id', 'camera_age_ms', 'inference_ms', 'frame_gap_ms', 'started_wall_clock_ms',
           'stopped_wall_clock_ms', 'click_attempts', 'click_success', 'missed_clicks', 'action_failures',
           'cancelled', 'submitted_only', 'frame_count', 'frame_age_p95_ms', 'inference_p95_ms',
           'pinch_jump_max', 'hand_switches', 'hand_loss_recovery_p95_ms', 'edge_samples',
           'edge_tracked', 'trace_dropped', 'queue_dropped', 'before_count', 'after_count'}


def parse_line(line):
    if PREFIX not in line:
        return None
    raw = line.split(PREFIX, 1)[1].strip()
    if len(raw) > 3500:
        return None
    try:
        data = json.loads(raw, parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
    except (ValueError, TypeError):
        return None
    if not isinstance(data, dict) or data.get('schema') != 1:
        return None
    if data.get('record') not in {'event', 'session', 'trace'}:
        return None
    if not all(isinstance(data.get(k), str) and re.fullmatch(r'[\w:.-]{1,160}', data[k])
               for k in ('session_id', 'build_id')):
        return None
    if type(data.get('seq')) is not int or data['seq'] < 0:
        return None
    if data['record'] == 'event' and data.get('event_kind') not in KINDS:
        return None
    result = {'schema': 1}
    for key, value in data.items():
        if key in STRINGS and isinstance(value, str) and re.fullmatch(r'[\w: .-]{0,160}', value):
            result[key] = value
        elif key in NUMBERS and type(value) in (int, float) and math.isfinite(value):
            result[key] = value
        elif key == 'clean' and type(value) is bool:
            result[key] = value
    return result


def atomic_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix='.', suffix='.tmp', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as stream:
            json.dump(data, stream, separators=(',', ':'), allow_nan=False)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def read_json(path, default=None):
    try:
        return json.loads(path.read_text())
    except (OSError, ValueError):
        return default


def metrics(session):
    result = {k: session[k] for k in NUMBERS if k in session}
    attempts = result.get('click_attempts', 0)
    result['click_success_rate'] = result.get('click_success', 0) / attempts if attempts else None
    edges = result.get('edge_samples', 0)
    result['edge_tracking_rate'] = result.get('edge_tracked', 0) / edges if edges else None
    return result


def compare_sessions(before, after):
    a, b = metrics(before), metrics(after)
    names = ('click_success_rate', 'missed_clicks', 'pinch_jump_max', 'hand_switches',
             'frame_age_p95_ms', 'inference_p95_ms', 'hand_loss_recovery_p95_ms', 'edge_tracking_rate')
    result = {}
    for key in names:
        old, new = a.get(key), b.get(key)
        assessment = 'insufficient_evidence'
        delta = None
        if old is not None and new is not None:
            delta = new - old
            higher_better = key in ('click_success_rate', 'edge_tracking_rate')
            assessment = 'unchanged' if delta == 0 else ('improved' if (delta > 0) == higher_better else 'regressed')
        result[key] = dict(before=old, after=new, delta=delta, assessment=assessment)
    return result


def regression_fixture(trace):
    """Generate expectations from recorded terminal evidence, never from requests alone."""
    records = sorted(trace.get('records', []), key=lambda r: (r.get('uptime_ms', r.get('event_uptime_ms', 0)), r.get('seq', 0)))
    terminal_ids = {r.get('request_id') for r in records if r.get('step') == 'terminal'}
    terminals, kinds = [], []
    for item in records:
        if item.get('step') == 'terminal':
            terminals.append(item['outcome'])
        elif item.get('step') == 'outcome' and item.get('outcome') == 'COMPLETED' and item.get('request_id') not in terminal_ids:
            terminals.append('SUCCESS')
        if item.get('record') == 'event':
            kinds.append(item['event_kind'])
    records += [dict(step='tick', uptime_ms=r['uptime_ms'], seq=r.get('seq', 0)) for r in records
                if r.get('step') == 'terminal' and r.get('outcome') == 'TIMED_OUT']
    records.sort(key=lambda r: (r.get('uptime_ms', r.get('event_uptime_ms', 0)), r.get('seq', 0)))
    return dict(schema=1, provenance='captured_device' if trace.get('captured_device') else 'synthetic_contract',
                session_id=trace.get('session_id'), build_id=trace.get('build_id'), records=records,
                expected=dict(terminals=terminals, event_kinds=kinds))


class Collector:
    def __init__(self, root, max_events=20, max_sessions=10, max_trace=3600):
        self.root = Path(root)
        self.max_events, self.max_sessions, self.max_trace = max_events, max_sessions, max_trace
        if min(max_events, max_sessions, max_trace) < 1:
            raise ValueError('retention must be positive')
        self.seen = read_json(self.root / 'dedup.json', [])

    def accept(self, line):
        data = parse_line(line)
        if data is None:
            return False
        key = f"{data['session_id']}:{data['seq']}"
        if key in self.seen:
            return False
        category = data['record']
        # Write idempotently first, persist dedup last: an interrupted write is safely replayed.
        filename = hashlib.sha256(key.encode()).hexdigest() + '.json'
        if category == 'event':
            atomic_json(self.root / 'events' / filename, data)
            atomic_json(self.root / 'latest-event.json', data)
            self._rotate('events', self.max_events)
        elif category == 'session':
            previous = read_json(self.root / 'sessions' / (data['session_id'] + '.json'), {})
            if previous.get('seq', -1) >= data['seq']:
                return False
            atomic_json(self.root / 'sessions' / (data['session_id'] + '.json'), data)
            atomic_json(self.root / 'session.json', data)
            self._rotate('sessions', self.max_sessions)
            if data.get('clean') is True:
                candidates = [read_json(p, {}) for p in (self.root / 'sessions').glob('*.json')]
                candidates = [s for s in candidates if s.get('clean') is True and
                              s.get('session_id') != data['session_id'] and s.get('build_id') != data['build_id'] and
                              s.get('started_wall_clock_ms', 0) < data.get('started_wall_clock_ms', 0)]
                if candidates:
                    before = max(candidates, key=lambda s: s.get('started_wall_clock_ms', 0))
                    atomic_json(self.root / 'comparison.json', dict(before_build=before['build_id'],
                                after_build=data['build_id'], before_session=before['session_id'],
                                after_session=data['session_id'], metrics=compare_sessions(before, data)))
        if category in {'trace', 'event'}:
            path = self.root / 'traces' / (data['session_id'] + '.json')
            trace = read_json(path, dict(schema=1, captured_device=True, session_id=data['session_id'],
                                         build_id=data['build_id'], records=[], truncated=False))
            trace['records'] = [x for x in trace['records'] if x.get('seq') != data['seq']] + [data]
            if len(trace['records']) > self.max_trace:
                trace['records'] = trace['records'][-self.max_trace:]
                trace['truncated'] = True
            atomic_json(path, trace)
            atomic_json(self.root / 'latest-trace.json', {'session_id': data['session_id']})
            self._rotate('traces', self.max_sessions)
        self.seen = (self.seen + [key])[-20_000:]
        atomic_json(self.root / 'dedup.json', self.seen)
        atomic_json(self.root / 'latest.json', self.view('latest'))
        return True

    def bridge_crash(self, crash):
        if not isinstance(crash, dict) or crash.get('package') != 'com.airgesture.control':
            return False
        timestamp = crash.get('timestamp')
        if type(timestamp) not in (int, float) or not math.isfinite(timestamp) or timestamp < 0:
            return False
        digest = hashlib.sha256(str(timestamp).encode()).hexdigest()[:16]
        session = read_json(self.root / 'session.json', {})
        payload = dict(schema=1, record='event', session_id='crash-' + digest,
                       build_id=session.get('build_id', 'unknown'), seq=0,
                       event_kind='PROCESS_RESTART', reason_code='recorded_crash', wall_clock_ms=timestamp)
        return self.accept(PREFIX + json.dumps(payload))

    def _rotate(self, directory, count):
        files = sorted((self.root / directory).glob('*.json'), key=lambda p: (p.stat().st_mtime_ns, p.name))
        for path in files[:-count]:
            path.unlink()

    def view(self, name):
        session = read_json(self.root / 'session.json', {})
        if name in {'manifest', 'session'}:
            return session
        if name == 'event':
            return read_json(self.root / 'latest-event.json', {})
        if name == 'history':
            return [read_json(p) for p in sorted((self.root / 'events').glob('*.json'), key=lambda p: p.stat().st_mtime_ns)]
        if name == 'replay':
            trace_id = session.get('session_id') or read_json(self.root / 'latest-trace.json', {}).get('session_id', '')
            return read_json(self.root / 'traces' / (trace_id + '.json'), {})
        clean = session.get('clean')
        state = session.get('state')
        termination = ('Session finished normally' if clean is True else 'Previous session ended unexpectedly'
                       if state == 'unclean' else 'Session is recording' if state == 'recording' else 'No session received yet')
        return dict(schema=1, session_id=session.get('session_id'), build_id=session.get('build_id'),
                    summary=dict(termination=termination,
                                 clicks=f"{session.get('click_success', 0)} completed; {session.get('missed_clicks', 0)} missed",
                                 evidence='Android completion callbacks prove dispatched taps completed. Intended attempts before recognition require recorded contact evidence.'),
                    metrics=metrics(session), manifest=session,
                    comparison=read_json(self.root / 'comparison.json', {}), latest_event=self.view('event'), history_count=len(list((self.root / 'events').glob('*.json'))))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['watch', 'collect', 'latest', 'session', 'event', 'manifest', 'history', 'status', 'replay', 'regression', 'compare'])
    parser.add_argument('--root', type=Path, default=Path.home() / 'agent-gates/aergis-live')
    parser.add_argument('--rish', default='rish')
    parser.add_argument('--before', type=Path)
    parser.add_argument('--after', type=Path)
    parser.add_argument('--json', action='store_true', help='Structured output (the default)')
    args = parser.parse_args(argv)
    collector = Collector(args.root)
    if args.command in {'collect', 'watch'}:
        child = None
        if args.command == 'watch':
            child = subprocess.Popen([args.rish, '-c', '/system/bin/logcat -v raw -T 2000 -s AergisDevLoop:I *:S'],
                                     stdout=subprocess.PIPE, text=True)
            signal.signal(signal.SIGTERM, lambda *_: child.terminate())
        stream = child.stdout if child else sys.stdin
        for line in stream:
            try:
                collector.accept(line)
            except OSError as exc:
                print(json.dumps(dict(state='collector_error', error=str(exc))), file=sys.stderr)
        return child.wait() if child else 0
    if args.command == 'compare':
        if not args.before or not args.after:
            parser.error('compare requires --before and --after')
        result = compare_sessions(read_json(args.before, {}), read_json(args.after, {}))
    elif args.command == 'regression':
        result = regression_fixture(collector.view('replay'))
    else:
        result = collector.view(args.command)
    print(json.dumps(result, indent=2, allow_nan=False))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
