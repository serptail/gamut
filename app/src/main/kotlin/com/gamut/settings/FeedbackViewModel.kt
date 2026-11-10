package com.gamut.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamut.core.player.PlaybackSettings
import com.gamut.core.player.SoundSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class FeedbackStatus { IDLE, SENDING, SENT }

data class FeedbackUi(
    val type: FeedbackType = FeedbackType.BUG,
    val rating: Int = 0,
    val message: String = "",
    val name: String = "",
    val email: String = "",
    val attachDiagnostics: Boolean = true,
    val status: FeedbackStatus = FeedbackStatus.IDLE,
    val error: String? = null,
    val emailError: Boolean = false,
) {
    val words: Int get() = FeedbackRules.wordCount(message)
}

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    private val telemetry: TelemetryCollector,
    private val playback: PlaybackSettings,
) : ViewModel() {
    private val sound: SoundSettings = SoundSettings.get(app)
    private val guard = SubmissionGuard(app)

    private val _ui = MutableStateFlow(FeedbackUi())
    val ui: StateFlow<FeedbackUi> = _ui.asStateFlow()

    private var contactTouched = false

    fun setType(t: FeedbackType) = _ui.update {
        it.copy(type = t, error = null, attachDiagnostics = if (t == FeedbackType.BUG) true else it.attachDiagnostics)
    }

    fun setRating(r: Int) = _ui.update { it.copy(rating = if (it.rating == r) 0 else r.coerceIn(0, 5)) }

    fun setMessage(raw: String) {
        var s = raw
        if (s.length > FeedbackRules.MAX_CHARS) s = s.take(FeedbackRules.MAX_CHARS)
        s = FeedbackRules.limitWords(s, FeedbackRules.MAX_WORDS)
        _ui.update { it.copy(message = s, error = null) }
    }

    fun setName(raw: String) {
        contactTouched = true
        _ui.update { it.copy(name = raw.replace('\n', ' ').take(FeedbackRules.MAX_NAME)) }
    }

    fun setEmail(raw: String) {
        contactTouched = true
        _ui.update { it.copy(email = raw.filterNot { c -> c.isWhitespace() }.take(FeedbackRules.MAX_EMAIL), emailError = false) }
    }

    fun prefill(name: String, email: String) {
        if (contactTouched) return
        _ui.update { if (it.name.isBlank() && it.email.isBlank()) it.copy(name = name.take(FeedbackRules.MAX_NAME), email = email) else it }
    }

    fun setAttachDiagnostics(v: Boolean) = _ui.update { it.copy(attachDiagnostics = v) }

    suspend fun diagnosticsPreview(): String = withContext(Dispatchers.IO) { telemetry.snapshot(audioExtras()) }

    private fun audioExtras(): Map<String, String> {
        val s = playback.state.value
        val n = sound.state.value
        return linkedMapOf(
            "stream_quality" to s.streamQuality.label,
            "preload" to "${s.preload} (wifi only: ${s.preloadUnmeteredOnly})",
            "crossfade_ms" to s.crossfadeMs.toString(),
            "fade_ms" to s.fadeMs.toString(),
            "skip_silence" to s.skipSilence.toString(),
            "pause_on_unplug" to s.pauseOnUnplug.toString(),
            "mono" to s.mono.toString(),
            "balance" to s.balance.toString(),
            "eq" to "${n.eqEnabled} (${n.preset})",
            "auto_headroom" to n.autoHeadroom.toString(),
            "limiter" to n.limiter.toString(),
            "dither" to n.dither.toString(),
            "crossfeed" to n.crossfeed.label,
            "stereo_width" to "${n.widthPercent}%",
        )
    }

    fun send(captcha: suspend () -> Result<String>) {
        val u = _ui.value
        if (u.status != FeedbackStatus.IDLE) return

        val message = FeedbackRules.sanitize(u.message, multiline = true)
        val name = FeedbackRules.sanitize(u.name, multiline = false).take(FeedbackRules.MAX_NAME)
        val email = FeedbackRules.sanitize(u.email, multiline = false)

        FeedbackRules.checkMessage(message)?.let { return fail(problemText(it)) }
        if (email.isNotEmpty() && !FeedbackRules.isValidEmail(email)) {
            _ui.update { it.copy(emailError = true, error = "That email address doesn\u2019t look right.") }
            return
        }
        val fingerprint = FeedbackRules.fingerprint(message)
        guard.check(fingerprint)?.let { return fail(blockText(it)) }

        _ui.update { it.copy(status = FeedbackStatus.SENDING, error = null, emailError = false) }
        viewModelScope.launch {
            val token = captcha().getOrNull()
            if (token == null) {
                _ui.update { it.copy(status = FeedbackStatus.IDLE, error = "Couldn\u2019t verify you\u2019re human. Please try again.") }
                return@launch
            }
            val diagnostics = if (u.attachDiagnostics) withContext(Dispatchers.IO) { telemetry.snapshot(audioExtras()) } else null
            val payload = FeedbackPayload(
                type = u.type,
                rating = u.rating,
                name = name,
                email = email,
                message = message,
                diagnostics = diagnostics,
                appVersion = appVersion(),
                captchaToken = token,
            )
            when (val r = FeedbackSender.send(payload)) {
                SendResult.Success -> {
                    guard.record(fingerprint)
                    telemetry.breadcrumb("feedback", "sent ${u.type.label}")
                    _ui.update { FeedbackUi(name = it.name, email = it.email, status = FeedbackStatus.SENT) }
                }

                else -> {
                    telemetry.breadcrumb("feedback", "send failed: ${r.javaClass.simpleName}")
                    _ui.update { it.copy(status = FeedbackStatus.IDLE, error = failureText(r)) }
                }
            }
        }
    }

    fun acknowledgeSent() = _ui.update { if (it.status == FeedbackStatus.SENT) it.copy(status = FeedbackStatus.IDLE) else it }

    private fun fail(text: String) = _ui.update { it.copy(error = text) }

    private fun appVersion(): String = runCatching {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        info.versionName.orEmpty()
    }.getOrDefault("unknown")

    private fun problemText(p: MessageProblem) = when (p) {
        MessageProblem.TOO_SHORT -> "Please write at least ${FeedbackRules.MIN_WORDS} words so we can understand it."
        MessageProblem.TOO_LONG -> "That\u2019s a bit long. Please keep it under ${FeedbackRules.MAX_WORDS} words."
        MessageProblem.TOO_MANY_LINKS -> "Please include at most ${FeedbackRules.MAX_LINKS} links."
        MessageProblem.REPETITIVE -> "That looks repetitive. Please describe it in your own words."
    }

    private fun blockText(b: SendBlock) = when (b) {
        is SendBlock.Cooldown -> "Please wait ${b.seconds} s before sending another."
        SendBlock.DailyLimit -> "You\u2019ve reached today\u2019s limit of reports. Please try again tomorrow."
        SendBlock.Duplicate -> "You already sent this exact message today. Thank you!"
    }

    private fun failureText(r: SendResult) = when (r) {
        SendResult.NoNetwork -> "No connection. Check your internet and try again."
        SendResult.Timeout -> "The server took too long. Your report may or may not have arrived, so please check before resending."
        SendResult.RateLimited -> "Too many reports right now. Please try again in a few minutes."
        SendResult.ServerError -> "The feedback service is having trouble. Please try again later."
        is SendResult.Rejected -> "The report was rejected (${r.code}). Please try again later."
        is SendResult.Failed -> "Couldn\u2019t send the report. Please try again."
        SendResult.Success -> ""
    }
}
