# ONNX Runtime rules
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Retrofit & OkHttp
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Moshi
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
