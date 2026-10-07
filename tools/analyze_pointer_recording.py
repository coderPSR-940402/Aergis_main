#!/usr/bin/env python3
"""Summarize local Aergis diagnostic ZIPs. Standard library; works in Termux."""
import argparse
import bisect
import collections
import json
import math
import pathlib
import zipfile


def point(value):
    if not isinstance(value, dict):
        return None
    x, y = value.get("x"), value.get("y")
    if isinstance(x, (int, float)) and isinstance(y, (int, float)) and math.isfinite(x) and math.isfinite(y):
        return float(x), float(y)
    return None


def distance(a, b):
    return math.hypot(a[0] - b[0], a[1] - b[1])


def metrics(rows, mode, segment):
    if not rows:
        return {"samples": 0}
    values = [r[mode] for r in rows]
    errors = [distance(r[mode], r["raw"]) for r in rows]
    steps = [distance(a, b) for a, b in zip(values, values[1:])]
    acceleration = [math.hypot(c[0] - 2*b[0] + a[0], c[1] - 2*b[1] + a[1])
                    for a, b, c in zip(values, values[1:], values[2:])]
    mean = tuple(sum(p[i] for p in values) / len(values) for i in (0, 1))
    times = [r["time"] for r in rows]

    def delayed_raw(time):
        index = bisect.bisect_left(times, time)
        if index == 0 or index >= len(rows):
            return None
        a, b = rows[index-1], rows[index]
        delta = b["time"] - a["time"]
        if delta <= 0 or delta > 150:
            return None
        f = (time - a["time"]) / delta
        return tuple(a["raw"][i] + (b["raw"][i] - a["raw"][i]) * f for i in (0, 1))

    # Fit causal delay against the measured trajectory. Not capture-to-display latency.
    lag = None
    if segment in ("TRAVEL", "FAST") and times[-1] - times[0] >= 1000:
        candidates = []
        for delay in range(0, 335, 5):
            comparisons = [(r[mode], delayed_raw(r["time"] - delay)) for r in rows
                           if r["time"] - times[0] >= 335]
            errors_at_delay = [distance(a, b)**2 for a, b in comparisons if b is not None]
            if errors_at_delay:
                candidates.append((sum(errors_at_delay) / len(errors_at_delay), delay))
        if candidates:
            lag = min(candidates)[1]

    settling = []
    if segment == "FAST":
        for i in range(1, len(rows)):
            if distance(rows[i]["raw"], rows[i-1]["raw"]) < .045:
                continue
            for r in rows[i:]:
                if r["time"] - rows[i]["time"] > 500:
                    break
                if distance(r[mode], rows[i]["raw"]) < .02:
                    settling.append(r["time"] - rows[i]["time"])
                    break

    return {
        "samples": len(rows),
        "stationaryJitterRms": math.sqrt(sum(distance(p, mean)**2 for p in values) / len(values)) if segment == "STATIONARY" else None,
        "meanDistanceToLatestMeasurement": sum(errors) / len(errors),
        "maxDistanceToLatestMeasurement": max(errors),
        "pathLength": sum(steps),
        "secondDifferenceRms": math.sqrt(sum(a*a for a in acceleration) / len(acceleration)) if acceleration else 0,
        "estimatedMeasurementLagMs": lag,
        "fastSettlingMeanMs": sum(settling) / len(settling) if settling else None,
        "edgeReach": {"minX": min(p[0] for p in values), "maxX": max(p[0] for p in values),
                      "minY": min(p[1] for p in values), "maxY": max(p[1] for p in values)},
    }


def analyze(frames, events, metadata):
    groups = collections.defaultdict(list)
    reasons = collections.Counter()
    visibility_interruptions = 0
    previous_visible = None
    previous_candidates = None
    missing_since_last = False
    reacquisition = {"current": [], "vc49": []}
    invalid = 0
    for frame in frames:
        reasons[frame.get("pointerRejection", frame.get("reason", "UNKNOWN"))] += 1
        visible = frame.get("cursorVisible")
        if visible is not None:
            visibility_interruptions += previous_visible is True and visible is False
            previous_visible = visible
        comparison = frame.get("comparison", {})
        raw, current, vc49 = (point(comparison.get(key)) for key in ("mappedTip", "current", "vc49"))
        if None in (raw, current, vc49):
            invalid += 1
            missing_since_last = True
            continue
        if missing_since_last and previous_candidates and not frame.get("ownerChanged", False):
            for mode, value in (("current", current), ("vc49", vc49)):
                reacquisition[mode].append(distance(value, previous_candidates[mode]))
        missing_since_last = False
        previous_candidates = {"current": current, "vc49": vc49}
        groups[frame.get("testSegment", "UNLABELLED")].append({"time": frame["timestampMs"], "raw": raw,
                                                               "current": current, "vc49": vc49})
    overlay = [e for e in events if e.get("event") == "overlay_applied"]
    return {
        "sourceCommit": metadata.get("sourceCommit"), "historicalSource": metadata.get("historicalSource"),
        "frames": len(frames), "invalidOrUncomparedFrames": invalid,
        "pointerVisibilityInterruptions": visibility_interruptions, "rejections": dict(reasons),
        "overlayEvents": len(overlay), "overlayHiddenEvents": sum(not e.get("visible", False) for e in overlay),
        "reacquisitionMaxDiscontinuity": {k: max(v) if v else None for k, v in reacquisition.items()},
        "segments": {s: {mode: metrics(rows, mode, s) for mode in ("current", "vc49")} for s, rows in groups.items()},
        "limits": ["Normalized screen units. Raw landmarks are measurements, not ground truth.",
                   "Estimated lag is relative to measured tips, not end-to-end camera/display latency.",
                   "Label STATIONARY only while holding one fixed target. Do not combine different target locations.",
                   "CameraX skips before analysis are not inferred as detector-invalid frames."]
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("recording", type=pathlib.Path)
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()
    if args.recording.is_dir():
        read = lambda name: (args.recording / name).read_text()
        archive = None
    else:
        archive = zipfile.ZipFile(args.recording)
        read = lambda name: archive.read(name).decode("utf-8")
    try:
        frames = [json.loads(line) for line in read("frames.jsonl").splitlines() if line]
        events = [json.loads(line) for line in read("events.jsonl").splitlines() if line]
        result = analyze(frames, events, json.loads(read("metadata.json")))
    finally:
        if archive:
            archive.close()
    output = args.output or args.recording.with_name(args.recording.stem + "-comparison.json")
    output.write_text(json.dumps(result, indent=2, allow_nan=False) + "\n")
    print(output)


if __name__ == "__main__":
    main()
