import unittest
from analyze_pointer_recording import analyze, point, read_records


def frame(time, x=.2, segment='STATIONARY', **extra):
    p = {'x': x, 'y': .5}
    return dict(timestampMs=time, testSegment=segment,
                comparison=dict(mappedTip=p, current=p, vc49=p), **extra)


class RecordingAnalysisTest(unittest.TestCase):
    def analyze(self, frames):
        return analyze(frames, [], {})

    def test_partial_log_remains_analyzable_without_stitching_across_damage(self):
        import json
        rows = read_records(json.dumps(frame(0)) + '\n{"timestampMs":\n' + json.dumps(frame(66, .8)))
        self.assertEqual(1, rows.count(None))
        result = self.analyze(rows)
        self.assertEqual(2, len(result['segments']))
        self.assertEqual(1, result['unreadableFrameRecords'])

    def test_precision_recordings_include_three_modes_and_older_logs_remain_readable(self):
        old = frame(0)
        new = frame(33)
        new['comparison']['precision'] = {'x': .2, 'y': .5}
        result = self.analyze([old, new])
        self.assertEqual(2, len(result['segments']))
        self.assertNotIn('precision', result['segments']['STATIONARY'])
        self.assertEqual(1, result['segments']['STATIONARY#2']['precision']['samples'])

    def test_repeated_labels_do_not_combine_different_targets(self):
        result = self.analyze([frame(0), frame(33), frame(66, segment='TRAVEL'),
                               frame(99, .8), frame(132, .8)])
        self.assertEqual({'STATIONARY', 'TRAVEL', 'STATIONARY#2'}, set(result['segments']))
        for label in ('STATIONARY', 'STATIONARY#2'):
            self.assertEqual(0, result['segments'][label]['current']['stationaryJitterRms'])

    def test_discontinuities_never_create_artificial_path_length(self):
        boundaries = [dict(timestampMs=500), dict(timestampMs=0), dict(ownerChanged=True),
                      dict(filterMode='VC49'), dict(commandOwnerId='replacement'),
                      dict(calibration='new'), dict(actionEpoch=2), dict(rotation=90)]
        for change in boundaries:
            with self.subTest(change=change):
                second = frame(33, .8)
                second.update(change)
                result = self.analyze([frame(0), second])
                self.assertEqual(2, len(result['segments']))
                self.assertTrue(all(s['current']['pathLength'] == 0 for s in result['segments'].values()))

    def test_missing_comparison_breaks_continuity(self):
        result = self.analyze([frame(0), {'timestampMs': 33, 'comparison': None}, frame(66, .8)])
        self.assertEqual(1, result['invalidOrUncomparedFrames'])
        self.assertEqual(2, len(result['segments']))

    def test_invalid_coordinates_and_time_are_excluded(self):
        for value in ({'x': True, 'y': .5}, {'x': -1, 'y': .5}, {'x': 1.1, 'y': .5}):
            self.assertIsNone(point(value))
        for time in (None, True, '33', float('nan'), -1):
            with self.subTest(time=time):
                result = self.analyze([frame(time)])
                self.assertEqual(1, result['invalidOrUncomparedFrames'])
                self.assertFalse(result['segments'])

    def test_same_owner_reacquisition_is_measured_across_a_dropout(self):
        result = self.analyze([frame(0, commandOwnerId='a', actionEpoch=1),
                               {'timestampMs': 33, 'commandOwnerId': None, 'actionEpoch': 2},
                               frame(66, .3, commandOwnerId='a', actionEpoch=2)])
        self.assertAlmostEqual(.1, result['reacquisitionMaxDiscontinuity']['current'])

    def test_owner_change_during_dropout_is_not_reacquisition(self):
        result = self.analyze([frame(0, commandOwnerId='a'),
                               {'timestampMs': 33, 'ownerChanged': True},
                               frame(66, .8, commandOwnerId='b')])
        self.assertIsNone(result['reacquisitionMaxDiscontinuity']['current'])


if __name__ == '__main__':
    unittest.main()
