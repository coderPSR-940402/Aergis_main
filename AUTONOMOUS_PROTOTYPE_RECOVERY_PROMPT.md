# Autonomous Aergis Prototype-Recovery Implementation Prompt

## Mission

You are the senior Android systems engineer responsible for improving the Aergis air-gesture accessibility application in the repository you have access to. The current repository is the authoritative implementation. A supplied APK, `AERMOTUS-v0.18.24-preview-vc49-r8-filter-shadow.apk`, is a black-box golden reference for interaction quality, not a source to copy blindly.

Your objective is to recover the prototype’s highest-value capabilities while preserving or strengthening the current branch’s explicit safety model, privacy minimization, CI controls, and non-bypassable final action authorization.

The desired end state is an observable, calibrated, replayable, benchmarked, low-latency gesture-control system—not merely a collection of additional gesture classes.

## Repository and evidence

1. Start in the active repository root.
2. Read `AGENTS.md` first.
3. Resolve `baseline-successful-apk` from GitHub and treat that commit as the functional reference point.
4. Read `APK_COMPARISON_REPORT.md` and `AUDIT_REPORT.md` if present.
5. Inspect the current code and tests before changing anything.
6. Treat the supplied APK as a behavioral reference. If it is present at `/home/ubuntu/upload/AERMOTUS-v0.18.24-preview-vc49-r8-filter-shadow.apk`, inspect it with Android tooling. Do not assume decoded smali is authoritative source code.

## Non-negotiable safety rules

- Do not weaken or bypass READY/ARMED mode, protected-foreground blocking, motion cancellation, ownership continuity, stale-frame rejection, release-before-rearm, or the final safety re-check before accessibility dispatch.
- Do not restore `canRetrieveWindowContent=true`, `flagRetrieveInteractiveWindows`, `INTERNET`, or debug packaging merely because the prototype had them. Any broader permission requires a written privacy/security justification and tests.
- Do not dispatch any action directly from a newly ported detector. Detectors produce evidence; the existing policy/coordinator remains the final authority.
- Do not change default user-visible behavior without a replay comparison, focused unit tests, and an explicit feature flag or controlled integration point.
- Never hardcode secrets, credentials, personal data, or device logs.
- Never commit APKs, decoded APK output, model binaries, build output, or generated secrets.

## Required workflow

### Phase 0 — Baseline and inventory

- Create a short-lived branch named `feature/prototype-recovery` or a narrower branch for one subsystem.
- Capture `git status`, current commit, baseline ref, source inventory, test inventory, and Gradle tasks.
- Map the current pipeline: camera frame → MediaPipe result → ownership → geometry/filtering → gesture transaction → action policy → accessibility dispatch.
- Identify the exact existing contract that each new component will satisfy.

### Phase 1 — Review every recoverable prototype subsystem

Review the APK evidence and current source for these prototype capabilities:

- `ProductionActivity` product shell and diagnostics/practice concepts.
- Pointer calibration and orientation-aware profiles.
- `CenterGateSwipeDetector` and telemetry.
- `LandmarkPoseRecognizer` and `PoseCategoryArbiter`.
- `HandIdentitySignature` and geometry-based ownership evidence.
- `WorldHandGeometryValidator` and `WorldGeometryTemporalGuard`.
- `VisionLatencyPolicy`, frame pacing, watchdog, stale-result rejection, and in-flight limits.
- `VisionPerformanceMonitor` and pipeline benchmark snapshots.
- `LandmarkReplayCodec`, replay engine, and replay report.
- `PointerAimEstimator`, `PointerTipContactEstimator`, and pointer motion filtering.
- `TouchActionCoordinator`, action tickets, pending pointer updates, and cancellation semantics.

For each capability, classify it as:

- **Recover now:** high value, safe, testable, and compatible with current architecture.
- **Recover later:** valuable but requires device data, replay corpus, UI decisions, or deeper refactoring.
- **Do not recover:** conflicts with privacy, release safety, or current correctness.

Record evidence and reasons in `docs/prototype-recovery-review.md`.

### Phase 2 — Implement in this order

#### 2.1 Replay and benchmark foundation

Build pure JVM-testable interfaces for timestamped landmark frames, detector input/output, reason codes, and aggregate reports. Add deterministic replay tests for valid gestures, diagonal ambiguity, stale gaps, ownership changes, occlusion, timestamp regressions, and malformed input. Keep the codec versioned and bounded; reject impossible lengths, NaN/infinite coordinates, negative timestamps, and unbounded allocations.

#### 2.2 Vision freshness and performance

Use the current `VisionPerformanceMonitor` and `VisionLatencyPolicy` as the starting point. Track submitted frames, result frames, stale drops, in-flight work, result age, inference latency, throttling, yield, and stale-drop percentage. Use monotonic timestamps. Reject results older than the hard pointer or gesture limit. Bound in-flight work. Add tests for out-of-order results, duplicate results, negative latency, reset, and counter invariants.

#### 2.3 Pointer calibration

Use the versioned validated `PointerCalibration` model. Add persistence only through the existing mapping store with migration/default behavior. Keep the current default mapping unchanged when no profile exists. Add orientation/display context only when it can be represented deterministically. Calibration changes coordinates, never authorization. Add tests for clamping, invalid profiles, version migration, mirror behavior, and round trips.

#### 2.4 Geometry and ownership evidence

Port only pure geometry contracts first. Add explicit reason codes and confidence, not opaque booleans. Feed results into the current ownership tracker and gesture transaction state machine. A rejected or ambiguous result must cancel the candidate, never select a fallback hand by guess.

#### 2.5 Center-gated swipe detector

Benchmark the prototype-inspired center-gated detector against the existing `SwipeGestureEngine` using identical replay traces. Require deliberate cross-gate motion, axis dominance, displacement, speed bounds, cooldown, and re-arm. Keep the detector independent from accessibility dispatch. Integrate only behind a feature flag after false-positive and latency criteria pass.

#### 2.6 Diagnostics and production UI

Only after runtime contracts stabilize, add diagnostics for camera readiness, result age, FPS, stale drops, in-flight work, current owner, calibration state, and rejection reasons. Do not expose sensitive window content. Keep diagnostics read-only with respect to authorization.

## Engineering quality requirements

- Kotlin idiomatic code with small pure components.
- No unexplained magic thresholds; every threshold has a named constant, unit, rationale, and test.
- Prefer immutable data classes and explicit state transitions.
- Use monotonic timestamps and reject time going backwards.
- Keep allocation bounded in per-frame code.
- Make concurrency ownership explicit; synchronize or confine mutable telemetry.
- Preserve public compatibility where possible, but do not hide breaking behavior behind defaults.
- Add tests before or with every behavior change.
- Add property/invariant tests for coordinate bounds, freshness, ownership, and counter conservation.
- Run `git diff --check` and inspect the complete diff before each commit.

## Validation gates

Before claiming completion:

1. `./gradlew :app:testDebugUnitTest`
2. `./gradlew :app:lintDebug`
3. `./gradlew :app:assembleDebug`
4. `./gradlew :app:assembleRelease`
5. Existing APK verification tasks, if available.
6. CodeQL/CI workflow where GitHub access is available.
7. No new permission, export, debuggable, network, or window-content regression.
8. All new metrics have tests and no counter can become negative.
9. Existing tests remain green.
10. If local Android SDK is unavailable, report that plainly and rely on the GitHub CI run; do not claim APK success locally.

## Commit discipline

Make atomic commits, normally in this order:

1. `test: add replay and vision metric contracts`
2. `feat: add vision freshness telemetry and policy`
3. `feat: add versioned pointer calibration`
4. `feat: add geometry-aware center-gated swipe evidence`
5. `feat: add diagnostics for vision and safety decisions`

Do not mix formatting-only changes with behavior. Do not move `baseline-successful-apk` manually. Push only when the user’s authorization and repository workflow permit it.

## Final report format

Return:

- Intent and scope.
- Files changed.
- Prototype evidence used.
- Safety properties preserved.
- Tests and commands run, with exact outcomes.
- CI run links and artifact verification if available.
- Known limitations and device-testing requirements.
- Explicitly untouched items and why.
- Recommended next smallest branch.

If any requirement is uncertain, inspect the code and tests first. Make the safest reversible choice. Do not ask for routine clarification when the repository evidence is sufficient; stop only for a genuinely material product, privacy, permission, or release decision.
