# Aergis Evidence Ledger and Decision Log

## Current implementation record — 2026-10-17

### Baseline identity and authority

The owner's current directive supersedes historical CI-only "functional baseline"
wording. Preserve current work; do not reset main to an older APK.

| Evidence | Identity / status |
|---|---|
| Latest located explicit device acceptance | Owner: "APK works" / establish working point, 2026-09-27; retrieved prior conversation associates it with CI #50 |
| Accepted source and rollback branch | `f4e51949491adc9d5a68f3fa701aee147ad3ed72`; `known-good/commit-50` resolves to that SHA |
| Accepted source CI | [Aergis CI #50](https://github.com/coderPSR-940402/Aergis_main/actions/runs/36311499379), success |
| Retained rollback artifact | [Aergis-apks-f4e51949491adc9d5a68f3fa701aee147ad3ed72](https://github.com/coderPSR-940402/Aergis_main/actions/runs/36311499379/artifacts/10929107070), not expired at inspection; debug file `app-debug.apk` |
| Artifact archive digest | `sha256:5187983a7a6653a0054f962ee6c87a260b765000f9b70a74c82cd51a3718836e` (ZIP digest, not APK checksum) |
| Development base / automatic CI marker | `d86a094e74740bfcb886f3b264dc1ae169d319a1`; main and `baseline-successful-apk` agree |
| Latest main build | [Aergis CI #272](https://github.com/coderPSR-940402/Aergis_main/actions/runs/38844686053), success; [APK artifact](https://github.com/coderPSR-940402/Aergis_main/actions/runs/38844686053/artifacts/12022463110) |
| Current development device status | No explicit acceptance located for d86a094; newer tested builds with reported defects are not accepted baselines |
| Current work | Branch `fix/classifier-occurrence-rearm`; owner authorised commit, push and PR on 2026-10-17; no open PRs at initial inspection |

The historical acceptance is not release certification, and its exact installed APK
checksum/signing identity was not supplied in the confirmation. The retained artifact
and source reference make rollback investigable; cross-version update/signature
compatibility is not established. Current preview signing certificate SHA-256 is
`c32815743b86b5ee2af3514fd03e135bbd04047a281fe4400eea80abc6cdac44` per
`ci/preview-certificate.sha256`; do not assume it matches the old APK. Do not uninstall
or clear data to test rollback without preserving settings/profile data.

### Interaction-contract audit and selected unit

Confirmed in current source, not inferred from historical symptoms:

- The classifier gate processed `AirAction.NONE` as neutral before confidence checks;
  the engine also replaced blocked commands with `NONE` / confidence 0. Low-confidence
  `None`, disabled and suppressed poses could therefore release a consumed command.
- Gate construction started ready. Reset while ready, tracking loss and owner changes
  could permit a continuing pose to execute without fresh neutral.
- Saved `holdMs` (100–1500 ms) and `repeatDelayMs` (300–3000 ms) controlled command
  activation/rearm. These conflict with the owner's new recognition-triggered contract.
- The 12 `RegisteredGesture` names and `gesture_<NAME>` preference keys are preserved;
  no ID, assignment, default or pointer-action restriction is changed in this unit.

Selected unit: classifier occurrence consumption/rearming and its necessary timing
migration. Acceptance: after valid neutral, one confident recognition emits one
mapped command immediately; persistent pose, weak/missing/blank/unknown evidence,
Disabled, ownership change, tracking loss, epoch changes and engine recreation cannot
replay it. Rearm requires 100 ms of fresh same-owner confident classifier `None`
evidence. No configured activation hold or automatic repetition is introduced.

Implemented:

- Start/reset/invalid tracking waits for neutral; retain timestamp high-water mark.
- Keep recognition identity separate from assignment/policy. Consume even an unavailable
  occurrence; only a real confident `None` category can rearm. Existing dispatch policy
  and queue-time checks remain in force.
- Pass runtime action epoch into the gate to catch pause/resume or switch changes
  between camera frames, not only when an invalid frame happens to be observed.
- Ignore legacy activation/repeat timers; retain their stored values for rollback.
  Remove those two sliders and explain the migration on Gestures and in the picker.
  Saving active settings preserves all 12 mappings and legacy timing fields.
- Update baseline terminology in AGENTS/README; CI workflow and remote refs are unchanged.

Affected: classifier recognition-to-dispatch gating and small mapping-UI integration.
CameraX, MediaPipe model/delegate, pointer mapping/filtering, calibration and native
accessibility touch/cancellation implementations are unchanged. Lifecycle events
now invalidate classifier readiness through the existing action epoch.

### Verification and limitations

- Before production changes, 9 of 10 new host Kotlin/JUnit tests failed against d86a094
  for the expected reasons (replay or prohibited hold/repeat delay). The cadence test
  already passed and was retained.
- After implementation, 30 focused Kotlin/JUnit tests pass: occurrence regressions,
  transaction boundaries, registered identities and tuning. Covers 15/30/60 FPS,
  134/200 ms and irregular cadence, invalid/duplicate/out-of-order input, ownership,
  reset and action epochs. Compiled actual production pure classes with host Kotlin
  2.2.0 / Java 17 and JUnit 4.13.2; this is not the Android project compiler/build.
- Added/updated Robolectric engine-to-native-dispatch tests for real neutral versus
  weak/missing/unknown/blank/disabled categories, startup, restart, between-frame
  pause/resume, saved landmark mappings and command-age bounds. Added persistence
  coverage for all 12 mappings and retained rollback settings. These await CI.
- Local `./gradlew :app:testDebugUnitTest --tests '*GestureTransactionStateMachineTest'`
  could not start: distribution download from services.gradle.org failed with
  `Network is unreachable`. No Android build, lint or Robolectric pass is claimed.
- Resource XML/changed string references and `git diff --check` are checked locally.
- Removing hold delay changes false-trigger risk. Classifier score thresholds and
  fixed geometric scores are not measured accuracy. Device testing remains required.
- Native cancellation is unchanged and has not been physically verified here. This
  unit does not certify the complete interaction contract or release readiness.

### Galaxy A54 test after approved commit and green CI

1. Install the exact candidate debug APK from its PR CI artifact without clearing data.
   Confirm all 12 saved assignments, Disabled entries and calibration survive. Open
   Gestures: no hold/repeat sliders; migration text appears.
2. Use a harmless test screen. With pointer disabled, assign a comfortable pose to
   Back. Start/arm while showing it: expect no action. Relax to an unclassified hand
   pose, then perform it: expect one Back without deliberate holding.
3. Keep the command visible for 3 seconds, briefly occlude/reacquire, switch to a
   disabled pose, and return: expect no extra command until valid neutral. Repeat
   after Pause/Resume, Stop/Start and switching the selected hand.
4. Relax and perform a fresh occurrence: expect exactly one more action. Repeat with
   pointer enabled for a compatible non-touch action; pinch must suppress competing
   classifier commands. Verify unchanged pointer reach, click placement and Stop.
5. Record candidate SHA/run, assignments, lighting, hand, distance, recognition delay,
   intended actions and unintended extras. Any reproducible extra command blocks
   this candidate. No baseline promotion from CI alone.

### Delivery, rollback and next task

Owner authorised committing this unit, pushing its feature branch and opening a PR
on 2026-10-17. Submit for Android CI and repair attributable failures. No merge/release
permission is assumed. Publication and CI links will be recorded after submission.

Development rollback: revert this unit on top of current main; no remote history
reset. Legacy timing keys remain intact, so rollback restores their earlier values.
Keep `known-good/commit-50` untouched as historical device-accepted source reference.

Next coherent unit: migrate thumb-middle recognition from release-tap/native held
input to one bounded tap at the visible hotspot, with competing recognition arbitration
and real cancellation checks. Current held-pinch long press/drag remains incompatible
and blocks release. Do not silently remap it. Then continue the ordered geometry,
calibration and action-catalogue work. Catalogue audit also finds volume multi-step
settings and lock-without-disarm; App search/web/settings actions and truthful
capability states remain incomplete. No new gestures or rotation actions are enabled.

## Historical Phase 0 evidence ledger

| ID | Evidence | Classification | Confidence | Decision/use |
|---|---|---|---:|---|
| E-001 | `main` and `baseline-successful-apk` both resolve to `602a26724728041d18b70838743906a61c0de1dc` | Verified | 1.00 | Historical audit reference only; resolve the remote baseline for new work |
| E-002 | Aergis CI run `37124289111` passed on baseline commit | Verified | 1.00 | Use as build/test/packaging baseline |
| E-003 | CodeQL run `37124289155` passed on baseline commit | Verified | 1.00 | Use as security-analysis baseline |
| E-004 | Manifest has cleartext disabled, no window-content retrieval, and non-exported capture service | Verified | 1.00 | Preserve as hard privacy/safety constraints |
| E-005 | Camera analysis uses `STRATEGY_KEEP_ONLY_LATEST` and closes `ImageProxy` in `finally` | Verified | 1.00 | Preserve; instrument freshness next |
| E-006 | Transaction gate rejects non-monotonic timestamps and resets after a large frame gap | Verified | 1.00 | Preserve; do not treat as complete pipeline freshness |
| E-007 | Prototype timing values around 220/450 ms freshness and 63 ms pointer latency | Directly observed/prototype reference | 0.65 | Treat as hypotheses requiring benchmark validation |
| E-008 | Physical-device campaign document requires zero unexplained actions in safety scenarios | Proposed acceptance rule | 0.90 | Use as release-gate target after device execution |
| E-009 | At Phase 0, the local sandbox did not provide the Android SDK | Verified | 1.00 | Historical environment limitation; superseded by the current audit |

## Decision log

### D-001 — Work from real remote `main`

**Decision:** Keep implementation branch based directly on remote `main` at `602a267`; do not use the earlier documentation/prototype-recovery branch as the implementation base.

**Reason:** The user requires changes to the actual Aergis repository and the directive requires comparison against the authoritative baseline.

### D-002 — First implementation slice is freshness measurement and rejection

**Decision:** Add a pure JVM-testable vision-result freshness policy before expanding gesture vocabulary or rebuilding the UI workflow.

**Reason:** It is a bounded safety/measurement slice that addresses a demonstrated architectural gap without changing normal valid-frame behavior.

### D-003 — Prototype timing values remain hypotheses

**Decision:** Do not hard-code prototype observations as product guarantees. Encode them as benchmark parameters or documented hypotheses until replay/device evidence supports them.

### D-004 — Device-dependent claims remain deferred

**Decision:** Do not claim production readiness for pointer smoothness, touch injection, IMU thresholds, camera latency, thermal impact, or service interruption until the physical-device campaign is executed.

### D-005 — No broad UI rewrite in Phase 0

**Decision:** Defer multi-screen prototype workflow recovery until the runtime safety and measurement boundaries are stronger.

**Reason:** A UI that visually resembles the prototype but lacks authoritative runtime state would violate the directive's truthfulness and safety gates.
