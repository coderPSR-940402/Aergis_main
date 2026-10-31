# Recorded-contact confirmation, latency and recovery

## Source and evidence

Base and remotely resolved automatic build reference:
`1c5a41e67ddbaccc95eb8b37816306def664d9e0` (merged PR #74).
The 2026-10-30 A54 recording identifies `eaee3519197c8bcf03381b23b6482a91274af808`;
that tested merge and current main have identical trees. Its three issued TAPs
completed. Recognition missed the contact at about 106 seconds independently of
dispatch: touch permission remained true, with no owner/filter/authority change.

The committed scalar fixture preserves all recorded sensor intervals and projected
thumb-middle distances from 104.916–106.628 s. Depth is the original thumb-middle
z difference divided by aspect-corrected palm width (270-degree input: z scale 1).
Images, hand coordinates and unrelated user data are excluded. Recording ZIP SHA-256:
`b36794b491a3afd13eacbbb0197069fc35b700e9e0988b9cd73811c52bb77bcc`.

## Changes and boundaries

- Pending contact survives at most 250 ms since its last actual contact sample.
  The recorded two excursions span 233 ms in sensor time. Another below-0.18
  eligible contact must arrive to confirm; remaining in the band cannot click.
  A clear opening, depth veto, invalid/out-of-order/stale sample or explicit reset
  discards pending evidence. Consumed contact still requires projected opening
  at 0.32 before rearming. Contact/release thresholds, 40 ms confirmation and
  110 ms refractory period retain their existing values and slow-cadence contract.
- Camera requests prefer advertised OFF noise/edge processing, then advertised
  FAST, otherwise device defaults. Capture and inference workers request Android's
  interactive display priority, with ordinary scheduling retained if unavailable.
  A second age check after model initialization/conversion skips expired native
  calls and lets the worker drain its newest waiting exposure. No new timestamp,
  queue, parallel native task or relaxed freshness bound is introduced.
- Ownership uses the midpoint of the existing index/pinky MCP width anchors;
  an unreliable wrist or unused MCP cannot lose an otherwise visible palm.
  Two-hand inference, continuity/ambiguity checks, preference acquisition and
  ownership expiry remain. This does not recover a hand the model cannot detect.
- Appearance fallback can seed a complete in-image patch adjacent to a border
  tip and preserves the tip's exact offset from that patch. No padding/clamped
  output point, synthetic landmark or action authority is created. Existing
  immutable-template correlation, ambiguity rejection and motion-only use remain.
- PRECISION derives speed and fine-travel coherence independently per axis. A
  deliberate horizontal move cannot open smoothing for vertical noise, nor can
  vertical noise make horizontal fine aim sticky. Jump rejection and timestamp
  validation remain; PointerPinchController still owns final frozen coordinates.

## Verification and risk

The clean host baseline passed 126 selected core JUnit tests. The new exact replay,
short interrupted confirmation, noisy fine travel and border-tip tests produced
four assertion failures before implementation. After correction and additional
negative/recovery coverage, 164 selected production-core tests pass. All 18 Python
tooling tests pass. The host compiles actual production sources; lightweight stubs
cover Android clock, unused MediaPipe overload and mapping-store access only.

Native regressions additionally exercise actual CameraX request configuration and
an engine pinch with an invalid/clipped wrist. Full Android unit/Compose tests,
lint, both APKs and package/signature checks require CI; local Gradle distribution
download is blocked. Final CI results belong in the delivery PR to avoid an
evidence-only rebuild.

Author self-review only, honoring the user's single-agent requirement. The contact
change deliberately accepts two genuine below-threshold samples separated by a
brief in-band excursion, rather than requiring every intervening sample to show
contact. Check unintended contacts as well as recovered misses. OFF ISP modes may
increase low-light noise; their actual latency/thermal/recognition effect remains
an A54 measurement. Model detection/inference throughput is not proven faster by
host tests. Keeping two-hand discovery preserves ownership behavior despite its
cost. No measured improvement over the recorded ~210 ms / 12.2 analyzed FPS,
physical success, release readiness, merge or functional-baseline promotion is
claimed for this patch.

Device acceptance: repeated natural contacts and long holds; clear release/reclick;
pointer-off selection contact; hand near each frame edge and close to the camera;
second-hand arrival, loss/recovery and pause; PRECISION horizontal/vertical/diagonal
aim and anchored clicks; bright and low light capture/inference age and cadence.

## 2026-10-31 continuation audit

Remote PR #75 still points to `3ed781b`; the automatic main build reference remains
`1c5a41e`. A read-only package query through the paired phone reports preview
versionCode 1344 with camera permission granted. It is older than PR #75's build
1346; neither that query nor the earlier recording validates the PR on the phone.

Found a boundary mismatch in the engine's appearance-template seed: finite model
coordinates in the inclusive interval [0,1] are accepted for pointer motion, but
`(coordinate * imageSize).toInt()` produces an invalid pixel index at exactly 1.0.
The tracker correctly rejects that index, silently losing image recovery at the
right/bottom endpoint. Move normalized-image seeding into the tracker, reject
off-image/nonfinite inputs before conversion, and map the inclusive endpoint to
the last real pixel. Interior pixel conversion, immutable templates, search work,
correlation/ambiguity thresholds and motion-only authority retain their behavior.
Rotation/mirroring/calibration still occur after image tracking, in their existing
order; no off-camera point is clamped into valid action evidence.

The new normalized-border regression failed before the endpoint correction;
the other 165 selected core tests passed. With the correction, all 166 selected
core JUnit tests and 18 Python tooling tests pass. Host compilation uses actual
production sources with stubs for Android's default clock, the unused MediaPipe
landmark overload and mapping-store access. Native engine regressions cover the
right/bottom/corner endpoint moving inward through all four camera rotations.
An additional native replay uses the recorded sensor intervals/contact/depth in a
declared synthetic palm with moving index evidence: it expects one completed tap
at the second recorded contact and an unchanged PRECISION target throughout
confirmation/hold. This is integration coverage, not new recorded hand geometry.

Local `./gradlew test --no-daemon` cannot download Gradle (`Network is unreachable`).
The new native tests, full Android suite, lint and both APKs require the final CI
run; its result and exact artifact identity belong in PR #75. No measured latency
improvement, new physical gesture success or functional-baseline promotion is
inferred. Review is single-agent, consistent with the existing project plan.
