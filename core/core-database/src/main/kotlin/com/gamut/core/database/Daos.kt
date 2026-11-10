package com.gamut.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks WHERE isrc = :isrc")
    suspend fun get(isrc: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE isrc IN (:isrcs)")
    suspend fun getByIsrcs(isrcs: List<String>): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY title")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("DELETE FROM tracks WHERE isrc = :isrc")
    suspend fun delete(isrc: String)
}

@Dao
interface ResolvedSourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: ResolvedSourceEntity)

    @Query("SELECT * FROM resolved_sources WHERE isrc = :isrc")
    suspend fun get(isrc: String): ResolvedSourceEntity?

    @Query("DELETE FROM resolved_sources WHERE isrc = :isrc")
    suspend fun delete(isrc: String)
}

@Dao
interface PlaylistDao {
    @Insert suspend fun insert(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists")
    suspend fun getAll(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): PlaylistEntity?

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE playlists SET name = :name, tags = :tags, coverUri = :coverUri WHERE id = :id")
    suspend fun update(id: Long, name: String, tags: String, coverUri: String?)

    @Query("UPDATE playlists SET name = :name, tags = :tags WHERE id = :id")
    suspend fun updateMeta(id: Long, name: String, tags: String)

    @Query("UPDATE playlists SET remoteId = :remoteId WHERE id = :id")
    suspend fun setRemoteId(id: Long, remoteId: String)

    @Query("UPDATE playlists SET remoteId = NULL")
    suspend fun clearRemoteIds()
}

@Dao
abstract class PlaylistTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(ref: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlisttrackcrossref WHERE playlistId = :playlistId AND isrc = :isrc")
    abstract suspend fun remove(playlistId: Long, isrc: String)

    @Query("SELECT t.* FROM tracks t INNER JOIN playlisttrackcrossref r ON t.isrc = r.isrc WHERE r.playlistId = :playlistId ORDER BY r.position")
    abstract fun observeTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Query("SELECT isrc FROM playlisttrackcrossref WHERE playlistId = :playlistId ORDER BY position")
    abstract suspend fun isrcsFor(playlistId: Long): List<String>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlisttrackcrossref WHERE playlistId = :playlistId")
    abstract suspend fun nextPosition(playlistId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM playlisttrackcrossref WHERE playlistId = :playlistId AND isrc = :isrc)")
    abstract suspend fun contains(playlistId: Long, isrc: String): Boolean

    @Query("DELETE FROM playlisttrackcrossref WHERE playlistId = :playlistId")
    abstract suspend fun clear(playlistId: Long)

    @Transaction
    open suspend fun replaceAll(playlistId: Long, isrcs: List<String>) {
        clear(playlistId)
        isrcs.distinct().forEachIndexed { index, isrc -> upsert(PlaylistTrackCrossRef(playlistId, isrc, index)) }
    }
}

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE isrc = :isrc")
    fun observe(isrc: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("DELETE FROM downloads WHERE isrc = :isrc")
    suspend fun delete(isrc: String)
}

@Dao
interface ArtworkCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ArtworkCacheEntity)

    @Query("SELECT * FROM artwork_cache WHERE releaseId = :releaseId")
    suspend fun get(releaseId: String): ArtworkCacheEntity?

    @Query("SELECT * FROM artwork_cache WHERE releaseId IN (:releaseIds)")
    suspend fun getAll(releaseIds: List<String>): List<ArtworkCacheEntity>
}

@Dao
abstract class FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun add(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE isrc = :isrc")
    abstract suspend fun remove(isrc: String)

    @Query("SELECT * FROM favorites WHERE isrc = :isrc")
    abstract suspend fun get(isrc: String): FavoriteEntity?

    @Query("SELECT * FROM favorites")
    abstract suspend fun all(): List<FavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE isrc = :isrc)")
    abstract suspend fun isFavorite(isrc: String): Boolean

    @Query("SELECT isrc FROM favorites")
    abstract fun observeIsrcs(): Flow<List<String>>

    @Query("SELECT t.* FROM tracks t INNER JOIN favorites f ON t.isrc = f.isrc ORDER BY f.addedAtEpochMs DESC")
    abstract fun observeTracks(): Flow<List<TrackEntity>>

    @Transaction
    open suspend fun setOrder(isrcs: List<String>) {
        val base = System.currentTimeMillis()
        isrcs.distinct().forEachIndexed { index, isrc -> add(FavoriteEntity(isrc, base - index)) }
    }
}

@Dao
interface RecentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: RecentEntity)

    @Query("SELECT t.* FROM tracks t INNER JOIN recently_played r ON t.isrc = r.isrc ORDER BY r.playedAtEpochMs DESC LIMIT :limit")
    fun observeTracks(limit: Int): Flow<List<TrackEntity>>

    @Query("DELETE FROM recently_played WHERE isrc NOT IN (SELECT isrc FROM recently_played ORDER BY playedAtEpochMs DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM recently_played")
    suspend fun clear()
}

@Dao
interface PendingSyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: PendingSyncEntity)

    @Query("SELECT * FROM pending_sync ORDER BY id")
    suspend fun all(): List<PendingSyncEntity>

    @Query("DELETE FROM pending_sync WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM pending_sync")
    fun observeCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM pending_sync WHERE type = :type AND itemKey = :key)")
    suspend fun has(type: String, key: String): Boolean

    @Query("DELETE FROM pending_sync WHERE type = :type AND itemKey = :key")
    suspend fun delete(type: String, key: String)

    @Query("DELETE FROM pending_sync WHERE type = :type AND itemKey LIKE :prefix")
    suspend fun deleteByPrefix(type: String, prefix: String)
}

@Dao
interface SyncStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: SyncStateEntity)

    @Query("SELECT value FROM sync_state WHERE name = :name")
    suspend fun get(name: String): String?
}

@Dao
interface FollowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(follow: FollowEntity)

    @Query("DELETE FROM artist_follows WHERE artistKey = :key")
    suspend fun remove(key: String)

    @Query("SELECT * FROM artist_follows WHERE artistKey = :key")
    suspend fun get(key: String): FollowEntity?

    @Query("SELECT * FROM artist_follows")
    suspend fun all(): List<FollowEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM artist_follows WHERE artistKey = :key)")
    suspend fun isFollowing(key: String): Boolean

    @Query("SELECT artistKey FROM artist_follows")
    fun observeKeys(): Flow<List<String>>

    @Query("SELECT * FROM artist_follows ORDER BY followedAtEpochMs DESC")
    fun observeAll(): Flow<List<FollowEntity>>
}
