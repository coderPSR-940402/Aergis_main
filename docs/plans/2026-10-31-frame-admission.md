# Camera input provenance and admission

Goal: prevent queued/in-flight exposures from acquiring action authority after
Pause/Resume, safety, settings, calibration or orientation changes; skip work that
has already exceeded the active input channel's existing age allowance.

Base: PR #75 `f12526b`; main/build reference `1c5a41e`. Spec: the project lifecycle,
freshness and independent pointer/gesture requirements, plus the existing
350 ms command and 600 ms pointer/READY-preview bounds.

One coherent implementation, single agent, same PR, no merge or baseline move:

- [x] Add failing pure admission tests for obsolete source generations, future
      exposures, 350/351 ms hidden-pointer actions and 600/601 ms pointer/preview.
- [x] Add `inputEpoch` beside runtime action/selection epochs. External session,
      safety, settings and configuration invalidation advances it. Internal vision
      gaps, ownership/refit and filter-history resets do not advance camera input
      provenance. Preserve existing action/selection invalidation behavior.
- [x] Stamp RGBA and deferred-YUV exposures before copying; preserve the stamp
      through conversion and latest-frame replacement. Reject obsolete input before
      initialization, again before native inference, and inside result publication.
- [x] Preserve the publication's action epoch for classifier/direct-motion dispatch,
      reusing native pre-dispatch/main-thread checks. Do not refresh a sample timestamp.
- [x] Add native regressions for Pause/Resume and capture-copy races, late result
      rejection, fresh recovery, normal vision-gap continuity and stale explicit
      classifier dispatch. Retain the exact pinch replay and endpoint regressions.
- [ ] Run core/tooling regressions, attempt Android tests locally, review the complete
      patch, publish once and finish Android tests/lint/debug/release/signing CI.

Implementation: `FrameAdmissionPolicy.kt` (pure existing-budget/source gate),
`AirRuntime.kt` (source-generation ownership), `CameraFrameInput.kt` (provenance),
`GestureRecognitionEngine.kt` (admission/publication/dispatch),
`AirAccessibilityService.kt` (retain caller epoch), `ActionMappingStore.kt`
(invalidate changed assignments/tuning, preserve no-op saves). Tests follow those boundaries.

Review focus: ABA Pause/Resume; source changes during pixel copy and conversion;
post-inference changes; vision-loss invalidation without frame starvation; pointer-off
pinches retaining their stricter existing command-age bound. No model/threshold,
coordinate/filter, gesture identity, mapping-value or UI changes.

Verification ledger: policy RED 3/170 failures; GREEN 170/170 core tests; 18/18 Python tests. Local Android bootstrap is network-blocked. Source review is single-agent; final Android CI remains pending publication.
