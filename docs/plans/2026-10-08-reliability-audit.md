# Reliability and measurement audit

Baseline resolved from GitHub before changes: `baseline-successful-apk` =
`008056747c93fb7f60f699b0c9eecf21d8dd09cd` (also main at audit start).
This is an audit reference, not a replacement for the dynamic baseline.

## Implementation sequence

1. Reproduce and fix gesture-only confirmation, pointer toggle action invalidation,
   and motion telemetry. Verify engine integration and runtime regression tests.
2. Reject ambiguous replay evidence and separate contiguous benchmark runs across
   missing frames, changed owners, mapping/filter changes, and timestamp gaps.
   Verify Python analyzer and Android export tests.
3. Expose retained recordings and recover interrupted sessions without truncating
   source logs or camera images. Verify recovery, selection, and export contents.
4. Apply compatible build patch releases, run all unit tests, lint, debug and
   minified release builds, and verify APK identity/model contents.
5. Review changes against the baseline and publish a reviewable branch/PR with
   remaining device validation requirements.

## Scope and evidence

Audit covers camera ownership/lifecycle, mirror rendering, hand ownership, gesture
transactions, pointer interaction, calibration, dispatch gating, recording/export,
benchmark analysis, dependencies and CI. Fixes are driven by reproducible findings.
Synthetic and Robolectric results do not establish real camera/display latency,
distance accuracy, accessibility reliability across OEMs, or comparative leadership.
Physical-device trials remain necessary, including interrupted recording/relaunch,
mirror landmark alignment, gesture-only operation, and sustained thermal load.

## Execution ledger

- Audit: confirmed defects in gesture-only state retention, stale pointer actions,
  frozen motion magnitude, recording discoverability and benchmark run boundaries.
- Parallel read-only reviewers identified findings; their subsequent review passes
  were unavailable due to usage limits. Final local diff review covered lifecycle, worker ordering, recovery data preservation,
  metric boundaries, build scripts and generated-artifact exclusions.
- Red/green: seven control/evidence assertions failed on the baseline and passed
  after the fixes; recording recovery/run separation also failed before their fixes.
- Implemented a scrollable retained-recording library and worker-thread recovery.
  Recovery preserves raw bytes, labels unknown counters, reports damaged records,
  and derives duration from the last complete frame (a lower bound after a crash).
- Android and Python reports split comparison runs on missing evidence/context
  changes. FAST first arrival is named accurately; missing synthetic measurements
  remain null. Replay evidence rejects invalid reports, duplicates and bad escapes.
- CI now exercises Python regressions and verifies the pinned model plus package
  and version in both APK variants. Interrupted model downloads cannot poison the
  final asset; verified temporary files are installed atomically.
- Final verification: `./gradlew test :app:lint :app:assembleDebug :app:assembleRelease`
  passed; 224 Android unit/Robolectric tests, zero failures/errors/skips. Lint has
  zero errors and 46 warnings (same count as the baseline).
- `python3 -m unittest discover -s tools -p 'test_*.py'`: 10 tests passed.
- Both APKs passed package/version/model SHA-256 verification; the debug APK passed
  apksigner verification with the committed preview certificate.
- Deliberately replaced the generated model asset with a partial download, ran
  `:app:prepareGestureModel`, and verified automatic repair to the pinned SHA-256.
- Parsed the generated synthetic schema-2 evidence and exercised the analyzer CLI
  against an exported diagnostic ZIP. `git diff --check` passed.
- GitHub CI must independently reproduce these results on the published branch;
  only successful main CI may advance the baseline. No physical device was available.

## Dependency decisions

Checked the official Google Maven and Maven Central artifact metadata on 2026-10-08.
Applied compatible patches: AGP 9.4.0 → 9.4.1, Kotlin/Compose compiler
2.4.20 → 2.4.21, Compose BOM 2026.06.00 → 2026.06.01.
CameraX 1.6.2 and Activity 1.13.0 already match the stable versions inspected.
Core 1.19.1 requires compileSdk 37; keep Core 1.18.0 with SDK 36 until the platform
migration is tested. Lifecycle 2.11.0, Compose BOM 2026.09.00, and MediaPipe 1.1.0
are follow-up minor upgrades; native inference changes need device benchmarking.

## Follow-up release evidence

1. Run the physical-device campaign with live mirror alignment and both portrait
   and landscape calibration, gesture-only operation, held-touch cancellation,
   multiple users/hand sizes and a range of measured distances/lighting conditions.
2. Measure true capture-to-display latency, false activations, target acquisition
   time/error, thermal behavior and battery draw against named alternatives.
3. Exercise relaunch recovery on a real interrupted capture, low storage, and
   document-provider save/share failures. Retained files consume storage until the
   user removes app data; a user-controlled storage-management flow remains future work.
4. Migrate SDK/dependency minor versions in isolated changes with the same CI gates.
   The audit does not establish that every remaining file is defect-free.
