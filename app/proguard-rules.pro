# قواعد حماية Firebase
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.firebase.** { *; }

# قواعد حماية Compose
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
