# Aergis DevLoop

PR #81 was merged by the owner. Continue its implementation in the follow-up `fix/devloop-observation-gaps`, draft PR #83. Never merge or advance the functional baseline from instrumentation or CI alone.

## Runtime and evidence

Capture-service creation starts the production diagnostic worker. The worker observes the existing accessibility `feedbackId` and the same epoch-checked `visibleHotspot` used by dispatch. Central `AirRuntime.recordActionFeedback` supplies outcomes. Diagnostics own no gesture, pointer, selection, or accessibility authority.

Accepted frame observations carry the actual controlling-owner ID, pointer/contact state, source/input epochs, camera age and inference duration. Observation admission is non-blocking and bounded; all persistence and logcat work runs on one diagnostic worker. Queue overflow marks evidence incomplete and stops interpreting absent callbacks as failures. A recorder or transport error cannot change input decisions.

A completed tap is recorded as a completed tap. It suppresses a missed-click event. An explicit rejection records its original request ID and reason. A concrete TAP request missing completion at the diagnostic deadline produces one MISSED_CLICK. This is a dispatch observation, not a count of every physical pinch attempted before recognition. Submission-only Android APIs retain submission evidence; absence of a callback they do not offer cannot prove failure. An intentionally held pointer drag has no diagnostic completion deadline.

The Toolbox summary now exposes measured quality findings even when dispatch timeouts are zero. It states the recognized TAP denominator, distinguishes Android completion from intended target activation, and lists latency, pinch pointer steps, cancellations, observation/trace loss and pointer reversals. `intended_click_success_rate` is null: the recorder cannot enumerate a user's intentions. `click_success_rate` is retained for compatibility and explicitly duplicated as `recognized_dispatch_completion_rate`.

Passive contact observations copy projected thumb-middle distance and depth separation after owner selection, before the interpreter's action gates. A projected distance below .18 begins a numbered geometric episode; separation at .32 ends it. These observation thresholds never authorize input. Episodes retain original sensor frame IDs, owner/epoch context, gate decisions and bounded geometry samples. Contact IDs are separate from authoritative request IDs. Only fresh same-frame, same-epoch recognition evidence may link a TAP or pointer-off SELECT_ITEM to an episode, using its matching recognition gate. Selection requests never enter recognized TAP counters; a linked request can still fail and retain its own outcome evidence. A recorded release without a linked action is `no_action_observed`, not a fabricated MISSED_CLICK. Loss of ownership/epoch/observations interrupts the episode instead. Raw landmarks, camera pixels and screen text are not exported. Rejected frames before owner selection and unobserved physical intentions remain outside this coverage.

Geometry also retains measured approaches that never cross .18. Crossing between separated, near-contact and closed bands is retained immediately, with periodic geometry samples at 100 ms otherwise. This is sampled diagnostic evidence, not a frame-exact landmark replay. Frame traces retain raw upright fingertip input and the final mapped/displayed pointer with the same frame ID and raw tracking flag. They occupy different coordinate spaces; applying the calibration/mapping context is necessary before treating their difference as a filter error.

Pointer measurements cover consecutive same-owner, same-epoch samples within 350 ms. Small direction reversals of at least .002 normalized units on successive steps within 150 ms are counted outside pinch too; deliberate direction changes remain possible. These are movement observations rather than a stationary-hand jitter diagnosis. `slow_frame_count` counts observed pipeline age above a 100 ms review budget; frame-gap p95 is also retained. The earlier pinch-step metric did not check epochs: comparing old/new maxima cannot establish a control fix because this measurement definition has changed.

App-private files are under `files/devloop/`: `session.json`, `session.open`, `latest.json`, bounded `events/`, `sessions/`, `traces/`, and `trace.json`. Clean capture stop drains accepted observations, cancels unfinished diagnostic transactions and persists terminal evidence before bulk log export. Every periodic manifest carries its original observation wall clock. A remaining open marker on the next start records an unclean previous session and emits PROCESS_RESTART.

Leaving the Aergis activity checkpoints the running session. It does not stop or mark the capture service clean: operating other apps is Aergis' normal background mode. Swiping the task away also checkpoints. Stop capture normally to finalize. Durable shell queries can discover an inactive capture whose final record was lost without requiring another capture to start. Such a session is explicitly unclean; discovery time is not invented as its termination time.

## Existing Toolbox transport

Promoted events transmit their bounded frame context immediately when finalized, with window/completeness fields. Restart recovery also transmits the prior unclean manifest under its original identity. The worker emits `AERGISTEL|` schema-1 lines tagged `AergisDevLoop`. Records are allowlisted session metadata, action traces, bounded sampled frame context, and promoted events. No camera images, screen text, contacts or unrelated app data are transported. No network endpoint, API key or second phone relay is added.

`python3 install_devloop_toolbox.py` (or `--server /absolute/path/to/existing/server`) adds `aergis.devloop` to the existing Toolbox catalog and builtin dispatcher. It backs up source before mutation, validates Python syntax, preserves existing actions and pairing, installs a runit collector, and adds a separate Termux:Boot launcher. The installer restarts the collector to load upgraded code and asks the existing supervisor to reload its dashboard/agent. Exact v1 extension blocks are replaced in place, preserving unrelated extensions. The collector polls every five seconds, retries after access failures, and recovers atomic app files through the read-only `com.airgesture.control.devloop` provider using the existing Shizuku/rish connection. The provider requires Android DUMP permission and additionally restricts callers to shell or the app's own UID. Bounded logcat polling remains a legacy fallback; logcat is never cleared. No additional phone relay is introduced.

For source-only verification, use `--no-service`. This was also exercised against the actual previously shipped MAX 0.4.3 server source, not just the minimal installer fixture. That check establishes compatibility with that source, not installation on the phone's newer runtime.

The resulting read-only Toolbox action accepts `view`:

| View | Output |
| --- | --- |
| `latest` | Plain-language summary, metrics, build/session identity, latest event and comparison |
| `session` / `manifest` | Current or final session manifest |
| `event` | Latest promoted event |
| `history` | Bounded promoted event history |
| `status` | Recording, clean termination, or confirmed unclean state |
| `replay` | A bounded page of the redacted action/frame trace |

The Toolbox action also accepts `session` (default `latest`), `offset` (default `0`), and `snapshot` (default `first`). A replay response supplies `snapshot_id` and `next_offset`; repeat with those values until `next_offset` is null. A changed local trace returns `snapshot_changed` explicitly. Pages stay below 24 KB when formatted, avoiding Toolbox's 64 KB output cutoff. Device snapshot pages are immutable across newer checkpoints and are retained in a bounded cache for retrieval.

The same views are available with `python3 aergis_live_collector.py VIEW --session ID --offset N --snapshot HASH`. Files are written atomically to `$HOME/agent-gates/aergis-live/`. `latest` includes capture freshness and collector health, separately from the original manifest. After 30 seconds without a current source heartbeat it says `stale`, never confidently `recording`. Stale evidence alone does not establish a clean or unclean exit. A missing local trace triggers full reconstruction even if a previous synchronization cursor survived. Toolbox's existing `data.json` can also read the cached JSON files.

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

## Deployment and current device evidence

The v1 collector is installed on the paired A54. Build `1a0ca7a13257909c120ad89317b58e98efffabf4:1381`, session `7189f081-b590-43dc-8bcc-4d4684d83a8c`, recorded seven completed clicks and zero missed clicks/action failures. The user confirmed the session had ended, but its manifest remained recording at sequence 2740. The redacted summary fixture is `tools/fixtures/devloop/a54-1381-stale-manifest.json`; it is not a complete replay or a recognition-root-cause fixture. The returned replay was truncated by Toolbox's 64 KB cap.

Build `0282d44466150be838828dd8ec6dd622c8c73bb0:1392`, session `8d52da3d-1b2a-4001-9712-01bdb55dfacf`, checkpoint 8698 recorded 7 completed TAPs from 8 recognized TAP requests, zero completion timeouts, 415 ms frame-age p95, a .461 normalized pinch step and 5,695 discarded trace rows. The owner reports ignored pinches, lag and jitter. `tools/fixtures/devloop/a54-1392-quality-gap.json` preserves the captured summary, explicitly not a complete recognition replay. The reported zero timeouts do not contradict that experience. This follow-up reproduces recorder/reporting defects; it does not claim a source fix for the physical gesture symptoms without their original contact evidence.

Upgrade both the APK (for durable evidence access) and collector (for recovery/pagination). Extract the installer ZIP and run `python3 install_devloop_toolbox.py` in its folder; no uninstall, data clearing, re-pairing or new relay is needed. The exposed phone catalog cannot upload or install extensions, so this deployment step cannot be performed silently through its read-only tools. Repaired behavior on the physical A54 still requires the upgraded installation; CI simulation does not establish that acceptance.

The enabled Aergis DevLoop Watch checks hourly, the fastest supported schedule. It requests new-session reconstruction, reproduced fixes and CI through the existing connectors, staying quiet for unchanged/missing evidence. It reports execution blockers rather than pretending background code ran. This is scheduled polling, not an instantaneous webhook, and never authorizes merge or publication.

## Observation thresholds

HAND_LOST records the transition from an owned hand to no observed owner. TRACKING_JUMP records a same-owner normalized pointer step above 0.15 while pinching. CAMERA_DELAY records measured pipeline age above 600 ms; INFERENCE_STALL records inference above 250 ms. These are diagnostic warning thresholds only; they do not change capture admission, gesture eligibility, smoothing, or dispatch. An expired matching selection-click observation produces SELECTION_FAILED; authority changes and observation shutdown cancel its diagnostic transaction instead.

## Execution decisions

- Activity backgrounding checkpoints the capture session; only capture shutdown finalizes it, preserving normal control of other apps.
- Rotation and semantic-scroll SUBMITTED results remain submission evidence; native swipe fallback still waits for its existing completion callback.
- Comparison results are scoped to exact session/build identities. Truncated input cannot certify a replay regression.
- The v1 device evidence is retained; acceptance of the upgraded recovery path requires its matching APK and collector. No second relay was added.
