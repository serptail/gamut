package com.gamut.feature.search

import com.gamut.core.common.TextMatch
import com.gamut.core.model.Track
import kotlin.math.log10
import kotlin.math.min

data class ArtistSignal(val query: String, val name: String?, val fans: Long?)

internal data class Candidate(
    val track: Track,
    val popularity: Double,
    val position: Int,
    val penalty: Double = 0.0,
    val boost: Double = 0.0,
)

internal object SearchRanking {
    const val ARTIST_SHOW_THRESHOLD = 0.35

    const val ARTIST_BOOST_THRESHOLD = 0.60

    const val MAX_RESULTS = 50

    fun nameMatch(query: String, artistName: String): Double {
        val q = TextMatch.norm(query)
        val n = TextMatch.norm(artistName)
        if (q.isEmpty() || n.isEmpty()) return 0.0
        if (q == n) return 1.0
        if (q.length < 3) return 0.0
        val qt = q.split(' ')
        val nt = n.split(' ')
        if (qt.size > nt.size) return 0.0
        for (i in 0 until qt.lastIndex) if (qt[i] != nt[i]) return 0.0
        if (!nt[qt.lastIndex].startsWith(qt.last())) return 0.0
        return if (nt.size == qt.size) 0.85 else 0.70
    }

    fun popularity(fans: Long?): Double {
        if (fans == null || fans <= 0) return 0.0
        return ((log10(fans.toDouble()) - 3.0) / 3.0).coerceIn(0.0, 1.0)
    }

    fun artistEvidence(artistName: String, tracks: List<Track>): Double {
        val n = TextMatch.norm(artistName)
        val hits = tracks.take(12).count { TextMatch.primaryArtist(it.artist) == n }
        return min(1.0, hits / 3.0)
    }

    private fun songCollision(query: String, artistName: String, tracks: List<Track>): Boolean {
        val q = TextMatch.norm(query)
        val n = TextMatch.norm(artistName)
        return tracks.take(10).any { TextMatch.norm(it.title) == q && TextMatch.primaryArtist(it.artist) != n }
    }

    fun artistConfidence(query: String, artistName: String, fans: Long?, tracks: List<Track>): Double {
        val name = nameMatch(query, artistName)
        if (name == 0.0) return 0.0
        val pop = popularity(fans)
        val length = TextMatch.norm(query).replace(" ", "").length
        val minPopularity = when {
            length <= 2 -> 0.85
            length == 3 -> 0.55
            else -> 0.0
        }
        if (pop < minPopularity) return 0.0
        var score = name * (0.6 * pop + 0.4 * artistEvidence(artistName, tracks))
        if (pop < 0.67 && songCollision(query, artistName, tracks)) score *= 0.5
        return score
    }

    fun showArtistCard(query: String, hitName: String, signal: ArtistSignal?, tracks: List<Track>): Boolean {
        if (signal == null || signal.query != TextMatch.norm(query)) return false
        val sameArtist = signal.name?.let { TextMatch.norm(it) == TextMatch.norm(hitName) } == true
        val fans = if (sameArtist) signal.fans else null
        return artistConfidence(query, hitName, fans, tracks) >= ARTIST_SHOW_THRESHOLD
    }

    fun trackPopularity(rank: Long): Double = ((log10(rank.coerceAtLeast(1).toDouble()) - 3.0) / 3.0).coerceIn(0.0, 1.0)

    fun viewsPopularity(views: Long): Double = ((log10(views.coerceAtLeast(1).toDouble()) - 4.0) / 4.0).coerceIn(0.0, 1.0)

    private fun tokenCoverage(queryTokens: List<String>, haystack: Set<String>): Double {
        if (queryTokens.isEmpty()) return 0.0
        val hits = queryTokens.withIndex().count { (i, token) ->
            token in haystack ||
                (i == queryTokens.lastIndex && token.length >= 3 && haystack.any { it.startsWith(token) })
        }
        return hits.toDouble() / queryTokens.size
    }

    private fun Track.dedupeKey() = TextMatch.norm(title) + "|" + TextMatch.primaryArtist(artist)

    private class Scored(val candidate: Candidate, val coverage: Double, val score: Double)

    fun rank(query: String, candidates: List<Candidate>): List<Track> {
        val q = TextMatch.norm(query)
        val queryTokens = q.split(' ').filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) return candidates.map { it.track }

        val scored = candidates.map { c ->
            val t = c.track
            val title = TextMatch.norm(t.title)
            val artist = TextMatch.primaryArtist(t.artist)
            val titleTokens = TextMatch.toks(t.title)
            val artistTokens = TextMatch.toks(t.artist)
            val all = titleTokens + artistTokens + TextMatch.toks(t.album.orEmpty())

            val coverage = tokenCoverage(queryTokens, all)
            var score = tokenCoverage(queryTokens, titleTokens) * 30.0 +
                tokenCoverage(queryTokens, artistTokens) * 30.0 +
                coverage * 20.0
            if (title == q) score += 40.0
            if (artist == q) score += 35.0
            if (q == "$artist $title" || q == "$title $artist") score += 50.0

            val junk = (all intersect TextMatch.DERIVATIVE) - queryTokens.toSet()
            score -= junk.size * 60.0

            score += c.popularity * 25.0 + 12.0 / (1.0 + 0.2 * c.position) + c.boost - c.penalty
            Scored(c, coverage, score)
        }

        val relevant = scored.filter { it.coverage >= 0.5 }.takeIf { it.size >= 5 } ?: scored

        return relevant
            .sortedByDescending { it.score }
            .distinctBy { it.candidate.track.dedupeKey() }
            .take(MAX_RESULTS)
            .map { it.candidate.track }
    }
}
