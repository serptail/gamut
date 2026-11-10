package com.gamut.feature.resolver

import com.gamut.core.network.YoutubeCandidate
import com.gamut.core.network.YoutubeMusicSearchSource
import com.gamut.core.network.isValidYoutubeVideoId
import com.gamut.core.player.StreamQuality
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

@Singleton
class NewPipeYoutubeSource @Inject constructor(
    @Named("youtube") private val client: OkHttpClient,
) : YoutubeMusicSearchSource {
    suspend fun audioUrl(videoId: String, quality: StreamQuality = StreamQuality.NORMAL): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            ensureInitialized()
            Result.success(
                StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId").audioStreams.pickFor(quality)
                    ?: error("NewPipe returned no direct audio stream"),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun List<AudioStream>.pickFor(quality: StreamQuality): String? {
        val usable = filter { it.isUrl && it.content.isNotBlank() }
        val ordered = when (quality) {
            StreamQuality.LOW -> usable.sortedWith(
                compareByDescending<AudioStream> { it.format == MediaFormat.M4A }
                    .thenBy { if (it.averageBitrate > 0) it.averageBitrate else Int.MAX_VALUE },
            )

            StreamQuality.NORMAL -> usable.sortedWith(
                compareByDescending<AudioStream> { it.format == MediaFormat.M4A }.thenByDescending { it.averageBitrate },
            )

            else -> usable.sortedByDescending { it.averageBitrate }
        }
        return ordered.firstOrNull()?.content
    }

    suspend fun bestAudioUrl(videoId: String): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            ensureInitialized()
            Result.success(
                StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
                    .audioStreams
                    .asSequence()
                    .filter { it.isUrl }
                    .sortedByDescending { it.averageBitrate }
                    .map { it.content }
                    .firstOrNull { it.isNotBlank() }
                    ?: error("NewPipe returned no direct audio stream"),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchSongs(query: String): List<YoutubeCandidate> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            ensureInitialized()
            val service = NewPipe.getService("YouTube")
            val handler = service.getSearchQHFactory().fromQuery(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
                "",
            )
            SearchInfo.getInfo(service, handler).relatedItems
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { it.toCandidate() }
                .take(25)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun searchBroad(query: String): List<YoutubeCandidate> = emptyList()

    suspend fun warmUp() {
        runCatching { searchSongs("music") }
    }

    private fun StreamInfoItem.toCandidate(): YoutubeCandidate? {
        val videoId = url.substringAfter("v=", missingDelimiterValue = "")
            .substringBefore('&')
            .takeIf(::isValidYoutubeVideoId) ?: return null
        val uploader = uploaderName.orEmpty()
        return YoutubeCandidate(
            videoId = videoId,
            title = name,
            channelName = uploader,
            channelIsTopicChannel = uploader.endsWith("- Topic", ignoreCase = true),
            durationMs = duration.coerceAtLeast(0) * 1_000L,
            viewCount = viewCount.coerceAtLeast(0),
            category = "songs",
            isMusicCatalog = true,
        )
    }

    @Synchronized
    private fun ensureInitialized() {
        if (NewPipe.getDownloader() == null) NewPipe.init(OkHttpDownloader(client))
    }
}

@Singleton
class HybridYoutubeMusicSearchSource @Inject constructor(
    private val newPipe: NewPipeYoutubeSource,
    private val ytDlp: AndroidYtDlpEngine,
) : YoutubeMusicSearchSource {
    override suspend fun searchSongs(query: String): List<YoutubeCandidate> {
        val extracted = newPipe.searchSongs(query)
        return extracted.ifEmpty { ytDlp.searchSongs(query) }
    }

    override suspend fun searchBroad(query: String): List<YoutubeCandidate> = ytDlp.searchBroad(query)
}

private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val headers = Headers.Builder().apply {
            request.headers().forEach { (name, values) -> values.forEach { value -> add(name, value) } }
        }.build()
        val body = request.dataToSend()?.let { okhttp3.RequestBody.create(null, it) }
        val call = OkHttpRequest.Builder()
            .url(request.url())
            .headers(headers)
            .method(request.httpMethod(), body)
            .build()
        client.newCall(call).execute().use { response ->
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string().orEmpty(),
                response.request.url.toString(),
            )
        }
    }
}
