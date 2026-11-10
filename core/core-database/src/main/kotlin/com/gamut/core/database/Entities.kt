package com.gamut.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val isrc: String,
    val musicBrainzId: String?,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val albumArtUrl: String?,
    val source: String = "MUSICBRAINZ",
    val youtubeVideoId: String? = null,
)

@Entity(tableName = "resolved_sources")
data class ResolvedSourceEntity(
    @PrimaryKey val isrc: String,
    val youtubeVideoId: String,
    val resolutionMethod: String,
    val confidenceScore: Float,
    val verifiedCount: Int,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAtEpochMs: Long,
    val coverUri: String? = null,
    @ColumnInfo(defaultValue = "''") val tags: String = "",
    val remoteId: String? = null,
)

@Entity(primaryKeys = ["playlistId", "isrc"], indices = [Index("isrc")])
data class PlaylistTrackCrossRef(val playlistId: Long, val isrc: String, val position: Int)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val isrc: String,
    val localFilePath: String,
    val downloadedAtEpochMs: Long,
    val fileSizeBytes: Long,
)

@Entity(tableName = "artwork_cache")
data class ArtworkCacheEntity(
    @PrimaryKey val releaseId: String,
    val artworkUrl: String,
    val cachedAtEpochMs: Long,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val isrc: String,
    val addedAtEpochMs: Long,
)

@Entity(tableName = "pending_sync", indices = [Index(value = ["type", "itemKey"], unique = true)])
data class PendingSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val itemKey: String,
    val payload: String? = null,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "recently_played")
data class RecentEntity(@PrimaryKey val isrc: String, val playedAtEpochMs: Long)

@Entity(tableName = "sync_state")
data class SyncStateEntity(@PrimaryKey val name: String, val value: String)

@Entity(tableName = "artist_follows")
data class FollowEntity(
    @PrimaryKey val artistKey: String,
    val name: String,
    val imageUrl: String?,
    val followedAtEpochMs: Long,
)

object SyncTypes {
    const val FAV = "fav"
    const val PL = "pl"
    const val PLS = "pls"
    const val PL_DEL = "pl_del"
    const val FOLLOW = "follow"
}
