import json
import tempfile
import unittest
import subprocess
import sys
import base64
from unittest.mock import patch
from urllib.parse import urlsplit, parse_qs
import shlex
from pathlib import Path

try:
    from tools.aergis_live_collector import Collector, parse_line, compare_sessions, regression_fixture, sync_device
except ImportError:
    from aergis_live_collector import Collector, parse_line, compare_sessions, regression_fixture, sync_device


def record(kind="event", seq=1, **extra):
    return dict(schema=1, record=kind, session_id="a54-session", build_id="sha:1374", seq=seq, **extra)


class CollectorTest(unittest.TestCase):
    def test_device_quality_problems_are_visible_even_with_zero_dispatch_timeouts(self):
        fixture = json.loads(Path('tools/fixtures/devloop/a54-1392-quality-gap.json').read_text())
        c = Collector(self.root)
        self.feed(c, fixture['manifest'])
        view = c.view('latest')
        self.assertTrue(view.get('findings'), 'Recorded latency and pointer problems must reach the summary')
        codes = {finding['code'] for finding in view['findings']}
        self.assertTrue({'frame_delay', 'pinch_pointer_jump', 'trace_loss'} <= codes)
        self.assertIsNone(view['metrics'].get('intended_click_success_rate'))
        self.assertEqual('unknown', view.get('coverage', {}).get('unrecognized_pinches'))
        self.assertEqual('unknown', view.get('coverage', {}).get('target_activation'))
        self.assertEqual(7, view['metrics']['click_success'])

    def test_passive_contact_records_survive_redaction_without_becoming_missed_clicks(self):
        c = Collector(self.root)
        self.feed(c, record(kind='trace', step='contact_geometry', contact_id=3,
                            contact_distance=.12, contact_depth=.4, gate='clicking_disabled'))
        row = c.replay()['records'][0]
        self.assertEqual(.12, row.get('contact_distance'))
        self.assertEqual('clicking_disabled', row.get('gate'))
        self.assertNotIn('request_id', row)
        self.assertEqual([], regression_fixture(c.replay())['expected']['event_kinds'])

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def feed(self, collector, data):
        return collector.accept("11-02 I/AergisDevLoop: AERGISTEL|" + json.dumps(data))

    def test_invalid_truncated_and_private_fields_never_reach_artifacts(self):
        self.assertIsNone(parse_line("AERGISTEL|{"))
        self.assertIsNone(parse_line("AERGISTEL|" + "a" * 4000))
        self.assertIsNone(parse_line('AERGISTEL|{"schema":9}'))
        c = Collector(self.root)
        self.feed(c, record(kind="session", clean=False, screen_text="SECRET", contacts=["PRIVATE"]))
        content = (self.root / "session.json").read_text()
        self.assertNotIn("SECRET", content)
        self.assertNotIn("PRIVATE", content)

    def test_dedup_survives_restart_and_history_is_bounded(self):
        c = Collector(self.root, max_events=2)
        for seq in range(1, 5):
            self.assertTrue(self.feed(c, record(seq=seq, kind="event", event_kind="MISSED_CLICK", request_id=seq)))
        c = Collector(self.root, max_events=2)
        self.assertFalse(self.feed(c, record(seq=4, kind="event", event_kind="MISSED_CLICK", request_id=4)))
        self.assertEqual(2, len(list((self.root / "events").glob("*.json"))))
        self.assertEqual(4, c.view("event")["request_id"])

    def test_manifest_exposes_proven_successes_and_clean_termination(self):
        c = Collector(self.root)
        self.feed(c, record(kind="session", clean=False, state="recording", started_wall_clock_ms=100_000))
        self.feed(c, record(kind="session", seq=2, clean=True, state="stopped", click_success=8,
                            click_attempts=10, missed_clicks=2, stopped_wall_clock_ms=110_000))
        view = c.view("latest")
        self.assertEqual("Session finished normally", view["summary"]["termination"])
        self.assertEqual(8, view["metrics"]["click_success"])
        self.assertEqual(0.8, view["metrics"]["click_success_rate"])
        self.assertEqual("a54-session", c.view("manifest")["session_id"])

    def test_trace_generates_replayable_fixture_with_original_target(self):
        c = Collector(self.root)
        self.feed(c, record(kind="trace", step="open", uptime_ms=1000, request_id=42, action="TAP",
                            target_x=321, target_y=654, session_epoch=7, input_epoch=8))
        self.feed(c, record(kind="trace", seq=2, step="outcome", uptime_ms=1100, request_id=42,
                            outcome="COMPLETED"))
        fixture = regression_fixture(c.view("replay"))
        self.assertEqual(["SUCCESS"], fixture["expected"]["terminals"])
        self.assertEqual(321, fixture["records"][0]["target_x"])
        self.assertEqual([], fixture["expected"]["event_kinds"])

    def test_crash_bridge_is_bounded_redacted_and_idempotent(self):
        c = Collector(self.root)
        crash = {"package": "com.airgesture.control", "timestamp": 101, "exception": "bad",
                 "stacktrace": "UNRELATED PRIVATE TEXT"}
        self.assertTrue(c.bridge_crash(crash))
        self.assertFalse(c.bridge_crash(crash))
        self.assertNotIn("PRIVATE", json.dumps(c.view("event")))
        self.assertEqual("PROCESS_RESTART", c.view("event")["event_kind"])

    def test_before_after_uses_evidence_and_never_turns_missing_into_zero(self):
        result = compare_sessions({"build_id": "old", "click_attempts": 10, "click_success": 7,
                                   "missed_clicks": 3, "frame_age_p95_ms": 200},
                                  {"build_id": "new", "click_attempts": 10, "click_success": 9,
                                   "missed_clicks": 1, "frame_age_p95_ms": 100})
        self.assertAlmostEqual(0.2, result["click_success_rate"]["delta"])
        self.assertEqual("improved", result["missed_clicks"]["assessment"])
        self.assertEqual("insufficient_evidence", result["edge_tracking_rate"]["assessment"])

    def test_captured_terminal_is_not_counted_twice_and_replay_is_time_ordered(self):
        trace = {"captured_device": True, "records": [
            record(kind="trace", seq=1, step="open", uptime_ms=1000, request_id=42, action="TAP"),
            record(kind="trace", seq=2, step="outcome", uptime_ms=1100, request_id=42, outcome="COMPLETED"),
            record(kind="trace", seq=3, step="terminal", uptime_ms=1100, request_id=42, outcome="SUCCESS"),
            record(kind="trace", seq=4, step="sample", uptime_ms=1050)]}
        fixture = regression_fixture(trace)
        self.assertEqual(["SUCCESS"], fixture["expected"]["terminals"])
        self.assertEqual([1000, 1050, 1100, 1100], [r["uptime_ms"] for r in fixture["records"]])

    def test_no_replay_session_means_no_invented_device_provenance(self):
        self.assertEqual("synthetic_contract", regression_fixture({})["provenance"])

    def test_watch_consumes_existing_rish_log_stream_without_a_second_relay(self):
        rish = self.root / 'rish'
        line = 'AERGISTEL|' + json.dumps(record(kind='session', clean=True, state='stopped'))
        rish.write_text('#!' + sys.executable + '\nprint(' + repr(line) + ')\n')
        rish.chmod(0o700)
        result = subprocess.run([sys.executable, 'tools/aergis_live_collector.py', 'watch', '--once',
                                 '--root', str(self.root), '--rish', str(rish)], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('a54-session', json.loads((self.root / 'session.json').read_text())['session_id'])

    def test_new_build_finalization_automatically_compares_against_previous_session(self):
        c = Collector(self.root)
        self.feed(c, record(kind='session', clean=True, state='stopped', started_wall_clock_ms=100,
                            click_attempts=10, click_success=7, missed_clicks=3))
        newer = record(kind='session', clean=True, state='stopped', started_wall_clock_ms=200,
                       click_attempts=10, click_success=9, missed_clicks=1)
        newer.update(session_id='next-session', build_id='next-sha:1375')
        self.feed(c, newer)
        comparison = c.view('latest')['comparison']
        self.assertEqual('sha:1374', comparison['before_build'])
        self.assertEqual('next-sha:1375', comparison['after_build'])
        self.assertEqual('improved', comparison['metrics']['missed_clicks']['assessment'])

    def test_late_old_manifest_cannot_overwrite_finished_session(self):
        c = Collector(self.root)
        self.feed(c, record(kind='session', seq=10, clean=True, state='stopped'))
        self.assertFalse(self.feed(c, record(kind='session', seq=2, clean=False, state='recording')))
        self.assertTrue(c.view('manifest')['clean'])

    def test_timeout_evidence_generates_a_timer_input_for_replay(self):
        trace = {'records': [record(kind='trace', step='open', uptime_ms=1000, request_id=42),
                             record(kind='trace', seq=2, step='terminal', uptime_ms=3500,
                                    request_id=42, outcome='TIMED_OUT')]}
        fixture = regression_fixture(trace)
        self.assertTrue(any(r.get('step') == 'tick' and r['uptime_ms'] == 3500 for r in fixture['records']))

    def test_truncated_trace_cannot_be_misrepresented_as_a_complete_regression(self):
        c = Collector(self.root, max_trace=3)
        for item in [record(kind='trace', step='open', uptime_ms=1000, request_id=42),
                     record(kind='trace', seq=2, step='outcome', uptime_ms=1100, request_id=42, outcome='COMPLETED'),
                     record(kind='trace', seq=3, step='terminal', uptime_ms=1100, request_id=42, outcome='SUCCESS'),
                     record(kind='trace', seq=4, step='open', uptime_ms=1200, request_id=43)]:
            self.feed(c, item)
        with self.assertRaisesRegex(ValueError, 'incomplete'):
            regression_fixture(c.view('replay'))

    def test_comparison_is_not_attached_to_a_different_current_session(self):
        c = Collector(self.root)
        self.feed(c, record(kind='session', clean=True, state='stopped', started_wall_clock_ms=100))
        next_session = record(kind='session', clean=True, state='stopped', started_wall_clock_ms=200)
        next_session.update(session_id='next', build_id='next-sha')
        self.feed(c, next_session)
        current = record(kind='session', clean=False, state='recording', started_wall_clock_ms=300)
        current.update(session_id='current', build_id='newest-sha')
        self.feed(c, current)
        self.assertEqual({}, c.view('latest')['comparison'])

    def test_prior_unclean_session_survives_a_new_recording_manifest(self):
        c = Collector(self.root)
        self.feed(c, record(kind='session', seq=1, clean=False, state='recording', started_wall_clock_ms=100))
        self.feed(c, record(kind='session', seq=2, clean=False, state='unclean', started_wall_clock_ms=100))
        current = record(kind='session', clean=False, state='recording', started_wall_clock_ms=200)
        current.update(session_id='next', build_id='next-sha')
        self.feed(c, current)
        history = c.view('latest')['session_history']
        self.assertTrue(any(s['session_id'] == 'a54-session' and s['state'] == 'unclean' for s in history))

    def test_frame_observations_are_not_falsely_claimed_as_correlator_regressions(self):
        fixture = regression_fixture({'records': [record(kind='event', event_kind='HAND_LOST', event_uptime_ms=1000)]})
        self.assertEqual([], fixture['expected']['event_kinds'])
        self.assertEqual(['HAND_LOST'], fixture['recorded_observation_kinds'])
        self.assertEqual('interaction_correlation', fixture['replay_scope'])

    def test_old_recording_is_stale_without_inventing_a_clean_or_unclean_exit(self):
        c = Collector(self.root)
        self.feed(c, record(kind='session', state='recording', clean=False,
                            observed_wall_clock_ms=1000, click_success=7))
        view = c.view('latest')
        self.assertEqual('stale', view.get('capture_status'))
        self.assertNotEqual('Session is recording', view['summary']['termination'])
        self.assertEqual('recording', view['manifest']['state'])
        self.assertEqual(7, view['metrics']['click_success'])

    def test_captured_a54_ended_session_cannot_be_presented_as_live(self):
        evidence = json.loads(Path('tools/fixtures/devloop/a54-1381-stale-manifest.json').read_text())
        c = Collector(self.root)
        self.feed(c, evidence['manifest'])
        self.assertEqual('stale', c.view('latest')['capture_status'])
        self.assertEqual(7, c.view('latest')['metrics']['click_success'])

    def test_collector_that_has_stopped_reporting_is_not_healthy_forever(self):
        self.root.joinpath('collector-health.json').write_text('{"state":"connected","checked_wall_clock_ms":1000}')
        self.assertEqual('stale', Collector(self.root).view('latest')['collector']['state'])

    def test_recovered_final_manifest_cannot_collide_with_a_collected_trace_sequence(self):
        c = Collector(self.root)
        self.feed(c, record(kind='session', state='recording', seq=40))
        self.feed(c, record(kind='trace', step='open', request_id=1, seq=41))
        self.assertTrue(self.feed(c, record(kind='session', state='unclean', clean=False, seq=41)))
        self.assertEqual('unclean', c.view('session')['state'])
        self.assertEqual(1, len(c.replay()['records']))

    def test_late_recording_cannot_resurrect_a_finalized_session(self):
        c = Collector(self.root)
        self.feed(c, record(kind='session', state='unclean', clean=False, seq=41))
        self.assertFalse(self.feed(c, record(kind='session', state='recording', clean=False, seq=100)))
        self.assertEqual('unclean', c.view('session')['state'])

    def test_prior_session_event_and_history_are_retrievable_by_identity(self):
        c = Collector(self.root)
        self.feed(c, record(kind='event', event_kind='MISSED_CLICK', request_id=42))
        other = record(kind='event', event_kind='HAND_LOST', seq=2)
        other['session_id'] = 'new'
        self.feed(c, other)
        self.assertEqual(42, c.view('event', session_id='a54-session').get('request_id'))
        self.assertEqual(1, len(c.view('history', session_id='a54-session')))

    def test_missing_local_trace_is_rebuilt_despite_watermark_and_old_dedup(self):
        c = Collector(self.root)
        opening = record(kind='trace', seq=5, step='open', request_id=42, target_x=321)
        self.feed(c, opening)
        (self.root / 'traces/a54-session.json').unlink()
        (self.root / 'sync.json').write_text('{"a54-session":10}')
        manifest = record(kind='session', seq=10, state='stopped', clean=True)
        def shell(argv, **_):
            uri = shlex.split(argv[-1])[3]
            params = parse_qs(urlsplit(uri).query)
            after = int(params.get('after_seq', ['0'])[0])
            data = dict(schema=2, manifest=manifest, session_history=[manifest],
                        snapshot_id='fixed', next_offset=None, trace_truncated=False,
                        records=[opening] if after < 5 else [])
            payload = base64.b64encode(json.dumps(data).encode()).decode()
            return subprocess.CompletedProcess(argv, 0, 'Row: 0 payload=' + payload, '')
        with patch('subprocess.run', side_effect=shell):
            sync_device(c, 'rish')
        # Now lose the file with the new structured cursor and persistent dedup intact.
        (self.root / 'traces/a54-session.json').unlink()
        with patch('subprocess.run', side_effect=shell):
            sync_device(c, 'rish')
        self.assertEqual([42], [r['request_id'] for r in c.replay()['records']])
        self.assertFalse(c.replay()['sync_incomplete'])

    def test_replay_pages_fit_relay_limit_and_reassemble_without_lost_targets(self):
        c = Collector(self.root)
        for seq in range(1, 201):
            self.feed(c, record(kind='trace', seq=seq, step='open', request_id=seq,
                                target_x=321, target_y=654))
        page = c.view('replay')
        self.assertLess(len(json.dumps(page, indent=2).encode()), 24000)
        self.assertIn('next_offset', page)
        records = list(page['records'])
        while page['next_offset'] is not None:
            page = c.view('replay', offset=page['next_offset'], snapshot=page['snapshot_id'])
            records.extend(page['records'])
        self.assertEqual(list(range(1, 201)), [r['request_id'] for r in records])
        self.assertTrue(all(r['target_x'] == 321 for r in records))

    def test_replay_revision_change_is_explicit_instead_of_mixing_pages(self):
        c = Collector(self.root)
        self.feed(c, record(kind='trace', step='open', request_id=1))
        page = c.view('replay')
        self.assertIn('snapshot_id', page)
        self.feed(c, record(kind='trace', seq=2, step='open', request_id=2))
        self.assertEqual('snapshot_changed', c.view('replay', snapshot=page['snapshot_id'])['state'])

    def test_late_prior_session_does_not_replace_the_current_session(self):
        c = Collector(self.root)
        current = record(kind='session', state='recording', started_wall_clock_ms=200)
        current['session_id'] = 'new'
        self.feed(c, current)
        self.feed(c, record(kind='session', seq=9, state='unclean', started_wall_clock_ms=100))
        self.assertEqual('new', c.view('session')['session_id'])
        self.assertEqual(2, len(c.view('latest')['session_history']))

    def test_watch_resyncs_durable_final_manifest_when_logcat_has_lost_it(self):
        rish = self.root / 'rish'
        final = record(kind='session', state='stopped', clean=True, click_success=7)
        payload = dict(schema=2, manifest=final, session_history=[final], records=[],
                       next_offset=None, snapshot_id='fixture', trace_truncated=False)
        encoded = base64.b64encode(json.dumps(payload).encode()).decode()
        rish.write_text('#!' + sys.executable + '\nimport sys\n'
                        'if "content query" in sys.argv[-1]: print("Row: 0 payload=' + encoded + '")\n')
        rish.chmod(0o700)
        result = subprocess.run([sys.executable, 'tools/aergis_live_collector.py', 'watch', '--once',
                                 '--root', str(self.root), '--rish', str(rish)], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('stopped', Collector(self.root).view('session')['state'])
        self.assertEqual(7, Collector(self.root).view('session')['click_success'])
        self.assertEqual('connected', Collector(self.root).view('latest')['collector']['state'])


if __name__ == "__main__":
    unittest.main()
