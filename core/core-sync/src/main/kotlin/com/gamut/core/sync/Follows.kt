package com.gamut.core.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamut.core.database.FollowDao
import com.gamut.core.database.FollowEntity
import com.gamut.core.database.PendingSyncDao
import com.gamut.core.database.PendingSyncEntity
import com.gamut.core.database.SyncTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Singleton
class FollowRepository @Inject constructor(
    private val dao: FollowDao,
    private val pending: PendingSyncDao,
) {
    val followedKeys: Flow<Set<String>> = dao.observeKeys().map { it.toSet() }
    fun observeAll(): Flow<List<FollowEntity>> = dao.observeAll()

    suspend fun importFollow(artistKey: String, name: String, imageUrl: String?, followedAt: Long): Boolean {
        if (dao.isFollowing(artistKey)) return false
        dao.add(FollowEntity(artistKey, name, imageUrl, followedAt))
        pending.put(PendingSyncEntity(type = SyncTypes.FOLLOW, itemKey = artistKey, createdAtEpochMs = System.currentTimeMillis()))
        return true
    }

    suspend fun toggle(artistKey: String, name: String, imageUrl: String?): Boolean {
        val now = System.currentTimeMillis()
        val wasFollowing = dao.isFollowing(artistKey)
        if (wasFollowing) dao.remove(artistKey) else dao.add(FollowEntity(artistKey, name, imageUrl, now))
        pending.put(PendingSyncEntity(type = SyncTypes.FOLLOW, itemKey = artistKey, createdAtEpochMs = now))
        return !wasFollowing
    }
}

@HiltViewModel
class FollowViewModel @Inject constructor(private val repository: FollowRepository) : ViewModel() {
    val followedKeys: StateFlow<Set<String>> =
        repository.followedKeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val artists: StateFlow<List<FollowEntity>?> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(artistKey: String, name: String, imageUrl: String?) {
        viewModelScope.launch { repository.toggle(artistKey, name, imageUrl) }
    }
}
