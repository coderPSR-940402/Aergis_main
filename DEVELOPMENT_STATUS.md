# Development Status

## Current working systems

- CameraX latest-frame gesture analysis with prompt `ImageProxy` cleanup.
- On-device MediaPipe gesture recognition with GPU-to-CPU fallback.
- StateFlow-backed runtime state and lifecycle-aware Compose UI.
- Pointer smoothing, kinematic validation, coordinate mapping, click hysteresis, and swipe detection.
- Accessibility gesture dispatch with configured action mappings.
- Bounded vision retry recovery and localized Compose status/error presentation.

## Changes this pass

- Added a pure gesture transaction state machine for classifier-mapped one-shot actions.
- Static gesture commands now require stable multi-frame evidence before dispatch.
- Added ownership continuity, stale-frame cancellation, cooldown, and neutral-release rearming.
- Reset the transaction gate on tracking loss, vision recovery, and engine close.
- Added regression tests for stable confirmation, release-before-rearm, ownership changes, and stale frames.
- Added continuity-based primary-hand selection using hand center, palm size, handedness preference, and safe rejection of distant replacement hands.
- Added regression tests for reordered detections, configured hand preference, and ownership takeover rejection.
- Added timestamped velocity history to predict the owned hand's next position.
- Added a score-margin ambiguity gate that rejects close competing hand candidates instead of guessing.
- Classifier commands now require a safely selected owner even when pointer rendering is disabled.

## Verified results

- Repository was inspected at baseline `ffb2882`.
- `git diff --check` passed for this pass.
- Obsolete frame-level classifier cooldown references were removed.
- Transaction-gate CI passed on `a685323`: run `36938950059`.
- Hand-ownership CI passed on `c086778`: run `36939617650`.
- CodeQL passed on `a685323`: run `36938950119`.
- CodeQL passed on `c086778`: run `36939617636`.
- Velocity-arbitration CI passed on `8f13c1c`: run `36950569617`.
- Velocity-arbitration CodeQL passed on `8f13c1c`: run `36950569678`.
- Local Android build execution remains unavailable because the sandbox does not provide the project Android SDK; CI is authoritative for APK/lint validation.

## Important unresolved issues

- Primary-hand ownership still needs device validation across occlusion, rapid crossings, and large hand-scale changes.
- IMU device-motion cancellation is not implemented.
- Protected foreground-context policy and explicit READY/ARMED control mode are not implemented.
- Physical-device false-positive, latency, jitter, thermal, and battery measurements are unavailable.

## Device testing needed

- Verify static gesture confirmation latency and release behavior on a physical device.
- Test hand crossings, occlusion, orientation changes, and service reconnects.
- Confirm that click and swipe primitives remain independent from classifier command gating.

## Next frontier

Implement persistent hand tracking and explicit control arming/context safety before expanding gesture vocabulary.
