package com.gamut.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class TrackSource { MUSICBRAINZ, ITUNES, YOUTUBE }

@Serializable
data class Track(
    val isrc: String,
    val musicBrainzId: String? = null,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long,
    val albumArtUrl: String? = null,
    val source: TrackSource = TrackSource.ITUNES,
    val youtubeVideoId: String? = null,
)

@Serializable
data class ResolvedSource(
    val isrc: String,
    val youtubeVideoId: String,
    val resolutionMethod: ResolutionMethod,
    val confidenceScore: Float,
    val verifiedCount: Int,
)

@Serializable
enum class ResolutionMethod { TOPIC_CHANNEL, HARD_FILTER, MANUAL, DIRECT }

@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val createdAtEpochMs: Long,
    val coverUri: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class DownloadedTrack(
    val isrc: String,
    val localFilePath: String,
    val downloadedAtEpochMs: Long,
    val fileSizeBytes: Long,
)

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Loading : PlaybackState
    data class Playing(val positionMs: Long, val track: Track) : PlaybackState
    data class Paused(val positionMs: Long, val track: Track) : PlaybackState
    data class Error(val message: String) : PlaybackState
}

enum class RepeatMode { OFF, ALL, ONE }

data class QueueState(
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = -1,
)
interface LocalAudioIndex {
    fun pathFor(isrc: String): String?
}
