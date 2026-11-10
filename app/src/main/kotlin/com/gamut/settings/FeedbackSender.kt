package com.gamut.settings

import java.io.IOException
import java.io.OutputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.UUID
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class FeedbackPayload(
    val type: FeedbackType,
    val rating: Int,
    val name: String,
    val email: String,
    val message: String,
    val diagnostics: String?,
    val appVersion: String,
    val captchaToken: String,
)

sealed interface SendResult {
    data object Success : SendResult
    data object NoNetwork : SendResult
    data object Timeout : SendResult
    data object RateLimited : SendResult
    data object ServerError : SendResult
    data class Rejected(val code: Int, val reason: String?) : SendResult
    data class Failed(val reason: String) : SendResult
}

object FeedbackSender {
    private const val ENDPOINT = "https://api.web3forms.com/submit"

    private const val ACCESS_KEY = "07a46e14-1d99-4668-8325-bf4080214a89"

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_RESPONSE_BYTES = 64 * 1024
    private const val MAX_ATTEMPTS = 2

    suspend fun send(p: FeedbackPayload): SendResult = withContext(Dispatchers.IO) {
        var last: SendResult = SendResult.Failed("unknown")
        for (attempt in 1..MAX_ATTEMPTS) {
            val (result, retryable) = try {
                post(p) to false
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnknownHostException) {
                SendResult.NoNetwork to true
            } catch (e: ConnectException) {
                SendResult.NoNetwork to true
            } catch (e: SocketTimeoutException) {
                SendResult.Timeout to false
            } catch (e: SSLException) {
                SendResult.Failed("secure connection failed") to false
            } catch (e: IOException) {
                SendResult.Failed(e.javaClass.simpleName) to false
            } catch (e: Exception) {
                SendResult.Failed(e.javaClass.simpleName) to false
            }
            last = result
            if (!retryable || attempt == MAX_ATTEMPTS) break
            delay(1_500)
        }
        last
    }

    private fun post(p: FeedbackPayload): SendResult {
        val boundary = "----gamut${UUID.randomUUID().toString().replace("-", "")}"
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            doOutput = true
            useCaches = false
            setChunkedStreamingMode(0)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }
        try {
            conn.outputStream.use { out ->
                val form = Multipart(out, boundary)
                form.field("access_key", ACCESS_KEY)
                form.field("subject", subjectFor(p))
                form.field("from_name", p.name.ifBlank { "Gamut user" })
                if (p.name.isNotBlank()) form.field("name", p.name)
                if (p.email.isNotBlank()) form.field("email", p.email)
                form.field("message", p.message)
                form.field("type", p.type.label)
                if (p.rating > 0) form.field("rating", "${p.rating}/5")
                form.field("app_version", p.appVersion)
                if (!p.diagnostics.isNullOrBlank()) form.field("diagnostics", p.diagnostics)
                form.field("h-captcha-response", p.captchaToken)
                form.field("botcheck", "")
                form.finish()
            }

            val code = conn.responseCode
            val body = readCapped(if (code in 200..299) conn.inputStream else conn.errorStream)
            val json = runCatching { JSONObject(body) }.getOrNull()
            val ok = json?.optBoolean("success", code in 200..299) ?: (code in 200..299)
            val reason = json?.optString("message")?.takeIf { it.isNotBlank() }
            return when {
                code in 200..299 && ok -> SendResult.Success
                code == 429 -> SendResult.RateLimited
                code in 500..599 -> SendResult.ServerError
                code == 413 -> SendResult.Rejected(code, "too large")
                else -> SendResult.Rejected(code, reason)
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun subjectFor(p: FeedbackPayload): String {
        val parts = buildList {
            add("Gamut")
            add(p.type.label)
            if (p.rating > 0) add("${p.rating}\u2605")
            add(p.message.replace(Regex("\\s+"), " ").take(48).trim())
        }
        return parts.joinToString(" \u00B7 ")
    }

    private fun readCapped(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        stream.use { input ->
            val buf = ByteArray(4096)
            val out = java.io.ByteArrayOutputStream()
            while (out.size() < MAX_RESPONSE_BYTES) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }

    private class Multipart(private val out: OutputStream, private val boundary: String) {
        fun field(name: String, value: String) {
            write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n")
            out.write(value.toByteArray(Charsets.UTF_8))
            write("\r\n")
        }

        fun finish() = write("--$boundary--\r\n")

        private fun write(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
    }
}
