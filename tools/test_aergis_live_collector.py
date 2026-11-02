import json
import tempfile
import unittest
from pathlib import Path

try:
    from tools.aergis_live_collector import Collector, parse_line, compare_sessions, regression_fixture
except ImportError:
    from aergis_live_collector import Collector, parse_line, compare_sessions, regression_fixture


def record(kind="event", seq=1, **extra):
    return dict(schema=1, record=kind, session_id="a54-session", build_id="sha:1374", seq=seq, **extra)


class CollectorTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
