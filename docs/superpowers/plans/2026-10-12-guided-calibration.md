# Guided calibration implementation

> Execute inline, one agent, under the user's approved usability roadmap.

Baseline: `baseline-successful-apk` = main = `8e024ba8c303d4cec76c9c499c71c324f39ce804`; Aergis CI #246 and CodeQL passed. PRs #53 and #54 are merged.

## Scope
Make the existing four-corner capture usable without presenting six advanced sliders first. Show a corner instruction and sample progress; keep the preview; allow restart, save and cancel. Beginning calibration disarms actions through the existing control-mode API; neither cancel nor save rearms them. Cancel resets the in-memory session only, preserving the saved profile. Successful save closes the capture draft. Advanced sliders, immediate preset, disable and reset remain accessible outside capture.

This is the guided manual-capture portion of roadmap unit 4. Automatic hands-free capture, timestamped sample freshness, named comfort profiles, clutch and target-validation practice remain later work. Do not claim those are implemented. The current collector equations and profile format remain unchanged.

## Files / contract
- `MainActivity.kt`: start/cancel/save session actions and corner-by-corner calibration card; use existing `PointerCalibrationSession`, `PointerCalibrationStore` and `AirRuntime.controlMode`.
- `res/values/strings.xml`: corner instructions, explicit Save calibration/Cancel calibration/Advanced adjustment labels, disclosure of actions disarmed and live advanced changes.
- `GuidedCalibrationFlowTest.kt`: Robolectric regressions for start disarming, cancel preserving profile and clearing samples, save closing draft, rejected sample not advancing.

## Acceptance
- [x] Observe regressions fail on baseline in CI before changing production behavior.
- [ ] Implement guided capture controls before advanced controls; full-width buttons support narrow screens and large text.
- [ ] Save enabled only in READY; invalid results retain error/retry path; Cancel changes no stored profile.
- [ ] Calibration start disarms actions; no automatic rearm on exit.
- [ ] Keep mapping equations, hand/orientation profile separation, camera, MediaPipe, classifier decisions and accessibility dispatch unchanged. Add a narrow READY-only raw preview path with no pointer/action authority.
- [ ] Unit suite/lint/debug/release/APK verification pass in CI.
- [ ] A54: four comfortable corners, save, edge reach, cancel preservation, portrait/landscape and large text.

## Execution notes
User authorized continuing implementation and PR publication. Physical-device acceptance remains separate from CI. Existing raw samples have no timestamp, so this PR must not advertise stale-sample rejection or automatic stability confirmation.

CI #248 confirmed five expected failures before engine implementation (272 tests total). Disarming previously suppressed raw calibration samples. The integration change supplies upright raw coordinates only for fresh, selected hands in READY, with safe foreground and no device motion. It hides the system pointer and resets touch state; existing command eligibility remains disarmed. Two integration tests cover this boundary, missing hands, protected context and motion. Leaving the calibration screen cancels its unsaved draft.
