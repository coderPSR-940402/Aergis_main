# Aergis — Air Gesture Control

Aergis is an Android air-gesture control application. The repository is the active product source and is maintained as an independently buildable project.

## Project contract

| Property | Current value |
|---|---|
| Package | `com.airgesture.control` |
| Version name | Declared in `app/build.gradle.kts` |
| Preview version code | `1000 + GITHUB_RUN_NUMBER` in CI, `1000` locally |
| Minimum SDK | `26` |
| Compile / target SDK | `36` |
| Java | `17` |
| Android Gradle Plugin | `9.4.1` |
| Gradle | `9.8.0` via the checked-in wrapper |
| Kotlin | `2.4.21` |
| CameraX | `1.6.2` |
| Compose BOM | `2026.06.01` |
| AndroidX Activity | `1.13.0` |
| AndroidX Lifecycle | `2.10.0` |
| AndroidX Core KTX | `1.18.0` |
| CI | GitHub Actions |

The version values above describe the current project configuration. **They are not the definition of the development baseline.**

## Build reference and functional baseline

`baseline-successful-apk` tracks the **newest successful APK-producing Aergis CI build on `main`**. It is a build reference. The functional baseline additionally requires explicit device confirmation tied to the exact APK, commit and CI run; see [the evidence ledger](docs/EVIDENCE_LEDGER.md). CI success alone does not establish usability or release readiness.

GitHub reference:

`baseline-successful-apk`

That reference is advanced automatically only after the CI workflow has successfully:

1. run unit tests;
2. run Android lint;
3. assembled the debug APK;
4. assembled the minified release APK;
5. verified the debug APK package, version, bundled models, and preview signing certificate;
6. verified the minified release APK package, version, and bundled models;
7. exercised isolated production signing with an ephemeral key;
8. generated APK SHA-256 checksums and uploaded the build artifacts; and
9. completed the baseline-reference update.

Failed or cancelled builds cannot advance this CI build reference.

### What this means for development

- Always resolve `baseline-successful-apk` before making functional changes.
- Compare new work against that commit.
- Preserve the last device-verified APK as rollback evidence without resetting current development to it.
- A newer successful `main` build advances the CI reference only; device confirmation advances the functional baseline.
- Pull-request builds are validation builds; they do not replace the `main` baseline.

## Current application state

The project includes:

- Compose-based launcher UI
- persistent action mappings and pointer-mode state
- accessibility-service gesture injection and global navigation
- CameraX-based foreground camera capture
- runtime and session policy models
- MediaPipe-based vision processing
- unit tests
- GitHub Actions CI
- debug and minified release APK artifacts
- APK verification and SHA-256 reporting

## Build and verification

The CI environment uses Java 17, Android SDK 36, and the checked-in Gradle 9.8.0 wrapper. Run `./gradlew` for reproducible local builds; AGP 9 uses built-in Kotlin support and the project keeps the Compose compiler plugin aligned with Kotlin 2.4.21. AndroidX, CameraX, Lifecycle, Core KTX, and Compose dependencies remain on their upgraded stable lines.

Run the Android and host checks from the repository root with Java 17, Android SDK 36 and build-tools 36.0.0, and Python 3.11 or newer.

```sh
python3 -m unittest discover -s tools -p 'test_*.py'
./gradlew test :app:lint :app:assembleDebug :app:assembleRelease
```

The full workflow is in [Aergis CI](.github/workflows/ci.yml).

A successful CI run produces:

- debug APK
- minified unsigned release APK
- APK SHA-256 checksums
- test and lint reports
- machine-readable baseline metadata

The APK artifact name is tied to the commit SHA, making the build traceable to its source revision.

### Offline model preparation

Before going offline, run `./gradlew :app:prepareGestureModel :app:prepareFaceModel`
or copy the two verified `.task` files from a trusted build of the same revision
into `app/src/main/assets/`. The model tasks check both the size and SHA-256
against the pins in `app/build.gradle.kts`. An offline build requires both
verified model files and a previously populated Gradle cache and Android SDK.

## Development workflow

1. Resolve the current `baseline-successful-apk` reference.
2. Make one controlled change.
3. Run tests and relevant validation locally when practical.
4. Prepare the complete diff and checks, then obtain owner approval before committing.
5. Push the approved feature branch and inspect PR CI; do not merge or release without authorisation.
6. Repair change-related CI failures. Keep current work intact and retain separate build and device rollback references.

## Repository rule

Do not replace the working implementation with unrelated historical Aergis repositories or assume that a remembered APK represents the current product state. The current repository plus its verified CI baseline is the source of truth.

## Installing preview updates

Use the **debug APK** from Aergis CI for device testing. Debug APKs use the
permanent public preview identity in `ci/aergis-preview.keystore`; CI verifies
its certificate before uploading an APK. Its password and key password are
`android`, and its alias is `androiddebugkey`. This is intentionally a public
test key, **never a production/release signing key**. Keep it unchanged across
preview builds. Release artifacts remain unsigned and require a separate
private production signing key.

CI preview version codes are `1000 + GITHUB_RUN_NUMBER`. Install newer CI builds
over older ones to retain app data, calibration and permissions; local builds
use version code 1000 and cannot replace a newer CI APK normally. Re-running the
same CI run retains the same version code and signing identity.

Previously generated CI debug keys were different on each clean runner. If the
currently installed APK uses one of those old keys, one final uninstall/install
is needed to enter the permanent preview update chain. An old APK certificate
alone cannot recover its private signing key.

When returning from diagnostics sharing or saving, Aergis refreshes foreground
safety when its own window actually regains focus. It does not automatically
arm control or enable gesture actions. Protected screens still block actions.

## Pointer touch controls

Aim with the index fingertip. With control armed, the pointer enabled and
**pinch clicking** enabled, bring the **thumb and middle fingertip** together:

- Confirmed contact requests one bounded tap at the frozen visible pointer target.
- Keeping contact closed does not repeat the tap or turn it into a long press.
- Separate to rearm and resume aiming. Release does not create a new tap.
- If the overlay is briefly behind, a previously confirmed tap may wait up to
  250 ms for the original target to agree with the applied visible hotspot.

Pinch clicking and mapped gesture actions have separate switches. Pinch clicking
can remain available with mapped gestures disabled. When the pointer is off,
pinch activation uses the highlighted selection rather than stale coordinates.
Both channels still require armed control and an allowed foreground context.

The detector requires observed opening before contact, contact confirmation and
separation before another click. The cursor anchors around contact. Pointer
travel or held pinch contact does not automatically start scrolling or dragging.
Mapped classifier commands are suppressed during pointer pinch evidence; a
consumed occurrence needs genuine neutral evidence before it can fire again.

For long press, assign the **Long press** action to a distinct gesture. For
precision dragging, assign **Precision drag: start / release**: invoke it to
press, aim to move, then relax and invoke it again to release. Pinch clicks are
blocked while that native drag is busy. Scroll/swipe mappings and **Arm one motion
command** are separate ways to navigate without automatic pointer-travel scrolling.

Tracking loss, disarming, unsafe foreground context, pointer disabling or display
changes revoke pending coordinate input. Mapped dragging also requires mapped
gestures enabled and has a freshness watchdog. Native drag cancellation uses an
undispatched-stroke continuation; its Android behavior still requires physical
device validation.

UI clients can observe `AirRuntime.state` / `AirRuntime.uiState` through
`pointerInteraction`. Pinch tracking reports `AIMING`, `PINCHING` or `PRESSED`;
`DRAGGING` belongs to the separate precision-drag path. `HOLDING` remains an enum
value for compatibility, not a current pinch-to-long-press feature.

### Precision tracking and clicking

**PRECISION** is the default pointer mode. It smooths stationary hand noise,
responds faster to consistent fine aiming and deliberate travel, and holds an
isolated large jump until another measurement confirms it. It does not predict
positions beyond the observed target. **CURRENT** and **VC49** remain selectable
in Testing Tools for comparison.

Pinch and pose distances account for the upright camera image's aspect ratio.
Ordinary pointer movement cannot arm a precision drag; the mapped drag command
starts and releases it. Disabling the pointer blocks late coordinate touch
dispatch and native drag continuation.

Synthetic 15/30/60 fps replays show 21–27% less stationary jitter and 80–95% less
target-relative error during steady travel than CURRENT on the tested traces.
These are filter measurements, not camera-to-screen latency or physical Galaxy
A54 results. See [the benchmark specification](docs/BENCHMARK_SPEC.md) for
reproduction and device validation.

### Retained recordings and comparison runs

Use **Calibrate → Saved recordings** to open older reports or recover an interrupted
recording after relaunch. Recovery preserves the original metadata, frame/event
logs and sampled images; it rebuilds PDF/ZIP exports and marks unavailable counters
as unknown. Nothing is automatically deleted. Save the ZIP to retain camera samples.

`python3 tools/analyze_pointer_recording.py recording.zip` accepts recovered logs
with incomplete records and reports the unreadable counts. Comparison metrics are
calculated per contiguous run (`STATIONARY`, `STATIONARY#2`, etc.), splitting at
tracking gaps or changes in owner, calibration, filter, action epoch or rotation.
Relabel when changing stationary targets. FAST first-arrival time is a response
proxy, not sustained settling or camera-to-display latency.

New recordings compare PRECISION, CURRENT and VC49 from the same measured input.
Older two-filter recordings still open and analyze normally; comparisons split
when the set of recorded candidates changes.

Run `python3 -m unittest discover -s tools -p 'test_*.py'` for the recording analyzer,
APK model/package checks and preview signing regression suite.

Device-motion sensing is removed. Aergis does not register gyroscope or accelerometer listeners, collect device-motion readings, or pause pointer, pinch, gesture actions or calibration based on phone movement. Camera/display orientation mapping still keeps screen coordinates aligned.


## Accessibility and local evidence

The accessibility service can retrieve the active window's accessibility node
tree. Highlighted-item navigation reads node labels, content descriptions,
identities and bounds, and activates selected nodes. The foreground safety policy
separately evaluates window package/class names and blocks protected or unknown
contexts. These checks do not guarantee detection of every sensitive screen.

The manifest declares no Internet permission. Diagnostic recordings are local
and can include sampled camera images, landmarks and action events. Saving or
sharing an exported ZIP can disclose those samples; Gaze Lab does not save its
face frames. See [the capability matrix](docs/CAPABILITY_MATRIX.md) for scope.

## Classifier occurrence behavior

Registered gesture identities and saved assignments are preserved. A fresh
transaction starts ready for its first valid occurrence. A short temporal
confirmation gate rejects transition poses and single-frame spikes: it requires
three distinct observations, or two spanning at least 80 ms, with valid same-owner
evidence. Legacy configurable hold/repeat delays are not the confirmation clock.

A continuing consumed pose cannot repeat. A disabled, unknown, low-confidence,
missing or blocked category is not neutral. Once consumed, recovery or an
action-epoch change does not rearm that occurrence: fresh, same-owner confident
`None` evidence spanning 100 ms is needed before another command can fire.

The Gestures screen and picker explain the change. Legacy hold/repeat preference
values remain stored and survive saving other controls, so reverting this change
restores their original semantics. Confidence and bounded output-action settings
are retained. Existing pointer-mode policy restrictions and dispatch checks remain.

Recognition-triggered pinch taps and separately mapped precision dragging are
implemented in current source. Their presence and successful CI do not establish
physical click accuracy or release readiness; exact-APK device acceptance remains
required. See [the audit follow-up](docs/audits/2026-11-15-follow-up.md).
