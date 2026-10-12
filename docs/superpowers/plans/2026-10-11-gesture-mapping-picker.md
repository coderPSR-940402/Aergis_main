# Explicit gesture mapping selection

Part of the user-approved usability excellence roadmap. Independent experience branch from verified baseline `0a86e482cb1aa77879dd28e8a29f539555f3ebfc`; no dependency on the command-timing branch.

## Problem and design
The current mapping button immediately cycles through actions, preventing intentional selection or cancellation and omitting Disabled. Replace it with a scrollable radio-choice dialog and explicit Save/Cancel. Show the selected action, support resetting the draft to the gesture's default, and explain pointer-mode restrictions using the existing dispatch policy. Do not change that policy or stored mappings without Save.

## Files and interfaces
- `MainActivity.kt`: mapping row launches the picker; saves with the existing `ActionMappingStore.setMapping(source, target)`; increments the existing UI refresh version.
- `ui/ActionMappingPicker.kt`: local saveable draft, all AirAction choices including NONE, radio semantics, >=48 dp actions, scrollable choices, shared action label.
- `res/values/strings.xml`: selection, cancellation, Disabled and restriction explanations.

## Work and acceptance
- [x] Replace cycle-to-save behavior with named selection.
- [x] Include Disabled and default-action draft reset.
- [x] Keep all persistence in Save; Back/outside-tap/Cancel only dismiss.
- [x] Explain pointer-mode restrictions without weakening policy.
- [x] Preserve draft through configuration changes using rememberSaveable.
- [x] Review source diff and validate XML/string references and whitespace.
- [ ] CI: unit tests, lint, debug/release APK builds and verification.
- [ ] Device: change, cancel, reopen, save, restart and confirm persistence; Disabled round-trip; default restore requires Save.
- [ ] Device: landscape, large font, TalkBack single selection semantics, scroll to all choices and reachable Save/Cancel.

## Evidence and limits
All resource XML parses, names are unique, and every picker/activity string reference resolves. `git diff --check` passes. Existing alert dialog/selectable patterns and theme are reused. No engine, camera, filtering, calibration, service lifecycle or action dispatch implementation changed. Android compilation and visual/device behavior remain unverified because local Gradle download is blocked and GitHub push was rejected by automatic approval review. Do not mark this unit complete until CI and device checks pass.
