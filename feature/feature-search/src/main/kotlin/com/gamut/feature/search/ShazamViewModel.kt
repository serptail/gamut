package com.gamut.feature.search

import androidx.lifecycle.ViewModel
import com.gamut.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

sealed interface ShazamState {
    data object Idle : ShazamState
    data class Listening(val searching: Boolean) : ShazamState
    data class Found(val track: Track) : ShazamState
    data object NotFound : ShazamState
    data class Failed(val message: String) : ShazamState
    data object PermissionDenied : ShazamState
}

@HiltViewModel
class ShazamViewModel @Inject constructor(
    private val session: ShazamSession,
) : ViewModel() {
    val state: StateFlow<ShazamState> = session.state
    val level: StateFlow<Float> = session.level

    fun start() = session.start()

    fun dismiss() = session.dismiss()

    fun permissionDenied() = session.permissionDenied()
}
