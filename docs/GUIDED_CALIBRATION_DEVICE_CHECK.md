# Guided calibration device check

Status: **not executed on a physical device**. Use the APK attached to PR #55's latest successful Aergis CI run. Record its commit, run number and SHA-256; do not substitute an older artifact. Compare failures against the latest successful main APK.

## Setup
Record phone/Android version, orientation, selected control hand, text size and lighting. Enable the existing accessibility service and start camera capture. Keep only the chosen hand visible, with the phone supported and steady.

## Checks
1. Open Controls → Calibration. Confirm a mirrored hand-position dot follows the chosen hand. With pointer control disabled or capture stopped, the tracking guidance should explain what is needed.
2. While armed, start guided calibration. Confirm controls become READY/disarmed, system pointer disappears, and pinches/gestures cause no system actions. The in-app hand preview must keep moving.
3. Capture comfortable top-left, top-right, bottom-right and bottom-left positions, using touchscreen buttons. Confirm step progress, disabled Save before four samples, and Save after four samples. Do not stretch to the camera image's edges.
4. Save. Confirm the draft closes and controls remain disarmed. Explicitly arm, then check reach to each screen edge and several central targets. Record target hits/misses and any unwanted activation.
5. Start again, capture one corner, then Cancel. Confirm the prior saved profile still applies. Repeat by leaving the calibration screen; an abandoned draft must not resume.
6. Capture four nearly identical positions. Save should reject insufficient range and offer Restart/Cancel without replacing the stored profile.
7. During capture, remove the hand or move the phone. Capture should disable after the next result. Reacquire a steady chosen hand and verify recovery. Camera-result silence has no sample-expiry guarantee in this PR; log it separately.
8. If control mode changes through another control, confirm Capture/Save refuse the draft and the screen asks for Restart to disarm again.
9. Repeat in landscape and for the other hand. Verify their stored profiles remain separate. Rotation can discard the current unsaved draft; no automatic rearm is expected.
10. At the largest supported text setting and with TalkBack, verify instructions and all full-width buttons remain reachable by scrolling. Open Advanced adjustment and confirm preset/sliders/disable/reset remain available and apply live as disclosed.

## Result record
- APK commit / CI / SHA-256:
- Phone / Android / orientation / hand / text size:
- Passed check numbers:
- Failed check numbers and reproduction:
- Unexpected actions, stuck touches or camera/accessibility crashes:
- Screen recording or diagnostic capture, if available:

Any unexpected system action during disarmed calibration blocks acceptance. CI verifies source behavior and APK construction; it does not establish comfortable reach, preview alignment or physical target accuracy.

## Touch-free extension (2026-10-28 candidate)

Map Calibrate pointer, enter from cold/warm activity with pointer disabled, and
keep capture running. Follow top-left → top-right → bottom-right → bottom-left.
At each comfortable corner, hold aim steady for at least 300 ms, then pinch thumb
to middle once. Aim is frozen before contact; held contact cannot collect twice.
Release before the next corner. After four captures, review the bounds and hold
Open Palm for 1 second to save. Closed Fist held 1.2 seconds cancels. These are
local calibration inputs while READY, independent of stored command bindings.
They never arm control, inject a click, start capture, or overwrite a saved profile
before explicit valid confirmation. Existing manual controls remain available.

Exercise wrong-corner order, jitter, depth-separated fingers, hand loss during aim,
held contact at reacquisition, opposite-hand replacement, stale/duplicate frames,
activity pause, rotation/recreation, hand preference change and screen exit. No
stale evidence may advance a step or save a profile. Confirm previous profiles
survive cancel/invalid geometry; test comfortable reach with narrower valid spans.
All timing/order thresholds need device acceptance; host tests are not an accuracy
measurement. Retained identity after brief loss is motion-only: phone actions
must observe fresh gesture/opening evidence after authority revocation.
