# Aergis Usability Excellence Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans inline, single agent. Check boxes only with evidence. User authorized continuous implementation; no repeated plan approval is needed.

**Goal:** Deliver the approved usability improvements through cohesive, reviewable PRs.
**Architecture:** Extend current services, runtime state and Compose UI. Separate recognition/action changes from experience work; avoid parallel replacement pipelines.
**Tech Stack:** Existing Kotlin/Compose/CameraX/MediaPipe/Android accessibility dependencies; GitHub Actions.
**Spec:** `docs/superpowers/specs/2026-10-11-usability-excellence.md`

## Global constraints
- Baseline: live `baseline-successful-apk`; initially `0a86e482cb1aa77879dd28e8a29f539555f3ebfc`.
- Phone-first; free dependencies; CI authority; one agent; no force push or manual baseline movement.
- Retain working pinch, long press, drag, mirror, coordinate mapping and safety behavior.
- Each PR: focused regressions, self-review, CI unit tests/lint/debug/release/package/checksum verification, APK link and explicit device-test status.
- Continue independent work while awaiting evidence, but do not stack speculative engine changes on unverified device behavior.

## Review focus
- 15/30/60 FPS and irregular cadence must not shorten deliberate action confirmation.
- Tracking loss, unknown owner and hand switching must not complete or rearm a pending command.
- Existing pinch, long press and drag must retain their independent path.
- Rotation, large text, TalkBack and narrow displays must retain usable controls.
- CI success must not be reported as successful physical A54 testing.

## Delivery sequence and acceptance gates

| Unit | Deliverable / primary source area | Acceptance gate | State |
|---|---|---|---|
| 0 | Baseline and capability audit; this spec/plan | Resolve live baseline; distinguish existing work from gaps | Inspected |
| 1 | Deliberate command timing and recovery (`GestureTransactionStateMachine.kt`) | Confirmation/release invariant across cadence; no repeat from held gesture after loss; preserve pinch path | In progress |
| 2 | Explicit gesture mapping picker (`MainActivity.kt`, UI, strings, `ActionMappingStore.kt`) | Select named action or disable; cancel changes nothing; persist selection; explain pointer-mode restrictions | Pending |
| 3 | Tracking/click validation and necessary fixes (`GestureRecognitionEngine.kt`, `HeldPointerTouch.kt`, filters) | Reproduce remaining failure from latest device recording; regression fails then passes; A54 target trials | Await latest-baseline device evidence |
| 4 | Guided calibration and comfort profiles (`PointerCalibrationSession/Store/Profile`, calibration UI) | Entire usable area reachable; save/cancel/retry; reject stale samples; preserve orientation/hand profiles; clutch cannot click | Pending |
| 5 | Interactive practice and meaningful feedback (UI, existing runtime outcomes) | Real target clicks, drag and scroll tasks; distinguish recognition/dispatch/task success; safe exit and pause | Pending |
| 6 | Deliberate scrolling (`pointer/SwipeGestureEngine.kt`, interpreter, UI) | Pointer reposition never scrolls; deliberate entry, neutral stop, controlled speed; cancel on loss | Pending |
| 7 | Three-destination navigation and useful mirror (`AergisShell`, Control, MainActivity, mirror UI) | Control/Gestures/Settings; all prior features reachable; state preserved; real image and overlay alignment | Pending |
| 8 | Accessible premium finish (theme/components/strings/UI) | Black/silver/yellow/purple; >=48 dp controls; text+color statuses; TalkBack, large text, reduced motion; no high-rate Compose churn | Pending |
| 9 | Precision lens feasibility, then implementation if viable | Explicit platform consent; safe protected-content behavior; measured overhead; fallback when unavailable | Pending |
| 10 | Sustained-use/device release campaign (`docs/BENCHMARK_SPEC.md`) | Evidence below; regression comparison; actual APK tested on A54 | Pending |

Units 2 and UI-only portions of later units can proceed without waiting for engine device acceptance. Do not claim the whole roadmap complete when only source/CI is complete. Re-inspect the current files before elaborating each later unit; future units are delivery contracts, not invented API specifications.

## Task 1: Time-based command transactions
**Files:** modify `app/src/main/java/com/airgesture/control/GestureTransactionStateMachine.kt`; test `app/src/test/java/com/airgesture/control/GestureTransactionStateMachineTest.kt`.
**Consumes:** existing `Input(action, confidence, timestampMs, tracking, ownershipId)`; monotonic frame milliseconds.
**Produces:** unchanged `process(Input): AirAction?`, `reset()`, `currentState()` entry points. Constructor uses `confirmationMs=100L`, `neutralReleaseMs=100L`, `cooldownMs=700L`, `maxFrameGapMs=250L`, existing confidence threshold.
- [x] Add cadence regression using default construction: at 15/30/60 FPS, first action cannot occur before 100 ms and must occur within the next sample interval; held gesture fires once.
- [x] Add regressions for lost tracking/low confidence not counting as release, changed owners not combining confirmation, stale/duplicate timestamps, invalid confidence and reset preserving a fired command's release latch.
- [x] Run pure Kotlin/JUnit tests against old source; record actual assertion failures. Android Gradle may be unavailable locally; use CI for Android verification.
- [x] Implement duration-based confirmation/release and continuity handling, retaining the external engine API. Never add artificial frames or modify pinch gesture handling.
- [ ] Run focused tests plus relevant pure regression suite. Run full existing CI after publishing branch; fix failures within this unit.
- [ ] Record source scope, evidence, limitations, commit and open PR; do not merge on synthetic evidence alone.

## Task 2: Explicit action selection
**Files:** `MainActivity.kt`, new `ui/ActionMappingPicker.kt`, `strings.xml`; existing `ActionMappingStore.kt` persistence.
**Consumes:** `mapping(source): AirAction`, `setMapping(source,target)`, `GestureActionPolicy.isClassifierActionEnabled`.
**Produces:** dialog selecting a named action (including NONE/Disabled) with selected state and cancellation, without changing dispatch policy.
- [ ] Replace tap-to-cycle with explicit choices; preserve existing mappings until selection.
- [ ] Explain which actions are unavailable while pointer control is active; never silently enable them.
- [ ] Confirm selection persists and NONE round-trips; dismissal changes nothing.
- [ ] Validate strings, layout and CI; keep this a separate experience PR.

## Physical acceptance campaign (proposed initial release gates)
Record APK SHA and setup for every trial. Repeat equivalent trials on baseline and candidate; do not invent a comparison result.
- Select 20 targets distributed over the display, first large then small. Initial target: >=19/20 intended selections, zero duplicate activations; record misses and acquisition time, not just recognition.
- Hold a relaxed hand for 60 seconds: zero unintended actions; record pointer jitter.
- Perform 10 loss/reacquisition cycles and 10 reposition cycles: zero unintended actions and no owner takeover.
- Scroll a long page, stop, reverse, tap a link, long press and drag; record each task outcome.
- Run 15 minutes with mirror on and off; record camera/results FPS, p50/p95 latency, thermal behavior and battery change. Report achievable sustained rates, not requested rates.
- Repeat portrait/landscape, either hand, near/far comfortable positions and ordinary indoor lighting. Any accessibility/camera crash or stuck touch blocks release.

## Progress / decisions
- 2026-10-11: Live baseline and main match; no open PR found. Current VIDEO mode already performs inter-frame tracking; LIVE_STREAM is not automatically an upgrade.
- Local Gradle wrapper cannot download its distribution (network error); Java 17 exists, Android SDK absent. CI remains authoritative. A standalone Kotlin compiler may verify pure logic only; it cannot establish Android build success.
- User's continuous-work request supplies execution authorization; no subagents. Cohesive feature branches and PRs are authorized; physical validation and merge remain separate gates.
- Unit 1 local evidence: 11 JUnit regressions failed on baseline, then 11/11 passed after implementation using standalone Kotlin 2.2.0 + JUnit 4.13.2. This is supplementary pure-logic evidence, not the project's Kotlin 2.4.21/Android CI result. `git diff --check` passed. Self-review checked engine reset call sites and preserved its process/reset signatures; no camera, pointer, calibration or dispatch implementation changed.
- Unit 1 behavior decision: reset clears incomplete evidence but preserves an already-fired command's neutral-release latch. Otherwise engine loss/recovery resets would permit repeated held commands. The same continuing engine instance requires tracked neutral before another command after recovery.

## Publication checkpoint
- Reliability branch: `work/usability-command-timing`; focused logic implementation and 11 passing local tests. Full CI and device validation are pending.
- Independent experience branch: `work/usability-gesture-picker`, commit `0108185`; explicit mapping picker with save/cancel/disable/default selection. XML/string/whitespace checks pass; Android compile and device UI validation pending. Its detailed checklist is `docs/superpowers/plans/2026-10-11-gesture-mapping-picker.md` on that branch.
- Automatic approval review rejected pushing source and planning documents to `coderPSR-940402/Aergis_main`, citing lack of explicit destination authorization in this turn. No alternate publication route was attempted. Ask permission for these concrete feature branches/PRs before retrying.
- No PR, CI result, new APK or device-success claim is available from this work yet. Resume at publication/CI after authorization, not by reimplementing the local commits.
