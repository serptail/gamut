package com.gamut.core.player

import com.gamut.core.model.Track

sealed interface BrowseEntry {
    val id: String

    data class Folder(
        override val id: String,
        val title: String,
        val subtitle: String? = null,
        val artUrl: String? = null,
    ) : BrowseEntry

    data class Song(override val id: String, val track: Track) : BrowseEntry
}

data class BrowseQueue(val tracks: List<Track>, val startIndex: Int)

interface BrowseTreeProvider {
    suspend fun children(parentId: String): List<BrowseEntry>

    suspend fun item(id: String): BrowseEntry?

    suspend fun queueFor(mediaId: String): BrowseQueue?

    suspend fun search(query: String): List<BrowseEntry.Song>

    suspend fun resumption(): BrowseQueue?

    companion object {
        const val ROOT = "root"
    }
}
