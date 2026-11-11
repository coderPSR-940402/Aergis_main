# 2026-11-11: palm-relative motion-only index-tip recovery

## Decision

EgoHand's full transformer/mesh reconstruction is not deployed. The Aergis foreground
camera pipeline remains CameraX + MediaPipe GestureRecognizer/HandLandmarker with
existing GPU/CPU fallback, inference modes, gesture mappings and pointer filters.
A small causal camera-space geometric fallback adds no neural inference.

## Problem

When the index fingertip is refitted/off-frame but palm MCP joints 5 and 17 stay
observable, an otherwise moving hand can leave the cursor frozen or hidden.
The previous immutable fingertip-patch fallback works when the entire landmark
hand drops out, but not on every partial-tip failure.

## Implementation

- Save a *trusted observed* index tip in an aspect-corrected palm basis.
- Recover only from fresh same-owner, same-input-epoch and same-action-epoch
  palm anchors for at most 300 ms.
- Bound projected palm-size change (0.6–1.7x), in-plane roll (about 93 degrees),
  screen travel and image bounds. Invalid or ambiguous inputs yield no estimate.
- Feed the estimate solely to cursor-only COASTING. Clear raw pointer tracking,
  cancel touch and pending actions before showing it. Never supply an estimate
  to MediaPipe, GestureInterpreter, click/selection/drag, calibration or mappings.
- A real index-tip result is required to reestablish action authority. It also
  seeds the next fallback. Rotation, filter, calibration, owner and tracking
  resets clear the geometric reference.
- Keep the old fingertip appearance fallback when the whole hand is missing,
  and preserve held-position grace when neither method has defensible evidence.
- Diagnostic marker: PALM_GEOMETRY_MOTION_ONLY. It must never count as a
  trusted tracking sample or click event.

## Regression evidence

`PalmRelativeTipFallbackTest` checks translation, 90-degree palm roll, scale
with aspect correction, stale history, owner/epoch mismatch, invalid geometry,
large jumps and the inability to learn from an invalid tip. Existing pointer,
click, calibration and safety unit tests should continue to pass. The tests
prove geometry/math/authority gates, not physical tracker accuracy.

## A54 acceptance gate

Compare unchanged main APK versus this branch's exact APK on the same
hand poses and camera configuration. Record:
1. 30 short index occlusions, 30 full-hand losses and 30 uninterrupted moves;
   count visible cursor dropouts, wrong-hand acquisitions and reacquire jumps.
2. Pointer-off pinch/select and pointer-on thumb-middle taps, at least 30 each;
   zero fallback-only dispatches, duplicate taps or synthetic calibration points.
3. Straight translation, palm roll, foreshortening, portrait/landscape and two
   hands crossing; exclude geometry estimates from action freshness.
4. Source-to-pointer p50/p95 age, inference cadence, CPU/GPU delegate, battery
   and sustained thermal behavior. No claim of better FPS before measurement.
5. Map/profile/filter/safety/ownership changes during coasting. The cursor may
   disappear; an obsolete fallback must never grant an action or select a hand.

If recorded regressions exceed the baseline, revert the integration rather than
loosening the action gates. Do not promote an APK as device-accepted on CI alone.
