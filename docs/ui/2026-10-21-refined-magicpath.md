# Refined native product UI

Design source: MagicPath project 463532014671396864, component 463532054102020096,
revision 463662896543571968. User explicitly requested implementation of this revision.

Base: main c8ef54c1bea04dfccd0cea553f4ac16d9d6dc361, also resolved from
baseline-successful-apk. This is CI verification, not new device acceptance.
The evidence ledger retains the separately identified device baseline. Preserve all
newer main behavior; do not apply the supplied historical engine patch.

Single-agent implementation, in the existing isolated worktree:
1. Native Canvas metal hand, control lens and direction disc. Decorative semantics;
   no camera processing, recognition feedback fabrication or animation loop.
2. Shared graphite/silver/violet/signal tokens, lighter typography, quieter panels,
   refined native buttons and navigation. Keep existing route/saveable contracts.
3. Control command center with explicit camera key and one primary Start/Arm/Disarm
   action, gesture/motion tiles and secondary precision tools. State and eligibility
   remain ProductSession-owned; preserve physical Stop and Disarm availability.
4. Gesture library overview, sculpted mapping rows and pose guide; motion disc in
   existing walkthrough. Keep search, filters, save/cancel, tuning and settings intact.
5. Native regressions for command callback isolation, disarm on unsafe state, narrow
   and large-text scrolling. Capture native previews in existing CI report artifacts.
   Run existing host tests and full Android CI; inspect report images and final diff.

Risks: layout at large font/landscape and Canvas API compilation; CI + native tests
are the verification gate. No changes to tracking, MediaPipe, CameraX, accessibility
execution, filters, calibration math/storage, gesture recognition or service lifecycle.
No new runtime dependency. Author self-review honors the no-subagents instruction.
