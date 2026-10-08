# Aergis UI experience implementation plan

> Execute inline in this workspace. Preserve the user's single-agent preference and approval before commits.

**Goal:** Establish the visual system and a usable control shell while retaining every existing feature.
**Architecture:** Reusable native Compose theme/components; four visible destinations. Activity callbacks remain the functional bridge. Home collects a distinct projection, not frame counters or coordinates.
**Tech stack:** Existing Compose/Material3 and Activity dependencies; no new dependencies.
**Spec:** `docs/superpowers/specs/2026-10-08-ui-experience-design.md`

## Constraints
- Branch work/ui-experience. At the start of work, and whenever resuming unchecked tasks, resolve `baseline-successful-apk` and use that commit as the functional reference. Any recorded SHA is historical evidence only; never move the baseline marker manually.
- Edit experience files only. No engine, dispatch, filter, calibration equation, service, CI or dependency changes.
- 48dp minimum targets, scalable text, system insets and explicit state labels.
- No infinite animations, blur, shaders, bitmap assets or fabricated functionality.
- Prepare a concrete diff before commit approval; CI is the Android verification authority.

## Review focus
Camera active without a hand must not say tracking. Armed plus motion/protected/unknown context must say actions paused. Configuration changes must preserve navigation. Narrow screens/large fonts must not hide stop/disarm. Telemetry must not redraw idle Control.

## Task 1: Shared system and truthful state
Files: create `ui/AergisTheme.kt`, `ui/AergisComponents.kt`, `ui/AergisIcons.kt`, `ui/HomeUiState.kt`; test `ui/HomeUiStateTest.kt`.
- [ ] Add state regression tests before the projection implementation; attempt Gradle execution and record environment blockers.
- [ ] Implement exact palette, typography, spacing, shapes and motion tokens from the spec.
- [ ] Components: `AergisPanel`, `AergisButton`, `AergisStatusChip`, `AergisSettingRow`, `AergisSection`, `AergisSlider`, `AergisIcon`, `AergisMark`.
- [ ] `HomeUiState.from(runtime: AirRuntimeUiState)` excludes coordinates and telemetry; `status(accessibilityEnabled: Boolean): HomeStatus` has explicit precedence.
- [ ] Verify palette contrast and state tests in CI after commit approval.

## Task 2: Shell and Control
Files: create `ui/AergisShell.kt`, `ui/ControlScreen.kt`; modify `MainActivity.kt`, strings and styles.
- [ ] Implement four destinations using saved destination name and native navigation semantics; Back returns to Control.
- [ ] Present identity, real status, Start/Stop then Arm/Disarm at the top; setup links when required; actual hand/pointer state and navigation shortcuts.
- [ ] Keep existing session, switch, mapping and export callback bodies unchanged.
- [ ] Use only HomeUiState for Control collection; lifecycle collection and flow distinctness.
- [ ] Review start denied, stop, disarm, idle, lost hand, safety pause and accessibility disabled.

## Task 3: Existing supporting flows
Files: `MainActivity.kt`, `TestingToolsCard.kt`.
- [ ] Move calibration, practice and existing test tools to Tracking; move mappings to Gestures; preferences and OS links to Settings.
- [ ] Replace default card/button treatment with shared components; retain every calibration slider and all report actions.
- [ ] Give preview canvas a clean frame; collect coordinate updates at the preview leaf only.
- [ ] Keep diagnostic filter comparisons explicitly experimental; do not add media/profile/pro/future controls.

## Task 4: Review and handoff
- [ ] Verify XML, resource references, contrast, `git diff --check`, callback/engine boundary, and that the functional baseline was resolved from `baseline-successful-apk` for this work session rather than pinned to a historical SHA.
- [ ] Self-review implementation against all original UI controls and the spec.
- [ ] Record local Gradle result accurately. Prepare one foundation commit for approval; do not commit or push yet.
- [ ] After approval, one commit and draft PR trigger Actions; fix failures before presenting an APK. Device validation precedes merge and later redesign units.
