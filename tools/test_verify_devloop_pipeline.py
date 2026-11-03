import json
import unittest
import base64
import tools.verify_devloop_pipeline as pipeline
from tools.verify_devloop_pipeline import validate


def line(record, seq, **fields):
    return 'AERGISTEL|' + json.dumps(dict(schema=1, record=record, seq=seq, session_id='simulated-session', build_id='sha:1374', **fields))


class PipelineTest(unittest.TestCase):
    def test_durable_snapshot_pipeline_is_verified_without_receiving_any_logcat(self):
        self.assertTrue(callable(getattr(pipeline, 'validate_snapshot', None)), 'Durable recovery needs its own end-to-end check')
        records = [json.loads(line('trace', 1, step='open', uptime_ms=1000, request_id=42, action='TAP').split('|', 1)[1]),
                   json.loads(line('trace', 2, step='outcome', uptime_ms=1100, request_id=42, outcome='COMPLETED').split('|', 1)[1]),
                   json.loads(line('trace', 3, step='terminal', uptime_ms=1100, request_id=42, outcome='SUCCESS').split('|', 1)[1]),
                   json.loads(line('trace', 4, step='open', uptime_ms=1200, request_id=43, action='TAP').split('|', 1)[1]),
                   json.loads(line('trace', 5, step='terminal', uptime_ms=1250, request_id=43, outcome='FAILED').split('|', 1)[1]),
                   json.loads(line('event', 6, event_kind='ACTION_FAILED', event_uptime_ms=1250, request_id=43).split('|', 1)[1])]
        manifest = json.loads(line('session', 7, clean=True, state='stopped', click_success=1, action_failures=1).split('|', 1)[1])
        page = dict(schema=2, capture_state='inactive', manifest=manifest, session_history=[manifest],
                    snapshot_id='test-revision', records=records, next_offset=None, trace_truncated=False)
        encoded = 'Row: 0 payload=' + base64.b64encode(json.dumps(page).encode()).decode()
        result, fixture = pipeline.validate_snapshot([encoded])
        self.assertEqual('passed', result['pipeline'])
        self.assertEqual(['SUCCESS', 'FAILED'], fixture['expected']['terminals'])
        self.assertEqual('simulated_android_runtime', fixture['provenance'])
    def test_requires_both_success_and_correlated_failure_before_claiming_pipeline_pass(self):
        with self.assertRaises(ValueError):
            validate([line('session', 1, clean=True, state='stopped', click_success=1, action_failures=1)])

    def test_reconstructs_production_log_records_and_returns_replay_fixture(self):
        records = [line('trace', 1, step='open', uptime_ms=1000, request_id=42, action='TAP', target_x=321, target_y=654),
                   line('trace', 2, step='outcome', uptime_ms=1100, request_id=42, outcome='COMPLETED'),
                   line('trace', 3, step='terminal', uptime_ms=1100, request_id=42, outcome='SUCCESS'),
                   line('trace', 4, step='open', uptime_ms=1200, request_id=43, action='TAP', target_x=321, target_y=654),
                   line('trace', 5, step='outcome', uptime_ms=1250, request_id=43, outcome='POLICY_DENIED'),
                   line('trace', 6, step='terminal', uptime_ms=1250, request_id=43, outcome='FAILED'),
                   line('event', 7, event_kind='ACTION_FAILED', event_uptime_ms=1250, request_id=43),
                   line('session', 8, clean=True, state='stopped', click_success=1, action_failures=1)]
        result, fixture = validate(records)
        self.assertEqual('passed', result['pipeline'])
        self.assertEqual(['SUCCESS', 'FAILED'], fixture['expected']['terminals'])
        self.assertEqual('simulated_android_runtime', fixture['provenance'])
