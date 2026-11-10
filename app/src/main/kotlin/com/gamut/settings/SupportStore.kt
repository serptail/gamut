package com.gamut.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object SupportConfig {
    const val KOFI_URL = "https://ko-fi.com/serptail"

    const val QUIET_DAYS = 3
    val SHEET_GAP_DAYS = longArrayOf(7, 30, 90)
    const val MAX_SHEETS = 3
    const val COLD_START_QUIET_MS = 20_000L
    const val PLAYLIST_MILESTONE = 10
    val PLAY_MILESTONES = intArrayOf(100, 300, 1000)
}

enum class SupportTrigger { FirstDownload, PlaylistMilestone, PlayMilestone, Donate }

@Singleton
class SupportStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("support", Context.MODE_PRIVATE)

    init {
        if (!prefs.contains(K_FIRST_LAUNCH)) prefs.edit().putLong(K_FIRST_LAUNCH, System.currentTimeMillis()).apply()
    }

    private val _nudge = MutableStateFlow(prefs.getBoolean(K_NUDGE, false))
    val nudge: StateFlow<Boolean> = _nudge.asStateFlow()

    private val _pending = MutableStateFlow(readPending())
    val pending: StateFlow<SupportTrigger?> = _pending.asStateFlow()

    private val _openRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openRequests: SharedFlow<Unit> = _openRequests.asSharedFlow()

    private val _donateOpen = MutableStateFlow(false)
    val donateOpen: StateFlow<Boolean> = _donateOpen.asStateFlow()
    fun setDonateOpen(open: Boolean) {
        _donateOpen.value = open
    }

    fun requestDonateSheet() {
        _openRequests.tryEmit(Unit)
    }

    val plays: Int get() = prefs.getInt(K_PLAYS, 0)
    private val shownCount: Int get() = prefs.getInt(K_SHOWN, 0)
    private val dismissedForever: Boolean get() = prefs.getBoolean(K_FOREVER, false)

    fun recordDownloadSuccess() {
        if (prefs.getBoolean(K_DOWNLOAD_DONE, false)) return
        prefs.edit().putBoolean(K_DOWNLOAD_DONE, true).apply()
        request(SupportTrigger.FirstDownload)
    }

    fun recordPlay() {
        val n = plays + 1
        prefs.edit().putInt(K_PLAYS, n).apply()
        if (n in SupportConfig.PLAY_MILESTONES) request(SupportTrigger.PlayMilestone)
    }

    fun recordPlaylistSize(largest: Int) {
        if (largest < SupportConfig.PLAYLIST_MILESTONE || prefs.getBoolean(K_PLAYLIST_DONE, false)) return
        prefs.edit().putBoolean(K_PLAYLIST_DONE, true).apply()
        request(SupportTrigger.PlaylistMilestone)
    }

    private fun request(trigger: SupportTrigger) {
        if (dismissedForever || _pending.value != null) return
        prefs.edit().putString(K_PENDING, trigger.name).apply()
        _pending.value = trigger
    }

    fun canShowNow(): Boolean {
        if (dismissedForever) return false
        val count = shownCount
        if (count >= SupportConfig.MAX_SHEETS) return false
        val now = System.currentTimeMillis()
        val first = prefs.getLong(K_FIRST_LAUNCH, now)
        if (now - first < days(SupportConfig.QUIET_DAYS.toLong())) return false
        val anchor = if (count == 0) first else prefs.getLong(K_LAST_SHOWN, now)
        return now - anchor >= days(SupportConfig.SHEET_GAP_DAYS[count.coerceAtMost(SupportConfig.SHEET_GAP_DAYS.lastIndex)])
    }

    fun onSheetShown() {
        prefs.edit().putInt(K_SHOWN, shownCount + 1).putLong(K_LAST_SHOWN, System.currentTimeMillis()).remove(K_PENDING).apply()
        _pending.value = null
    }

    fun onLater() = setNudge(!dismissedForever)

    fun onSupported() = setNudge(false)

    fun onNeverAgain() {
        prefs.edit().putBoolean(K_FOREVER, true).remove(K_PENDING).apply()
        _pending.value = null
        setNudge(false)
    }

    fun clearNudge() = setNudge(false)

    private fun setNudge(on: Boolean) {
        prefs.edit().putBoolean(K_NUDGE, on).apply()
        _nudge.value = on
    }

    private fun readPending() = prefs.getString(K_PENDING, null)?.let { runCatching { SupportTrigger.valueOf(it) }.getOrNull() }
    private fun days(n: Long) = n * 24L * 60L * 60L * 1000L

    private companion object {
        const val K_FIRST_LAUNCH = "first_launch"
        const val K_SHOWN = "shown_count"
        const val K_LAST_SHOWN = "last_shown"
        const val K_FOREVER = "dismissed_forever"
        const val K_NUDGE = "nudge"
        const val K_PENDING = "pending"
        const val K_PLAYS = "plays"
        const val K_DOWNLOAD_DONE = "download_done"
        const val K_PLAYLIST_DONE = "playlist_done"
    }
}

@HiltViewModel
class SupportViewModel @Inject constructor(val store: SupportStore) : ViewModel()