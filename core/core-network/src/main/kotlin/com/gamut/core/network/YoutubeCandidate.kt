package com.gamut.core.network

private val YoutubeVideoId = Regex("^[A-Za-z0-9_-]{11}$")

data class YoutubeCandidate(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelIsTopicChannel: Boolean,
    val durationMs: Long,
    val viewCount: Long,
    val category: String,
    val isMusicCatalog: Boolean = false,
)

fun isValidYoutubeVideoId(value: String): Boolean = YoutubeVideoId.matches(value)

interface YoutubeMusicSearchSource {
    suspend fun searchSongs(query: String): List<YoutubeCandidate>

    suspend fun searchBroad(query: String): List<YoutubeCandidate>
}
