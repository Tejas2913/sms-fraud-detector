# Proguard rules for SMS Guard
# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Kotlin serialization (for future audit log export)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep entity classes for Room reflection
-keep class com.smsfraud.detector.data.db.entity.** { *; }

# ONNX Runtime Mobile — keep JNI entry points and all runtime classes
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**
