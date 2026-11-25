package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        WatchedFolderEntity::class,
        ListeningHistoryEntity::class,
        ExcludedFolderEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class PhotonDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun watchedFolderDao(): WatchedFolderDao
    abstract fun listeningHistoryDao(): ListeningHistoryDao
    abstract fun excludedFolderDao(): ExcludedFolderDao

    companion object {
        @Volatile
        private var INSTANCE: PhotonDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `listening_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `trackId` INTEGER NOT NULL,
                        `genre` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `playedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_listening_history_trackId` ON `listening_history` (`trackId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_listening_history_playedAt` ON `listening_history` (`playedAt`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `excluded_folders` (
                        `identifier` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `isUriTree` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`identifier`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `trackNumber` INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `composer` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `customGainDb` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `preferredEqPreset` TEXT DEFAULT NULL")
            }
        }

        fun getInstance(context: Context): PhotonDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PhotonDatabase::class.java,
                    "photon_audio.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
