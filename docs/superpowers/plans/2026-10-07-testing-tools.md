# Testing Tools Implementation Plan
> Execution: inline, preserving Stephan's no-subagent preference and standing implementation authority.
**Goal:** movable camera/landmark mirror and stop-to-export recording.
**Architecture:** sample the existing analysis image; bounded asynchronous recorder; Android PDF and FileProvider export.
**Tech Stack:** Kotlin, CameraX RGBA, Android Canvas/PdfDocument, Compose, Robolectric.
**Spec:** ../specs/2026-10-07-testing-tools-design.md

## Global Constraints
No new camera permission or second camera session. Preview 8 fps; camera samples 2 fps / 320 px. Maximum 10 minutes / 18,000 records / 32 queued writes. Keep five sessions; exports remain local until user shares.

## Review Focus
- No-hand and inference-failure records must survive.
- Camera rotation, mirrored landmarks and padded rows must align.
- Stop must wait for admitted records; later frames cannot enter that session.
- Overload must not block pointer tracking and must be reported.
- Export errors must retain data for retry; overlay detaches with accessibility.

## Task 1: Data, image geometry and export
- [ ] Write failing tests for DiagnosticSession(File,JSONObject,Int), append(JSONObject,ByteArray?), finish(Long), DiagnosticCameraImage.decodeRgba and MirrorGeometry.imageRect.
- [ ] Run CI and verify missing feature failures.
- [ ] Implement streamed JSONL, summaries, heatmap PDF and ZIP. Test limits, empty/no-hand sessions and padded image rows.
- [ ] Run tests and inspect synthetic PDF rendering.

## Task 2: Camera mirror and recorder controls
- [ ] Connect diagnostic capture in analyze finally, including errors and missing results.
- [ ] Add TestingTools state, bounded writer, stop/export/retry and retained-session selection.
- [ ] Add TrackingMirrorOverlay to accessibility lifecycle; drag header, X hide, aspect fit skeleton.
- [ ] Add testing Compose card with show/hide, start/stop, Save PDF, Save ZIP and Share.
- [ ] Add FileProvider and workflow upload of synthetic QA report.

## Task 3: Verification and delivery
- [ ] Review lifecycle, timestamps, frame ownership and local data sharing inline.
- [ ] Verify unit tests, lint, CodeQL and both APK assemblies.
- [ ] Merge after checks pass; verify main CI and automatic baseline advancement; deliver APK link.
