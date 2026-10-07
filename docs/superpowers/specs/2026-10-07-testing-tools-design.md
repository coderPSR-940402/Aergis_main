# Aergis testing mirror and recording
Stephan needs to see where hand detection is failing and export evidence from phone-only tests. Add explicit opt-in testing controls before the next installation.

## Design
Use the existing RGBA analysis frames rather than opening another camera or binding another preview. Show a 200 dp movable accessibility overlay, closeable with X. Fit the whole upright, horizontally mirrored camera image without cropping; draw all returned hand landmarks, highlight index tips, and show pointer feedback. Preview is limited to 8 fps with one pending frame.

Record every analyzed frame's timestamps, camera geometry, all raw/upright landmarks and model scores, selected pointer hand/ownership, filter and mapped pointer, calibration, motion/safety, pose and cumulative vision/action outcomes. Include failures and no-hand frames. JSONL is streamed off the vision thread; sampled camera JPEGs (maximum 2 fps, 320 px long edge) accompany it. This is not full-rate video. Count any recorder backlog drops explicitly. Stop automatically exports PDF and ZIP; stopping capture also finishes the recording. No upload occurs.

Bound recording to 10 minutes or 18,000 records. Queue at most 32 frame writes; never block tracking for disk. Retain the newest five sessions. PDF includes duration, coverage/reason counts, frame gaps, inference latency, pointer coverage heatmap, configuration and recording limits. ZIP contains PDF, metadata, summary, JSONL and camera samples. Errors preserve logs and offer retry. Android FileProvider supports sharing; CreateDocument supports saving PDF/ZIP anywhere chosen by the user.

## Verification
Regression-first tests prove image rotation/mirroring and padded RGBA rows, aspect-fit geometry, no-hand recording, bounded recording and complete PDF/ZIP export. Full CI, lint, debug/release APK verification and CodeQL are required. Render a synthetic test PDF for layout inspection; physical dragging and camera performance remain phone checks.
