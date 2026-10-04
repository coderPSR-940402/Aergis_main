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
