# Pointer and click tracking continuity

Execution: single agent, inline under the owner's standing continuation authority.
Continue existing PR #74 at `3fdee36`; main/build reference remains `a53596b`.

Goal: retain dependable pointer positioning and thumb-middle click recognition
through fine image motion, bad model coordinates and transient hand-label refits.

Design: fix evidence handling at its source. Preserve the selected filter and
all existing timing/contact thresholds, anchors, settings, mappings and action
authority. Adding smoothing would hide invalid inputs and increase latency;
replacing the tracking stack would unnecessarily risk the working baseline.

## Tasks

- [x] Reproduce odd-pixel fallback loss, invalid-coordinate filter contamination,
  and fixed-hand-preference label refits with production-core JUnit regressions.
- [x] Search every pixel within the existing bounded fallback area, including
  at image edges. Cache scores once for uniqueness rejection. Preserve immutable
  templates and motion-only authority. Verify repeated/occluded textures reject.
- [x] Reject off-frame/nonfinite index coordinates before either pointer filter
  learns them. Preserve explicitly authorized refits using the last stable point;
  they cannot seed/change filter history. Verify no replay on held-contact recovery.
- [x] Apply configured handedness to acquisition. For a continuously tracked owner,
  retain geometric continuity despite a label refit. Keep existing loss/ambiguity,
  preference-change, expiry and opposite-hand-after-loss cancellation contracts.
- [ ] Exercise actual engine fallback and native clicking/ownership regressions,
  self-review the diff, run local core/tooling tests, publish one coherent update,
  then require full GitHub CI/lint/debug/release/APK verification and CodeQL.

Review focus: odd motion on both axes/near edges; unrelated missing joints;
invalid coordinates before contact; held contact after loss; preferred-hand
refits with a second detection and detection-order changes.

Sensitive scope: pointer image fallback, interpreter input validation and ownership.
No camera/model/delegate/mode, coordinate transforms/calibration, accessibility
backend or service-lifecycle changes. Physical stability/recognition/comfort
remain exact-APK device acceptance; no merge or baseline promotion.
