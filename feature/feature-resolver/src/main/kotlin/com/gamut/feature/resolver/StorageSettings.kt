package com.gamut.feature.resolver

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DownloadQuality(val label: String, val hint: String) {
    BEST("Best available", "The original stream, Opus about 160 kbps when YouTube has it. Largest files."),
    STANDARD("Standard", "AAC / m4a, about 128 kbps. Smaller files that play everywhere."),
}

data class StorageSettingsState(
    val wifiOnly: Boolean = false,
    val downloadQuality: DownloadQuality = DownloadQuality.BEST,
    val cacheLimitMb: Int = 0,
)

@Singleton
class StorageSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("storage_settings", Context.MODE_PRIVATE)
    private val flow = MutableStateFlow(read())

    val state: StateFlow<StorageSettingsState> = flow.asStateFlow()
    val current: StorageSettingsState get() = flow.value

    @Synchronized
    fun update(block: (StorageSettingsState) -> StorageSettingsState) {
        val next = block(flow.value)
        flow.value = next
        prefs.edit()
            .putBoolean("wifiOnly", next.wifiOnly)
            .putString("downloadQuality", next.downloadQuality.name)
            .putInt("cacheLimitMb", next.cacheLimitMb)
            .apply()
    }

    private fun read() = StorageSettingsState(
        wifiOnly = prefs.getBoolean("wifiOnly", false),
        downloadQuality = runCatching { DownloadQuality.valueOf(prefs.getString("downloadQuality", null).orEmpty()) }
            .getOrDefault(DownloadQuality.BEST),
        cacheLimitMb = prefs.getInt("cacheLimitMb", 0),
    )
}
