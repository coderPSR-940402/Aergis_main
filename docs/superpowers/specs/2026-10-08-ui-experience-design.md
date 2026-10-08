# Aergis UI experience

## Intent and baseline
Transform the installed app into a distinctive, readable native control product using the supplied mockup's navy, cyan and violet visual language. Preserve all existing camera, tracking, calibration, mapping, dispatch and recording behavior. Reference: main and `baseline-successful-apk` both resolve to `19bfa6805bbee746712e00a0b11a1153e47c9c78`, verified by Aergis CI #205.

## Current weaknesses
The entire app is one LazyColumn in MainActivity. Start/stop and arm/disarm appear after diagnostics and five mappings. MaterialTheme has no custom palette, typography or shapes. Readiness, capture, pointer and action state are fragmented. Debug tools dominate the initial experience. There is no navigation; system bars use a light theme. Calibration is functional but lacks visual hierarchy. Live counters are collected at the screen root.

## Visual system: Aergis Aurora
Background #060C1B; surface #101C32; raised surface #17253E; outline #304663. Text #F2F6FF; secondary text #B4C3D9. Cyan #67E7FF for activation and selection; violet #B7A0FF for secondary emphasis. Success #73E6BB; caution #FFD18A; error #FFB4BD. Text and icons must have at least 4.5:1 contrast for ordinary text; glow is never the only state indicator.

Use system sans typography (no downloaded fonts): 32sp/38sp screen headline, 24sp/30sp section headline, 18sp/24sp card title, 16sp/24sp body, 14sp/20sp supporting text, 12sp/16sp labels. Spacing 4/8/12/16/24/32dp. Cards 24dp corners; controls 16dp; chips pill-shaped. Minimum touch target 48dp; primary controls minimum 56dp. All text wraps; no fixed card heights.

Build a shared theme, bordered surfaces, gradient primary button, quiet secondary button, status chip, switch row, section heading, labeled slider, native navigation and small code-native line icons. Depth comes from contrast and a one-pixel rim. Gradients are localized to activation and identity. No blur, shaders, image downloads, animated bitmaps or infinite animation. State color transitions 160ms; navigation fade 180ms; easing FastOutSlowIn. Native Compose animation APIs respect platform duration scale. Readable labels persist when motion is off.

## Architecture and capability mapping
Four top-level destinations, saved across activity recreation: Control, Tracking, Gestures, Settings. Back from any secondary destination returns to Control. Content is scrollable with system/gesture insets. Only the visible destination collects live state.

| Mockup concept | Repository capability | UI decision |
|---|---|---|
| Welcome/onboarding | Existing permission/settings links, start policy | Inline setup checklist on Control; dedicated onboarding later |
| Home/control | Capture, arm/disarm, pointer and gesture toggles | Implement first; separate capture from action arming |
| Calibration | Live profile, six sliders, sample capture and reset | Retain all controls on Tracking; no equations changed |
| Live tracking | Optional draggable camera/landmark mirror | Keep actual mirror controls; no fabricated camera view |
| Tracking health | Accepted/rejected vision and dispatch counters, pose evidence | Actual diagnostics; no invented percentage, FPS or latency |
| Gesture library/detail | Five classifier mappings, pointer tap and scroll, practice | Five editable mappings with action labels and practice guidance |
| Settings | Pointer, gestures, hand preference, OS settings links | Group existing settings; preserve stores and callbacks |
| Quick actions | No media/brightness/screenshot dispatch interface | Defer; navigation shortcuts only, clearly labeled destinations |
| Profiles | Hand/orientation calibration contexts only | Show actual calibration context; no general profile manager |
| Tutorials | Practice text only | Keep practice guidance; no fake videos |
| Appearance | No stored appearance preferences | Defer customization; ship coherent fixed dark theme |
| App mappings | No per-app store or engine routing | Defer |
| Pro upgrade | No billing or entitlement system | Omit |
| 3D tracking / Gesture Lab | No implementation | Omit from navigation; concept/future only in this document |

## State and engine boundary
Use an immutable HomeUiState projection containing running, camera/vision readiness, error, hands, handedness, pointer feedback, control mode and safety. Exclude pointer coordinates, pose evidence and counters so per-frame telemetry cannot invalidate the home hierarchy. Do not claim confidence, excellent health or low latency: those metrics are not exposed accurately. Idle, setup-required, starting, camera-unavailable, vision-unavailable, error, ready, tracking and paused states have explicit text. Armed state is separate from tracking and may coexist with a safety pause.

Existing Activity methods continue to own permission launchers, service start/stop, calibration sessions, mapping writes and report export. No changes to AirRuntime, engine, services, filters, gesture policies or dispatch. All current buttons, enable conditions and callbacks remain reachable.

## Scope and sequence
First review unit: theme/components, navigation and Control integration; organize existing supporting cards into destinations and theme them without rewriting algorithms. Later units: dedicated guided calibration, gesture action picker/detail, permission onboarding, supporting visual polish and device accessibility/performance fixes. Each unit stays on work/ui-experience, gets review, CI and a device APK check before integration. No changes to main or baseline marker.

## Verification
Add state-projection tests for camera-ready/no-hand, lost tracking, armed safety pause, disabled accessibility, errors and telemetry-only equality. Run unit tests, lint and both APK builds in CI. Check contrast and XML/resources locally; inspect the diff for engine changes and unchanged callback bodies. Device gate: Galaxy A54 portrait/landscape, large font, TalkBack, system animation scale zero; all original controls; background mirror; exports; start/stop and arm/disarm; tracking performance comparison. Local Gradle/SDK are unavailable: do not label the change build-verified before Actions passes.
