package com.gamut.core.database

import com.gamut.core.model.Track
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RecentlyPlayedRepository @Inject constructor(
    private val recent: RecentDao,
    private val tracks: TrackDao,
) {
    fun observe(): Flow<List<Track>> = recent.observeTracks(MAX_SHOWN).map { rows -> rows.map { it.toDomain() } }

    suspend fun record(track: Track) {
        tracks.insertIgnore(listOf(track.toEntity()))
        recent.put(RecentEntity(track.isrc, System.currentTimeMillis()))
        recent.trim(MAX_KEPT)
    }

    suspend fun clear() = recent.clear()

    private companion object {
        const val MAX_SHOWN = 50
        const val MAX_KEPT = 100
    }
}
