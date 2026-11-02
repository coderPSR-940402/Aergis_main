import json
import unittest
from tools.verify_devloop_pipeline import validate


def line(record, seq, **fields):
    return 'AERGISTEL|' + json.dumps(dict(schema=1, record=record, seq=seq, session_id='simulated-session', build_id='sha:1374', **fields))


class PipelineTest(unittest.TestCase):
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
