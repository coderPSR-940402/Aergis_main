# Aergis UI draft status

Plan: `docs/superpowers/plans/2026-10-08-ui-experience.md`
Base: `19bfa6805bbee746712e00a0b11a1153e47c9c78`
Branch: `work/ui-experience`. User approved the foundation commit and draft PR on 2026-10-08.

## Written
- Shared Aurora colors, typography, spacing, shapes and motion tokens; bordered cards, gradient buttons, line icons, status chips, switch rows and sliders.
- Saved four-destination shell; Back returns to Control; real capture/action state remains visible on every destination.
- Control with capture and arm/disarm at the top, actual readiness/hand/pointer state, permission guidance and navigation shortcuts.
- Existing calibration, practice, mirror, recording, export and diagnostics organized under Tracking. Existing mappings under Gestures; preferences and OS links under Settings.
- Home excludes frame counters, pose evidence and pointer coordinates. Tracking diagnostics sample UI state at 4 Hz. Pointer preview reads coordinates in the draw phase. Recorder, recognition and camera rates are untouched.
- Seven state-projection test methods, written before the model; execution has not reached the tests.

## Verification evidence
- `git diff --check`: pass, with additional whitespace checks on new Kotlin files.
- All resource XML parses; 202 unique string resources; all referenced Kotlin string names exist.
- Contrast calculation: all declared text/accent colors have at least 4.5:1 against background, surface and raised surface; primary gradient labels pass at each stop. Rendered UI contrast still requires device review.
- Original non-composable Activity logic matches baseline byte-for-byte except explicit edge-to-edge system-bar styling in onCreate. All six live calibration callback equations and sample/start/complete/disable/reset callbacks retained.
- Tracked diff touches only MainActivity, TestingToolsCard, ControlHandSelector, strings and styles. New sources are under UI and UI tests; engine, runtime, policies, services, filters, model, dependencies, CI and baseline are unchanged.
- Main rechecked on GitHub after edits: still `19bfa6805bbee746712e00a0b11a1153e47c9c78`.
- `./gradlew test :app:lint :app:assembleDebug :app:assembleRelease --console=plain`: blocked before compilation by `java.net.SocketException: Network is unreachable` while fetching Gradle 9.8.0. No tests, lint or APK assembly ran. No Android SDK is installed here. No claim of build or device correctness.

## Review
Self-review only, following the user's single-agent preference. Reviewed source against the original controls, engine boundary, capability matrix, meaningful state, minimum control sizing and performance constraints. New native Compose rendering, navigation restoration, large-font layout, TalkBack behavior and physical tracking performance remain unverified.

Scope decision: keep existing mapping cycle behavior for this foundation; document it clearly. A dedicated action picker and gesture detail are a later review unit. Guided calibration, onboarding and additional motion polish are also later units. No fake profiles, media shortcuts, confidence percentage, latency number, billing or future-feature screens were added.

## Next gate
User approval received for ONE foundation commit:
`feat(ui): establish Aergis visual system and control shell`

Then push the isolated branch and open a draft PR against main to run existing Actions. Monitor unit tests, lint, debug/release builds and APK checks. Correct failures within this UI scope. Provide the successful debug APK for Galaxy A54 testing. Do not merge until CI and device behavior are verified; do not advance the baseline marker manually.

## PR verification follow-up
Draft PR #42 published the foundation as `285bfa2a805c51bd69b8ca91c13a8b6586ecc382`.
Aergis CI #208 and CodeQL reached compilation and failed at the ControlScreen string-resource reference with `NONE_APPLICABLE`: generated class R cannot be used as an expression. The other UI files explicitly import the app R class; ControlScreen used a package wildcard. Add the explicit app R import as the smallest correction, following the working pattern. User approved continuing the CI correction on 2026-10-08. The failing compiler run is the regression evidence; the next Actions run must verify the correction, all tests, lint and APKs before any success claim.

## Later implementation units
1. Guided tracking/calibration flow using existing sample/profile APIs.
2. Gesture action picker/details using existing mappings, plus better practice guidance.
3. Permission onboarding and supporting-screen polish.
4. Accessibility/motion/performance cleanup informed by device testing.
