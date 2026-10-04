# Prototype APK Code Review and Current-Repo Recovery

## Scope and limitation

The supplied artifact was reviewed as a compiled Android APK through its manifest, packaged resources, DEX/package class surface, and decoded implementation fingerprints where available. A compiled APK is not equivalent to the original source repository: names and method signatures are recoverable, but comments, source-level intent, tests, replay corpora, build provenance, and some behavior cannot be reconstructed with certainty.

The current repository was reviewed as source at baseline commit `602a267` with its existing unit tests and CI configuration.

## One-sentence intent recap

The current application is an Android accessibility tool that turns front-camera hand gestures and fingertip motion into guarded pointer and system actions; the prototype APK adds a full calibration, diagnostics, benchmark, replay, geometry-validation, and production-shell layer around that core.

## Overall assessment

The current repository’s safety work is valuable: it has explicit READY/ARMED behavior, motion cancellation, ownership continuity, stale-frame transaction cancellation, bounded retries, final action policy checks, and model integrity verification. However, the current runtime is under-instrumented and under-calibrated. The prototype APK’s strongest contribution is not simply additional gesture code; it is the existence of independent evidence layers and feedback loops that make the system tunable.

The recovery should therefore be additive and staged. New detectors must remain pure evidence producers. The current action policy must remain the only authority that can dispatch accessibility actions.

## Critical findings

### C1 — Vision performance telemetry was a placeholder

`VisionPerformanceMonitor` exposed only FPS and returned `inferenceMs = 0`. That prevented reliable tuning of stale results, queue pressure, result yield, or actual analysis latency. The prototype had dedicated latency limits and counters for submitted frames, results, stale drops, throttling, and in-flight work.

**Implemented:** `VisionLatencyPolicy` and an expanded, synchronized `VisionPerformanceMonitor` with submitted/result/stale-drop/in-flight/latency/yield metrics. Existing `recordFrame()` and the original three-argument snapshot constructor remain compatible.

**Remaining:** wire these methods into the actual asynchronous `GestureRecognitionEngine` callbacks, reject stale results at the result boundary, and expose the snapshot through diagnostics.

### C2 — Pointer mapping had no calibration contract

The current mapping uses fixed 2% margins, mirror behavior, and no orientation/device-specific profile. The prototype contained versioned pointer calibration and a dedicated calibration workflow.

**Implemented:** validated version-2 `PointerCalibration` with bounds, gains, offsets, and an opt-in mapper overload. The default mapping remains unchanged.

**Remaining:** persist profiles through `ActionMappingStore`, add orientation/display context, create a calibration UI, and gate activation on successful calibration validation. Calibration must never alter action authorization.

### C3 — Swipe recognition lacked the prototype’s deliberate center-gate contract

The current swipe engine uses a bounded sample window, velocity, displacement, angle, and cooldown. The prototype used center crossing, axis ambiguity, speed limits, cooldown, re-arm state, and telemetry.

**Implemented:** a replayable `CenterGateSwipeDetector` with axis dominance, center-gate crossing, displacement, speed bounds, cooldown, reset behavior, and telemetry. It is not yet wired into production dispatch, so current behavior remains unchanged.

**Remaining:** benchmark it against the current engine on identical traces and only then integrate behind a controlled feature flag.

## Major findings

### M1 — No replay/benchmark harness in the current repo

The prototype class surface included landmark replay codecs/engines/reports and pointer/vision benchmarks. The current repo has manual campaign documentation but no deterministic corpus or replay runner.

This is the highest-value missing engineering capability. Without it, improvements are judged by live-camera intuition and can regress quality silently.

### M2 — Geometry and pose evidence was reduced

The prototype contained landmark pose recognition, category arbitration, world-hand validation, temporal geometry guards, and hand identity signatures. The current repo has good ownership and kinematic filtering, but fewer independent semantic evidence layers.

These components should be recovered as pure, testable validators that feed existing transaction and safety gates rather than dispatching actions themselves.

### M3 — The production shell and diagnostics were lost

The prototype had separate Control, Setup, Diagnostics, and Practice surfaces plus calibration and tracking mirror workflows. The current single-screen UI exposes some state but not enough reason codes or latency/quality metrics for tuning.

Diagnostics should be read-only, privacy-minimized, and unable to arm or dispatch actions directly.

### M4 — Accessibility action coordination is less explicit

The prototype’s `TouchActionCoordinator`, `TouchActionTicket`, pending pointer update, and owned-gesture outcome classes suggest stronger cancellation and action-lifecycle semantics. The current accessibility integration has important final re-checks but should eventually adopt explicit tickets/epochs for stale action cancellation and observability.

## Minor findings

- The prototype’s broader accessibility/window-content permissions should not be restored by default.
- The prototype artifact is debuggable and should not be treated as a release-quality packaging reference.
- Thresholds in recovered components need device/replay evidence before production integration.
- Current CI is the authoritative Android build environment because the sandbox lacks an Android SDK.

## Positive feedback

- Current ownership selection rejects ambiguous candidates instead of guessing.
- Gesture transactions require stable evidence and release before re-arm.
- Motion and protected-foreground policies are explicit and covered by tests.
- Camera image cleanup is handled in a `finally` block.
- Model download integrity is verified by both SHA-256 and byte length.
- CI permissions and baseline advancement were deliberately separated.
- The recovery components are isolated and do not change default action behavior.

## Implemented files

| File | Purpose |
|---|---|
| `VisionLatencyPolicy.kt` | Prototype-derived hard freshness and in-flight limits |
| `RuntimeModels.kt` | Thread-safe vision performance metrics and compatibility-preserving snapshot expansion |
| `PointerCalibration.kt` | Versioned, validated calibration transform |
| `PointerCoordinateMapper.kt` | Opt-in calibrated mapping overload |
| `pointer/CenterGateSwipeDetector.kt` | Replayable center-gated swipe evidence |
| `PrototypeRecoveryTest.kt` | Tests for telemetry, freshness, calibration, and swipe safety |
| `AUTONOMOUS_PROTOTYPE_RECOVERY_PROMPT.md` | Autonomous implementation prompt for the remaining work |

## Validation status

`git diff --check` and source inspection should be run before committing. The local Android unit-test task was attempted but could not execute because this sandbox has no Android SDK and no `ANDROID_HOME`/`local.properties` SDK path. The failure is environmental:

```text
SDK location not found. Define a valid SDK with ANDROID_HOME or sdk.dir.
```

Therefore no local test pass is claimed. GitHub CI must validate compilation, unit tests, lint, debug assembly, release assembly, APK verification, and CodeQL.

## Recommended next implementation slice

Wire `VisionPerformanceMonitor` into `GestureRecognitionEngine` and add a deterministic result-boundary freshness test. This is smaller and more valuable than adding another gesture, and it will provide the measurements required to judge all later detector/filter changes.
