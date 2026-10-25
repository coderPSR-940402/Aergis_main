# Aergis Evidence Ledger and Decision Log

## Current continuation — 2026-10-19: visible viewport authority

### Current reality and recording evidence

- Main and automatic build marker: `0c9eec32598ea2838be5fb35f1a0321df89d59e9`.
  PR #64 merged externally. Preserve newer open [PR #65](https://github.com/coderPSR-940402/Aergis_main/pull/65)
  at `88d7177b1c5ccfb62f83be1d1ed40bdd6eb5d090`; this change branches from that head.
- [CI #281](https://github.com/coderPSR-940402/Aergis_main/actions/runs/39145223332)
  passed Android tests, lint, debug/release builds and APK checks on merge
  `55167770c14d2e48331fb39e51769aebbe5156ce`.
  [APK artifact](https://github.com/coderPSR-940402/Aergis_main/actions/runs/39145223332/artifacts/12143566110),
  debug SHA-256 `72f6a6e73c7129c4ce6ef4e4b6d761bead9ab806a16e091217fb4f42d70aac5a`.
  [CodeQL #270](https://github.com/coderPSR-940402/Aergis_main/actions/runs/39145223325) passed.
- `recording(8).zip` identifies that exact CI #281 merge on SM-A546E / Android 16,
  recorded 2026-10-18 18:52:12 +0200. ZIP SHA-256:
  `1e1d4b807eaf4e45c86c787d69be15ad8ec53d80ce6470ee3ce43ec0d3045655`.
  Accompanying `report(10).pdf` SHA-256:
  `5b5219a072926755eaf28d5d3becf28861e62b5f3f5ea5dda679ade0f1ae99a5`.
- 1,549 frames over 209.576 seconds; three TAP requests and three Android COMPLETED
  callbacks, at 6.978, 84.487 and 182.115 seconds. This is device execution evidence,
  not acceptance, target activation proof or a click-accuracy denominator. The recording
  has no labelled intended attempts, so all missed intended gestures cannot be counted.
  Missing/stale frames must not be relabelled as deliberate release or failed commands.
- All 758 overlay events marked visible report a 1080×2260 window at (0,80).
  68 hotspots lie outside that window. The first native tap used (678.4373,0),
  outside its drawable extent. Attachment-only authorization is the confirmed defect.
- Functional rollback remains CI #50, `known-good/commit-50`, source
  `f4e51949491adc9d5a68f3fa701aee147ad3ed72`,
  [run](https://github.com/coderPSR-940402/Aergis_main/actions/runs/36311499379),
  [artifact](https://github.com/coderPSR-940402/Aergis_main/actions/runs/36311499379/artifacts/10929107070).
  Neither CI #281 nor this new unit advances that accepted baseline.

### Complete unit and acceptance

Request full-display overlay layout on API 30+ (`setFitInsetsTypes(0)`), with explicit
cutout layout policy (API 28–29 short edges). Independently require the applied hotspot
inside the actual attached window and outside physical cutout rectangles. Keep the
coordinate unchanged; never clamp it onto another target. Revoke queued coordinate
authority and invoke the existing bounded-input cancellation path on clipping/detachment.
Recording now distinguishes window attachment from visible hotspot eligibility.

Acceptance: recorded y=0 fails with the inset window, succeeds with an unobscured
full-display window; all drawable edges retain their exact coordinates; cutouts and
zero/invalid extents cannot authorize input; relayout restores only the original target;
a queued tap cannot execute after that target becomes clipped. Protected-screen policy
and non-touchable/non-focusable overlay flags remain in force.

Affected: pointer overlay/window geometry and accessibility target eligibility/cancellation.
CameraX, MediaPipe, landmark tracking, filters, calibration profiles, gesture recognition,
12 gesture IDs, assignments and action catalogue are unchanged. No migration needed.
Risks: OEM inset/cutout layout and actual Android cancellation require device verification;
older API paths retain fail-closed containment if full edge coverage is unavailable.
Rotation actions remain gated. Revert this unit to return to `88d7177`; do not reset main
or discard PR #65. No merge/release authorization is implied.

### Verification and next gate

- Local host suites: **84 tests passed** (42 classifier/tracking/identity; 42
  pinch/policy/recovery/viewport). Includes the recorded top-edge regression, portrait
  corners, landscape inset axis, invalid numbers and zero-sized windows.
- Production containment replay of all 758 recorded visible events rejects the exact
  68 clipped positions and retains 690. This verifies rejection logic, not physical
  full-screen layout on the phone.
- Added Android tests for full-screen parameters, retained coordinates through relayout,
  physical cutout exclusion/recovery and clipped queued-command invalidation. Android
  tests/build/lint await CI; no local Android success is claimed.
- Owner authorized continuation after the explicit commit/push and attributable-CI-repair
  request on 2026-10-19. Publication and Android CI are the next gates; no merge or release
  authorization. Final commit/run/artifact evidence will be recorded in the delivery PR.
- Galaxy A54 gate after CI: in an allowed test screen, move the yellow hotspot to all
  four edges and tap labelled targets (avoid the physical camera hole); target and
  injected point must agree. Repeat after pause/resume and inset changes. A clipped or
  detached target must produce no new touch; interrupt a bounded long press and verify
  actual cancellation/no later replay. Check Settings/SystemUI remain protected and
  physical Stop remains reachable. Use labelled trials for the 95/100 early click gate;
  passing it does not certify release or promote the baseline automatically.
- Next task after this geometry gate: reconcile labelled intended clicks with recognition,
  dispatch and target response; investigate remaining losses only from that evidence.

API reference: [WindowManager.LayoutParams.setFitInsetsTypes](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#setFitInsetsTypes(int))
documents that the default avoids all system bars; zero opts out of fitting those insets.


## Previous continuation — 2026-10-18: bounded pinch command

### Delivery and baseline

- Approved geometry correction: source `264fca3b96ea3af793eb0eb7b97fd4baddbe7269`,
  [draft PR #64](https://github.com/coderPSR-940402/Aergis_main/pull/64).
  [CI #276](https://github.com/coderPSR-940402/Aergis_main/actions/runs/39094476996) passed
  tests, lint, debug/release builds and APK checks on PR merge
  `846903f26834c80e3f4cc00c8dbb58ebd5bd554f`.
  [APK artifact](https://github.com/coderPSR-940402/Aergis_main/actions/runs/39094476996/artifacts/12124456643),
  debug SHA-256 `203ed94ffe266dd72a679b5b7072df9f3d5c7d43d78f73c49605feb36d89d0ad`.
  [CodeQL #265](https://github.com/coderPSR-940402/Aergis_main/actions/runs/39094476997) passed.
- Main/build marker at inspection: `77a1b6a0a097ce585596ae9bf4f87f0419f00aeb`,
  externally merged PR #63; CI #275 passed. Device-accepted rollback remains CI #50 /
  `known-good/commit-50`, identified below. No functional baseline promotion.
- Follow-up branch `fix/bounded-pinch-command` starts at approved geometry commit
  `264fca3`. Owner approved commit/push and CI repairs on 2026-10-18;
  Android CI and device verification are pending. No new APK is claimed.

Publication inspection: PR #64 was merged externally as `0c9eec32598ea2838be5fb35f1a0321df89d59e9`; main and the automatic build marker now agree there. Its tree matches reviewed parent `264fca3`. Publish on this current main without discarding newer work. Final CI/commit/artifact evidence is recorded in the follow-up PR to avoid documentation-only CI loops.

CI #278 caught protected `onServiceConnected` access in the new test setup during unit-test compilation. The fixture now invokes the callback reflectively, matching existing reflection-based engine fixtures; production visibility is unchanged. CI rerun required.

CI #279 compiled and ran 382 tests, exposing delayed first-overlay attachment: the visible target remained unset until another camera update. Hotspot publication now follows actual attach/layout application, and detachment clears it. Two older low-cadence tests were migrated from release-triggered expectations to the explicitly approved contact-triggered contract; the same scenarios and noise protection remain. Rerun pending.

CI #280 passed 381/382 tests. The remaining existing inset/relayout test showed pre-attachment positioning had been skipped. Preserve that geometry computation and gate only hotspot publication on attachment; no test expectation changed. The expanded host suite now passes 80 tests. Final Android rerun required.

### Confirmed problem and acceptance

The recording contains PRESSED/HOLDING/DRAGGING and native held-touch completions.
Current code confirms pinch duration/travel becomes long press/drag, and slow-cadence
contact can emit only on release. These conflict with the latest explicit contract.
Included: pinch recognition, anchor, recognizer arbitration, bounded native output,
visible-hotspot targeting, cancellation and necessary picker/help integration.
Acceptance: contact initiates one bounded tap before release; hold, travel, release,
missing frames and pause/resume cannot create a second command. Competing recognizers
resolve before dispatch. Coordinate output uses the applied visible hotspot.

### Implementation and migration

- At existing slow cadence (sample interval at least 80 ms), accept a valid observed
  open-to-contact transition on contact. High-rate input retains 40 ms internal noise
  evidence. Release only rearms; startup/reset requires opening. Existing thresholds,
  refractory guard and 350 ms continuity limit remain.
- Freeze the pre-contact anchor throughout contact; no hold/drag conversion. Preserve
  the 80 ms release blend. Camera path requests one TAP; pinch evidence excludes the
  classifier before its gate. Epoch change resets recognition between frames too.
- Move the continuous-touch controller unchanged to a **test-only rollback fixture**.
  Keep its historical regressions; no held-touch entry point ships in the APK.
- Distinct assigned poses can request bounded touch actions with the pointer enabled.
  Pointing Up coordinate mappings remain disabled to prevent aiming commands. All 12
  identities, defaults, stored assignments and legacy timing values remain intact.
  No preference remapping. Picker/help explain the eligibility and hold/drag migration.
- Double tap is one native request with two finite strokes. Long press and scrolling
  remain finite assigned outputs. Ordinary pointer travel never dispatches scrolling.
- Snapshot the last applied visible hotspot; reject hidden/old-epoch targets. Publish
  pointer updates as immutable epoch-tagged snapshots. Queue-time checks retain the
  original epoch and reject commands invalidated by explicit tracking cancellation.
- One outstanding native command; busy requests fail without backlog/retry. Duplicate
  and late callbacks cannot duplicate completion. Permission/service exceptions fail.
- Pause, Stop, tracking loss, pointer disable, rotation and interruption request native
  cancellation. Check authority every 15 ms; request cancellation if completion is
  missing after the bounded duration plus 250 ms. Rejected cancellation does not clear
  the original command: remain blocked until its callback or explicit service recovery.
  Cancellation callbacks are not removed by lifecycle handler cleanup.

Cancellation continues an undispatched stroke, using Android's invalid-continuation
path to cancel pending input without a new DOWN/UP; see
[AOSP MotionEventInjector](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/services/accessibility/java/com/android/server/accessibility/MotionEventInjector.java).
This is the prior controller's platform technique. Local state clearing is not proof
of cancellation. Actual ACTION_CANCEL behavior on Galaxy A54 remains a device gate.

Affected: pinch recognition/arbitration, final pointer anchor, accessibility dispatch,
queue invalidation and service lifecycle; small picker/help integration. CameraX,
MediaPipe model/delegate, coordinate transforms, filters, calibration storage/profiles,
dependencies and protected-screen policy are unchanged. No gyro path is introduced.
Catalogue expansion, new gestures, performance changes and UI redesign remain separate.

### Verification, risks and continuation

- Before/after: 12 focused pinch/anchor tests against parent production sources had
  **6 failures** (contact timing, hold/drag conversion and moving anchor). All 12 pass
  with the correction. **73 host JVM tests passed**: 31 pinch/pointer/policy plus 42
  classifier/tracking/identity/tuning tests, using Kotlin 2.2.0 and JUnit 4.13.2.
- Added Android integration tests for same-frame competing recognizers, between-frame
  pause/resume, bounded double tap, visible/runtime target mismatch, hidden target,
  queued cancellation, permission exception, duplicate callbacks, cancellation rejection
  and timeout. Existing engine tests now require injection before release/no drag.
  Historical held-touch regressions remain against the test-only controller.
- Local `./gradlew :app:testDebugUnitTest --no-daemon` cannot bootstrap: distribution
  download fails `Network is unreachable`. Android tests/lint/build are **pending CI**.
  Host checks are not Android-build or release authority.
- Risks: slow-cadence single-contact false positives, newly eligible saved distinct
  touch mappings, overlay timing and device-specific native cancellation. Recording
  lacks intention labels: it cannot identify every intended miss or prove accuracy.

Submit this approved follow-up and inspect CI; repair failures
attributable to it. No merge/release/baseline promotion. Roll back only this unit to
`264fca3`, preserving classifier repairs and saved data; that restores the incompatible
held-touch behavior and is a diagnostic rollback, not directive-compliant release.

Galaxy A54 acceptance after green CI:

1. On a harmless target screen, make 100 ordinary thumb-middle contacts with separation.
   Expect one click before release at the visible hotspot. Count misses/extras; 95/100
   is the early repair gate, not release certification.
2. Hold contact two seconds and move: no repeat, long press or drag. Release does nothing.
   Hide/reintroduce pinched hand and Pause/Resume: no replay until separation/new contact.
3. Assign distinct poses to double tap, long press and scroll: one finite output each.
   Pointing Up must not click while aiming; ordinary travel must not scroll.
4. During long press, use physical Pause/Stop and interrupt accessibility. Verify native
   cancellation, no delayed release/click and no replay after recovery. Failure blocks
   dependent release claims; preserve reachable physical Stop throughout testing.
5. Check mappings/calibration and edge targets; record exact APK/source/run. Next ordered
   unit is pointer geometry/edge/calibration evidence, then catalogue/App search once
   the interaction prerequisite is supported. Device pending does not block independent
   useful implementation, but must not be reported as acceptance.

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
| Current work | [Draft PR #63](https://github.com/coderPSR-940402/Aergis_main/pull/63), implementation commit `d24d0300dccd229a424b4aefe061cdfa59977296`; owner authorised commit, push and PR on 2026-10-17 |

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
  coverage for all 12 mappings and retained rollback settings. The Android unit-test
  step passed in CI #273 below.
- Local `./gradlew :app:testDebugUnitTest --tests '*GestureTransactionStateMachineTest'`
  could not start: distribution download from services.gradle.org failed with
  `Network is unreachable`. This local limitation remains; Android CI subsequently
  passed as recorded below.
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

Owner authorised committing this unit, publishing its feature branch and opening a
PR on 2026-10-17. [Draft PR #63](https://github.com/coderPSR-940402/Aergis_main/pull/63)
contains implementation commit
[`d24d0300dccd229a424b4aefe061cdfa59977296`](https://github.com/coderPSR-940402/Aergis_main/commit/d24d0300dccd229a424b4aefe061cdfa59977296).
The GitHub connector published the exact approved tree
`849fe9a68b5ea89507893d2950f1a3d8305ddac2`; shell push lacked credentials.

Post-submission evidence for that implementation (2026-10-17):

- [Aergis CI #273](https://github.com/coderPSR-940402/Aergis_main/actions/runs/39020165829)
  passed Android unit tests, lint, debug and minified release builds, package/version/
  model checks, persistent preview signing verification, and checksums.
- [CodeQL #262](https://github.com/coderPSR-940402/Aergis_main/actions/runs/39020165859)
  passed. No attributable CI failures required repair.
- Tested PR merge ref: `ba9637fb83fb459b3be911f39438c1d8c1f54b96` (artifact names use
  the PR test-merge SHA, distinct from its source head d24d030).
- [APK artifact](https://github.com/coderPSR-940402/Aergis_main/actions/runs/39020165829/artifacts/12096138993):
  `Aergis-apks-ba9637fb83fb459b3be911f39438c1d8c1f54b96`.
  Debug APK SHA-256: `f19348725d492343be5cb2341904de03ace75075d61aadc48abe4f98ff054871`.
  [Test/lint reports](https://github.com/coderPSR-940402/Aergis_main/actions/runs/39020165829/artifacts/12096084120).
- The automatic baseline-update job was skipped for the PR, as intended. Neither
  the CI build reference nor the device-accepted rollback reference was advanced.
- This evidence-record update changes documentation only. Its separate final-head
  CI result/artifact links are maintained in [PR #63 checks](https://github.com/coderPSR-940402/Aergis_main/pull/63/checks)
  and the PR description, avoiding self-referential evidence-only commit loops.

Implemented and CI-passed; not device-verified or release-ready. No merge/release
permission is assumed. The exact next external gate is the A54 procedure above.

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

### A54 recording received 2026-10-17 — observed, acceptance still pending

Reviewed the newly supplied `recording(7).zip` and `report(9).pdf`; the PDF is
byte-identical to the report inside this ZIP. No historical recording was reused.
Input SHA-256 values:

- ZIP: `fe7b678a901667d89f519d040313a055d2c3b1eff839ad186bc4964bccd43b8a`
- PDF: `80d5f5f715067ee0a8f6512b30eb644d333ce99b601eae76ea9a98ab85753e3b`

Recording metadata identifies Samsung SM-A546E, Android 16, portrait, LEFT hand,
PRECISION filter, starting 2026-10-17 11:50:58 +0200. Its embedded source is
`e1246e0e777a2f14f14c42cd124e006a97db3657`, matching the tested PR merge SHA of
[CI #274](https://github.com/coderPSR-940402/Aergis_main/actions/runs/39020609074)
and its [APK artifact](https://github.com/coderPSR-940402/Aergis_main/actions/runs/39020609074/artifacts/12096257524).
Source head is `89235774b124e171f78384165ffeffb201441925`. The installed APK's
binary checksum is not included in the recording; this is embedded-source identity,
not independent checksum verification. PR #63 remains open/draft and CI #274 success
was rechecked. Do not interpret the metadata's `historicalSource` as the installed build.

Read-only checks: parsed all JSONL records and summary, inspected sampled camera
images, rendered PDF page 1, and compared action events with current engine/service
source. Observations:

- 2,356 frames over 226.680 seconds; 1,025 TRACKING, 1,261 NO_HAND, 44 COASTING,
  26 VISION_REJECTED. All 26 vision rejections are labelled stale results.
  410 camera JPEGs, no recorder drops, unreadable records or image failures.
- Mean inference 92.4 ms, maximum 363 ms, maximum analyzed-frame gap 372 ms.
  These recorded timings are not end-to-end display latency or a no-recorder benchmark.
- 43.5% tracked is a whole-session frame proportion, not hand-detection accuracy.
  Several sampled NO_HAND periods visibly have no hand in frame. Empty screen-coverage
  cells do not establish a reach defect. All segments are UNLABELLED; no stationary
  jitter comparison or click success percentage can be certified.
- Nine named requests: Notifications, Home, Media play/pause, Quick settings,
  Screenshot and Recents once each, plus three TAPs. Fourteen COMPLETED outcomes
  and one SUBMITTED outcome; no recorded policy denial, platform rejection or
  cancellation. Outcome counts must not be treated as successful intended clicks:
  held native touches also emit outcomes without named request events.
- The existing pinch path is exercised: 14 PRESSED, 2 HOLDING and 16 DRAGGING frame
  states. At elapsed 213.832 s and 219.264 s, the final pinch sequence enters
  DRAGGING. This supports the already identified need for the separate bounded-tap
  migration; it does not establish whether those movements were intentional.
- Every recorded frame is ARMED. The recording does not demonstrate Pause/Resume,
  Stop/Start or service-restart acceptance. Sparse camera samples and unlabelled
  intended commands cannot certify absence of false activations or command replay.

No runtime change or functional-baseline promotion from this evidence review. The
next implementation unit remains recognition-triggered thumb-middle tapping and
recognizer arbitration, with explicit migration away from held input. Owner feedback
needed for this run: which actions were intended, missed or extra, especially the
TAPs at 99.093 s, 171.442 s and 172.288 s. This feedback refines regression cases;
it does not block implementation of the already specified interaction contract.

### Recorded classifier miss correction — prepared 2026-10-18

Owner confirmed missed clicks/gestures in the new recording and instructed continued
investigation. The current uncommitted unit is based on PR #63 head `8923577`, preserving
all newer work. The remote CI build marker was rechecked at `d86a094`; no baseline
or rollback reference was moved.

**Confirmed defect:** the existing engine conflates a momentary classifier geometry
rejection with tracking loss. Production-code replay of the recording, including
`LandmarkGestureRecognizer`, shows valid neutral readiness at elapsed 69.000 s.
At 70.729 s, one fresh, same-owner frame rejects `EXCESSIVE_INDEX_BONE`, resets the
gate, and suppresses the following 53-frame Victory run. It is not missing neutral
before that transition. Recorded camera timestamps for the reduced regression are
23676555, 23676655, 23676788 and 23676888 ms. The last valid neutral to valid Victory
gap is 233 ms. Replay uses a declared BACK assignment and default confidence because
the device's saved assignments/tuning are absent; it establishes the gate defect,
not the intended action or a total device miss count.

**Correction and acceptance:** separate continuity from valid command evidence only
for `EXCESSIVE_INDEX_BONE` (the evaluator already requires finite, in-bounds landmarks
and a nonzero palm before returning that reason). Such a frame may preserve previously
established readiness, but cannot dispatch, consume a command or contribute to neutral
release. It breaks partial neutral evidence. Recovery must occur within the existing
250 ms evidence-gap limit, measured from the last valid geometry, not refreshed by
each rejected frame. Longer interruption, missing/out-of-frame/non-finite evidence,
staleness, ownership or action-epoch change, unsafe control and competing pinch still
invalidate readiness. Consumed commands remain consumed until genuine valid neutral.

**Scope and regression risks:** three production files change classifier eligibility,
transaction state and their engine integration. No CameraX, MediaPipe configuration,
pointer tracking/filtering, calibration, native accessibility dispatch/cancellation,
service lifecycle, UI, dependencies, gesture identities or saved assignments change.
No persistence migration is needed. Risk: an incorrectly classified interruption could
retain readiness; the narrow rejection reason and bounded total gap limit that risk.
This unit does not repair the separate thumb-middle held-touch/release-click path.

**Verification:**

- Before correction, four of eight new focused tests fail, including the recorded
  transition. Added input/API scaffolding alone did not change behavior.
- After correction, 42 host Kotlin/JUnit tests pass across geometry interruption,
  existing occurrence/replay gates, tracking eligibility, tuning and all 12 identities.
  Coverage includes no action on rejected evidence, no rearming, broken partial neutral,
  startup/consumed states, ownership/epoch/loss, long interruptions and 250/251 ms bounds.
- Replayed recorded categories/landmarks through the production recognizer and gate:
  at 70.729 s readiness now survives without dispatch; at 70.833 s Victory is accepted
  once. The separate 81-second sequence remains blocked by freshness boundaries.
  This is offline evidence, not a new physical-device test or installed-APK result.
- Added two Robolectric engine-to-native-dispatch tests for the 133+100 ms transition,
  one bounded command, no repeat, genuine neutral recovery and prolonged rejection.
  These require CI; they have not been run locally.
- Full `./gradlew :app:testDebugUnitTest --no-daemon` was attempted; Gradle distribution
  retrieval failed with `Network is unreachable` before Android tests could start.
  No Android build or CI success is claimed for this uncommitted correction.

**Delivery and rollback:** owner approved commit/push and CI on 2026-10-18.
Publication inspection found PR #63 already merged externally at `77a1b6a0a097ce585596ae9bf4f87f0419f00aeb`.
Main and `baseline-successful-apk` now point there; [main CI #275](https://github.com/coderPSR-940402/Aergis_main/actions/runs/39076427486) passed.
Its tree is identical to the reviewed base `8923577`, so the approved correction is
published on `fix/classifier-geometry-continuity` as a follow-up PR without discarding
newer work. This advances the CI reference only, not device acceptance.
Final correction CI and artifact links will be recorded in the follow-up PR description
after submission, avoiding evidence-only CI loops. Inspect CI before A54 testing. Revert
only the correction commit if needed; preserve PR #63's exactly-once protections and
all saved settings. No merge, release or functional-baseline promotion is authorised.

**A54 acceptance after green CI:** keep existing settings; map Victory to Back on a
harmless screen. Start with valid unclassified neutral, transition to Victory at normal
speed, and expect one Back without holding. Keep the pose visible: no repeat. Repeat
after neutral, then check occlusion and Pause/Resume: no replay until fresh neutral.
Record misses/extras and the exact APK source. Broader click accuracy remains pending.
Next separate implementation unit remains recognition-triggered thumb-middle bounded
tapping and recognizer arbitration, with explicit migration away from held touch/drag.

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


## 2026-10-21 — PR #68 first-occurrence CI continuation

Resumed source `b1f441835d02c86a5babc63db320834949fffd00` on existing
`fix/device-gesture-pinch-regressions`; current main and CI build reference both
resolve to `dcee550410730286ee0b543322c999ac13dd97b7`. No functional-baseline
promotion: device acceptance remains separate.

CI #294 (`39531411832`) ran 416 tests with 12 failures; CodeQL #283 passed.
The prior handoff's anticipated run numbers were not completed verification.
Eleven failing assertions retained the superseded startup/unconsumed-reset
neutral requirement. Migrate those to the owner-approved contract: the first
fresh valid occurrence is immediately eligible; invalid evidence cannot dispatch;
consumed occurrences still require genuine confident neutral, including after
reset, stale gaps, owner/epoch changes and tracking loss. A newly constructed
engine starts a new first-occurrence session. This explicitly changes the old
startup/recreation expectations; it is not evidence of physical-device accuracy.

The remaining failure exposed actual pinch arbitration replay: `tracking=false`
now preserves unconsumed readiness, so a competing static pose could dispatch
when contact opens. Pass explicit pinch suppression to the transaction gate and
consume that occurrence without output. Release alone cannot execute the
suppressed static command; confident neutral then a new occurrence may execute.
The existing engine-to-native pinch arbitration regression is retained unchanged.

Verification before publication: the new pure-JVM pinch regression failed with
unexpected BACK before the correction, then all 35 focused JVM tests passed;
11 Python tooling tests passed; diff whitespace checks passed. Local Android
Gradle bootstrap failed with `Network is unreachable` fetching its distribution;
full Android tests, lint, debug/release packaging and verification remain CI gates.
Final source/run/artifact evidence belongs in PR #68 after CI, avoiding another
code-free CI cycle. No merge/release or baseline update is authorized.

Scope: explicit static-command suppression during pointer pinch plus test-contract
migration. Existing first-occurrence behavior, focus-action retirement and pointer
release-anchor repair remain on this same PR. No CameraX/MediaPipe, pointer
filtering/calibration, native dispatch or service lifecycle implementation changes.
Device gate: first mapped pose with pointer disabled acts once immediately;
maintained pose cannot repeat; neutral then new pose acts once; thumb-middle
contact/release has no large pointer jump or delayed classifier action; fast
separate contacts retain responsiveness. Install the final CI debug APK without
clearing data and record exact build identity and misses/extras.


## 2026-10-25 — Pointer-disabled recording review

Reviewed `Aergis-20261025-114910-334-74a41d1b.zip` locally; camera images and
landmarks are not committed. Metadata reports source
`404818ee4c1cfee09af9dfa67b6f3a3ac0550c5c`, which is not an object in the
current repository clone. Its relationship to current main is unverified;
do not promote it to a functional baseline or assume that it tests current code.
Current main and remotely resolved `baseline-successful-apk` are both
`dd785124ab2b499c9fa8869bd75a494b77496747`.

The recording spans 498.420 seconds with 6,134 readable frames. Pointer is
disabled and gestures enabled throughout. There are 47 action requests across
14 action types, 31 COMPLETED outcome events and 16 SUBMITTED outcome events.
No failure outcomes are recorded. SUBMITTED does not prove completion, and
aggregate outcomes are not paired to requests. This demonstrates a functioning
pointer-independent command path, not a measured recognition success rate.

Command eligibility is true on 3,413 frames. Pose reasons include 2,144 NO_POSE,
252 OUT_OF_RANGE_LANDMARK and 252 EXCESSIVE_INDEX_BONE frames. These are
observations, not proof of 504 missed intended gestures. There are no labelled
attempts. Pointer rejection DISABLED is expected in this test and must not be
reported as gesture failure or lost pointer tracking.

Instrumented frame-to-publish age: median 194 ms, nearest-rank p95 291 ms.
Age at analysis start: median 114 ms, p95 205 ms. Inference: median 64 ms,
p95 98 ms. These timings are not display or Android-response latency; disabled
pointer frames still contain the historical `frameToPointerMs` timing field.
The upstream age requires a current-build reproduction before camera changes.

The replay analyzer now emits action/outcome counts, pose reasons, command
eligibility and robust timing summaries without requiring pointer comparisons.
Existing pointer comparison behavior is retained. Two added regression tests
first failed on absent summaries; all 13 Python tooling tests then passed.
The supplied ZIP was processed successfully. No Android runtime code changed.
Current pinch/partial-hand fixes remain intact; physical validation is outstanding.
