package com.gamut.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamut.core.database.RecentlyPlayedRepository
import com.gamut.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RecentlyPlayedViewModel @Inject constructor(private val repo: RecentlyPlayedRepository) : ViewModel() {
    val tracks = repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun record(track: Track) {
        viewModelScope.launch { repo.record(track) }
    }
}
