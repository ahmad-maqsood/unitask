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
# Keep all @Serializable classes used with Postgrest, Realtime, and Auth
-keep @kotlinx.serialization.Serializable class com.example.** { *; }
-keep class com.example.sync.CourseRow { *; }
-keep class com.example.sync.TaskRow { *; }
-keep class com.example.sync.SlotRow { *; }
-keep class com.example.data.groups.** { *; }
-keep class com.example.util.Announcement { *; }
-keep class com.example.util.AppFeature { *; }

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
-keep class com.example.sync.SyncWorker { *; }

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
