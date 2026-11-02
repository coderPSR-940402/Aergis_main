# Live Event-Driven Diagnostics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add always-on, event-driven diagnostics that capture bounded pre/post context while Aergis is active and relay compact structured events to Android Power Toolbox without blocking the control pipeline.

**Architecture:** Reuse the existing `TestingTools`/`DiagnosticSession` telemetry schema, PR #75 action/input epochs, and action-outcome evidence. Add a bounded in-memory live timeline and event promoter inside Aergis, retain schema-compatible local event bundles, emit compact `AERGISTEL|<json>` logcat records, and add a Termux collector that deduplicates/enriches those records into Toolbox-readable JSON under `$HOME/agent-gates/aergis-live/`.

**Tech Stack:** Kotlin/Android, CameraX, MediaPipe, Robolectric/JUnit, shell + Python tool tests, Android logcat, Termux/Shizuku/rish.

**Spec:** `docs/superpowers/specs/2026-11-02-live-diagnostics-design.md`

## Global Constraints

- Base implementation on PR #75 head `2b3489a59e7b19cb4dc644983538214356612279` until that work merges.
- Preserve `START_NOT_STICKY`, action/input/selection epoch semantics, release-to-rearm, anchored click coordinates, pointer-off gesture operation, and current freshness limits.
- No disk/network/relay I/O on CameraX capture, inference, or accessibility dispatch threads.
- Pre-event window: 30 seconds. Post-event window: 10 seconds.
- `AERGISTEL|` relay must exclude images, arbitrary screen text, contacts, and unrelated-app content.
- Existing Testing Tools recording/export remains explicit and unchanged for users.
- ChatGPT pull/condition-watch remains hourly at fastest; do not claim direct push into the current chat.

## Review Focus

- Rapid contact fluctuations must not create duplicate `MISSED_CLICK` events or mislabel a delayed successful tap.
- Hand loss near camera/frame edges must pair with reacquisition without flooding repeated events.
- Camera/inference latency spikes that are already rejected by freshness policy must not recursively spam diagnostics.
- Diagnostic writer/relay failure must never change pointer, gesture, selection, or action authority.
- Boot/watchdog restarts must produce one collector and preserve dedup state across process restarts.

---

### Task 1: Live telemetry model and bounded timeline

**Files:**
- Create: `app/src/main/java/com/airgesture/control/LiveDiagnosticEvent.kt`
- Create: `app/src/main/java/com/airgesture/control/LiveDiagnosticBuffer.kt`
- Test: `app/src/test/java/com/airgesture/control/LiveDiagnosticBufferTest.kt`

**Interfaces:**
- Produces: `LiveDiagnosticSample`, `LiveDiagnosticKind`, `LiveDiagnosticSeverity`, `LiveDiagnosticTrigger`, `LiveDiagnosticSnapshot`.
- Produces: `LiveDiagnosticBuffer.add(sample)`, `snapshotBefore(eventUptimeMs, 30_000L)`, `snapshotAfter(eventUptimeMs, 10_000L)`, bounded by sample count and time.

- [ ] Write failing tests for 30-second pre-window, 10-second post-window, oldest-first eviction, monotonic ordering, and empty-buffer behavior.
- [ ] Run `./gradlew testDebugUnitTest --tests '*LiveDiagnosticBufferTest'` and verify failure.
- [ ] Implement the immutable event/sample types and bounded ring buffer.
- [ ] Run the focused tests and verify pass.
- [ ] Commit `feat: add bounded live diagnostic timeline`.

### Task 2: Event promotion, deduplication, local evidence, and compact relay

**Files:**
- Create: `app/src/main/java/com/airgesture/control/LiveDiagnostics.kt`
- Create: `app/src/main/java/com/airgesture/control/LiveDiagnosticStore.kt`
- Create: `app/src/main/java/com/airgesture/control/LiveDiagnosticRelay.kt`
- Test: `app/src/test/java/com/airgesture/control/LiveDiagnosticsTest.kt`

**Interfaces:**
- Consumes: Task 1 timeline types.
- Produces: `LiveDiagnostics.start(context)`, `stop(clean: Boolean)`, `sample(sample)`, `trigger(trigger)`, `recordActionRequest(...)`, `recordActionOutcome(...)`, `recordHandState(...)`.
- Produces app-private evidence at `filesDir/live-diagnostics/events/<eventId>/event.json` plus `timeline.jsonl`, using field names compatible with current diagnostic event/frame telemetry.
- Produces relay lines with prefix `AERGISTEL|` and compact JSON schema version 1.

- [ ] Write failing tests for trigger deduplication, rate limiting, 30s/10s capture lifecycle, same-request idempotency, local bundle rotation, relay length bound, and redaction of arbitrary text/image fields.
- [ ] Run focused tests and verify failure.
- [ ] Implement promotion state machine and asynchronous store/relay executor; ordinary samples remain memory-only.
- [ ] Add failure-path tests proving store/relay exceptions do not throw into callers or recursively emit diagnostic events.
- [ ] Run focused tests and verify pass.
- [ ] Commit `feat: promote live diagnostic events`.

### Task 3: Instrument recognition, timing, hand ownership, and service lifecycle

**Files:**
- Modify: `app/src/main/java/com/airgesture/control/GestureCaptureService.kt`
- Modify: `app/src/main/java/com/airgesture/control/GestureRecognitionEngine.kt`
- Modify: `app/src/main/java/com/airgesture/control/DiagnosticFrameData.kt`
- Modify: `app/src/main/java/com/airgesture/control/AirRuntime.kt`
- Test: `app/src/test/java/com/airgesture/control/LiveDiagnosticsEngineIntegrationTest.kt`
- Test: `app/src/test/java/com/airgesture/control/AirRuntimeTest.kt`

**Interfaces:**
- Consumes: `LiveDiagnostics.sample/trigger/recordHandState` from Task 2.
- Produces diagnostic events: `HAND_LOST`, `TRACKING_JUMP`, `CAMERA_DELAY`, `INFERENCE_STALL`, `PROCESS_RESTART`.

- [ ] Write failing integration tests for ordinary frames producing samples but no events, sustained hand loss + one reacquisition pair, one tracking-jump event, bounded latency hysteresis, and unclean service restart detection.
- [ ] Run focused tests and verify failure.
- [ ] Start/stop live diagnostics with `GestureCaptureService`; never change `START_NOT_STICKY`.
- [ ] Feed accepted frame/inference/pipeline-age/owner/pointer data from `GestureRecognitionEngine` into compact samples after current freshness/provenance checks.
- [ ] Centralize diagnostic thresholds in `LiveDiagnostics`; do not create independent control thresholds.
- [ ] Run focused tests and existing frame-provenance tests from PR #75.
- [ ] Commit `feat: instrument live vision diagnostics`.

### Task 4: Instrument click, action, and selection outcomes

**Files:**
- Modify: `app/src/main/java/com/airgesture/control/AirAccessibilityService.kt`
- Modify: `app/src/main/java/com/airgesture/control/AirRuntime.kt`
- Modify: `app/src/main/java/com/airgesture/control/BoundedGestureDispatcher.kt`
- Test: `app/src/test/java/com/airgesture/control/AirAccessibilityServiceDispatchTest.kt`
- Test: `app/src/test/java/com/airgesture/control/LiveActionDiagnosticsTest.kt`

**Interfaces:**
- Consumes: Task 2 action request/outcome API.
- Produces diagnostic events: `MISSED_CLICK`, `ACTION_FAILED`, `SELECTION_FAILED`.

- [ ] Write failing tests proving successful bounded taps never emit `MISSED_CLICK`, failed/rejected dispatch emits one `ACTION_FAILED`, click confirmation with no successful completion in the allowed window emits exactly one `MISSED_CLICK`, and selection change/activation success suppresses `SELECTION_FAILED`.
- [ ] Run focused tests and verify failure.
- [ ] Correlate existing request IDs/action telemetry with live diagnostics instead of inventing a second action identity system.
- [ ] Record frozen pointer target for click requests while preserving anchored coordinates from PR #75.
- [ ] Feed existing `selection_changed` evidence and activation outcomes into the same request correlation.
- [ ] Run focused tests plus existing dispatch/navigation regression tests.
- [ ] Commit `feat: diagnose live action outcomes`.

### Task 5: Phone-side event collector, system enrichment, and Toolbox-readable queue

**Files:**
- Create: `tools/aergis_live_collector.sh`
- Create: `tools/test_aergis_live_collector.py`
- Create: `docs/aergis-live-diagnostics.md`

**Interfaces:**
- Consumes: logcat records prefixed `AERGISTEL|` plus the existing Aergis crash/ANR gate output.
- Produces: `$HOME/agent-gates/aergis-live/latest.json`, `$HOME/agent-gates/aergis-live/events/<timestamp>-<id>.json`, persistent dedup state, bounded event retention.
- Produces phone-side events `THERMAL_THROTTLE` and `CRASH_OR_ANR` when system/crash evidence crosses their documented thresholds while Aergis is active.
- Enrichment: when available through rish, append bounded Aergis process memory/CPU and Samsung/Android thermal state without failing event capture if enrichment is unavailable.

- [ ] Write Python fixture tests for valid relay parsing, malformed/truncated JSON rejection, duplicate event suppression, bounded retention, restart-persistent dedup, thermal-state promotion, crash/ANR bridging, and no-event idle behavior.
- [ ] Run `python -m unittest tools/test_aergis_live_collector.py` and verify failure.
- [ ] Implement collector with one streaming logcat/rish reader, atomic JSON writes, bounded enrichment calls only on promoted events, and no busy polling.
- [ ] Bridge the existing `$HOME/agent-gates/latest-aergis-crash.json` evidence into the same queue without duplicating an already-seen crash hash.
- [ ] Add install/start/stop/status commands to the script and document Termux:Boot/watchdog integration.
- [ ] Run tool tests and ShellCheck/bash syntax validation.
- [ ] Commit `feat: add toolbox live diagnostic collector`.

### Task 6: Recorded-input regression, CI, and device acceptance

**Files:**
- Modify: `app/src/test/java/com/airgesture/control/TestingToolsRegressionTest.kt`
- Modify/add the existing recorded-input replay test that covers the 106.117-second missed contact from PR #75.
- Modify: `.github/workflows/*` only if the new Python/shell tests are not already covered.
- Update: `docs/aergis-live-diagnostics.md`

**Interfaces:**
- Consumes all previous tasks.
- Produces verified event semantics and measurable overhead evidence.

- [ ] Add replay assertions: three known completed clicks emit no failure event; the supported missed-contact sequence emits exactly one appropriate event; anchored click coordinates remain unchanged.
- [ ] Add regression asserting diagnostics disabled/relay failure leaves production gesture results identical.
- [ ] Run selected Android/JUnit suite, all Python tooling tests, lint, debug assembly, and minified release assembly.
- [ ] On Galaxy A54, validate natural click/reclick, pointer-off navigation, second-hand/loss recovery, near-camera/frame-edge behavior, PRECISION anchored taps, bright/low-light cadence, and thermal/load conditions while live diagnostics are enabled.
- [ ] Record measured diagnostic overhead and event examples; do not claim performance improvement without measurement.
- [ ] Commit `test: verify live diagnostic pipeline`.

## Final integration

- Keep this as a stacked branch/PR on `fix/pinch-confirmation-latency` while PR #75 remains open; retarget to `main` after #75 merges.
- Deploy `tools/aergis_live_collector.sh` to the phone through Android Power Toolbox after the app-side relay is present.
- Update the existing `Aergis Crash Watch` automation into a broader live-event fallback only after `$HOME/agent-gates/aergis-live/latest.json` is verified on-device.
- Literal sub-minute AI analysis is a separate opt-in project requiring an approved OpenAI API bridge; it is not silently enabled here.
