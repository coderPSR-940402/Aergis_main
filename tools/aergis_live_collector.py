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
import base64
import time
import shlex
from urllib.parse import urlencode

PREFIX = 'AERGISTEL|'
KINDS = {'MISSED_CLICK', 'ACTION_FAILED', 'SELECTION_FAILED', 'HAND_LOST', 'TRACKING_JUMP',
         'CAMERA_DELAY', 'INFERENCE_STALL', 'PROCESS_RESTART'}
STRINGS = {'record', 'session_id', 'build_id', 'state', 'event_kind', 'severity', 'action',
           'reason_code', 'step', 'outcome', 'hand_id', 'contact_state', 'gesture', 'previous_session_id', 'gate'}
NUMBERS = {'seq', 'request_id', 'uptime_ms', 'event_uptime_ms', 'wall_clock_ms', 'session_epoch',
           'input_epoch', 'target_x', 'target_y', 'pointer_x', 'pointer_y', 'raw_x', 'raw_y',
           'frame_id', 'camera_age_ms', 'inference_ms', 'frame_gap_ms', 'started_wall_clock_ms',
           'stopped_wall_clock_ms', 'click_attempts', 'click_success', 'missed_clicks', 'action_failures',
           'cancelled', 'submitted_only', 'frame_count', 'frame_age_p95_ms', 'inference_p95_ms',
           'pinch_jump_max', 'hand_switches', 'hand_loss_recovery_p95_ms', 'edge_samples',
           'pre_window_ms', 'post_window_ms', 'context_sample_count', 'context_event_seq', 'edge_tracked', 'trace_dropped', 'queue_dropped', 'before_count', 'after_count',
           'observed_wall_clock_ms', 'finalized_wall_clock_ms', 'contact_id', 'contact_distance', 'contact_depth',
           'contact_observation_frames', 'contact_candidates', 'contacts_linked', 'contacts_without_action',
           'contacts_interrupted', 'pointer_reversals', 'pointer_step_max', 'slow_frame_count',
           'frame_gap_p95_ms', 'source_frame_id'}


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
        elif key in {'clean', 'post_window_complete', 'evidence_complete'} and type(value) is bool:
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
    result['recognized_dispatch_completion_rate'] = result['click_success_rate']
    # Recognition supplies the denominator. Nothing here counts all user intentions.
    result['intended_click_success_rate'] = None
    edges = result.get('edge_samples', 0)
    result['edge_tracking_rate'] = result.get('edge_tracked', 0) / edges if edges else None
    return result


def quality_findings(session):
    """Surface measured symptoms; thresholds describe review budgets, not control gates."""
    findings = []
    def add(code, value, message):
        findings.append(dict(code=code, value=value, message=message))
    age = session.get('frame_age_p95_ms')
    if age is not None and age > 100:
        add('frame_delay', age, f'Frame age p95 is {age} ms, above the 100 ms responsiveness review budget.')
    inference = session.get('inference_p95_ms')
    if inference is not None and inference > 50:
        add('inference_cost', inference, f'Inference p95 is {inference} ms, above the 50 ms processing review budget.')
    recovery = session.get('hand_loss_recovery_p95_ms')
    if recovery is not None and recovery > 500:
        add('hand_loss_recovery', recovery, f'Observed owner return after hand loss p95: {recovery} ms.')
    jump = session.get('pinch_jump_max')
    if jump is not None and jump > .15:
        add('pinch_pointer_jump', jump, f'Largest recorded pointer step during pinch: {jump:.3f} normalized screen units.')
    for field, code, label in (
        ('trace_dropped', 'trace_loss', 'Trace rows discarded by bounded retention'),
        ('queue_dropped', 'observation_loss', 'Observations lost before recording'),
        ('cancelled', 'cancelled_actions', 'Cancelled actions across all action types'),
        ('hand_switches', 'owner_changes', 'Controlling-owner ID changes'),
        ('pointer_reversals', 'pointer_reversals', 'Small rapid pointer direction reversals; deliberate movement is possible'),
        ('contacts_without_action', 'contact_without_action', 'Observed closed-contact episodes without a linked action; intent is unknown'),
    ):
        if session.get(field, 0) > 0:
            add(code, session[field], f'{label}: {session[field]}.')
    return findings


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
    if trace.get('truncated') or trace.get('sync_incomplete'):
        raise ValueError('incomplete trace: bounded retention removed records; collect a shorter complete session')
    records = sorted(trace.get('records', []), key=lambda r: (r.get('uptime_ms', r.get('event_uptime_ms', 0)), r.get('seq', 0)))
    open_ids = {r.get('request_id') for r in records if r.get('step') == 'open'}
    if any(r.get('step') in {'terminal', 'outcome'} and r.get('request_id') not in open_ids for r in records):
        raise ValueError('incomplete trace: an outcome has no opening record')
    terminal_ids = {r.get('request_id') for r in records if r.get('step') == 'terminal'}
    terminals, kinds, observations = [], [], []
    for item in records:
        if item.get('step') == 'terminal':
            terminals.append(item['outcome'])
        elif item.get('step') == 'outcome' and item.get('outcome') == 'COMPLETED' and item.get('request_id') not in terminal_ids:
            terminals.append('SUCCESS')
        if item.get('record') == 'event':
            if item['event_kind'] in {'MISSED_CLICK', 'ACTION_FAILED', 'SELECTION_FAILED'}:
                kinds.append(item['event_kind'])
            else:
                observations.append(item['event_kind'])
    records += [dict(step='tick', uptime_ms=r['uptime_ms'], seq=r.get('seq', 0)) for r in records
                if r.get('step') == 'terminal' and r.get('outcome') == 'TIMED_OUT']
    records.sort(key=lambda r: (r.get('uptime_ms', r.get('event_uptime_ms', 0)), r.get('seq', 0)))
    return dict(schema=1, provenance='captured_device' if trace.get('captured_device') else 'synthetic_contract',
                session_id=trace.get('session_id'), build_id=trace.get('build_id'), records=records,
                replay_scope='interaction_correlation', recorded_observation_kinds=observations,
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
        identity = f"{data['session_id']}:{data['seq']}"
        key = f"{data['record']}|{identity}"
        if key in self.seen:
            return False
        category = data['record']
        # Write idempotently first, persist dedup last: an interrupted write is safely replayed.
        # Keep v1 event filenames stable while namespacing dedup for recovered manifests.
        filename = hashlib.sha256(identity.encode()).hexdigest() + '.json'
        if category == 'event':
            atomic_json(self.root / 'events' / filename, data)
            atomic_json(self.root / 'latest-event.json', data)
            self._rotate('events', self.max_events)
        elif category == 'session':
            previous = read_json(self.root / 'sessions' / (data['session_id'] + '.json'), {})
            if previous.get('seq', -1) >= data['seq']:
                return False
            if previous.get('state') in {'stopped', 'unclean'} and data.get('state') == 'recording':
                return False
            atomic_json(self.root / 'sessions' / (data['session_id'] + '.json'), data)
            current = read_json(self.root / 'session.json', {})
            if (current.get('session_id') == data['session_id'] or not current or
                    data.get('started_wall_clock_ms', 0) >= current.get('started_wall_clock_ms', 0)):
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

    def replay(self, session_id=None):
        session = read_json(self.root / 'session.json', {})
        trace_id = session_id or session.get('session_id') or read_json(self.root / 'latest-trace.json', {}).get('session_id', '')
        if trace_id and not re.fullmatch(r'[\w:.-]{1,160}', trace_id):
            raise ValueError('invalid session identity')
        return read_json(self.root / 'traces' / (trace_id + '.json'), {})

    def view(self, name, session_id=None, offset=0, snapshot=None):
        session = read_json(self.root / 'session.json', {})
        if session_id:
            if not re.fullmatch(r'[\w:.-]{1,160}', session_id):
                raise ValueError('invalid session identity')
            session = read_json(self.root / 'sessions' / (session_id + '.json'), {})
        if name in {'manifest', 'session'}:
            return session
        if name == 'event':
            target = session_id or session.get('session_id')
            if target:
                events = [read_json(p, {}) for p in (self.root / 'events').glob('*.json')]
                return max((e for e in events if e.get('session_id') == target), key=lambda e: e.get('seq', 0), default={})
            return read_json(self.root / 'latest-event.json', {})
        if name == 'history':
            events = [read_json(p) for p in sorted((self.root / 'events').glob('*.json'), key=lambda p: p.stat().st_mtime_ns)]
            return [e for e in events if not session_id or e.get('session_id') == session_id]
        if name == 'replay':
            trace = self.replay(session_id)
            revision = hashlib.sha256(json.dumps(trace, sort_keys=True).encode()).hexdigest()
            if snapshot and snapshot != revision:
                return dict(state='snapshot_changed', snapshot_id=revision, records=[])
            rows = trace.get('records', [])
            if type(offset) is not int or not 0 <= offset <= len(rows):
                raise ValueError('invalid replay offset')
            result = {k: v for k, v in trace.items() if k != 'records'}
            result.update(records=[], snapshot_id=revision, offset=offset, total_records=len(rows), next_offset=None)
            for row in rows[offset:offset + 24]:
                candidate = dict(result, records=result['records'] + [row])
                if len(json.dumps(candidate, indent=2).encode()) > 22000:
                    break
                result['records'].append(row)
            end = offset + len(result['records'])
            result['next_offset'] = end if end < len(rows) else None
            return result
        clean = session.get('clean')
        state = session.get('state')
        heartbeat = session.get('observed_wall_clock_ms', 0)
        fresh = 0 <= int(time.time() * 1000) - heartbeat <= 30_000
        capture_status = 'stale' if state == 'recording' and not fresh else state or 'unknown'
        termination = ('Session finished normally' if clean is True else 'Previous session ended unexpectedly'
                       if state == 'unclean' else 'Recording status is stale; session end has not been recovered'
                       if capture_status == 'stale' else 'Session is recording' if state == 'recording' else 'No session received yet')
        comparison = read_json(self.root / 'comparison.json', {})
        if comparison.get('after_session') != session.get('session_id'):
            comparison = {}
        event = self.view('event')
        if event.get('session_id') != session.get('session_id'):
            event = {}
        history = [read_json(p, {}) for p in (self.root / 'sessions').glob('*.json')]
        history.sort(key=lambda s: s.get('started_wall_clock_ms', 0))
        health = read_json(self.root / 'collector-health.json', {'state': 'unknown'})
        if health.get('state') != 'unknown' and not 0 <= int(time.time() * 1000) - health.get('checked_wall_clock_ms', 0) <= 30_000:
            health = dict(health, state='stale', reason_code='collector_not_reporting')
        return dict(schema=1, session_id=session.get('session_id'), build_id=session.get('build_id'),
                    capture_status=capture_status, collector=health,
                    findings=quality_findings(session),
                    coverage=dict(
                        unrecognized_pinches='geometry_observed_intent_unknown' if session.get('contact_observation_frames', 0) else 'unknown',
                        target_activation='unknown',
                        pointer_jitter='reversals_measured' if 'pointer_reversals' in session else 'unknown',
                        trace='incomplete' if session.get('trace_dropped', 0) or session.get('queue_dropped', 0) else 'no_recorded_loss'),
                    summary=dict(termination=termination,
                                 clicks=f"{session.get('click_success', 0)} Android completions / {session.get('click_attempts', 0)} recognized TAP requests; {session.get('missed_clicks', 0)} completion timeouts",
                                 evidence='Completed callbacks are positive dispatch evidence. Unrecognized intended pinches and activation of the intended screen item are not proven by these counts.'),
                    metrics=metrics(session), manifest=session,
                    comparison=comparison, session_history=history, latest_event=event, history_count=len(list((self.root / 'events').glob('*.json'))))


def sync_device(collector, rish):
    """Recover atomic app files through the existing authorized shell, independently of logcat."""
    def query(**params):
        uri = 'content://com.airgesture.control.devloop/snapshot?' + urlencode(params)
        result = subprocess.run([rish, '-c', 'content query --uri ' + shlex.quote(uri) + ' --projection payload'],
                                capture_output=True, text=True, timeout=20)
        match = re.search(r'payload=([A-Za-z0-9+/=]+)', result.stdout)
        if result.returncode != 0 or not match:
            raise RuntimeError('durable_snapshot_unavailable')
        page = json.loads(base64.b64decode(match[1], validate=True))
        if page.get('schema') != 2 or page.get('state') in {'snapshot_changed', 'snapshot_expired'}:
            raise RuntimeError('snapshot_changed')
        atomic_json(collector.root / 'collector-health.json', dict(schema=1, state='syncing',
                    checked_wall_clock_ms=int(time.time() * 1000), page_offset=params.get('offset', 0)))
        return page

    initial = query(offset=0)
    history = initial.get('session_history', [])
    identities = [s['session_id'] for s in history if s.get('session_id')]
    current = initial.get('manifest', {})
    if current.get('session_id') and current['session_id'] not in identities:
        identities.append(current['session_id'])
    watermarks = read_json(collector.root / 'sync.json', {})
    def digest(trace, seq):
        rows = sorted((r for r in trace.get('records', []) if r.get('seq', 0) <= seq), key=lambda r: r.get('seq', 0))
        return hashlib.sha256(json.dumps(rows, sort_keys=True).encode()).hexdigest()
    for session_id in identities:
        trace_path = collector.root / 'traces' / (session_id + '.json')
        local = read_json(trace_path, {})
        cursor = watermarks.get(session_id, {})
        valid = (isinstance(cursor, dict) and isinstance(local.get('records'), list) and
                 not local.get('sync_incomplete') and cursor.get('digest') == digest(local, cursor.get('seq', 0)))
        after_seq = cursor['seq'] if valid else 0
        if not valid:
            # A cursor without its evidence cannot skip reconstruction; dedup is not storage.
            collector.seen = [k for k in collector.seen if not k.startswith(('trace|' + session_id + ':', 'event|' + session_id + ':'))]
        page = query(session_id=session_id, offset=0, after_seq=after_seq)
        revision = page['snapshot_id']
        manifest = page.get('manifest', {})
        if manifest:
            collector.accept(PREFIX + json.dumps(manifest))
        trace = read_json(trace_path, dict(schema=1, captured_device=True, session_id=session_id,
                                          build_id=manifest.get('build_id'), records=[], truncated=False))
        # Incomplete synchronization is never presented as a replayable complete trace.
        trace.update(sync_incomplete=True)
        atomic_json(trace_path, trace)
        for _ in range(200):
            if page.get('snapshot_id') != revision:
                raise RuntimeError('snapshot_changed')
            for row in page.get('records', []):
                if row.get('session_id') != session_id:
                    raise RuntimeError('snapshot_identity_mismatch')
                collector.accept(PREFIX + json.dumps(row))
            if page.get('next_offset') is None:
                trace = read_json(trace_path, {})
                trace.update(sync_incomplete=False, truncated=bool(trace.get('truncated') or page.get('trace_truncated')))
                atomic_json(trace_path, trace)
                seq = manifest.get('seq', after_seq)
                watermarks[session_id] = dict(seq=seq, digest=digest(trace, seq))
                break
            page = query(session_id=session_id, offset=page['next_offset'], snapshot=revision, after_seq=after_seq)
        else:
            raise RuntimeError('snapshot_page_limit')
    # Bound cursor state with the same session retention as evidence.
    atomic_json(collector.root / 'sync.json', {k: watermarks[k] for k in identities if k in watermarks})
    collector._rotate('traces', collector.max_sessions)
    return initial.get('capture_state', 'unknown')


def watch_cycle(collector, rish):
    health = dict(schema=1, checked_wall_clock_ms=int(time.time() * 1000), state='checking')
    atomic_json(collector.root / 'collector-health.json', health)
    try:
        capture_state = sync_device(collector, rish)
        health.update(state='connected', durable_sync=True, capture_state=capture_state)
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as exc:
        health.update(state='recovery_needed', durable_sync=False,
                      reason_code=str(exc) if isinstance(exc, RuntimeError) else 'shell_unavailable')
    # Legacy builds remain readable, but lost logcat is never mistaken for a current heartbeat.
    try:
        result = subprocess.run([rish, '-c', '/system/bin/logcat -d -v raw -T 2000 -s AergisDevLoop:I *:S'],
                                capture_output=True, text=True, timeout=15)
        if result.returncode == 0:
            for line in result.stdout.splitlines():
                collector.accept(line)
        elif not health.get('durable_sync'):
            health.update(state='unavailable', reason_code='shizuku_unavailable')
    except (OSError, subprocess.TimeoutExpired):
        if not health.get('durable_sync'):
            health.update(state='unavailable', reason_code='shell_unavailable')
    health['checked_wall_clock_ms'] = int(time.time() * 1000)
    atomic_json(collector.root / 'collector-health.json', health)
    atomic_json(collector.root / 'latest.json', collector.view('latest'))
    return health


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['watch', 'collect', 'latest', 'session', 'event', 'manifest', 'history', 'status', 'replay', 'regression', 'compare'])
    parser.add_argument('--root', type=Path, default=Path.home() / 'agent-gates/aergis-live')
    parser.add_argument('--rish', default='rish')
    parser.add_argument('--before', type=Path)
    parser.add_argument('--after', type=Path)
    parser.add_argument('--json', action='store_true', help='Structured output (the default)')
    parser.add_argument('--once', action='store_true', help='Run one recovery/poll cycle')
    parser.add_argument('--session', dest='session_id')
    parser.add_argument('--offset', type=int, default=0)
    parser.add_argument('--snapshot')
    args = parser.parse_args(argv)
    collector = Collector(args.root)
    if args.command == 'watch':
        stopping = False
        def stop(*_):
            nonlocal stopping
            stopping = True
        signal.signal(signal.SIGTERM, stop)
        while not stopping:
            try:
                watch_cycle(collector, args.rish)
            except OSError:
                pass  # A temporary filesystem failure is retried by this supervised collector.
            if args.once:
                return 0
            for _ in range(50):
                if stopping:
                    break
                time.sleep(.1)
        return 0
    if args.command == 'collect':
        for line in sys.stdin:
            try:
                collector.accept(line)
            except OSError as exc:
                print(json.dumps(dict(state='collector_error', error=str(exc))), file=sys.stderr)
        return 0
    if args.command == 'compare':
        if not args.before or not args.after:
            parser.error('compare requires --before and --after')
        result = compare_sessions(read_json(args.before, {}), read_json(args.after, {}))
    elif args.command == 'regression':
        result = regression_fixture(collector.replay(args.session_id))
    else:
        result = collector.view(args.command, session_id=args.session_id, offset=args.offset, snapshot=args.snapshot)
    print(json.dumps(result, indent=2, allow_nan=False))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
