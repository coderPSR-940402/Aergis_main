# Native Aergis product UI

Design source: MagicPath project `463532014671396864`, component `463532054102020096`, approved revision `463576019098570752`. React/CSS is a visual and interaction reference only; none is bundled in Android.

## Scope and baseline

UI-only implementation stacked on PR #68, base `939f3ce9ab2c93d08f426e5b068fb85f14aadeb3`. Main and CI build reference `baseline-successful-apk` resolve to `dcee550410730286ee0b543322c999ac13dd97b7`. The evidence ledger does not promote that CI reference to device acceptance. User feedback confirms improvement but does not identify an exact accepted APK; no functional baseline is changed here.

Preserve recognition, CameraX/MediaPipe, coordinate mapping, filtering, native dispatch, service lifecycle, all existing bindings and tuning. The newer branch has 22 poses and independent pointer-click controls plus screen selection and gaze; preserve those capabilities rather than freezing the older 18-pose design snapshot.

## Implementation plan (single-agent, approved design)

1. Native three-root navigation: Control, Gestures, Settings; saveable supporting history, Back restores the originating page. First-use setup reads actual permissions. Lifecycle-aware session presentation distinguishes capture, arming, safety, gestures and pointer clicks.
2. Apply shared near-black/silver/soft-yellow/violet tokens, subtle raised surfaces, native accessible controls, scalable typography. Control prioritizes gesture and direct-motion entry points; pointer settings are secondary.
3. Connect welcome/permissions/hand choice, mapping search/filter and atomic draft editor, pose guide and motion walkthrough, optional precision/calibration/practice, live feedback, diagnostics/recovery/export and about. Existing Activity owns side effects. Rehearsal/calibration entry explicitly disarms; leaving calibration cancels its draft and never auto-arms. No simulated metrics or fake recognition results ship.
4. Preserve actual settings stores, existing calibration capture/save/validation and mirrored preview, TestingTools recording/recovery/export, independent pointer-click settings, gaze launch. Separate filter comparison under Advanced.
5. Verify navigation history, independent input status, arming availability, pointer-off gesture session, real mapping save/cancel and runtime isolation. Run CI tests/lint/debug/release/package checks. Review the final diff against the base for protected engine paths.

## Verification and review focus

- Hardware Back and process recreation retain a valid route; tab selection resets supporting history.
- Pointer disabled must not label an armed gesture session as paused. Pointer clicking can remain enabled when classifier gestures are off.
- Disarm remains accessible on protected screens and errors; UI never bypasses the existing final execution gate.
- Search/filter empty states, unavailable action warnings and mapping draft cancellation preserve saved values.
- Large text/rotation/landscape: scrolling, minimum 48 dp controls, insets, no fixed-height product screens.
- Calibration uses actual raw tracking, preserves per-hand/orientation profiles, and cancels on exit without auto-arming.
- Diagnostics show real recording/error/export states; filters are not placed among everyday controls.

## Progress

- Preparation: clean isolated worktree created; MagicPath source and PR #68 differences inspected. No subagents used (user preference).
- Local baseline Gradle bootstrap attempted; distribution download is blocked by network. Android SDK is absent. CI remains the Android verification authority; no local Android pass claimed.
