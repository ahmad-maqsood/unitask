# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Preserve line numbers and source file names for crash stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# -----------------------------------------------------------------------------
# 1. kotlinx.serialization
# -----------------------------------------------------------------------------
-keepclassmembers class * {
    public static final ** Companion;
}
-keepclasseswithmembers class * {
    ** $$serializer;
}
-keepclassmembers class * {
    ** $$serializer;
}
-keep @kotlinx.serialization.Serializable class * {
    <fields>;
}
-keepclassmembers @kotlinx.serialization.Serializable class * {
    <init>(...);
    *** Companion;
    ** $$serializer;
}
-keep class * implements kotlinx.serialization.KSerializer {
    <init>(...);
}

# -----------------------------------------------------------------------------
# 2. Supabase row DTOs & Payloads
# -----------------------------------------------------------------------------
# Keep all @Serializable classes used with Postgrest, Realtime, and Auth.
# This covers the row classes in data/groups (GroupRows.kt, GroupModels.kt,
# GroupCacheRepository.kt) and Announcement. The rest of data/groups, such as
# GroupRepository, is ordinary code that nothing looks up by name, so R8 can shrink it.
-keep @kotlinx.serialization.Serializable class com.example.** { *; }

# Supabase & Ktor optional runtime warnings
-dontwarn io.ktor.**
-dontwarn io.github.jan.supabase.**

# -----------------------------------------------------------------------------
# 3. Room Entities and DAOs
# -----------------------------------------------------------------------------
# Keep entity field names to ensure column mappings and raw queries don't break
-keep @androidx.room.Entity class * {
    <fields>;
}
-keep class com.example.data.local.Course { *; }
-keep class com.example.data.local.TaskItem { *; }
-keep class com.example.data.local.TaskAttachment { *; }
-keep class com.example.data.local.LectureSlot { *; }
-keep class com.example.data.local.GroupCacheEntity { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# -----------------------------------------------------------------------------
# 4. WorkManager Workers
# -----------------------------------------------------------------------------
# Workers are instantiated reflectively by WorkManager's WorkerFactory
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.example.sync.PeriodicSyncWorker { *; }


# -----------------------------------------------------------------------------
# 5. BroadcastReceivers
# -----------------------------------------------------------------------------
# Receivers instantiated reflectively by Android OS when alarms/intents fire
-keep class * extends android.content.BroadcastReceiver {
    public <init>();
}
-keep class com.example.reminder.BootReceiver { *; }
-keep class com.example.reminder.ClassStartReceiver { *; }
-keep class com.example.reminder.DailySummaryReceiver { *; }
-keep class com.example.reminder.RepeatRolloverReceiver { *; }
-keep class com.example.reminder.TaskReminderReceiver { *; }

# -----------------------------------------------------------------------------
# 6. ZXing & Google Code Scanner
# -----------------------------------------------------------------------------
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-dontwarn com.google.android.gms.code.scanner.**

# -----------------------------------------------------------------------------
# 7. Remove verbose, debug and info logs from release builds
# -----------------------------------------------------------------------------
# Log.w and Log.e are kept on purpose, so real problems still show up.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}