# Aergis voice integration

## Provider-side voice personality
ElevenLabs agent: `agent_4101m73erqmeenm9mydanx3fjayk`
Voice: Daniel, `onwK4e9ZLuTAKqWW03F9`
Client tools: `perform_aergis_action` and `get_aergis_status`

## How to use
1. Install this PR's debug APK and grant microphone permission after opening Control.
2. Explicitly tap **Start voice assistant**. A distinct persistent microphone notification must appear.
3. Start Aergis camera capture and **Arm control** locally. Voice never arms controls itself.
4. Try “Aergis, go home”, “scroll down”, “volume up”, “show notifications”, “disable pointer”, or “what's your status?”
5. **Disconnect voice assistant** in Control or tap **Stop voice** in notification. Capture remains separately stoppable. For offline gesture confirmations, toggle **Spoken action feedback**.

## Safety and architecture
- Voice is optional; gesture tracking/capture does not depend on network or ElevenLabs uptime.
- A separate explicitly started microphone foreground service hosts the official ElevenLabs Android SDK 0.13.0. Its process does not auto-restart (`START_NOT_STICKY`), and its notification contains a one-tap stop.
- Client-side tools are whitelisted. No arbitrary text command, taps, long presses, screenshot, lock, system permissions, or microphone command can bypass local safety checks.
- Regular actions use the existing accessibility dispatch path, epoch checks and protected-screen policy, and require `ARMED`. Voice can still PAUSE, DISARM or STOP capture without arming the device.
- Action results distinguish `SUBMITTED` from `COMPLETED`. Android may acknowledge submission before visible app completion.
- Spoken feedback uses device TextToSpeech for discrete action outcomes when conversational voice is not connected, never per-frame events. Disable it independently.
- Audio and conversations are processed by ElevenLabs during voice sessions; microphone use is not local-only. No API key is included in source, build config or APK.
- **Deployment limitation:** This integration uses an unauthenticated/public ElevenLabs agent ID because no authenticated token-minting backend was provided. The ID can be extracted from a distributed APK, and public usage may consume the owner's ElevenLabs allowance. For private production distribution, switch the agent to authenticated conversations and obtain short-lived session tokens from a secure backend. Never embed a permanent API key.
- Physical-device testing (Android 16/17, Galaxy A54) is required to validate foreground microphone and camera coexistence, background restrictions, LiveKit audio routing, and real spoken tool calls. CI alone cannot confirm these.

## Acceptance
- Toggle does nothing until microphone permission granted.
- Voice cannot issue dangerous coordinate actions, act while disarmed, or act on a protected window.
- Voice with gestures toggled off still uses its independent explicit activation gate.
- Spoken feedback does not speak for each hand frame, nor claim a submitted action has completed.
- Stop microphone in settings/notification and verify it stops capturing even if Android process is killed/restarted.
