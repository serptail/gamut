package com.gamut.feature.resolver

import com.gamut.core.common.TextMatch
import com.gamut.core.model.ResolutionMethod
import com.gamut.core.model.ResolvedSource
import com.gamut.core.model.Track
import com.gamut.core.network.YoutubeCandidate
import javax.inject.Inject

class CandidateScorer @Inject constructor() {
    data class Scored(val candidate: YoutubeCandidate, val score: Double)

    fun rank(candidates: List<YoutubeCandidate>, track: Track): List<Scored> = candidates.map { Scored(it, score(it, track)) }.sortedByDescending { it.score }

    companion object {
        const val ACCEPT = 0.60
        const val LOW_CONFIDENCE = 0.45
    }

    fun selectBest(candidates: List<YoutubeCandidate>, track: Track): ResolvedSource? {
        val best = rank(candidates, track).firstOrNull()?.takeIf { it.score >= LOW_CONFIDENCE } ?: return null
        val method = if (best.candidate.isMusicCatalog || best.candidate.channelIsTopicChannel) ResolutionMethod.TOPIC_CHANNEL else ResolutionMethod.HARD_FILTER
        return ResolvedSource(track.isrc, best.candidate.videoId, method, best.score.toFloat().coerceIn(0f, 1f), 0)
    }

    private fun score(candidate: YoutubeCandidate, track: Track): Double {
        val title = TextMatch.toks(track.title)
        val artist = TextMatch.toks(track.artist)
        val candidateTitle = TextMatch.toks(candidate.title)
        val channel = TextMatch.toks(candidate.channelName.removeSuffix(" - Topic"))
        val coverage = title.count { it in candidateTitle }.toDouble() / title.size.coerceAtLeast(1)
        val artistHit = artist.any { it in candidateTitle || it in channel }
        val junk = (candidateTitle - title - artist).size.coerceAtMost(6)
        var score = 0.55 * coverage + if (artistHit) 0.25 else 0.0
        if (!artistHit) score -= 0.35
        score -= 0.03 * junk
        if (candidate.channelIsTopicChannel) score += 0.15
        if (candidate.isMusicCatalog) score += 0.10
        if (((candidateTitle intersect TextMatch.DERIVATIVE) - (title + artist)).isNotEmpty()) score -= 0.40
        if (candidate.durationMs > 0L && track.durationMs > 0L) {
            val seconds = kotlin.math.abs(candidate.durationMs - track.durationMs) / 1_000
            score += when {
                seconds <= 3 -> 0.20
                seconds <= 8 -> 0.05
                seconds > 20 -> -0.50
                else -> -0.15
            }
        }
        return score + kotlin.math.ln((candidate.viewCount + 1).toDouble()) / 1_000_000.0
    }
}
