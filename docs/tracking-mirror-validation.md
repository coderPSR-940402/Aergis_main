# Tracking mirror and camera lifetime repair

Reference: `baseline-successful-apk` resolved from GitHub on 2026-10-08 to
`4f3147cc68341be456e8c47b5794699edade5f9a`. This records the reference used for
this change; future work must resolve the moving baseline again.

## Cause and repair

`GestureRecognitionEngine` wrapped CameraX's underlying Android image in
`MediaImageBuilder`. Closing the resulting `MPImage` closed that borrowed image
before `TestingTools.onFrame` decoded it. Decode exceptions were discarded and
the mirror kept a black or previous image. This also compromised saved camera
samples. CameraX explicitly requires the application to close the `ImageProxy`,
not its borrowed Android image.

Inference now uses an owned, reusable, packed RGBA buffer copied from the
CameraX output plane. Row padding is removed without changing the plane's
position or limit. The input remains unrotated/unmirrored for MediaPipe; image
processing options rotate inference, and the existing coordinate transform
projects landmarks into the upright mirror. Only the analyzer closes CameraX's
image. Buffer reuse is valid for the existing synchronous VIDEO inference mode;
an asynchronous inference migration must revisit this ownership contract.

The diagnostic decoder reads a rewound duplicate, reports invalid/truncated
planes, and cannot silently retain a previous successful frame after failure.
The mirror marks a frame older than one second as stalled, dims it, and stops
drawing its landmarks. Its periodic redraw detects a stall without requiring
another camera callback.

## Calibration and recording

- Green skeleton: selected pointer hand. Cyan: other detected hands.
- Yellow landmark 8: aiming fingertip. Magenta landmarks 4 and 12: thumb/middle
  pinch endpoints. Numbers identify all landmarks on the selected hand.
- White box: the active calibration reach, or the actual default mapper's 2%
  inset. Unmirrored calibration profiles are reflected into mirror coordinates.
- The footer reports frame age, detected hands, gesture/pointer feedback,
  selected hand/rejection, raw and mapped coordinates, pinch ratio and touch
  phase, cursor position and action eligibility.
- Drag the header, use +/− to expand/shrink, and × to hide. The whole camera
  frame is fitted without cropping. The mirror itself does not arm controls.
- Recording remains explicit and local: all eligible analyzed-frame telemetry
  and camera samples up to 2 fps, not full-rate video. JPEGs now retain up to
  960 pixels on their longest edge at quality 90. Each sample is paired with
  timestamped landmarks/geometry in `frames.jsonl`; raw JPEGs are not annotated.
- Starting a recording no longer deletes any older or failed session. The
  ten-minute/18,000-frame limit and bounded writer queue remain. Users should
  export valuable sessions and manage app storage; uninstalling/clearing app
  data still removes app-private recordings.

## Camera lifecycle

Stopping capture immediately prevents publication and invalidates tracking.
Native recognizer disposal is queued after inference on its own executor,
preserving GPU thread affinity. Repeated/delayed disposal cannot reset a new
session. A provider callback arriving after stop cannot bind a camera or unbind
another session. The service releases only its own analysis use case.

The analysis target rotation follows the default display, including 180-degree
changes. Rotation invalidates pending actions and cancels an active touch.

## Verification and device acceptance

Run:

```sh
./gradlew test :app:lint :app:assembleDebug :app:assembleRelease
python3 tools/test_preview_signing.py
git diff --check
```

Regression coverage includes padded RGBA packing, buffer position independence,
camera/input ownership, diagnostic failure visibility, recording preservation,
JPEG dimensions/export, actual canvas image/landmark alignment, stalled-frame
status, reflected calibration bounds, late-result rejection, orderly native
cleanup and display rotation. `app/build/reports/testing-tools/mirror-render.png`
is a synthetic render test artifact, not a physical-camera screenshot.

Physical-device acceptance remains necessary:

1. Start capture in READY; enable the mirror with no hand visible. Verify a
   moving, correctly colored camera image appears even without recognized hands.
2. Move each hand through all four image corners; verify one horizontal mirror,
   correct rotation, and that landmarks remain attached to the fingers. Cross
   hands and check that the green selected hand follows ownership.
3. Expand the mirror; change reach bounds and verify the white box follows the
   active hand/orientation profile. Aim with 8 and pinch with 4+12. Test tap,
   hold, drag and release after arming control explicitly.
4. Rotate portrait → landscape → portrait and through 180 degrees. Check image,
   landmarks, pointer axes, reach bounds, and reachable mirror controls.
5. Stop/start capture repeatedly, including during a pinch and model startup.
   Verify the old session cannot restore a pointer or interfere with the new one.
6. Interrupt the camera; verify the mirror warns about the stalled feed instead
   of showing frozen landmarks as live. Resume and verify recovery.
7. Record, export ZIP, and open several JPEGs alongside their corresponding
   frame landmarks. Start more than five sessions and verify earlier evidence
   remains. Check frame drops, image failures, inference time and storage usage.
8. Measure pointing error, stationary jitter, false activations, recovery,
   latency, battery and thermal load at several lighting conditions and user
   distances before changing filtering or gesture thresholds. A monocular hand
   scale/pinch ratio does not establish physical distance in centimetres.

Source contracts: [CameraX ImageProxy](https://developer.android.com/reference/androidx/camera/core/ImageProxy#getImage())
and [MediaPipe MediaImageContainer.close](https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/java/com/google/mediapipe/framework/image/MediaImageContainer.java).
