package com.gamut.feature.resolver

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class PlaybackHealth @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val consecutiveFailures = AtomicInteger(0)
    private val _failing = MutableStateFlow(false)
    val failing: StateFlow<Boolean> = _failing.asStateFlow()

    fun reportSuccess() {
        consecutiveFailures.set(0)
        _failing.value = false
    }

    fun reportFailure() {
        if (!online()) return
        if (consecutiveFailures.incrementAndGet() >= FAILURES_BEFORE_WARNING) _failing.value = true
    }

    private fun online(): Boolean = try {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    } catch (_: SecurityException) {
        true
    }

    private companion object {
        const val FAILURES_BEFORE_WARNING = 3
    }
}
