package com.gamut.core.designsystem

fun squareArtUrl(url: String?, px: Int = 800): String? {
    if (url == null) return null
    return try {
        when {
            "mzstatic.com" in url ->
                url.replace(Regex("""/\d{2,4}x\d{2,4}(bb|cc|sr)?(-\d+)?\.(jpg|jpeg|png|webp)$""", RegexOption.IGNORE_CASE)) {
                    "/${px}x${px}bb.${it.groupValues[3]}"
                }

            "dzcdn.net" in url ->
                url.replace(Regex("""/\d{2,4}x\d{2,4}-"""), "/${px}x$px-")

            "googleusercontent.com" in url || "ggpht.com" in url ->
                url.replace(Regex("""=w\d+-h\d+"""), "=w$px-h$px").replace(Regex("""=s\d+"""), "=s$px")

            "ytimg.com" in url || "img.youtube.com" in url ->
                Regex("""^(https?://[^/]*(?:ytimg\.com|img\.youtube\.com))/vi(?:_webp)?/([^/]+)/""").find(url)
                    ?.let { "${it.groupValues[1]}/vi/${it.groupValues[2]}/mqdefault.jpg" } ?: url

            "coverartarchive.org" in url ->
                url.replace(Regex("""-(250|500)(\.\w+)$"""), "-1200$2")

            else -> url
        }
    } catch (_: Exception) {
        url
    }
}
