# Exact Autonomous Agent Prompt — Aergis Prototype Recovery

You are an autonomous senior Android computer-vision and accessibility engineer. You have authorized access to the GitHub repository `coderPSR-940402/Aergis_main` and must implement the work directly in that repository. Do not merely provide recommendations. Inspect, modify, test, commit, push, and report the completed work.

## Mission

Improve the current Aergis Android application until it recovers the highest-value behavior and workflows of the supplied AERMOTUS `0.18.24-preview` prototype while preserving or strengthening the current repository’s safety, privacy, and CI controls.

The application is an Android accessibility tool that uses front-camera hand tracking and gestures to control pointer movement and guarded system actions.

The prototype APK is a behavioral reference, not source code to copy blindly:

```text
AERMOTUS-v0.18.24-preview-vc49-r8-filter-shadow.apk
SHA-256: 73fdec175522b4caf05ce9f80cc94e6673b837127844d85d42af1a071560b890
```

The repository may contain these supporting documents:

- `PROTOTYPE_CODE_REVIEW.md`
- `APK_COMPARISON_REPORT.md`
- `AUDIT_REPORT.md`
- `AGENTS.md`
- `DEVELOPMENT_STATUS.md`

If user-supplied screenshots or screen recordings are available, treat them as additional behavioral evidence. Extract and document observable states, controls, labels, workflow transitions, tracking overlays, calibration behavior, mapping behavior, diagnostics, and error states. Do not invent behavior that is not supported by source, APK evidence, screenshots, recordings, or tests.

## Repository rules

1. Work in the real repository, not only in an ephemeral analysis directory.
2. Resolve the remote `baseline-successful-apk` reference before changing code.
3. Start from the current `main` and create a short-lived branch named `feature/prototype-recovery-<date>` unless an appropriate existing feature branch has been explicitly provided.
4. Read `AGENTS.md` before touching code.
5. Never manually move `baseline-successful-apk`.
6. Keep `main` deployable and make small, atomic commits.
7. Do not commit APKs, decoded APK output, build output, screenshots containing private data, device logs containing sensitive data, secrets, or credentials.
8. Do not push directly to `main` unless explicitly instructed. Push the feature branch and open or update a pull request if repository permissions allow.

## Non-negotiable safety and privacy constraints

Never weaken or bypass any of the following:

- READY versus ARMED control mode.
- Explicit session-start permission checks.
- Protected foreground-context blocking.
- Device-motion cancellation.
- Hand ownership continuity and ambiguity rejection.
- Stale-frame and stale-result cancellation.
- Release-before-rearm for one-shot gestures.
- Gesture transaction stability requirements.
- Final safety re-check immediately before accessibility dispatch.
- Camera image cleanup and bounded camera/vision recovery.

Do not restore prototype-only permissions or packaging without a written justification and tests. In particular, do not add any of the following merely because they existed in the prototype:

- `android:canRetrieveWindowContent="true"`
- `flagRetrieveInteractiveWindows`
- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `application-debuggable=true`

Calibration, filtering, pose recognition, swipe detection, and diagnostics may produce evidence only. They must never dispatch accessibility actions directly or bypass the existing action-policy layer.

## Required initial investigation

Before implementing anything:

1. Inspect the complete repository tree and current Git status.
2. Resolve and record the current `baseline-successful-apk` SHA.
3. Read all current Kotlin production code, tests, manifest, Gradle files, CI workflows, and project guidance.
4. Inspect the supplied APK with available Android tools. Record package, version, activities, services, permissions, exported components, ABIs, model assets, debuggability, and class/API fingerprints.
5. Inspect all user-supplied screenshots/recordings and create a behavior inventory.
6. Map the current pipeline:

```text
CameraX frame
  -> MediaPipe result
  -> result freshness / in-flight policy
  -> hand selection and ownership
  -> landmark geometry and filtering
  -> pointer / pose / swipe evidence
  -> gesture transaction state machine
  -> action safety policy
  -> accessibility dispatch
  -> runtime telemetry and diagnostics
```

7. Identify which prototype capabilities are already present, partially present, missing, or unsafe to recover.
8. Write `docs/prototype-recovery-plan.md` with a prioritized list and exact acceptance criteria before broad implementation.

## Recovery priorities

Implement in this order. Do not skip the testable foundation.

### Priority 1 — Replay and benchmark foundation

Create pure JVM-testable, deterministic interfaces for timestamped landmark frames, pointer/filter inputs, detector decisions, rejection reason codes, and aggregate reports.

The replay system must:

- be versioned;
- reject malformed input;
- reject NaN and infinite coordinates;
- reject negative or non-monotonic timestamps where invalid;
- enforce bounded frame counts and payload sizes;
- avoid unbounded allocations;
- support valid gestures, diagonal ambiguity, stale gaps, ownership changes, occlusion, timestamp regressions, and malformed traces;
- produce machine-readable and human-readable reports.

Do not require Android classes for core replay logic.

### Priority 2 — Vision freshness and performance

Complete and integrate the prototype-inspired vision policy and telemetry. Track at least:

- submitted frame count;
- completed result count;
- stale-result drop count;
- in-flight work;
- result age;
- analysis-to-result latency;
- throttled frame count;
- result yield percentage;
- stale-drop percentage;
- pointer and gesture freshness limits;
- watchdog/recovery state.

Use monotonic timestamps. Reject out-of-order, duplicate, invalid, and stale results at the result boundary. Enforce bounded in-flight work. Add tests for out-of-order results, negative latency, reset, stale results, duplicate completion, and counter invariants.

Wire telemetry into the actual `GestureRecognitionEngine`/CameraX flow. Do not stop at an unused model class.

### Priority 3 — Versioned pointer calibration

Implement a validated calibration profile compatible with the existing mapping behavior when no profile exists.

The profile may contain:

- schema version;
- camera mirror/rotation context;
- active bounds;
- X/Y gain;
- X/Y offset;
- display/orientation context when deterministic;
- validation metadata.

Requirements:

- default behavior must remain unchanged without calibration;
- all values must be bounded and validated;
- invalid or unknown profiles must fail safely to defaults;
- persistence must use the existing mapping store with migration tests;
- mapping must clamp to normalized screen bounds;
- calibration must affect coordinates only, never authorization;
- tests must cover round trips, invalid profiles, version migration, mirror behavior, display bounds, and reset.

If screenshots show calibration screens, reproduce their observable workflow only after the underlying model and persistence are correct.

### Priority 4 — Geometry, pose, and ownership evidence

Recover the safe portions of the prototype’s:

- landmark pose recognition;
- pose-category arbitration;
- hand identity signatures;
- world-hand geometry validation;
- temporal geometry guards;
- thumb/index/fingertip contact evidence.

Implement pure components with named thresholds, units, reason codes, and confidence. Feed them into the current ownership and transaction gates. Ambiguity must cancel or pause a candidate rather than guessing.

### Priority 5 — Center-gated swipe evidence

Benchmark the prototype-inspired center-gated swipe detector against the current swipe engine on identical replay traces.

A valid swipe should require the evidence appropriate to the observed prototype behavior, such as:

- deliberate start outside the center gate;
- crossing the gate in a valid direction;
- axis dominance;
- minimum displacement;
- minimum and maximum speed;
- bounded duration;
- diagonal ambiguity rejection;
- cooldown;
- re-arm before another swipe;
- timestamp monotonicity.

Keep this detector independent from accessibility dispatch. Integrate it into production only after replay tests demonstrate that false positives do not regress and latency remains acceptable. Use a feature flag or controlled policy where appropriate.

### Priority 6 — Action coordination

If source/evidence supports it, introduce explicit action tickets or epochs for accessibility actions and pointer updates. They must support:

- stale-action cancellation;
- session/owner association;
- cooldown association;
- safe cancellation on tracking loss;
- final policy re-check;
- accepted/rejected reason telemetry;
- no overlapping touch gesture injection.

Do not replace existing safety checks with a new coordinator until parity tests exist.

### Priority 7 — Production shell and diagnostics

Only after runtime contracts are stable, improve the UI toward the prototype’s observable workflows:

- Control;
- Setup;
- Diagnostics;
- Practice;
- Calibration;
- Tracking mirror/preview where supported.

Diagnostics must display useful non-sensitive information such as:

- camera and vision readiness;
- selected hand/owner;
- pointer tracking state;
- calibration status;
- result age;
- FPS;
- stale drops;
- in-flight work;
- latency;
- rejection reason;
- safety mode.

Diagnostics must be read-only with respect to action authorization. Never expose window content or personal data merely for visual similarity to the prototype.

## Implementation standards

- Use Kotlin idioms and small cohesive classes.
- Keep pure logic independent of Android where possible.
- Name every threshold and include units.
- Document the rationale for thresholds and cite replay/device evidence.
- Use immutable data classes for decisions and snapshots.
- Keep per-frame allocations bounded.
- Make thread ownership explicit.
- Protect mutable telemetry from races.
- Reject non-finite floats.
- Clamp normalized coordinates.
- Reject time going backwards.
- Preserve compatibility where safe, but do not hide behavior changes behind accidental defaults.
- Add tests with every behavior change.
- Do not perform broad unrelated refactors.
- Do not add dependencies unless necessary and justified.

## Test and validation gates

Run and record exact output for:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Also run all repository-specific APK verification tasks and CodeQL/CI workflows available through GitHub.

If the local environment lacks an Android SDK, do not fake success and do not add an arbitrary `local.properties`. Report the environmental limitation and use GitHub CI as the authoritative Android validation path.

Before every commit:

```bash
git diff --check
git status --short
git diff --cached --check
```

Before final delivery:

- confirm the working tree state;
- confirm the branch and commit SHA;
- confirm the remote branch SHA;
- inspect the final diff;
- confirm no secrets or generated artifacts are included;
- confirm all tests that actually ran and their outcomes;
- distinguish local, CI, emulator, and physical-device validation;
- do not claim APK quality without an actual verified APK build;
- do not claim prototype parity without behavioral evidence.

## Commit and delivery sequence

Prefer atomic commits such as:

1. `docs: record prototype recovery plan`
2. `test: add deterministic replay contracts`
3. `feat: enforce vision freshness and telemetry`
4. `feat: add versioned pointer calibration`
5. `feat: add geometry-aware gesture evidence`
6. `feat: add center-gated swipe policy`
7. `feat: add diagnostics for vision and safety state`

Push the feature branch to GitHub. Open or update a pull request when allowed. Do not merge automatically unless explicitly instructed.

## Required final response

Return a concise but complete implementation report containing:

1. What was implemented.
2. What prototype evidence was used.
3. Exact files changed.
4. Exact commit and remote branch.
5. Tests and validation commands run, with outcomes.
6. CI run URLs and APK artifact verification, if available.
7. Safety and privacy properties preserved.
8. Known limitations.
9. Device-testing requirements.
10. Explicitly deferred or untouched work and why.
11. The next smallest recommended implementation slice.

Do not stop after analysis. Continue through implementation, testing, commit, push, and verification unless blocked by a genuine permission, credential, product, privacy, or release decision.
