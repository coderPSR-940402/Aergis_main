## Scope

Describe the single coherent change and the user-visible behavior it is intended to affect.

## Baseline and evidence

- [ ] Resolved `baseline-successful-apk` before changing functional code.
- [ ] Identified the latest relevant device evidence separately from the CI build reference.
- [ ] No unrelated engine/UI/dependency/persistence/release refactor is bundled into this PR.

## Regression-sensitive areas

Check every area touched by this PR:

- [ ] CameraX / frame freshness / lifecycle
- [ ] MediaPipe / model / delegate / inference
- [ ] Hand ownership / partial-hand tracking
- [ ] Pointer mapping / filtering / edges / reacquisition
- [ ] Pinch / click anchor / hold / drag / release-to-rearm
- [ ] Gesture recognition / arbitration / cooldown / neutral rearm
- [ ] Calibration / orientation / migration
- [ ] Accessibility dispatch / global actions / safety / action epochs
- [ ] Settings / persistence / upgrade / rollback
- [ ] None of the above

## Verification

- [ ] Focused regression test reproduces the changed behavior where applicable.
- [ ] Full unit suite passes.
- [ ] Android lint passes.
- [ ] Debug APK assembles and verifies.
- [ ] Minified release APK assembles and verifies.
- [ ] Production-signing smoke test passes.
- [ ] CodeQL passes.
- [ ] PR is rebased/retargeted to the actual current `main` before final merge verification.

## Device gate

- [ ] Device validation is **not required** because this change cannot affect physical/runtime behavior; explain why below.
- [ ] Device validation **is required** and the exact APK/commit/run plus results are recorded below.

Device evidence / reason for N/A:

## Merge decision

Do not merge solely because compilation is green. A functional change may merge only after its regression-sensitive tests and CI pass; camera, gesture, pointer, click, latency, thermal, accessibility execution, and other physical Android behavior remain device gates when affected.
