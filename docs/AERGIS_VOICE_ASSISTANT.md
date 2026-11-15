# Optional Aergis voice assistant

Voice is off by default. It is an adjunct to the existing camera control session:
start camera capture and arm ordinary controls manually. Voice does not arm or
start capture. Turning gestures off does not revoke a voice-owned screen target,
but safety changes, camera/session changes and disconnect/reconnect do revoke it.
There is no standalone voice-only arming path. PAUSE, DISARM and STOP reduce
control authority. STOP immediately disarms and requests camera stop; voice can
remain connected until explicitly disconnected.

The on-screen **Agree and start voice** button discloses that microphone audio
goes to ElevenLabs. Android microphone permission, an authenticated broker and
an explicit user start are required. Voice never restarts itself after process
death. The persistent notification provides Stop voice. Spoken action feedback
is separately preference-off; system TTS engines may use their own network
services. TTS stops and remains suppressed throughout connecting, reconnecting,
conversation and cleanup.

## Operator setup (required before this feature can connect)

Build with `-PaergisVoiceTokenBrokerUrl=https://your-host.example/voice/token`.
The URL is public configuration, not a credential. Default builds have no URL and
fail before creating a microphone/provider session. Configured builds show a masked **Voice access code** field. The user supplies a
**temporary broker access token** there and deliberately starts voice. Unconfigured
builds show **Voice setup required**, disable Start and show no access-code field. This is a broker-issued access token, never an ElevenLabs permanent API
key. It stays in memory, is removed from the start Intent and is never persisted
or logged. It is cleared from the field immediately on start. Production account
sign-in/SSO can replace this manual short-lived-token entry later.

Provide an operator-owned HTTPS endpoint accepting authenticated `POST` requests
with `Authorization: Bearer <temporary broker token>`. It must validate user
identity, expiry, authorization and usage limits; only then issue a short-lived
private-agent **voice conversation token** using server-held provider credentials.
Return HTTP 200 with `{"conversation_token":"..."}`. Redirects are refused.
There is intentionally no open anonymous broker implementation. Do not place
permanent provider keys in Gradle, BuildConfig, resources, the APK or this field.
SDK `signedUrl` is for text conversations and is not used for this microphone flow.

Configure a private ElevenLabs voice agent with exactly these client tools:

- `get_aergis_status`: no parameters; returns local camera/control/safety status.
- `perform_aergis_action`: required string `action`. Supported ordinary values are
  listed in `AergisVoiceCommandPolicy`; session controls are `PAUSE`, `DISARM`, `STOP`.

The assistant should invoke tools only for explicit user requests, preserve their
result wording, and never claim a submitted action or Android-completed input
proved the target app changed. Local action whitelist, ARMED/screen policy,
epoch checks and injection rechecks remain authoritative regardless of agent
behavior. Arbitrary coordinate touches, screenshots, lock and permission controls
are excluded. Results are correlated by request ID independently of the latest
visual feedback, so overlapping gestures cannot steal voice results. SUBMITTED
only acknowledges Android dispatch: the tool waits up to 3 seconds for a terminal
result. It reports an unconfirmed timeout if no terminal result arrives.

## Remaining release checks

No provider account, private agent or authenticated broker deployment is assumed
available. The feature remains unavailable until the operator supplies them.
Android unit tests, lint, debug/release builds and shrinking must pass in CI.
Physical-device testing is required for camera/microphone coexistence, actual
provider tools, pending-connect stop, rapid stop/restart, routing/Bluetooth,
reconnect and protected-screen denial. Both the pending SDK-start wait and session cleanup are bounded; if SDK start
or endSession
cannot confirm completion, the app keeps voice suppressed and refuses a new
session until the process is force-stopped and reopened. Physical microphone
release cannot be established by pure policy tests.
