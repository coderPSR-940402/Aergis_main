# Aergis DevLoop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Aergis device testing into a closed diagnostic-development loop by correlating each interaction from recognition through Android outcome, exporting bounded session evidence to Android Power Toolbox, and making real failures replayable as regressions.

**Architecture:** Extend the existing Live Diagnostics ring buffer and Testing Tools telemetry. Add immutable interaction transactions keyed by existing Aergis request/session/input identities, promote bounded events only after outcome correlation, emit compact redacted relay records for the existing Toolbox transport, and add deterministic replay fixtures. The GitHub/AI side consumes those records; no second phone relay is introduced.

**Tech Stack:** Kotlin/Android, JUnit/Robolectric, existing Aergis diagnostic JSON/JSONL, Python + shell tooling, Android logcat, Android Power Toolbox, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-02-aergis-devloop-design.md`

## Global Constraints

- Base: current `main` / `baseline-successful-apk` at `2c72d059e54ece6b96ee82af497fc11bc1b22b61` when this plan was written.
- Preserve `START_NOT_STICKY`, action/input/session epoch semantics, release-to-rearm, anchored click coordinates, protected-screen policy, pointer-off gesture operation, and freshness limits.
- Reuse existing action/request IDs and existing diagnostic schema vocabulary; do not create competing control identities.
- No disk, network, or logcat I/O on CameraX capture, inference, or accessibility dispatch threads.
- Pre-event context is 30 seconds; post-event context is 10 seconds.
- Relay prefix is `AERGISTEL|` and must exclude images, arbitrary screen text, contacts, and unrelated-app data.
- Diagnostics are observational. Any recorder/store/relay/collector failure must leave production control behavior unchanged.
- CI success is not device acceptance; exact-APK Galaxy A54 validation remains a separate gate.

## Review Focus

- Delayed successful tap completion must suppress `MISSED_CLICK` rather than race the timeout.
- Duplicate contact/dispatch signals for one request must produce one terminal transaction and at most one promoted failure.
- Ownership loss/reacquisition and stale-frame rejection must not manufacture action failures from missing evidence.
- Store/relay exceptions and queue saturation must never affect gesture/pointer/action authority.
- Restart recovery must distinguish a genuinely unclean prior session from a normal clean stop.

---

### Task 1: Complete the live telemetry/event model and interaction transaction state machine

**Files:**
- Modify: `app/src/main/java/com/airgesture/control/LiveDiagnosticEvent.kt`
- Create: `app/src/main/java/com/airgesture/control/LiveInteractionTransaction.kt`
- Test: `app/src/test/java/com/airgesture/control/LiveInteractionTransactionTest.kt`

**Interfaces:**
- Produces: `LiveDiagnosticKind`, `LiveDiagnosticSeverity`, `LiveDiagnosticTrigger`, `LiveDiagnosticSnapshot` and enriched `LiveDiagnosticSample` fields with safe defaults.
- Produces: `LiveInteractionTransaction.open(...)`, `markDispatched(...)`, `complete(...)`, `timeout(...)`, `cancel(...)` and immutable terminal `LiveInteractionOutcome`.
- Terminal state is exactly-once; repeated or late terminal signals return the original outcome without changing it.

- [ ] **Step 1: Write failing transaction tests** for success, dispatch failure, timeout, cancellation, duplicate terminal signals, and preservation of frozen click coordinates/request ID.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests '*LiveInteractionTransactionTest'` and verify failures are due to missing production types.
- [ ] **Step 3: Implement** the immutable model/state machine without Android dependencies.
- [ ] **Step 4: Run focused tests** and verify pass; then run `./gradlew test`.
- [ ] **Step 5: Commit** `feat: add DevLoop interaction transactions`.

### Task 2: Event promotion, missed-click correlation, bounded store, and relay

**Files:**
- Create: `app/src/main/java/com/airgesture/control/LiveDiagnostics.kt`
- Create: `app/src/main/java/com/airgesture/control/LiveDiagnosticStore.kt`
- Create: `app/src/main/java/com/airgesture/control/LiveDiagnosticRelay.kt`
- Test: `app/src/test/java/com/airgesture/control/LiveDiagnosticsTest.kt`

**Interfaces:**
- Consumes Task 1 models plus existing `LiveDiagnosticBuffer`.
- Produces `start`, `stop(clean)`, `sample`, `openInteraction`, `recordDispatch`, `recordOutcome`, and `trigger` observation APIs.
- Produces app-private bounded event bundles and compact schema-1 `AERGISTEL|<json>` relay lines.

- [ ] **Step 1: Write failing tests** proving successful taps suppress `MISSED_CLICK`, timed-out confirmed clicks promote exactly once, action rejection promotes one `ACTION_FAILED`, trigger dedup/rate limiting works, pre/post windows are exact, relay is redacted/bounded, and store/relay exceptions never escape callers.
- [ ] **Step 2: Run focused tests** and verify RED.
- [ ] **Step 3: Implement** single-writer asynchronous store/relay boundaries; ordinary samples remain memory-only.
- [ ] **Step 4: Run focused tests and full unit suite** and verify GREEN.
- [ ] **Step 5: Commit** `feat: promote correlated DevLoop events`.

### Task 3: Wire DevLoop into recognition, accessibility dispatch, and lifecycle

**Files:**
- Modify: `app/src/main/java/com/airgesture/control/GestureCaptureService.kt`
- Modify: `app/src/main/java/com/airgesture/control/GestureRecognitionEngine.kt`
- Modify: `app/src/main/java/com/airgesture/control/AirAccessibilityService.kt`
- Modify: `app/src/main/java/com/airgesture/control/AirRuntime.kt`
- Test: `app/src/test/java/com/airgesture/control/LiveDiagnosticsEngineIntegrationTest.kt`
- Test: `app/src/test/java/com/airgesture/control/LiveActionDiagnosticsTest.kt`

**Interfaces:**
- Consumes Task 2 observation API.
- Uses existing request/session/input epochs and frozen click target; no new control authority.
- Produces correlated samples/events for `MISSED_CLICK`, `ACTION_FAILED`, `SELECTION_FAILED`, `HAND_LOST`, `TRACKING_JUMP`, `CAMERA_DELAY`, `INFERENCE_STALL`, and `PROCESS_RESTART` where existing evidence is authoritative.

- [ ] **Step 1: Write failing integration tests** for ordinary success, failed dispatch, one missed-click timeout, frozen-target preservation, hand-loss/reacquisition pairing, and clean/unclean service restart.
- [ ] **Step 2: Run focused tests** and verify RED.
- [ ] **Step 3: Instrument existing paths** only after current freshness/ownership/provenance gates; diagnostics calls must be non-throwing.
- [ ] **Step 4: Run focused tests plus existing click/frame/navigation regressions and full unit suite**.
- [ ] **Step 5: Commit** `feat: instrument Aergis DevLoop outcomes`.

### Task 4: Phone collector and Toolbox-readable event queue

**Files:**
- Create: `tools/aergis_live_collector.py`
- Create: `tools/test_aergis_live_collector.py`
- Create: `tools/aergis_live_collector.sh`
- Create: `docs/aergis-devloop.md`

**Interfaces:**
- Consumes `AERGISTEL|` logcat lines and existing crash-gate JSON.
- Produces `$HOME/agent-gates/aergis-live/latest.json`, bounded `events/*.json`, and persistent dedup state using atomic writes.
- May append bounded thermal/process context when available; enrichment failure never drops the base event.

- [ ] **Step 1: Write failing Python fixture tests** for valid parsing, malformed/truncated rejection, duplicate suppression, bounded retention, restart-persistent dedup, crash bridging, and idle behavior.
- [ ] **Step 2: Run** `python3 -m unittest tools/test_aergis_live_collector.py` and verify RED.
- [ ] **Step 3: Implement** parser/store plus a thin install/start/stop/status shell wrapper with no busy polling.
- [ ] **Step 4: Run Python tests and** `bash -n tools/aergis_live_collector.sh`; verify GREEN.
- [ ] **Step 5: Commit** `feat: add DevLoop phone event collector`.

### Task 5: Deterministic session replay and CI contract

**Files:**
- Create: `app/src/main/java/com/airgesture/control/DevLoopReplay.kt`
- Test: `app/src/test/java/com/airgesture/control/DevLoopReplayTest.kt`
- Modify/add existing recorded-input replay tests where appropriate.
- Modify: `.github/workflows/ci.yml` only if the new tool tests are not already covered.

**Interfaces:**
- Consumes compact transaction/timeline fixtures, not raw camera video.
- Produces deterministic replay results containing terminal interaction outcomes and promoted event kinds.

- [ ] **Step 1: Write failing replay tests** for successful click, brief contact fluctuation followed by success, missed-contact timeout, second-hand ownership interruption, and anchored target preservation.
- [ ] **Step 2: Run focused tests** and verify RED.
- [ ] **Step 3: Implement deterministic replay adapter** over Task 1/2 state machines.
- [ ] **Step 4: Run full unit tests, Python tooling tests, lint, debug assembly, and release assembly through CI**.
- [ ] **Step 5: Commit** `test: make DevLoop failures replayable`.

### Task 6: PR, Toolbox deployment, and exact-device validation handoff

**Files:**
- Update: `docs/aergis-devloop.md`
- Update: `docs/EVIDENCE_LEDGER.md` with CI-only evidence after CI completes.

**Interfaces:**
- Produces a reviewable PR and exact artifact identity.
- Deployment uses the existing Android Power Toolbox relay; no new transport is installed.

- [ ] **Step 1: Open a draft PR** from `feat/aergis-devloop` to `main` after code/tests are present.
- [ ] **Step 2: Inspect GitHub Actions**; repair only failures attributable to this branch and re-run until green.
- [ ] **Step 3: Deploy the collector** to the paired phone through Android Power Toolbox once the app-side relay exists.
- [ ] **Step 4: Verify one real session creates a Toolbox-readable event/session artifact**; do not claim behavioral acceptance yet.
- [ ] **Step 5: Record exact APK/run identity and leave final physical acceptance to the next normal Aergis usage session.**

## Execution ruling

Native execution is selected for this session because no independent subagent tool is available. The branch remains isolated from `main`; no merge or production release is authorized by this plan.
