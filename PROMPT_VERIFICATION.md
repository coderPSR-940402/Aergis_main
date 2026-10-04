# Autonomous Prompt Verification

**Prompt reviewed:** `AUTONOMOUS_AGENT_EXECUTION_PROMPT.md`
**Prototype APK:** `AERMOTUS-v0.18.24-preview-vc49-r8-filter-shadow.apk`
**APK SHA-256:** `73fdec175522b4caf05ce9f80cc94e6673b837127844d85d42af1a071560b890`

## Result

The prompt is suitable for autonomous repository execution after the parameter/configuration guardrails added in this revision. It now distinguishes verified prototype facts from values that must be benchmarked, and it explicitly prevents unsafe prototype packaging from being copied.

## Verified facts

| Area | Verification | Prompt coverage |
|---|---|---|
| Package | `com.airgesture.control` | Preserve package unless migration is deliberate |
| Prototype release identity | `AERMOTUS`, `0.18.24-preview`, code 49 | Do not copy version identity automatically |
| SDK | min 26, target/compile 36 | Keep API-36 compatibility |
| Model | 8,373,440 bytes; SHA-256 `97952348cf6a6a4915c2ea1496b4b37ebabc50cbbf80571435643c455f2b0482` | Exact size and checksum verification |
| Foreground capture | Camera foreground service | Preserve current service configuration |
| Launcher | `ProductionActivity` in prototype | Recover workflow before changing launcher |
| Calibration | Profile version 2 exposed by prototype | Versioned schema and migration tests |
| Vision freshness | Pointer 220 ms; gesture 450 ms | Named initial policy constants; benchmark before tuning |
| In-flight work | Pointer 2; gesture 2 | Enforce bounded work; benchmark device suitability |
| Native ABIs | arm64-v8a, armeabi-v7a, x86, x86_64 | Preserve support unless an explicit size decision exists |
| Packaging | Debuggable preview artifact | Explicitly do not copy into production |

## Feature coverage audit

The prompt explicitly covers:

- replay codec/engine/report contracts;
- deterministic benchmark reports;
- malformed-input and allocation bounds;
- vision freshness, stale-result drops, and in-flight work;
- frame pacing/watchdog/recovery;
- versioned calibration and safe persistence;
- pose and world-geometry evidence;
- hand identity and ownership arbitration;
- center-gated, axis-dominant, speed-bounded swipes;
- cooldown and re-arm behavior;
- accessibility action tickets/epochs;
- production Control, Setup, Diagnostics, Practice, Calibration, and Tracking views;
- screenshots and recordings as behavioral evidence;
- safety and privacy constraints;
- branch, commit, CI, and artifact-validation discipline.

## Configuration flags that must not be copied blindly

The prototype’s broader configuration must remain excluded by default:

- `application-debuggable=true`;
- `android:canRetrieveWindowContent=true`;
- `flagRetrieveInteractiveWindows`;
- `INTERNET`;
- `ACCESS_NETWORK_STATE`;
- any exported diagnostic or calibration component without an explicit component review;
- prototype version code/name and signing properties.

The current repository’s `canRetrieveWindowContent=false`, `usesCleartextTraffic=false`, minimal permissions, explicit non-exported capture service, and release minification should remain the default baseline.

## Recommended agent behavior

The agent should treat `220 ms`, `450 ms`, `2`, and calibration schema `2` as **initial reference constants**, not unquestionable truths. They must be named, tested, logged in benchmark reports, and adjusted only with replay/device evidence. The agent must not claim prototype parity from class names or screenshots alone.

## Remaining evidence limitation

APK inspection verifies package/resource/build/class fingerprints and the model checksum, but it cannot recover the original source comments, complete test corpus, tuning dataset, or physical-device latency/false-positive behavior. Screenshots and recordings improve UI/workflow reconstruction; replay traces and device measurements are required for algorithmic parity.
