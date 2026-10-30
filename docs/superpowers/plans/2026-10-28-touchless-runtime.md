# Touchless runtime implementation plan

Execution: single agent, inline, as requested. Continue PR #74 from `e825204`;
main/build reference is `a53596b`. Do not merge or advance device acceptance.

Goal: gesture-specific visibility, lower camera/pipeline work, bounded pinch-anchor
continuity, and a mapped entry into the existing guided calibration workflow.

Architecture: retain CameraX's latest-frame handoff, the pinned MediaPipe model,
existing action arbitration and native Compose screens. Keep full classification
whenever gesture commands are enabled; use the identical hand-landmarker submodel
for pointer-only operation, with full-recognizer fallback. Never invent neutral
classifier evidence. No model/network download at runtime or new dependency.

## Tasks and acceptance

- [ ] Gesture visibility: add explicit joint masks for every registered pose and
  conservative neutral evidence. Skip irrelevant DIP joints; require every joint
  actually used to distinguish the pose. Preserve full default evaluator and canned
  index-bone continuity rules; custom angles already provide their own geometry.
  Recognize custom geometry once per frame, reusing it for directional gun identity.
  Tests: all existing poses, required vs irrelevant missing joints, partial-hand
  commands and neutral/rearm, ambiguous poses and ownership changes.
- [ ] Camera delivery: request supported FAST noise/edge modes on the actual front
  camera, retain advertised <=60 FPS selection and bind fallback, prefer CameraX
  native RGBA conversion over the Java per-pixel YUV path. Keep owned buffer lifetime
  and the replaceable waiting frame. No manual exposure or unsupported request values.
  Tests: supported/unsupported processing modes, capture-format integration, existing
  frame ownership/stop/backpressure tests. Actual latency improvement needs A54 timing.
- [ ] Inference cost: lazily load the pinned bundle's hand-landmarker submodel for
  gestures-off operation. Cache instances on the same inference executor; only one
  runs per frame. Preserve full inference for every enabled gesture mapping and
  rehearsal. Reset action history across existing settings epochs. Fall back to the
  full recognizer if the optional path is unavailable. Close both on failure/stop.
  Tests: model extraction and invalid bundles, result adapter categories absent (not
  confident neutral), mode transitions, all mappings and native-source compatibility.
- [ ] Anchored click protection: retain a previously trusted visible target through
  at most 350 ms of index-tip refits, only with fresh owned thumb/middle/palm evidence
  and current pinch confirmation. No new movement authority, stale replay, ownership
  transfer, pending-click retry or template seeding from rejected index positions.
  Epoch, mapping/filter, safety and camera-loss resets remain authoritative.
  Tests: precision jump, nonfinite index, one native anchored tap, no repeat,
  deadline, hand loss, owner/pause/rotation/disable cancellation and false overlap.
- [ ] Calibration action: append a stable mapped action, cancel native touch, disarm,
  open/reuse MainActivity and start the existing guided capture. Handle cold/warm
  activity entry exactly once, preserve saved profiles until explicit valid Save,
  and do not start camera or rearm on entry/exit. Existing corner capture/review UI
  remains the calibration workflow. Tests: mapping persistence, platform intent,
  pointer-off dispatch, cold/warm navigation, recreation and cancellation.
- [ ] Review the complete diff, run local core/Python tests, publish one coherent
  batch and repair attributable CI failures. Require full tests/lint/debug/release,
  APK checks and CodeQL; link the exact APK and distinguish device-only acceptance.

Sensitive areas: pose eligibility, CameraX request configuration and pixel conversion,
MediaPipe task selection/lifetime, pinch authority and calibration entry. Existing
pointer x/y transforms, filters, calibration math/storage, action mappings and
native gesture injection retain their current contracts.


## Implementation checkpoint

All five implementations and their focused/native regression tests are written.
Host core suite: 63 passed; tooling: 13 passed; XML/whitespace pass. Exact pinned
submodel extraction verified. Native/Compose suite and full APK/lint checks await CI.
Author review only, per the user's no-agent instruction. Execution choices: the
lighter path is restricted to armed commands-off sessions to preserve rehearsal;
calibration reuses existing explicit corner capture/review rather than inventing
new dwell gestures or automatic profile overwrite. Hardware speed/quality and GPU
availability remain device-only acceptance items.
