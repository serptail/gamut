package com.gamut.feature.resolver

import android.content.Context
import com.gamut.core.model.Track
import com.gamut.core.network.YoutubeCandidate
import com.gamut.core.network.YoutubeMusicSearchSource
import com.gamut.core.network.isValidYoutubeVideoId
import com.gamut.core.player.StreamQuality
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class YtMusicSearchResultJson(val entries: List<YtMusicEntryJson> = emptyList())

@Serializable
private data class YtMusicEntryJson(
    val id: String = "",
    val title: String = "",
    val uploader: String? = null,
    val channel: String? = null,
    val duration: Double? = null,
    @SerialName("view_count") val viewCount: Long? = null,
)

@Serializable
private data class SearchResultJson(val entries: List<SearchEntryJson> = emptyList())

@Serializable
private data class SearchEntryJson(
    val id: String = "",
    val title: String = "",
    val uploader: String? = null,
    val channel: String? = null,
    val duration: Double? = null,
    @SerialName("view_count") val viewCount: Long? = null,
)

@Singleton
class AndroidYtDlpEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updater: YtDlpUpdater,
) : YoutubeMusicSearchSource {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Volatile private var initialized = false
    private val streamScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val streamCache = ConcurrentHashMap<String, CachedUrl>()
    private val streamInFlight = ConcurrentHashMap<String, Deferred<Result<String>>>()

    override suspend fun searchSongs(query: String): List<YoutubeCandidate> = withContext(Dispatchers.IO) {
        ensureReady()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val filtered = searchMusicUrl("https://music.youtube.com/search?q=$encoded$SONGS_FILTER", true)
        if (filtered.isNotEmpty()) return@withContext filtered

        val musicUrl = searchMusicUrl("https://music.youtube.com/search?q=$encoded", false)
        if (musicUrl.isNotEmpty()) return@withContext musicUrl
        val broad = searchBroad(query)
        if (broad.isNotEmpty()) return@withContext broad

        if (!updater.recover()) return@withContext broad
        val retryFiltered = searchMusicUrl("https://music.youtube.com/search?q=$encoded$SONGS_FILTER", true)
        if (retryFiltered.isNotEmpty()) return@withContext retryFiltered
        val retryMusic = searchMusicUrl("https://music.youtube.com/search?q=$encoded", false)
        if (retryMusic.isNotEmpty()) return@withContext retryMusic
        searchBroad(query)
    }

    private fun searchMusicUrl(url: String, isMusicCatalog: Boolean): List<YoutubeCandidate> {
        val request = YoutubeDLRequest(url)
            .addOption("--flat-playlist")
            .addOption("--dump-single-json")
            .addOption("--skip-download")
            .addOption("--no-warnings")
            .addOption("--playlist-end", "15")
        val output = runCatching { YoutubeDL.getInstance().execute(request).out }.getOrNull() ?: return emptyList()
        return runCatching { json.decodeFromString<YtMusicSearchResultJson>(output) }
            .getOrNull()
            ?.entries
            ?.mapNotNull { it.toCandidate(isMusicCatalog) }
            ?: emptyList()
    }

    override suspend fun searchBroad(query: String): List<YoutubeCandidate> = withContext(Dispatchers.IO) {
        ensureReady()
        val request = YoutubeDLRequest("ytsearch8:$query")
            .addOption("--flat-playlist")
            .addOption("--dump-single-json")
            .addOption("--skip-download")
            .addOption("--no-warnings")
            .addOption("--extractor-args", CLIENT_ARGS)
        val output = runCatching { YoutubeDL.getInstance().execute(request).out }.getOrNull() ?: return@withContext emptyList()
        runCatching { json.decodeFromString<SearchResultJson>(output) }
            .getOrNull()
            ?.entries
            ?.mapNotNull { entry ->
                entry.id.takeIf(::isValidYoutubeVideoId)?.let {
                    YoutubeCandidate(
                        videoId = it,
                        title = entry.title,
                        channelName = entry.channel ?: entry.uploader.orEmpty(),
                        channelIsTopicChannel = isTopicChannel(entry.channel ?: entry.uploader),
                        durationMs = entry.durationMsOrZero(),
                        viewCount = entry.viewCount ?: 0L,
                        category = "songs",
                        isMusicCatalog = false,
                    )
                }
            }
            ?: emptyList()
    }

    suspend fun search(track: Track): List<YoutubeCandidate> {
        val query = "${track.artist} ${track.title}"
        val songs = searchSongs(query)
        if (songs.isNotEmpty()) return songs
        return searchBroad(query)
    }

    fun forget(videoId: String) {
        streamCache.keys.removeIf { it.startsWith("$videoId|") }
    }

    fun forgetAll() {
        streamCache.clear()
    }

    suspend fun audioUrl(videoId: String, quality: StreamQuality = StreamQuality.NORMAL): Result<String> {
        val key = "$videoId|${quality.name}"
        streamCache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let {
            return Result.success(it.url)
        }
        val deferred = streamInFlight.computeIfAbsent(key) {
            streamScope.async {
                resolveWithRetry(videoId, quality).onSuccess { url ->
                    streamCache[key] = CachedUrl(url, expiryOf(url))
                    if (streamCache.size > MAX_STREAM_CACHE_ENTRIES) {
                        streamCache.keys.firstOrNull()?.let(streamCache::remove)
                    }
                }
            }
        }
        return try {
            deferred.await()
        } finally {
            if (deferred.isCompleted) streamInFlight.remove(key, deferred)
        }
    }

    private suspend fun resolveWithRetry(videoId: String, quality: StreamQuality): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            ensureReady()
            withTimeoutOrNull(AUDIO_RESOLVE_TIMEOUT_MS) { resolveAudioUrl(videoId, quality) }
                ?: error("Timed out resolving a playable source for this track")
        }.recoverCatching { error ->
            if (updater.recover()) {
                withTimeoutOrNull(AUDIO_RESOLVE_TIMEOUT_MS) { resolveAudioUrl(videoId, quality) }
                    ?: throw error
            } else {
                throw error
            }
        }
    }

    private fun resolveAudioUrl(videoId: String, quality: StreamQuality): String {
        val request = YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId")
            .addOption("-f", formatFor(quality))
            .addOption("--no-playlist")
            .addOption("--no-warnings")
            .addOption("--extractor-args", CLIENT_ARGS)
            .addOption("--retries", "2")
        return YoutubeDL.getInstance().getInfo(request).url?.takeIf { it.isNotBlank() }
            ?: error("yt-dlp returned no playable audio URL")
    }

    private fun formatFor(quality: StreamQuality): String = when (quality) {
        StreamQuality.LOW -> "worstaudio[ext=m4a]/worstaudio/bestaudio/best"
        StreamQuality.NORMAL -> "bestaudio[ext=m4a]/bestaudio/best"
        else -> "bestaudio/best"
    }

    private fun Throwable.looksLikeBotCheck(): Boolean {
        val text = message.orEmpty()
        return text.contains("Sign in to confirm", ignoreCase = true) ||
            text.contains("429", ignoreCase = true) ||
            text.contains("Too Many Requests", ignoreCase = true)
    }

    private fun YtMusicEntryJson.toCandidate(isMusicCatalog: Boolean): YoutubeCandidate? {
        val videoId = id.takeIf(::isValidYoutubeVideoId) ?: return null
        val channelOrUploader = channel ?: uploader
        return YoutubeCandidate(
            videoId = videoId,
            title = title,
            channelName = channelOrUploader.orEmpty(),
            channelIsTopicChannel = isTopicChannel(channelOrUploader),
            durationMs = durationMsOrZero(),
            viewCount = viewCount ?: 0L,
            category = "songs",
            isMusicCatalog = isMusicCatalog,
        )
    }

    private fun YtMusicEntryJson.durationMsOrZero(): Long = ((duration ?: 0.0) * 1_000).toLong()
    private fun SearchEntryJson.durationMsOrZero(): Long = ((duration ?: 0.0) * 1_000).toLong()

    private fun isTopicChannel(channelName: String?): Boolean = channelName?.trim()?.endsWith("- Topic", ignoreCase = true) == true

    private fun ensureReady() {
        if (!initialized) {
            synchronized(this) {
                if (!initialized) {
                    YoutubeDL.getInstance().init(context)
                    initialized = true
                }
            }
        }
    }

    suspend fun warmUp() = withContext(Dispatchers.IO) {
        ensureReady()
        updater.updateIfDue()
    }

    private fun expiryOf(url: String): Long = Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)
        ?.toLongOrNull()?.let { it * 1_000L - STREAM_EXPIRY_SAFETY_MS }
        ?: (System.currentTimeMillis() + DEFAULT_STREAM_TTL_MS)

    private data class CachedUrl(val url: String, val expiresAtMs: Long)

    private companion object {
        const val SONGS_FILTER = "&sp=EgWKAQIIAWoKEAoQAxAEEAkQBQ%3D%3D"
        const val AUDIO_RESOLVE_TIMEOUT_MS = 12_000L
        const val STREAM_EXPIRY_SAFETY_MS = 120_000L
        const val DEFAULT_STREAM_TTL_MS = 20 * 60_000L
        const val MAX_STREAM_CACHE_ENTRIES = 200
        const val CLIENT_ARGS = "youtube:player_client=ios,android,web_safari"
    }
}
