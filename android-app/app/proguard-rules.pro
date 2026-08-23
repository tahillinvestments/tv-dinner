# TV Dinner Proguard / R8 Rules

# OkHttp 4.x
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# Media3 ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

# Gson & Data Models
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.troyh.tvdinner.data.model.** { *; }
-keepclassmembers class com.troyh.tvdinner.data.model.** { *; }
-keep class com.google.gson.** { *; }

# Coil Image Loader
-dontwarn coil.**
-keep class coil.** { *; }

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# AndroidX WebKit & JavaScript Interface
-keep class androidx.webkit.** { *; }
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
