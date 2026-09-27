# MediaPipe Tasks uses Java/JNI bindings and native graph components.
-keep class com.google.mediapipe.** { *; }
-keep class com.airgesture.control.** { *; }

# MediaPipe framework contains optional profiler/template references that are
# not packaged by the Tasks dependency used by Aergis. R8 must ignore these
# absent optional classes when producing the minified release APK.
-dontwarn com.google.mediapipe.proto.CalculatorProfileProto$CalculatorProfile
-dontwarn com.google.mediapipe.proto.GraphTemplateProto$CalculatorGraphTemplate
