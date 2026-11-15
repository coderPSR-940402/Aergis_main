# Optional private voice control rework — 2026-11-15

## Authorized scope and design

The owner requested all review follow-ups, including selective rework of the old
voice branch onto current main. Main's visible hotspot validation, drag escape
arbitration, control dashboard and current dependency pins are preserved. This is
a camera-session adjunct, not a new standalone arming method. No OpenAI integration.

A wholesale merge would replace newer work and retain reviewed defects. A public
agent embeds someone else's availability/billing assumptions. The selected design
ports only the voice delta, requires an operator-configured authenticated HTTPS
broker returning a private voice conversationToken, and fails closed when absent.
Temporary broker bearer entry is masked, memory-only and cleared on start. No
anonymous broker or permanent provider secret is added. The initial manual
short-lived-token handoff deliberately avoids inventing an unverified SSO system.

Local authority remains ARMED + safe foreground + current epoch; voice also
requires healthy active camera capture and a connected current session. Session
commands only reduce authority. Every disconnect/reconnect revokes pending
commands and selection. Voice selection records its origin and voice-session ID,
so gestures-off does not clear it and a replacement session cannot inherit it.

Provider callbacks use a shared generation fence; stop revokes immediately.
Cleanup waits for an in-flight start and ends a late session, and prevents overlap
with a replacement service. Microphone-active remains true until cleanup ends.
If bounded SDK cleanup does not confirm, voice stays blocked until process restart.
TTS watches microphone activity, stops outstanding speech on start and suppresses
new feedback through connect/reconnect/cleanup. Touch completion wording does not
claim visible app effects. Per-request result StateFlows are registered before
dispatch and removed in finally; the latest UI-feedback slot stays independent.

## Test-first evidence and verification

Before production edits, added VoiceSessionPolicyTest and VoiceRequestResultTest
for generation revocation, mic speech suppression, voice selection session owner,
HTTPS broker requirements and overlapping-request outcome retention. Invoked
`./gradlew testDebugUnitTest --tests '*Voice*Test' --no-daemon` before implementation.
It could not start Gradle: fetching gradle-9.8.0 from services.gradle.org failed
with `java.net.SocketException: Network is unreachable`. This is infrastructure
blocking test execution, not an observed red assertion; no red/green proof is
claimed. Added further checks for protected/unknown/paused denial, actual speech
stop policy, unconfigured service start and no automatic process restart.

CI still must run the full unit suite, lint, debug/release APK assembly and APK
verification, including shrinker dependency compatibility. SDK/private-token API
compatibility is checked against the provider documentation separately by the
parent task. Local static diff/whitespace review is recorded in the final handoff.
No Android SDK/provider/device runtime success is claimed. Provider account,
private-agent tool configuration and authenticated broker deployment remain
operator prerequisites; no deployment or login is attempted by this change.

Final local checks: `git diff --check HEAD` passed; Manifest and strings XML parse,
and Kotlin sources contain no unresolved merge markers. The final full command
`./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease --no-daemon`
also stopped at the same Gradle distribution network failure before compilation.
The focused or full Android tests/build/lint therefore remain unexecuted locally.

Pinned SDK source checks (GitHub reads, not compilation):

- [ElevenLabs v0.13.0 ConversationConfig](https://github.com/elevenlabs/elevenlabs-android/blob/v0.13.0/elevenlabs-sdk/src/main/java/io/elevenlabs/ConversationConfig.kt)
  declares nullable default `agentId` and `conversationToken`, `textOnly = false`,
  and the client-tool/status/disconnect callbacks used here.
- [ElevenLabs v0.13.0 README](https://github.com/elevenlabs/elevenlabs-android/blob/v0.13.0/README.md)
  confirms private voice uses conversationToken; signedUrl is private text-only.
- [SDK dependency catalog](https://github.com/elevenlabs/elevenlabs-android/blob/v0.13.0/gradle/libs.versions.toml)
  pins LiveKit 2.26.0. [LiveKit v2.26.0 catalog](https://github.com/livekit/client-sdk-android/blob/v2.26.0/gradle/libs.versions.toml)
  declares `com.github.davidliu:audioswitch`, so JitPack is restricted to that group.
  Maven artifact resolution and shrinker behavior still require the real CI build.


## Pre-publication review corrections

- A real service regression now creates a voice-owned ScreenTargetNavigator target,
  toggles the actual AirRuntime.gesturesEnabled setter, runs the guard timer and
  checks retention, then disarms and checks revocation. AirRuntime records selection
  owner/epoch; only a current connected voice selection under healthy running camera,
  ARMED and SAFE authority survives gesture-setting changes. Other selection origins
  retain the existing epoch revocation behavior.
- Per-request waiters ignore SUBMITTED until a terminal outcome or a 3-second timeout;
  a timeout is explicitly unconfirmed, never returned as completed. Coroutine tests
  exercise SUBMITTED -> CANCELLED and bounded no-terminal behavior.
- Default unconfigured UI shows Voice setup required with disabled Start and no
  credential field. Configured builds label the masked field Voice access code and
  explain temporary in-memory authentication. The broker contract is unchanged.
- The pending-start join is bounded to 3 seconds as well as endSession. A timeout
  publishes force-stop/reopen guidance while preserving fail-closed microphone
  ownership and keeping the pending start job alive so a late successful session
  still executes cleanup. A coroutine orchestration regression exercises a hung
  start, timeout, authority revocation and late release. No cleanup success is
  inferred from a timeout.

The additional focused Android test command was attempted before these fixes and
remains blocked at Gradle distribution fetch (Network is unreachable), with no
assertion execution. Static diff/XML/conflict-marker checks are repeated below.


General README and capability-matrix disclosures were updated after integration
review: the manifest now declares Internet/microphone/microphone-FGS permissions;
optional default-off voice sends audio to ElevenLabs and uses an authenticated
broker, while recognition remains on-device and diagnostic recordings remain
local unless exported/shared. Unconfigured disabled UI and system-TTS network
behavior are explicit. The root task's 59-test Python suite proof is retained;
these documentation edits do not replace or broaden that execution claim.


## First voice CI failures and corrections

Run 43707256629 (initial voice commit 085c) compiled production and test sources,
then reported three failures among 761 completed tests. Downloaded HTML confirms
VoiceRequestResultTest and AergisVoiceServiceTest used manifest target SDK 36 by
default; Robolectric requires Java 21 for that sandbox while CI runs Java 17.
Both new test classes now explicitly select SDK 35, matching existing service
regressions. This does not change app compileSdk/targetSdk or production behavior.

The unconfigured UI regression attempted performScrollTo on text in an uncomposed
LazyColumn item. It now asks the actual dashboard LazyColumn to scroll to its
stable `voice` key, then requires setup text displayed, disabled Start and absence
of credential entry. Assertions remain intact; no product behavior was weakened.
The next CI run must verify these corrections and the previously staged review
follow-ups. Local Gradle remains distribution-network blocked.
