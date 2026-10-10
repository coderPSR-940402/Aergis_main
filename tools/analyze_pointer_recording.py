#!/usr/bin/env python3
"""Summarize local Aergis diagnostic ZIPs. Standard library; works in Termux."""
import argparse
import bisect
import collections
import json
import math
import pathlib
import zipfile


def read_records(text):
    """Keep a boundary marker for damaged lines in an interrupted recording."""
    records = []
    for line in text.splitlines():
        if not line.strip():
            continue
        try:
            record = json.loads(line)
            records.append(record if isinstance(record, dict) else None)
        except (ValueError, TypeError):
            records.append(None)
    return records


def point(value):
    if not isinstance(value, dict):
        return None
    x, y = value.get("x"), value.get("y")
    if type(x) in (int, float) and type(y) in (int, float) and math.isfinite(x) and math.isfinite(y) and 0 <= x <= 1 and 0 <= y <= 1:
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
        "fastFirstArrivalMeanMs": sum(settling) / len(settling) if settling else None,
        "edgeReach": {"minX": min(p[0] for p in values), "maxX": max(p[0] for p in values),
                      "minY": min(p[1] for p in values), "maxY": max(p[1] for p in values)},
    }


def analyze(frames, events, metadata):
    groups = collections.defaultdict(list)
    labels = {}
    run_counts = collections.Counter()
    previous_context = None
    previous_time = None
    run = None
    context_fields = ("testSegment", "filterMode", "commandOwnerId", "calibration", "actionEpoch", "rotation")
    reasons = collections.Counter()
    visibility_interruptions = 0
    previous_visible = None
    previous_candidates = None
    previous_identity = None
    identity_fields = ("filterMode", "commandOwnerId", "calibration", "rotation")
    missing_since_last = False
    reacquisition = {"current": [], "vc49": [], "precision": []}
    invalid = 0
    for frame in frames:
        if not isinstance(frame, dict):
            invalid += 1
            missing_since_last = True
            previous_candidates = None
            run = None
            continue
        reasons[frame.get("pointerRejection", frame.get("reason", "UNKNOWN"))] += 1
        visible = frame.get("cursorVisible")
        if visible is not None:
            visibility_interruptions += previous_visible is True and visible is False
            previous_visible = visible
        context = tuple(frame.get(key) for key in context_fields)
        identity = tuple(frame.get(key) for key in identity_fields)
        if frame.get("ownerChanged", False) or (previous_identity is not None and any(
                frame.get(key) is not None and frame.get(key) != previous_identity[i]
                for i, key in enumerate(identity_fields))):
            previous_candidates = None
        comparison = frame.get("comparison")
        comparison = comparison if isinstance(comparison, dict) else {}
        timestamp = frame.get("timestampMs")
        valid_time = type(timestamp) in (int, float) and math.isfinite(timestamp) and timestamp >= 0
        raw, current, vc49 = (point(comparison.get(key)) for key in ("mappedTip", "current", "vc49"))
        precision = point(comparison.get("precision"))
        context += (precision is not None,)
        changed = context != previous_context or frame.get("ownerChanged", False)
        candidates = {"current": current, "vc49": vc49}
        if precision is not None:
            candidates["precision"] = precision
        if not valid_time or None in (raw, current, vc49):
            invalid += 1
            missing_since_last = True
            run = None
            continue
        if (missing_since_last and previous_candidates and identity == previous_identity and
                previous_time is not None and 0 < timestamp - previous_time <= 500):
            for mode, value in candidates.items():
                if mode in previous_candidates:
                    reacquisition[mode].append(distance(value, previous_candidates[mode]))
        segment = frame.get("testSegment", "UNLABELLED")
        if run is None or changed or previous_time is None or not 0 < timestamp - previous_time <= 150:
            run_counts[segment] += 1
            run = segment if run_counts[segment] == 1 else f"{segment}#{run_counts[segment]}"
            labels[run] = segment
        missing_since_last = False
        previous_candidates = candidates
        previous_context, previous_time = context, timestamp
        previous_identity = identity
        groups[run].append({"time": timestamp, "raw": raw, **candidates})
    overlay = [e for e in events if isinstance(e, dict) and e.get("event") == "overlay_applied"]
    return {
        "sourceCommit": metadata.get("sourceCommit"), "historicalSource": metadata.get("historicalSource"),
        "frames": len(frames), "invalidOrUncomparedFrames": invalid,
        "unreadableFrameRecords": sum(not isinstance(f, dict) for f in frames),
        "unreadableEventRecords": sum(not isinstance(e, dict) for e in events),
        "pointerVisibilityInterruptions": visibility_interruptions, "rejections": dict(reasons),
        "overlayEvents": len(overlay), "overlayHiddenEvents": sum(not e.get("visible", False) for e in overlay),
        "reacquisitionMaxDiscontinuity": {k: max(v) if v else None for k, v in reacquisition.items()},
        "segments": {s: {mode: metrics(rows, mode, labels[s]) for mode in ("current", "vc49", "precision") if mode in rows[0]} for s, rows in groups.items()},
        "limits": ["Normalized screen units. Raw landmarks are measurements, not ground truth.",
                   "Estimated lag is relative to measured tips, not end-to-end camera/display latency.",
                   "FAST first arrival measures entry within 0.02 of a measured step target, not sustained settling.",
                   "Each segment key describes one contiguous run; #2 etc. mark later runs of the same label.",
                   "Runs split at missing comparisons, owner/filter/calibration/epoch/rotation changes and gaps over 150 ms.",
                   "Label STATIONARY only while holding one fixed target. Relabel when moving to another target.",
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
        frames = read_records(read("frames.jsonl"))
        try:
            events = read_records(read("events.jsonl"))
        except (FileNotFoundError, KeyError):
            events = []
        result = analyze(frames, events, json.loads(read("metadata.json")))
    finally:
        if archive:
            archive.close()
    output = args.output or args.recording.with_name(args.recording.stem + "-comparison.json")
    output.write_text(json.dumps(result, indent=2, allow_nan=False) + "\n")
    print(output)


if __name__ == "__main__":
    main()
