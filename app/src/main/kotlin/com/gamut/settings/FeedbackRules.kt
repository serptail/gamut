package com.gamut.settings

import android.content.Context
import android.util.Patterns
import java.security.MessageDigest

enum class FeedbackType(val label: String) { BUG("Bug"), FEEDBACK("Feedback"), IDEA("Idea") }

enum class MessageProblem { TOO_SHORT, TOO_LONG, TOO_MANY_LINKS, REPETITIVE }

object FeedbackRules {
    const val MIN_WORDS = 4
    const val MAX_WORDS = 250
    const val MAX_CHARS = 2_000
    const val MAX_NAME = 60
    const val MAX_EMAIL = 254
    const val MAX_LINKS = 2

    private val INVISIBLE = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\u2066-\\u2069\\uFEFF]")
    private val CONTROL = Regex("[\\p{Cntrl}&&[^\\n]]")
    private val BLANK_LINES = Regex("\\n{3,}")
    private val LINK = Regex("(?i)(https?://|www\\.)\\S+")
    private val LONG_RUN = Regex("(.)\\1{19,}")

    fun sanitize(input: String, multiline: Boolean): String {
        var s = input.replace("\r\n", "\n").replace('\r', '\n').replace(INVISIBLE, "")
        if (!multiline) s = s.replace('\n', ' ')
        s = s.replace(CONTROL, "")
        if (multiline) s = s.replace(BLANK_LINES, "\n\n")
        return s.trim()
    }

    fun wordCount(s: String): Int {
        var count = 0
        var i = 0
        while (i < s.length) {
            while (i < s.length && s[i].isWhitespace()) i++
            if (i >= s.length) break
            while (i < s.length && !s[i].isWhitespace()) i++
            count++
        }
        return count
    }

    fun limitWords(s: String, max: Int): String {
        var count = 0
        var i = 0
        while (i < s.length) {
            while (i < s.length && s[i].isWhitespace()) i++
            if (i >= s.length) break
            while (i < s.length && !s[i].isWhitespace()) i++
            count++
            if (count == max) return s.substring(0, i)
        }
        return s
    }

    fun isValidEmail(s: String): Boolean = s.length <= MAX_EMAIL && s.none { it.isWhitespace() } && Patterns.EMAIL_ADDRESS.matcher(s).matches()

    fun checkMessage(message: String): MessageProblem? {
        val words = wordCount(message)
        return when {
            words < MIN_WORDS -> MessageProblem.TOO_SHORT
            words > MAX_WORDS || message.length > MAX_CHARS -> MessageProblem.TOO_LONG
            LINK.findAll(message).count() > MAX_LINKS -> MessageProblem.TOO_MANY_LINKS
            LONG_RUN.containsMatchIn(message) -> MessageProblem.REPETITIVE
            words >= 12 && uniqueRatio(message) < 0.3 -> MessageProblem.REPETITIVE
            else -> null
        }
    }

    private fun uniqueRatio(message: String): Float {
        val words = message.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return if (words.isEmpty()) 1f else words.toSet().size.toFloat() / words.size
    }

    fun fingerprint(message: String): String {
        val normal = message.lowercase().replace(Regex("\\s+"), " ").trim()
        return MessageDigest.getInstance("SHA-256").digest(normal.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
    }
}

sealed interface SendBlock {
    data class Cooldown(val seconds: Int) : SendBlock
    object DailyLimit : SendBlock
    object Duplicate : SendBlock
}

class SubmissionGuard(context: Context) {
    private val prefs = context.getSharedPreferences("feedback_guard", Context.MODE_PRIVATE)

    fun check(fingerprint: String, now: Long = System.currentTimeMillis()): SendBlock? {
        val recent = recentTimes(now)
        if (recent.size >= DAILY_LIMIT) return SendBlock.DailyLimit
        val last = recent.maxOrNull()
        if (last != null && now - last < COOLDOWN_MS) return SendBlock.Cooldown(((COOLDOWN_MS - (now - last)) / 1000 + 1).toInt())
        val lastAt = prefs.getLong("fp_at", 0L)
        if (prefs.getString("fp", null) == fingerprint && now - lastAt in 0 until DAY_MS) return SendBlock.Duplicate
        return null
    }

    fun record(fingerprint: String, now: Long = System.currentTimeMillis()) {
        val times = (recentTimes(now) + now).joinToString(",")
        prefs.edit().putString("times", times).putString("fp", fingerprint).putLong("fp_at", now).apply()
    }

    private fun recentTimes(now: Long): List<Long> = prefs.getString("times", "").orEmpty().split(",").mapNotNull { it.toLongOrNull() }.filter { now - it in 0 until DAY_MS }

    private companion object {
        const val COOLDOWN_MS = 60_000L
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val DAILY_LIMIT = 5
    }
}
