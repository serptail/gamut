package com.gamut.core.common

import java.text.Normalizer

object TextMatch {
    private val NOISE = Regex(
        """\((official|lyrics?|audio|video|visualizer|hd|hq)[^)]*\)|\[[^]]*]|official (music )?video""",
        RegexOption.IGNORE_CASE,
    )
    val DERIVATIVE = setOf(
        "remix", "cover", "karaoke", "instrumental", "slowed", "sped", "reverb",
        "nightcore", "8d", "tribute", "live", "lullaby", "mashup", "acoustic",
        "made", "famous", "originally", "performed", "style",
    )

    fun norm(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(NOISE, " ")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun toks(value: String): Set<String> = norm(value)
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .toSet()

    fun primaryArtist(value: String): String = norm(
        value.split(Regex("\\s+(feat\\.?|ft\\.?|featuring|&|x)\\s+|,", RegexOption.IGNORE_CASE)).first(),
    )
}
