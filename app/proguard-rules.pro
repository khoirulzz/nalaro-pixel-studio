# Preserve generic type signatures, annotations, and enclosing methods for R8 full mode
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keepattributes *Annotation*

# Keep Kotlin Metadata & Reflection internals
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

# ONNX Runtime rules
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Retrofit & OkHttp (R8 Full Mode safe rules)
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep interface com.nalaro.pixelstudio.data.network.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Moshi & Data Models
-keep class com.nalaro.pixelstudio.data.models.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.Json <methods>;
}
-if @com.squareup.moshi.JsonClass class *
-keepnames class <1>
-if @com.squareup.moshi.JsonClass class *
-keep class <1>JsonAdapter {
    <init>(com.squareup.moshi.Moshi);
}

# Room Database, DAOs, and Entities
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomOpenHelper$Delegate { *; }
-keep class com.nalaro.pixelstudio.data.db.** { *; }
-dontwarn androidx.room.paging.**

# Enums (Room & UI state enums)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# WorkManager CoroutineWorkers
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.nalaro.pixelstudio.data.worker.** { *; }
