package com.gamut.core.network

import android.util.Log
import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONObject

sealed interface ShazamResult {
    data class Match(val track: Track, val shazamUrl: String?) : ShazamResult
    data object NoMatch : ShazamResult
    data class Failure(val message: String) : ShazamResult
}

@Singleton
class ShazamClient @Inject constructor(private val configs: ShazamConfigStore) {
    private class Sent(val result: ShazamResult, val configProblem: Boolean = false)

    suspend fun recognize(pcm: ByteArray): ShazamResult = withContext(Dispatchers.IO) {
        val signature = try {
            ShazamSignature.fromPcm16(pcm)
        } catch (e: Exception) {
            return@withContext ShazamResult.Failure("Couldn't analyse the audio")
        }
        configs.refreshIfDue()
        val first = send(signature, configs.current())
        if (first.configProblem && configs.refreshNow()) {
            Log.i(TAG, "retrying with the newer request config")
            return@withContext send(signature, configs.current()).result
        }
        first.result
    }

    private suspend fun send(signature: ShazamSignature.Result, config: ShazamConfig): Sent {
        val body = JSONObject()
            .put("timezone", config.timezone)
            .put("signature", JSONObject().put("uri", signature.uri).put("samplems", signature.sampleMs))
            .put("timestamp", System.currentTimeMillis())
            .put("context", JSONObject())
            .put("geolocation", JSONObject())
            .toString()
            .toByteArray()

        val url = config.endpoint
            .replace("{uuid1}", UUID.randomUUID().toString().uppercase())
            .replace("{uuid2}", UUID.randomUUID().toString().uppercase())

        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return Sent(ShazamResult.Failure("Recognition failed"), configProblem = true)
        }
        val onCancel = currentCoroutineContext().job.invokeOnCompletion { cause -> if (cause != null) connection.disconnect() }
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Shazam-Platform", config.platform)
            connection.setRequestProperty("X-Shazam-AppVersion", config.appVersion)
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Accept-Language", config.language)
            connection.setRequestProperty("User-Agent", config.userAgent)
            config.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }

            when (val code = connection.responseCode) {
                200 -> Sent(parse(connection.inputStream.bufferedReader().use { it.readText() }))

                204 -> Sent(ShazamResult.NoMatch)

                429 -> Sent(ShazamResult.Failure("Too many tries, wait a moment and try again"))

                else -> {
                    val snippet = connection.errorStream?.bufferedReader()?.use { it.readText().take(200) }.orEmpty()
                    Log.w(TAG, "Shazam answered HTTP $code: $snippet")
                    Sent(ShazamResult.Failure("Shazam had a problem ($code)"), configProblem = true)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Sent(ShazamResult.Failure("Couldn't reach Shazam"))
        } catch (e: Exception) {
            Sent(ShazamResult.Failure("Recognition failed"))
        } finally {
            onCancel.dispose()
            connection.disconnect()
        }
    }

    private suspend fun parse(text: String): ShazamResult {
        if (text.isBlank()) return ShazamResult.NoMatch
        val track = JSONObject(text).optJSONObject("track") ?: return ShazamResult.NoMatch
        val title = track.str("title") ?: return ShazamResult.NoMatch
        val artist = track.str("subtitle").orEmpty()

        var album: String? = null
        track.optJSONArray("sections")?.let { sections ->
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i) ?: continue
                if (section.optString("type") != "SONG") continue
                val metadata = section.optJSONArray("metadata") ?: continue
                for (j in 0 until metadata.length()) {
                    val item = metadata.optJSONObject(j) ?: continue
                    if (item.optString("title") == "Album") album = item.str("text")
                }
            }
        }
        val images = track.optJSONObject("images")
        val cover = images?.str("coverarthq") ?: images?.str("coverart")

        val extra = itunes(title, artist)

        val isrc = track.str("isrc") ?: "shazam:${track.str("key") ?: "$artist-$title".lowercase()}"
        return ShazamResult.Match(
            track = Track(
                isrc = isrc,
                title = title,
                artist = artist,
                album = album ?: extra?.album,
                durationMs = extra?.durationMs ?: 0L,
                albumArtUrl = cover ?: extra?.artworkUrl,
                source = TrackSource.ITUNES,
            ),
            shazamUrl = track.str("url"),
        )
    }

    private class ItunesInfo(val durationMs: Long?, val album: String?, val artworkUrl: String?)

    private suspend fun itunes(title: String, artist: String): ItunesInfo? {
        val wantedTitle = norm(title)
        val wantedArtist = norm(artist)
        if (wantedTitle.isEmpty()) return null
        val results = try {
            val term = URLEncoder.encode("$artist $title", "UTF-8")
            val connection = URL("https://itunes.apple.com/search?term=$term&entity=song&limit=5")
                .openConnection() as HttpURLConnection
            val onCancel = currentCoroutineContext().job.invokeOnCompletion { cause -> if (cause != null) connection.disconnect() }
            try {
                connection.connectTimeout = ITUNES_TIMEOUT_MS
                connection.readTimeout = ITUNES_TIMEOUT_MS
                if (connection.responseCode != 200) return null
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results")
            } finally {
                onCancel.dispose()
                connection.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        } ?: return null

        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val foundTitle = norm(item.optString("trackName"))
            val foundArtist = norm(item.optString("artistName"))
            val sameArtist = wantedArtist.isNotEmpty() && foundArtist.isNotEmpty() &&
                (foundArtist.contains(wantedArtist) || wantedArtist.contains(foundArtist))
            if (foundTitle == wantedTitle && sameArtist) {
                return ItunesInfo(
                    durationMs = item.optLong("trackTimeMillis", 0L).takeIf { it > 0 },
                    album = item.str("collectionName"),
                    artworkUrl = item.str("artworkUrl100")?.replace("100x100bb", "600x600bb"),
                )
            }
        }
        return null
    }

    private fun norm(text: String): String = text.replace(BRACKETS, "").filter { it.isLetterOrDigit() }.lowercase()

    private fun JSONObject.str(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 20_000
        const val ITUNES_TIMEOUT_MS = 2_000
        const val TAG = "ShazamClient"
        val BRACKETS = Regex("[(\\[].*?[)\\]]")
    }
}
