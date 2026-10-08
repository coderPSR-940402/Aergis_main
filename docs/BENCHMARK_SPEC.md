# Aergis Benchmark Specification

## Purpose

Measure whether each implementation slice is an improvement over the verified `main` baseline and the prototype evidence without treating a single recording as a performance guarantee.

## Tracks

### Repository-complete track

Runs on JVM/CI and covers timestamp ordering, freshness, state transitions, calibration validation, ownership arbitration, gesture classification, rejection reasons, counter invariants, persistence migration, manifest permissions, and model integrity.

### Device-dependent track

Requires physical Android devices and covers camera latency, tracking quality, pointer jitter, touch injection, service interruption, IMU thresholds, thermal/battery behavior, display rotation, and behavior across foreground applications. Until run, these areas remain **device-unverified**.

## Initial metrics

| Metric | Definition | Baseline | Target/decision rule |
|---|---|---|---|
| Fresh-result acceptance | Ordered, non-duplicate results accepted | Not yet measured | 100% of valid replay cases |
| Stale-result rejection | Results older than policy rejected | Not yet measured | 100% of stale replay cases |
| Out-of-order rejection | Non-monotonic timestamps rejected | Downstream partial handling only | 100% of invalid replay cases |
| Pointer freshness | Maximum age permitted for pointer update | Prototype hypothesis: 220 ms | Benchmark before production claim |
| Gesture freshness | Maximum age permitted for gesture action | Prototype hypothesis: 450 ms | Benchmark before production claim |
| In-flight work | Maximum concurrent inference submissions | CameraX latest-frame; explicit count absent | Bounded and observable; initial hypothesis 2 |
| Result rate | Published results per second | Not measured in current repo | Report median and p95 by device |
| Source-to-pointer latency | Frame timestamp to pointer publication | Prototype observation: ~63 ms | Report by device; no single-device claim |
| Source-to-action latency | Stable evidence to dispatch request | Prototype observations: ~110/370 ms | Report distribution by action/device |
| False actions | Unintended accessibility actions per campaign hour | Device-unverified | Zero unexplained actions in release gate |
| Duplicate-fire rate | Extra action per deliberate gesture | Device-unverified | Zero re-arm violations in release gate |

## Deterministic pointer replay contract

Repository-complete pointer benchmarks use `PointerReplayTrace` and
`PointerReplayBenchmark` so results are reproducible without Android hardware.

- Every trace has a stable `traceId`, contiguous zero-based frame sequence, and
  non-decreasing timestamp in milliseconds.
- Source and expected coordinates are finite normalized values in `[0, 1]`.
- Invalid traces are rejected before the mapper is invoked; they must never be
  silently clamped into a passing result.
- Reports include frame count, duration, mean absolute Euclidean error, p95
  absolute error, maximum error, and output-step metrics.
- A benchmark record must include the commit SHA, trace ID, mapper/profile
  configuration, and whether the result is repository-complete or
  device-dependent.

`PointerReplayEvidenceCodec` serializes these aggregate results as a bounded,
versioned line-oriented payload. It deliberately excludes camera frames,
landmarks, foreground package names, and user content. Decoding is fail-closed
for malformed numbers, unknown schema versions, missing required fields, and
payloads above the size limit. This makes evidence suitable for CI artifacts
and diagnostics without turning the benchmark format into a raw recording
channel.

## Legacy-versus-candidate comparison

`PointerMappingComparisonBenchmark` runs the same validated trace through the
legacy mapper and a candidate mapper, then evaluates deltas against explicit
thresholds. The comparison fails when the candidate changes frame count or
duration, or exceeds any configured tolerance for mean error, p95 error, peak
error, mean output step, or peak output step. A candidate does not need to be
better on every trace to proceed, but it must not introduce an unexplained
regression beyond the declared tolerance.

## Center-gated swipe contract

Swipe replay cases must begin inside the normalized center gate (`x` and `y`
between `0.25` and `0.75`, inclusive) before directional motion can arm the
detector. A trace that remains outside the gate must produce no swipe action,
even when its displacement and velocity exceed the normal directional
thresholds. After valid initiation, the hand may leave the gate to complete the
swipe. Non-finite, out-of-range, duplicate, or out-of-order samples are invalid
and must reset the pending swipe trace. A detected swipe also requires a new
center initiation after cooldown; it may not re-fire from continued edge motion.

## Pose geometry evidence contract

Pose evidence is an aggregate diagnostic record, not a landmark recording. It
may contain landmark count, finite-landmark count, bounded palm-width and bone
ratios, an acceptance bit, and a named rejection reason. It must not contain
raw coordinates, camera frames, foreground package names, accessibility-node
content, or user content. Invalid, non-finite, out-of-range, insufficient, or
kinematically excessive input fails closed in the evidence evaluator. Evidence
generation is observational in this slice and does not bypass the existing
gesture, freshness, motion, foreground, or action-dispatch gates.

The initial tolerances are intentionally conservative and are measurement
defaults, not product claims: mean error `+0.01`, p95 error `+0.02`, peak error
`+0.03`, mean output step `+0.02`, and peak output step `+0.04` in normalized
screen coordinates. Any release decision must report the trace IDs and the
threshold set used.

## Regression rules

- A safety regression is an automatic failure.
- A new rejection must be justified by a named invalid-input case.
- A valid ordered replay case may not become rejected without a documented product decision.
- No benchmark result may be reported without commit SHA, device/test conditions, trace ID, and evidence type.
- Two consecutive refinement iterations without objective improvement cause the slice to be deferred or reverted.

## Recording run boundaries (2026-10 audit)

Android `summary.json` and the Python comparison analyzer treat each contiguous
labelled run separately. A missing/invalid comparison, owner change, filter or
calibration change, action epoch change, camera rotation, non-increasing timestamp,
or gap exceeding 150 ms starts a new run. Repeated labels use `#2`, `#3`, etc.; do
not average their stationary target positions together. The PDF previews as many
runs as fit; the ZIP summary retains all runs.

Reacquisition discontinuity is separate from within-run path/jitter: the analyzer
compares the last output before a short dropout with the recovered output only
for the same owner/filter/calibration/rotation within 500 ms. An action-epoch
change caused by the dropout itself does not imply a different hand.

FAST first arrival is the first output within 0.02 normalized units of a measured
step target, searched within 500 ms by the analyzer. It is not sustained settling.
A missing observation is null, never an inferred zero. Synthetic benchmark JSON
schema 2 includes source commit, evidence type, scope and a `scenarios` object.
Malformed raw records remain in recovered ZIPs and are counted by the analyzer;
comparisons never bridge unreadable frame records.
