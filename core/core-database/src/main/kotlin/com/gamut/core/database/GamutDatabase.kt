package com.gamut.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackEntity::class,
        ResolvedSourceEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        DownloadEntity::class,
        ArtworkCacheEntity::class,
        FavoriteEntity::class,
        PendingSyncEntity::class,
        SyncStateEntity::class,
        RecentEntity::class,
        FollowEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class GamutDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun resolvedSourceDao(): ResolvedSourceDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistTrackDao(): PlaylistTrackDao
    abstract fun downloadDao(): DownloadDao
    abstract fun artworkCacheDao(): ArtworkCacheDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun pendingSyncDao(): PendingSyncDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun recentDao(): RecentDao
    abstract fun followDao(): FollowDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        val legacyColumn = listOf("spot", "ifyId").joinToString("")
        database.execSQL("ALTER TABLE tracks RENAME COLUMN $legacyColumn TO musicBrainzId")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE tracks ADD COLUMN source TEXT NOT NULL DEFAULT 'MUSICBRAINZ'")
        database.execSQL("ALTER TABLE tracks ADD COLUMN youtubeVideoId TEXT")
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS artwork_cache (" +
                "releaseId TEXT NOT NULL PRIMARY KEY, artworkUrl TEXT NOT NULL, cachedAtEpochMs INTEGER NOT NULL)",
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS favorites (" +
                "isrc TEXT NOT NULL, addedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(isrc))",
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE playlists ADD COLUMN coverUri TEXT")
        database.execSQL("ALTER TABLE playlists ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE playlists ADD COLUMN remoteId TEXT")
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS pending_sync (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "type TEXT NOT NULL, itemKey TEXT NOT NULL, payload TEXT, createdAtEpochMs INTEGER NOT NULL)",
        )
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pending_sync_type_itemKey ON pending_sync (type, itemKey)")
        database.execSQL("CREATE TABLE IF NOT EXISTS sync_state (name TEXT NOT NULL, value TEXT NOT NULL, PRIMARY KEY(name))")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS recently_played (isrc TEXT NOT NULL, playedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(isrc))")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS artist_follows (artistKey TEXT NOT NULL, name TEXT NOT NULL, " +
                "imageUrl TEXT, followedAtEpochMs INTEGER NOT NULL, PRIMARY KEY(artistKey))",
        )
    }
}
