# Live Event-Driven Diagnostics Design

## Intent

While Aergis is actively running, continuously retain a lightweight local diagnostic timeline and promote only meaningful failures or anomalies into structured diagnostic events. Preserve enough evidence before and after each event to diagnose what actually happened without forcing the user to manually start a recording or describe whether an action succeeded.

The primary goal is to make device testing self-observing: gesture recognition, pointer tracking, action dispatch, selection, camera/inference timing, hand ownership, process health, and selected Android system state should be correlated into one event record that can be consumed by Android Power Toolbox and then analyzed by AI.

## Goals

- Capture diagnostics only while `com.airgesture.control` is actively running.
- Keep a rolling 30-second pre-event timeline and collect 10 seconds of post-event context for promoted events.
- Detect and promote meaningful events such as missed click, action failure, selection failure, tracking jump, hand loss/reacquisition, camera delay, inference stall, process restart, crash/ANR, and thermal throttling.
- Record actual execution outcomes, not merely requested actions.
- Reuse existing Aergis `TestingTools`, `DiagnosticSession`, `DiagnosticFrameData`, `AirRuntime` telemetry, action outcome telemetry, and PR #75 frame/action provenance rather than creating a competing recorder.
- Never block CameraX, the inference worker, or the accessibility dispatch path on disk or relay I/O.
- Deduplicate and rate-limit noisy events.
- Preserve raw evidence locally and emit a small structured event suitable for downstream AI review.
- Make the phone-side collector boot-persistent and watchdog-protected.

## Non-goals

- Do not continuously upload raw camera frames or full logcat streams.
- Do not claim sub-minute push into the current ChatGPT conversation: the current Android Power Toolbox connector is pull-only and ChatGPT scheduled condition watches can run at most hourly.
- Do not add an OpenAI API bridge or incur API billing without separate explicit user approval.
- Do not weaken existing freshness, action-epoch, release-to-rearm, anchored-click, or safety checks to make diagnostics easier.
- Do not replace the existing explicit Testing Tools recording/export workflow.

## Current foundation

Aergis already has a bounded, writer-thread-confined diagnostic recorder. `DiagnosticSession` writes `frames.jsonl` and `events.jsonl`, derives summaries, preserves interrupted evidence, and keeps disk work off the vision thread. `TestingTools` already records frame metadata, sampled camera images, action events, pointer state, inference time, and recording queue loss. `AirRuntime` already tracks freshness and action dispatch telemetry, and `AirAccessibilityService` already records selection and action outcomes. PR #75 adds camera input provenance via `inputEpoch` and preserves action authority across queued/in-flight work.

The live system should therefore add an always-available bounded in-memory timeline plus event promotion, while using the existing diagnostic schema and event vocabulary wherever possible.

## Selected approach

### Event-driven live diagnostics

A lightweight in-process monitor maintains a ring buffer of compact telemetry samples while Aergis is running. Event producers submit structured signals to the monitor. Ordinary samples remain memory-only. When an event detector promotes a signal, the monitor snapshots the previous 30 seconds, keeps collecting 10 seconds after the event, and asynchronously writes one bounded event bundle.

A small relay record is also emitted to Android logcat using a stable prefix so the Termux/Android Power Toolbox side can consume it without access to Aergis app-private storage. The relay contains IDs, timestamps, event type/severity, compact metrics, and the local bundle identifier; it does not contain camera images.

The Termux collector reads only the prefixed structured records, deduplicates by event ID/hash, adds bounded system context when useful, and writes the latest event plus a capped queue under `$HOME/agent-gates/aergis-live/`. Android Power Toolbox can read these JSON files on demand. The existing hourly automation remains a fallback consumer.

## Event model

Each promoted event must contain:

- schema version
- stable `eventId`
- wall-clock and monotonic timestamps
- event type and severity
- Aergis build/source commit
- session/action/input/selection epochs
- pointer enabled/click enabled/gesture enabled/control mode
- selected/control hand identity where available
- gesture/contact state where applicable
- pointer position and frozen click target where applicable
- action request ID, requested action, dispatch outcome, and completion outcome where applicable
- camera frame age/delivery age and inference time
- effective analyzed-frame cadence over the recent window
- thermal/system context when supplied by the phone collector
- 30-second pre-event compact timeline
- 10-second post-event compact timeline
- links/identifiers for any retained local raw diagnostic evidence

## Initial promoted events

- `MISSED_CLICK`: contact/gesture confirmation occurred but no successful bounded tap completion is recorded in the allowed completion window.
- `ACTION_FAILED`: an action request reaches dispatch but produces a recorded failure/rejection outcome.
- `SELECTION_FAILED`: navigation/selection request does not produce the expected selection transition or activation outcome.
- `TRACKING_JUMP`: accepted pointer movement exceeds the diagnostic jump threshold while source evidence remains otherwise valid.
- `HAND_LOST`: controlling hand disappears long enough to interrupt control; pair with reacquisition duration when recovered.
- `CAMERA_DELAY`: synchronized frame age or callback age exceeds the existing freshness policy warning threshold without being merely an already-rejected stale frame.
- `INFERENCE_STALL`: inference time or analyzed-frame gap exceeds a bounded threshold derived from existing freshness/performance policy.
- `PROCESS_RESTART`: capture service/process starts after an unclean previous live session marker.
- `CRASH_OR_ANR`: existing crash/ANR watcher evidence is bridged into the same event queue.
- `THERMAL_THROTTLE`: phone-side collector observes a meaningful thermal/throttling state while Aergis is active.

Thresholds must be centralized, named, covered by tests, and avoid generating an event for every rejected frame or ordinary hand-loss blip.

## Relay and privacy

The Aergis process emits a compact one-line JSON relay to logcat with prefix `AERGISTEL|`. The relay is deliberately small enough to avoid logcat line truncation and excludes raw images, arbitrary screen text, contacts, and unrelated app content.

The Termux collector may read Android process/system metrics through the existing authorized Shizuku/rish path. It stores only bounded event JSON in Termux home. Raw Aergis diagnostic bundles stay in Aergis app-private storage unless the user explicitly exports them through the existing UI.

## AI ingestion constraint

The current ChatGPT/Android Power Toolbox integration cannot receive a phone-originated push into this conversation. Therefore:

1. phone capture and event generation are near-live;
2. Toolbox-readable event files are created immediately;
3. this chat can inspect them whenever active/on request;
4. the existing condition-watch automation checks hourly as a fallback;
5. a future optional OpenAI API bridge can provide sub-minute automatic model analysis, but is outside this approved scope until the user explicitly approves API use/cost.

## Reliability requirements

- No disk writes on camera callback, inference, or accessibility dispatch threads.
- Ring buffer memory has a hard cap and drops oldest samples first.
- Event queue has a hard cap and rotates oldest completed event bundles.
- Event promotion is idempotent for the same action/request/failure episode.
- A dead/slow writer cannot block control.
- Recorder failure is itself recorded locally without recursively generating infinite diagnostic events.
- Boot/watchdog scripts avoid duplicate collectors.
- If logcat relay access is unavailable, Aergis control continues unaffected and local evidence remains intact.

## Validation

Unit tests must cover event promotion/deduplication, pre/post window boundaries, ring-buffer eviction, action success versus failure, missed-click exactly-once logic, hand-loss/reacquisition pairing, latency threshold hysteresis, and relay size/redaction. Existing replay fixtures must prove that known successful clicks are not mislabeled while the recorded missed-contact sequence is promoted once.

Device acceptance on the Galaxy A54 must verify natural click/release/reclick, pointer-off gesture navigation, second-hand appearance, hand loss/recovery, edge/near-camera behavior, PRECISION anchored taps, bright/low-light cadence, and thermal/load behavior while the live monitor is enabled. Diagnostic overhead must be measured rather than assumed negligible.
