# MediaPipe Tasks uses Java/JNI bindings and native graph components.
-keep class com.google.mediapipe.** { *; }
# Platform-instantiated entry points; ordinary app classes can be shrunk/obfuscated.
# MediaPipe JNI bindings remain protected above. Validate on a release APK/device.
-keep,allowoptimization class com.airgesture.control.MainActivity { public <init>(); }
-keep,allowoptimization class com.airgesture.control.GestureCaptureService { public <init>(); }
-keep,allowoptimization class com.airgesture.control.AirAccessibilityService { public <init>(); }

# MediaPipe framework contains optional profiler/template references that are
# not packaged by the Tasks dependency used by Aergis. R8 must ignore these
# absent optional classes when producing the minified release APK.
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate
