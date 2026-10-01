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

## Verified results

- Repository was inspected at baseline `ffb2882`.
- `git diff --check` passed for this pass.
- Obsolete frame-level classifier cooldown references were removed.
- Full Android build/test verification is pending CI because the sandbox does not provide the project Android SDK.

## Important unresolved issues

- Primary-hand ownership still needs velocity history and explicit multi-hand ambiguity scoring for robust arbitration.
- IMU device-motion cancellation is not implemented.
- Protected foreground-context policy and explicit READY/ARMED control mode are not implemented.
- Physical-device false-positive, latency, jitter, thermal, and battery measurements are unavailable.

## Device testing needed

- Verify static gesture confirmation latency and release behavior on a physical device.
- Test hand crossings, occlusion, orientation changes, and service reconnects.
- Confirm that click and swipe primitives remain independent from classifier command gating.

## Next frontier

Implement persistent hand tracking and explicit control arming/context safety before expanding gesture vocabulary.
