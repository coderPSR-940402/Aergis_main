# Aergis DevLoop

Continue development in `feat/aergis-devloop`, draft PR #81. Never merge or advance the functional baseline from instrumentation or CI alone.

## Runtime and evidence

Capture-service creation starts the production diagnostic worker. The worker observes the existing accessibility `feedbackId` and the same epoch-checked `visibleHotspot` used by dispatch. Central `AirRuntime.recordActionFeedback` supplies outcomes. Diagnostics own no gesture, pointer, selection, or accessibility authority.

Accepted frame observations carry the actual controlling-owner ID, pointer/contact state, source/input epochs, camera age and inference duration. Observation admission is non-blocking and bounded; all persistence and logcat work runs on one diagnostic worker. Queue overflow marks evidence incomplete and stops interpreting absent callbacks as failures. A recorder or transport error cannot change input decisions.

A completed tap is recorded as a completed tap. It suppresses a missed-click event. An explicit rejection records its original request ID and reason. A concrete TAP request missing completion at the diagnostic deadline produces one MISSED_CLICK. This is a dispatch observation, not a count of every physical pinch attempted before recognition. Submission-only Android APIs retain submission evidence; absence of a callback they do not offer cannot prove failure. An intentionally held pointer drag has no diagnostic completion deadline.

App-private files are under `files/devloop/`: `session.json`, `session.open`, `latest.json`, bounded `events/`, `sessions/`, and `trace.json`. Clean capture stop drains accepted observations, cancels unfinished diagnostic transactions, flushes partial post-context and writes the final manifest. A remaining open marker on the next start records an unclean previous session and emits PROCESS_RESTART.

Leaving the Aergis activity checkpoints the running session. It does not stop or mark the capture service clean: operating other apps is Aergis' normal background mode. Swiping the task away also checkpoints. Stop capture normally to finalize; Android process death is detected on restart.

## Existing Toolbox transport

Promoted events transmit their bounded frame context immediately when finalized, with window/completeness fields. Restart recovery also transmits the prior unclean manifest under its original identity. The worker emits `AERGISTEL|` schema-1 lines tagged `AergisDevLoop`. Records are allowlisted session metadata, action traces, bounded sampled frame context, and promoted events. No camera images, screen text, contacts or unrelated app data are transported. No network endpoint, API key or second phone relay is added.

`python3 install_devloop_toolbox.py` (or `--server /absolute/path/to/existing/server`) adds `aergis.devloop` to the existing Toolbox catalog and builtin dispatcher. It backs up source before mutation, validates Python syntax, preserves existing actions and pairing, installs a runit collector, and adds a separate Termux:Boot launcher. The installer detects the active runtime and asks the existing supervisor to reload its dashboard/agent; it reports when a manual restart is still needed. The collector needs the already-authorized Shizuku/rish connection to read logcat. It uses a blocking log stream, with bounded startup replay and persistent deduplication; it never clears logcat.

For source-only verification, use `--no-service`. This was also exercised against the actual previously shipped MAX 0.4.3 server source, not just the minimal installer fixture. That check establishes compatibility with that source, not installation on the phone's newer runtime.

The resulting read-only Toolbox action accepts `view`:

| View | Output |
| --- | --- |
| `latest` | Plain-language summary, metrics, build/session identity, latest event and comparison |
| `session` / `manifest` | Current or final session manifest |
| `event` | Latest promoted event |
| `history` | Bounded promoted event history |
| `status` | Recording, clean termination, or confirmed unclean state |
| `replay` | Bounded redacted action/frame trace |

The same views are available with `python3 aergis_live_collector.py VIEW`. Files are written atomically to `$HOME/agent-gates/aergis-live/`. Toolbox's existing `data.json` can also read `latest.json`, `session.json`, and `latest-event.json` without the new catalog action.

## Replay and comparison

`python3 aergis_live_collector.py regression` creates a JSON regression fixture from recorded outcomes. Request IDs and frozen targets remain intact. Incomplete/truncated traces are rejected clearly rather than generating orphaned expectations. Terminal outcomes are counted once; recorded diagnostic timeouts become timer inputs. Fixtures state provenance, so synthetic contracts and simulated Android evidence are never described as captured physical-device failures.

CI runs the normal Python and Android unit suites. An actual accessibility-service regression then exports the production worker's log records. CI reconstructs them through the real Python collector, produces a replay fixture, and replays it through the production Kotlin correlator before lint, debug/release assembly and APK verification. The source fixture under `app/src/test/resources/devloop/` is explicitly synthetic. Frame-condition warnings remain labelled recorded observations; the interaction replay does not claim to regenerate unseen hand geometry. Physical A54 failure fixtures should be added there with their exact source/build/session identity; none have been fabricated.

Finalizing a session from a different build automatically compares it with the preceding clean session. Metrics include click completions/rate and missed clicks, maximum normalized pointer movement during pinch, owner changes, frame-age/inference p95, hand-loss recovery p95, and edge tracking rate. Missing measurements remain absent rather than becoming zero. Click counts cover concrete recognized requests; timing percentiles cover a bounded recent sample window. They are comparisons of recorded sessions, not causal proof that a code change repaired every user-intended interaction.

## Engineering loop

1. Read latest evidence through the existing Toolbox connection. Tie each conclusion to the exact session, request, outcome and APK identity.
2. Trace the recorded reason to source. `AirAccessibilityService` owns dispatch/targets; `AirRuntime` owns authoritative feedback; `GestureRecognitionEngine` owns frame and ownership evidence. A failure type is a search hint, not proof of a specific source defect.
3. Reproduce each confirmed code defect with a failing behavioral regression. Modify production code only after observing RED; retain the real evidence fixture. Recognition defects need their original landmark/contact evidence from existing Testing Tools; action summaries alone cannot recreate unseen hand geometry.
4. Run normal unit tests, collector/replay contracts, lint, debug/release builds, APK verification and CodeQL in the same PR. Keep CI failures visible. Do not merge automatically.
5. Install the exact next APK and compare the next normal A54 session. Recorded successful completion is positive evidence; unknown pre-recognition intentions remain unknown.

## Remaining deployment gate

The currently connected Toolbox catalog exposes neither arbitrary code execution nor an extension-upload/install task. Its paired relay is online, but the collector cannot be silently installed through the available read/file/workflow tools. The installer and collector must reach the existing Termux runtime once before the new action can appear. APK installation and physical A54 session collection are separate gates from the simulated Android CI path.

The enabled Aergis DevLoop Watch checks hourly, the fastest supported schedule. It requests new-session reconstruction, reproduced fixes and CI through the existing connectors, staying quiet for unchanged/missing evidence. It reports execution blockers rather than pretending background code ran. This is scheduled polling, not an instantaneous webhook, and never authorizes merge or publication.

## Observation thresholds

HAND_LOST records the transition from an owned hand to no observed owner. TRACKING_JUMP records a same-owner normalized pointer step above 0.15 while pinching. CAMERA_DELAY records measured pipeline age above 600 ms; INFERENCE_STALL records inference above 250 ms. These are diagnostic warning thresholds only; they do not change capture admission, gesture eligibility, smoothing, or dispatch. An expired matching selection-click observation produces SELECTION_FAILED; authority changes and observation shutdown cancel its diagnostic transaction instead.

## Execution decisions

- Activity backgrounding checkpoints the capture session; only capture shutdown finalizes it, preserving normal control of other apps.
- Rotation and semantic-scroll SUBMITTED results remain submission evidence; native swipe fallback still waits for its existing completion callback.
- Comparison results are scoped to exact session/build identities. Truncated input cannot certify a replay regression.
- Physical installation/acceptance remains pending because the connected phone task catalog cannot upload or install extensions. No second relay was added.
