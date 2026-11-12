package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Course::class, TaskItem::class, TaskAttachment::class, LectureSlot::class, GroupCacheEntity::class, Note::class, NoteFolder::class],
    version = 17,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun taskDao(): TaskDao
    abstract fun taskAttachmentDao(): TaskAttachmentDao
    abstract fun lectureSlotDao(): LectureSlotDao
    abstract fun groupCacheDao(): GroupCacheDao
    abstract fun noteDao(): NoteDao

    companion object {
        /** Versions that were never released to users. Their data may be reset on upgrade. */
        val PRE_RELEASE_VERSIONS = intArrayOf(1, 2)

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lecture_slots ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `task_attachments` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `taskId` INTEGER NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `relativePath` TEXT NOT NULL,
                        `fileSize` INTEGER NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_task_attachments_taskId` ON `task_attachments` (`taskId`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeatRule TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `group_cache` (
                        `userId` TEXT NOT NULL,
                        `groupId` TEXT NOT NULL,
                        `groupName` TEXT NOT NULL,
                        `tasksJson` TEXT NOT NULL,
                        `doneJson` TEXT NOT NULL,
                        `coursesJson` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`userId`, `groupId`)
                    )
                    """.trimIndent()
                )
            }
        }
        
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN completedAtMillis INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeatAnchorDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN autoDeleteWhenDone INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, courseId INTEGER, isPinned INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, isDeleted INTEGER NOT NULL, deletedAt INTEGER NOT NULL, isSynced INTEGER NOT NULL, cloudId TEXT NOT NULL, cloudUpdatedAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_courseId ON notes (courseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_updatedAt ON notes (updatedAt)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE lecture_slots ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN archivedSemester TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN keepScreenOn INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note_folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE notes ADD COLUMN folderId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_folderId` ON `notes` (`folderId`)")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE note_folders ADD COLUMN colorHex TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Every migration, in one place. getDatabase() and the migration test both use this list. */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17
        )
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "unitask_database"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    // Only the pre-release versions may ever be wiped. AppDatabaseSafetyTest fails if a
                    // released version is added here or if a migration is missing.
                    .fallbackToDestructiveMigrationFrom(*PRE_RELEASE_VERSIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
