package com.gamut.core.database

import com.gamut.core.model.LocalAudioIndex
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Singleton
class DownloadIndex @Inject constructor(database: GamutDatabase) : LocalAudioIndex {
    private val dao = database.downloadDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val downloads: StateFlow<Map<String, DownloadEntity>> = dao.observeAll()
        .map { rows -> rows.associateBy { it.isrc } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override fun pathFor(isrc: String): String? = downloads.value[isrc]?.localFilePath

    suspend fun save(entity: DownloadEntity) = dao.upsert(entity)

    suspend fun remove(isrc: String): String? {
        val path = downloads.value[isrc]?.localFilePath
        dao.delete(isrc)
        return path
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadIndexModule {
    @Binds abstract fun localAudioIndex(impl: DownloadIndex): LocalAudioIndex
}
