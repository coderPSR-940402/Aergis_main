# Aergis DevLoop Design

## Intent

Turn Aergis device testing into a closed engineering loop. The user should be able to install a build, use Aergis normally on the phone, stop testing, and have the resulting evidence reconstructed into interaction-level outcomes that an AI development agent can use to diagnose defects, map them to source, add regressions, change the GitHub branch, and validate the next build.

This extends the existing `2026-11-02-live-diagnostics-design.md`; it does not replace Testing Tools, current action telemetry, or working runtime behavior.

## Goals

- Every meaningful user interaction gets a stable transaction ID spanning recognition, target selection, accessibility dispatch, and recorded outcome.
- Keep a bounded monotonic session timeline with enough state to distinguish recognition, ownership, pointer, dispatch, selection, latency, and Android-response failures.
- Produce machine-readable session summaries after Aergis stops, while keeping raw evidence local unless explicitly exported.
- Reuse existing `DiagnosticSession`, `TestingTools`, `AirRuntime` telemetry, action/request IDs, input/session epochs, and Live Diagnostics buffer.
- Emit compact `AERGISTEL|` relay records that Android Power Toolbox can collect without app-private file access.
- Add a deterministic replay format so real device failures become regression fixtures.
- Make successful actions suppress false failure events; DevLoop must reason from actual outcome evidence rather than request issuance alone.
- Keep diagnostics observational: recorder, relay, store, or collector failure must not alter control authority or block camera/inference/accessibility paths.

## Non-goals

- No autonomous merge to `main` and no production release without an explicit later authorization.
- No OpenAI API bridge, background cloud upload, or continuous raw screen/camera streaming.
- No second gesture/action identity system; DevLoop uses existing request/session/input epochs.
- No weakening of freshness, ownership, anchored-click, release-to-rearm, protected-screen, or pointer-off gesture behavior.

## Selected architecture

### 1. In-app flight recorder

Extend Live Diagnostics from timestamp-only samples to compact immutable samples that can optionally include frame/input/session epochs, control-hand identity, pointer/frozen-target coordinates, gesture/contact state, action request ID, requested action, dispatch state, completion state, selection state, camera age, analyzed-frame gap, and inference time.

Ordinary samples stay in memory. Promoted events snapshot the existing 30-second pre-event and 10-second post-event windows.

### 2. Interaction transactions

A transaction starts when an existing Aergis action request becomes concrete. The transaction retains the existing request ID and may carry a click target or selection expectation. Outcomes close the transaction as `SUCCESS`, `FAILED`, `TIMED_OUT`, or `CANCELLED`. Exactly one terminal result is allowed per request.

A confirmed click without a successful bounded-tap completion inside the diagnostic completion window becomes one `MISSED_CLICK`; a successful completion suppresses that event.

### 3. Session artifact

A clean session stop writes a compact manifest plus promoted events. An unclean restart is detectable from a previous open marker. Session data is bounded and rotates oldest-first. The manifest includes build/source identity and aggregate counts; raw camera images remain under the existing explicit Testing Tools flow.

### 4. Phone relay and collector

Promoted events emit one small redacted `AERGISTEL|<json>` logcat line. The Termux collector deduplicates those records, bridges crash/ANR evidence, adds bounded system/thermal context when available, and writes atomic JSON under `$HOME/agent-gates/aergis-live/` for Android Power Toolbox.

### 5. AI/GitHub orchestration contract

The AI side consumes the latest session/event JSON through Android Power Toolbox, correlates failures with Aergis source through the GitHub connector, and may create a development branch/PR with regression tests and fixes. CI plus the next exact-APK device session are the acceptance gates. A plugin or skill can orchestrate these existing connectors; no new phone relay is required.

## Outcome classification

DevLoop must be able to separate at least:

- recognition/contact not confirmed;
- controlling-hand ownership changed or was lost;
- pointer/frozen-target authority invalid;
- action rejected before dispatch;
- dispatch attempted but completion failed;
- dispatch completed but expected selection/UI transition absent when such evidence exists;
- camera/inference freshness or cadence degradation;
- clean versus unclean process/session termination.

Unknown evidence remains `UNKNOWN`; it must not be relabeled as failure merely because a later signal is absent.

## Reliability and privacy

- No disk/network/logcat I/O on CameraX capture, inference, or accessibility dispatch threads.
- Bounded memory and disk retention with oldest-first rotation.
- Relay lines exclude arbitrary screen text, contacts, images, and unrelated app content.
- Duplicate signals for one request/failure episode produce at most one promoted event.
- Diagnostics exceptions are swallowed at the observation boundary and never become control decisions.

## Validation

Tests must cover transaction terminal-state idempotency, successful-click suppression, missed-click exactly-once behavior, pre/post timeline boundaries, monotonic ordering, dedup/rate limiting, session close/restart markers, relay redaction/size limits, collector restart dedup, and deterministic replay of recorded failure sequences.

The existing Galaxy A54 device gate remains required for exact-APK validation. CI proves build/test integrity; it does not by itself prove touchless behavior.
