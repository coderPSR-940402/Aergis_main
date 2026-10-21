# Independent controls, finger guns, screen items and Gaze Lab

Owner approved the in-chat design on 2026-10-21. Continue on draft PR #68;
no merge/release or baseline promotion is authorized. The user reports smoother
tracking and improved tapping after the preceding handoff, but did not supply an
exact installed build identity. Do not turn that report into a new verified APK baseline.
Main and baseline-successful-apk resolved to dcee550410730286ee0b543322c999ac13dd97b7.
The existing PR source 39bbb3a and its tested #295 artifact remain the regression comparison.

## Implemented contract

- Independent persisted Pointer, Pointer Clicking and Gesture Commands switches.
  Pointer-origin TAP requires pointer + clicking, not commands. Mapped commands
  require commands, not clicking. Coordinate mappings still require a valid visible
  pointer. Both paths retain ARMED, safety, freshness and action-epoch checks.
- Explicit action origin survives queueing and bounded native input. Toggle/pause/
  ownership invalidation rejects old queued commands; no automatic retry.
- Finger gun reuses L_SHAPE; generic double gun reuses THUMB_INDEX_MIDDLE.
  Existing stable IDs, mappings and defaults remain. Four additional optional IDs
  distinguish double gun left/right/up/down in upright mirrored screen coordinates.
  Directions only override generic recognition when assigned. Diagonals are uncertain,
  not a guessed axis. There are now 22 registered gesture slots.
- NEXT_ITEM / PREVIOUS_ITEM / SELECT_ITEM are distinct from retired keyboard-focus
  actions. They enumerate fresh visible, enabled, non-password clickable accessibility
  nodes and highlight one in a non-touchable, non-focusable yellow overlay. Selection
  re-fetches and validates the target fingerprint, window and action epoch; it consumes
  selection before ACTION_CLICK. No arbitrary coordinate/parent fallback. Scans are
  bounded at 512 nodes; unavailable/oversized/custom-drawn windows fail closed.
  Window-content/scroll/window-state events, other commands, safety/epoch changes,
  interruption, rotation and service destruction invalidate selection.
- App Search also accepts Next/Previous/Select. Whole-page vertical scrolling and
  horizontal swiping remain available with pointer disabled.

## Suggested optional mappings

Use Double Gun Up/Down for Scroll Up/Down, Double Gun Left/Right for Swipe Left/Right.
Use distinct comfortable existing poses for Next Item and Previous Item, and Finger
Gun for Select Highlighted Item. Explicit configuration preserves existing bindings.
One occurrence produces one action; genuine neutral rearms the next occurrence.

## Gaze experiment: implemented lab, not production control

Controls > Pointer > Gaze Lab starts a local, non-exported calibration activity.
Start ordinary camera capture first. The lab disarms actions; exiting does not rearm.
Only while the lab is foreground does a background CPU FaceLandmarker worker exist.
It samples at most 10 Hz from a dedicated downsampled copy of the existing hand-camera
frame, after hand result publication. One job at a time; busy frames drop, no camera
session replacement and no shared mutable frame ownership. Closing stops observation
and closes native resources on the owning worker. Late callbacks are generation-gated.

The pinned 3,758,596-byte official face model is prepared/verified at build time:
https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task
SHA-256 64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff.
It uses the already-declared MediaPipe Tasks Vision library; no dependency upgrade,
new permission, network access at runtime, image upload or gaze logging is introduced.
The extra model increases APK size. Face/iris landmarks do not themselves supply
screen-gaze coordinates (Google's iris documentation explicitly says so).

The lab estimates four eye-relative/head-position features, rejects closed/missing eyes,
collects five fresh stable observations per target, and fits nine personalized points
with ridge regression. Degenerate/non-finite/high-residual fits fail. Fresh targets
at different positions measure held-out normalized screen error; training residual
is not accuracy evidence. Stale, missing-eye and out-of-range estimates disappear.
Calibration is in memory and resets on activity recreation. No gaze observation can
move the production pointer or dispatch a click, gesture, blink or dwell action.
Gaze-to-target nomination with hand confirmation remains a future gated step after
physical-device accuracy and latency evidence; the lab does not claim to implement it.

## Verification and risk

New independent-channel, directional recognition, selection state and gaze-fit tests
failed against their pre-implementation behavior, then passed after corrections.
74 focused JVM tests and 11 Python tooling tests passed locally. Android integration
coverage adds command-off pointer tapping, clicking-off commands, pointer-off navigation,
queued-toggle rejection, and disarmed gaze lifecycle. Full Android CI remains the
build authority; final run/artifact links will be recorded in the PR description.

Material effects: recognition and action arbitration, accessibility node retrieval,
selection overlay and native dispatch source checks, an additional persisted setting,
and opt-in face inference sharing copies of camera frames. CameraX configuration,
hand model/delegate, click hysteresis, pointer filters/calibration and gesture timing
are preserved. The default-disabled gaze hook does not start inference or copy frames.
Prototype gaze overhead, target selection across apps, and new pose comfort/accuracy
require Galaxy A54 evidence. Do not claim 100% clicks or production-ready gaze.

## Device acceptance

1. Preserve data. Pointer+clicking ON/commands OFF: normal thumb-middle tapping works;
   pointer+clicking OFF/commands ON: Back/Home/scroll/swipe/item selection work.
2. Verify all four double-gun directions, left/right hand and device rotation; reject
   diagonals and pinch transitions. Existing generic bindings must survive.
3. Navigate/confirm highlighted items; change screen or scroll before confirmation:
   stale selection must do nothing. Custom-drawn unsupported controls must report
   unavailable, never click a guessed coordinate.
4. Maintain a gesture: one output only. Pause/Stop/protected windows/owner changes:
   no queued replay. Repeat normal and fast contacts for smooth anchored tapping.
5. Optional Gaze Lab: camera capture running, nine stable targets, then held-out
   validation targets. Record error, result cadence and hand-pipeline latency with
   lab OFF versus ON. Leave lab: no continuing face inference or automatic rearm.
