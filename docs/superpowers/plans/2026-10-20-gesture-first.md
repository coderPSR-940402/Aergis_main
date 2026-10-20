# Gesture-first implementation

User directive is the specification; execute inline, no agents and no merge.
Base: 19dbcd7. Stable name-based preferences need no ordinal migration.

1. Extend stable gesture/action catalogues and labels; test legacy IDs/defaults and independent mappings.
2. Decouple screen scrolling/swiping from visible pointer, retaining native callback and epoch cancellation. Add public settings/search actions, app picker and rotation permission handling.
3. Add an independent motion clutch: explicit arming action, stationary acquisition then one normalized directional stroke; cancel on stale/lost/changed ownership. Static/pinch suppressed throughout clutch.
4. Require time-based static confirmation in the production engine; retain neutral-only release. Test weak/transient evidence.
5. Repair anatomical pinch normalization and irrelevant landmark veto; preserve contact-once and anchored target. Audit fingertip continuity and explicit precision drag separately.
6. Focused regressions, diff check, one integrated PR CI plus attributable repairs. Report unresolved physical evidence honestly.

Risks: static confirmation changes latency; public settings can be protected; background activity launch and rotation are OEM-sensitive; native cancellation must remain authoritative; synthetic geometry does not establish device accuracy.
