# Gaze Lab guided integration (13 November 2026)

Base: `main` 8a3cc2c285db6f8073a931085021e3fd7fabb2c8. This is an isolated optional-screen change, not a new production input mode.

## Existing pipeline and invariants

The app already owns one CameraX camera stream and passes occasional copied, downsampled RGBA frames to an opt-in background FaceLandmarker. The model is locally verified at build time. The observer receives estimated iris-relative/head features; it does **not** infer reliable screen targets without personalized calibration.

- Hand gesture controls remain primary and do not depend on Gaze Lab.
- Entering the lab disarms and leaving it never rearms Aergis.
- Gaze samples and coordinates are in memory only; no face image upload, recording, or persistent calibration.
- Gaze recognition never writes to `AirRuntime.pointerState`, `ActionDispatchGate`, or `AirAccessibilityService`.
- The face worker is created only when the lab is resumed and stops when it pauses. Existing hand inference and camera binding remain untouched.

## Implemented experience

- Replaced the old utilitarian calibration layout with Aergis-branded Compose surface, high-contrast targets, eye-estimate preview, explicit status and large controls.
- Exposed Gaze Lab from Control → Precision (in addition to Settings → Advanced).
- Supports starting shared camera capture from the lab with runtime permission request when needed.
- Keeps nine personalized calibration targets; now validates nine distinct held-out canvas positions: corners, edge midpoints and near-center. These are **canvas** positions, not a full-screen corner reach test.
- Automatic capture after at least 1,800 ms on the target, only with five distinct fresh eye observations; per-target stability includes iris direction and head coordinates. Manual capture remains available.
- Rejects missing/non-finite, old, duplicate, moving or prior-target observations. Changing target clears the previous sample window.
- Corrects the v1 square-coordinate error metric: calculate Euclidean error in display pixels (dx times screen width, dy times screen height), divided by the actual full-screen pixel diagonal. Report signed horizontal/vertical pixel misses and the worst labeled target; copied reports identify metric v2 and APK provenance. Training-only scores remain excluded.\n- **Prior build 1425 numbers (6.2% median / 13.2% worst) cannot be numerically corrected without the underlying horizontal and vertical residuals. They are not directly comparable with v2.**
- Gaze estimation visibly disappears after lost/stale samples. Leaving the screen releases its worker and cannot authorize gestures.

## Verification and acceptance

**Host/CI:** GazeCapturePolicyTest covers freshness, target transition, stable automatic capture, unstable/non-finite frames and timestamp replay. GazeValidationTest covers pixel-weighted portrait/landscape error, signed residuals, median/worst, invalid input and nontraining targets. Existing GazeLabActivityTest checks disarm and no rearm. Run the full existing Aergis CI test, lint, debug and release tasks and CodeQL for the PR SHA.

**Device-only gate (Galaxy A54, exact candidate APK):**
1. Start camera directly from the lab, grant permission if needed, and confirm face detection without a visible hand.
2. Complete all nine targets automatically using ordinary head posture. Repeat manual-capture mode, deliberate instability, and eyes-out-of-frame interruptions.
3. Validate all nine unseen canvas targets, copy each point's dx/dy in pixels and median/worst pixel-weighted diagonal error. Measure with portrait/landscape re-entry separately. Repeat complete calibration at least twice to check variance.
4. Compare hand camera-to-pointer latency and heat with Gaze Lab off and on for a matched session; ensure no camera duplicate/slowdown.
5. Disarm/pause/leave/return; ensure no face inference remains running and **zero** gaze-generated Android actions. Verify normal gesture navigation, pointer and thumb-middle clicking remain unaffected.

## Decision gate

The completed lab is a research/validation tool. **Production gaze-assisted targeting is not enabled** until the exact APK achieves acceptable held-out target selection, stability, no unintended actions, and sustainable performance on actual hardware. Do not merge this with experimental PR #92 or pinch PR #93 without independent checks. The control-path rollback is the current `main` build reference.
