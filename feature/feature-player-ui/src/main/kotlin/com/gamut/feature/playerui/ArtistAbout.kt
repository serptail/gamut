package com.gamut.feature.playerui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.MusicNote
import com.gamut.core.model.Track
import com.gamut.feature.playerui.components.PlayerSpinner
import com.gamut.feature.playerui.components.tapTarget
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Immutable
data class ArtistCredit(val name: String, val roles: String)

@Immutable
data class ArtistInfo(
    val name: String,
    val imageUrl: String?,
    val fans: Long?,
    val album: String?,
    val year: String?,
    val credits: List<ArtistCredit>,
)

object ArtistInfoRepository {
    private const val TAG = "ArtistInfo"
    private const val USER_AGENT = "Gamut/1.0 ( contact@your-domain.example )"

    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, ArtistInfo>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArtistInfo>?) = size > 40
        },
    )

    private fun key(track: Track) = track.isrc.orEmpty().ifBlank { "${track.title}|${track.artist}" }

    fun cached(track: Track): ArtistInfo? = cache[key(track)]

    suspend fun fetch(track: Track): ArtistInfo {
        cached(track)?.let { return it }
        val info = withContext(Dispatchers.IO) { load(track) }
        if (info.album != null || info.credits.size > 1) cache[key(track)] = info
        return info
    }

    private val artistSplit = Regex("""\s*(?:,|;|/|&|\band\b|\bx\b|\bwith\b|\bfeat\.?|\bft\.?|\bfeaturing\b)\s*""", RegexOption.IGNORE_CASE)
    private fun lead(artist: String) = artist.split(artistSplit).firstOrNull { it.isNotBlank() }?.trim() ?: artist
    private fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    private fun enc(v: String) = URLEncoder.encode(v, "UTF-8")

    private val isrcShape = Regex("^[A-Z]{2}[A-Z0-9]{3}[0-9]{7}$")

    private suspend fun load(track: Track): ArtistInfo {
        val leadName = lead(track.artist)
        val isrc = track.isrc.orEmpty().uppercase().takeIf { isrcShape.matches(it) }
        val coreTitle = track.title.replace(Regex("""\s*[(\[].*?[)\]]"""), "").substringBefore(" - ").trim().ifBlank { track.title }
        var image: String? = null
        var fans: Long? = null
        var album: String? = null
        var year: String? = null
        val roles = LinkedHashMap<String, LinkedHashSet<String>>()
        fun add(name: String?, role: String) {
            val n = name?.trim().orEmpty()
            if (n.isNotEmpty()) roles.getOrPut(n) { LinkedHashSet() }.add(role)
        }
        fun realPicture(url: String?) = url?.takeIf { it.isNotBlank() && !it.contains("/artist//") }

        var deezer: JSONObject? = null
        if (isrc != null) deezer = json("https://api.deezer.com/track/isrc:$isrc")?.takeIf { !it.has("error") }
        if (deezer == null) {
            val wantTitle = norm(coreTitle)
            val wantArtist = norm(leadName)
            for (q in listOf("artist:\"$leadName\" track:\"$coreTitle\"", "$leadName $coreTitle")) {
                val hits = json("https://api.deezer.com/search?q=${enc(q)}&limit=5")?.optJSONArray("data") ?: continue
                var id = 0L
                for (i in 0 until hits.length()) {
                    val h = hits.optJSONObject(i) ?: continue
                    val t = norm(h.optString("title_short").ifBlank { h.optString("title") })
                    val a = norm(h.optJSONObject("artist")?.optString("name").orEmpty())
                    if ((t == wantTitle || t.startsWith(wantTitle)) && a.isNotEmpty() && (a.contains(wantArtist) || wantArtist.contains(a))) {
                        id = h.optLong("id")
                        break
                    }
                }
                if (id > 0) deezer = json("https://api.deezer.com/track/$id")?.takeIf { !it.has("error") }
                if (deezer != null) break
            }
        }
        var realIsrc: String? = isrc
        deezer?.let { o ->
            image = realPicture(o.optJSONObject("artist")?.optString("picture_xl"))
            album = o.optJSONObject("album")?.optString("title")?.takeIf { it.isNotBlank() }
            year = o.optString("release_date").take(4).takeIf { it.length == 4 }
            o.optString("isrc").uppercase().takeIf { isrcShape.matches(it) }?.let { realIsrc = it }
            o.optJSONArray("contributors")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val c = arr.optJSONObject(i) ?: continue
                    add(c.optString("name"), if (c.optString("role").equals("Main", true)) "Main artist" else "Featured artist")
                }
            }
        }
        json("https://api.deezer.com/search/artist?q=${enc(leadName)}&limit=1")
            ?.optJSONArray("data")?.optJSONObject(0)?.let { a ->
                val n = norm(a.optString("name"))
                if (n == norm(leadName) || n.contains(norm(leadName))) {
                    if (image == null) image = realPicture(a.optString("picture_xl"))
                    fans = a.optLong("nb_fan", -1L).takeIf { it >= 0 }
                }
            }
        if (roles.isEmpty()) add(leadName, "Main artist")

        var musicBrainz = "skipped"
        runCatching {
            var rec: String? = null
            if (realIsrc != null) {
                rec = json("https://musicbrainz.org/ws/2/recording?query=isrc:$realIsrc&fmt=json&limit=1")
                    ?.optJSONArray("recordings")?.optJSONObject(0)?.optString("id")
            }
            if (rec.isNullOrBlank()) {
                if (realIsrc != null) delay(1_100)
                val query = enc("recording:\"${coreTitle.replace("\"", "")}\" AND artist:\"${leadName.replace("\"", "")}\"")
                rec = json("https://musicbrainz.org/ws/2/recording?query=$query&fmt=json&limit=1")
                    ?.optJSONArray("recordings")?.optJSONObject(0)?.takeIf { it.optInt("score") >= 90 }?.optString("id")
            }
            if (!rec.isNullOrBlank()) {
                musicBrainz = "recording found"
                delay(1_100)
                val rels = json("https://musicbrainz.org/ws/2/recording/$rec?inc=artist-rels+work-rels&fmt=json")?.optJSONArray("relations")
                var workId: String? = null
                if (rels != null) {
                    for (i in 0 until rels.length()) {
                        val r = rels.optJSONObject(i) ?: continue
                        when (r.optString("target-type")) {
                            "artist" -> {
                                val role = when (r.optString("type")) {
                                    "producer" -> "Producer"
                                    "mix" -> "Mixer"
                                    "engineer" -> "Engineer"
                                    "recording" -> "Recording engineer"
                                    "mastering" -> "Mastering engineer"
                                    else -> null
                                }
                                if (role != null) add(r.optJSONObject("artist")?.optString("name"), role)
                            }

                            "work" -> if (workId == null) workId = r.optJSONObject("work")?.optString("id")
                        }
                    }
                }
                if (!workId.isNullOrBlank()) {
                    delay(1_100)
                    json("https://musicbrainz.org/ws/2/work/$workId?inc=artist-rels&fmt=json")?.optJSONArray("relations")?.let { wr ->
                        for (i in 0 until wr.length()) {
                            val r = wr.optJSONObject(i) ?: continue
                            val role = when (r.optString("type")) {
                                "composer" -> "Composer"
                                "lyricist" -> "Lyricist"
                                "writer" -> "Writer"
                                else -> null
                            }
                            if (role != null) add(r.optJSONObject("artist")?.optString("name"), role)
                        }
                    }
                }
            } else {
                musicBrainz = "no recording"
            }
        }
        Log.i(TAG, "\"${track.title}\": deezerTrack=${deezer != null} realIsrc=$realIsrc musicBrainz=$musicBrainz credits=${roles.size} photo=${image != null}")

        fun rank(set: Set<String>) = when {
            set.any { it == "Composer" || it == "Lyricist" || it == "Writer" } -> 0
            set.any { it.contains("Producer") || it.contains("Mixer") || it.contains("ngineer") } -> 1
            else -> 2
        }
        val credits = roles.entries
            .sortedBy { rank(it.value) }
            .take(10)
            .map { ArtistCredit(it.key, it.value.joinToString(", ")) }
        return ArtistInfo(leadName, image, fans, album, year, credits)
    }

    private fun json(url: String): JSONObject? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "GET"
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept", "application/json")
            c.connectTimeout = 6_000
            c.readTimeout = 8_000
            if (c.responseCode == 200) JSONObject(c.inputStream.bufferedReader().use { it.readText() }) else null
        } finally {
            c.disconnect()
        }
    } catch (e: IOException) {
        null
    } catch (e: org.json.JSONException) {
        null
    }
}

@Composable
internal fun ArtistAboutSection(
    artistName: String,
    info: ArtistInfo?,
    loading: Boolean,
    fallbackArtUrl: String?,
    onArtistClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text("About the artist", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val photo = info?.imageUrl ?: fallbackArtUrl
            Box(
                Modifier.size(140.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)).tapTarget(onArtistClick),
                contentAlignment = Alignment.Center,
            ) {
                if (photo != null) {
                    AsyncImage(model = photo, contentDescription = artistName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(PhosphorIcons.Bold.MusicNote, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                info?.name ?: artistName,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.tapTarget(onArtistClick),
            )
            if (loading && info == null) {
                Spacer(Modifier.height(16.dp))
                PlayerSpinner(Modifier.size(20.dp), Color.White.copy(alpha = 0.7f))
            }

            val credits = info?.credits.orEmpty()
            if (credits.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Column(Modifier.fillMaxWidth()) {
                    Text("Credits", color = Color.White, style = MaterialTheme.typography.titleSmall)
                    info?.let { i ->
                        val meta = listOfNotNull(i.album, i.year).joinToString(" \u00B7 ")
                        if (meta.isNotEmpty()) Text(meta, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(10.dp))
                    credits.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(c.name, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(c.roles, color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
