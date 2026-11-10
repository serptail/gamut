package com.gamut.core.network

import android.app.Application
import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ShazamConfig(
    val endpoint: String = DEFAULT_ENDPOINT,
    val platform: String = "IPHONE",
    val appVersion: String = "14.1.0",
    val userAgent: String = "Dalvik/2.1.0 (Linux; U; Android 13; Pixel 6 Build/TQ3A.230901.001)",
    val timezone: String = "Europe/Paris",
    val language: String = "en-US",
    val headers: Map<String, String> = emptyMap(),
) {
    companion object {
        const val DEFAULT_ENDPOINT = "https://amp.shazam.com/discovery/v5/en-US/GB/android/-/tag/{uuid1}/{uuid2}" +
            "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true" +
            "&hubv5minorversion=v5.1&hidelb=true&video=v3"

        fun parse(text: String): ShazamConfig? = try {
            val json = JSONObject(text)
            val d = ShazamConfig()
            fun field(name: String, fallback: String): String = if (json.isNull(name)) fallback else json.optString(name).takeIf { it.isNotBlank() } ?: fallback

            val endpoint = field("endpoint", d.endpoint)
            if (!isShazamUrl(endpoint)) {
                Log.w(TAG, "config ignored: endpoint is not an https shazam.com address")
                null
            } else {
                val headers = buildMap {
                    json.optJSONObject("headers")?.let { h ->
                        for (name in h.keys()) if (name.isNotBlank()) put(name, h.optString(name))
                    }
                }
                ShazamConfig(
                    endpoint = endpoint,
                    platform = field("platform", d.platform),
                    appVersion = field("appVersion", d.appVersion),
                    userAgent = field("userAgent", d.userAgent),
                    timezone = field("timezone", d.timezone),
                    language = field("language", d.language),
                    headers = headers,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "config ignored: ${e.message}")
            null
        }

        private fun isShazamUrl(endpoint: String): Boolean = try {
            val uri = URI(endpoint.replace("{uuid1}", "x").replace("{uuid2}", "x"))
            val host = uri.host.orEmpty()
            uri.scheme == "https" && (host == "shazam.com" || host.endsWith(".shazam.com"))
        } catch (e: Exception) {
            false
        }
    }
}

private const val TAG = "ShazamConfig"

@Singleton
class ShazamConfigStore @Inject constructor(app: Application) {
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Mutex()

    @Volatile
    private var config: ShazamConfig = prefs.getString(K_JSON, null)?.let(ShazamConfig::parse) ?: ShazamConfig()

    fun current(): ShazamConfig = config

    suspend fun refreshIfDue() {
        if (System.currentTimeMillis() - prefs.getLong(K_FETCHED, 0L) < CHECK_INTERVAL_MS) return
        fetch(RETRY_GAP_MS)
    }

    suspend fun refreshNow(): Boolean = fetch(RECOVER_GAP_MS)

    private suspend fun fetch(minGapMs: Long): Boolean = lock.withLock {
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(K_ATTEMPT, 0L) < minGapMs) return@withLock false
        prefs.edit().putLong(K_ATTEMPT, now).apply()
        val text = withContext(Dispatchers.IO) { download() } ?: return@withLock false
        val parsed = ShazamConfig.parse(text) ?: return@withLock false
        val changed = parsed != config
        config = parsed
        prefs.edit().putString(K_JSON, text).putLong(K_FETCHED, System.currentTimeMillis()).apply()
        if (changed) Log.i(TAG, "request config updated")
        changed
    }

    private fun download(): String? = try {
        val connection = URL(CONFIG_URL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Cache-Control", "no-cache")
            if (connection.responseCode == 200) connection.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val CONFIG_URL = "https://raw.githubusercontent.com/serptail/test-releases/main/shazam-config.json"
        const val PREFS = "shazam_config"
        const val K_JSON = "json"
        const val K_FETCHED = "fetched_ms"
        const val K_ATTEMPT = "attempt_ms"
        const val CHECK_INTERVAL_MS = 6 * 3_600_000L
        const val RETRY_GAP_MS = 30 * 60_000L
        const val RECOVER_GAP_MS = 10 * 60_000L
        const val TIMEOUT_MS = 3_000
    }
}
