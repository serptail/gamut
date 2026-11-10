package com.gamut.feature.resolver

import com.gamut.core.database.ResolvedSourceDao
import com.gamut.core.database.toDomain
import com.gamut.core.database.toEntity
import com.gamut.core.model.ResolvedSource
import com.gamut.core.network.BuildConfig
import com.gamut.core.network.ResolvedSourceDto
import com.gamut.core.network.ResolverSyncApi
import com.gamut.core.network.YoutubeMusicSearchSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface ResolvedSourceRepository {
    suspend fun getLocal(isrc: String): ResolvedSource?
    suspend fun getRemote(isrc: String): ResolvedSource?
    suspend fun saveLocal(source: ResolvedSource)
    suspend fun submitRemote(source: ResolvedSource)
}

class ResolvedSourceRepositoryImpl @Inject constructor(private val dao: ResolvedSourceDao, private val api: ResolverSyncApi) : ResolvedSourceRepository {
    private val remoteEnabled = false
    override suspend fun getLocal(isrc: String) = dao.get(isrc)?.toDomain()
    override suspend fun getRemote(isrc: String) = if (remoteEnabled) runCatching { api.get(isrc).body()?.toDomain() }.getOrNull() else null
    override suspend fun saveLocal(source: ResolvedSource) = dao.upsert(source.toEntity())
    override suspend fun submitRemote(source: ResolvedSource) {
        if (remoteEnabled) runCatching { api.save(source.toDto()) }
    }
}

private fun ResolvedSourceDto.toDomain() = ResolvedSource(isrc, youtubeVideoId, com.gamut.core.model.ResolutionMethod.valueOf(resolutionMethod), confidenceScore, verifiedCount)
private fun ResolvedSource.toDto() = ResolvedSourceDto(isrc, youtubeVideoId, resolutionMethod.name, confidenceScore, verifiedCount)

@Module
@InstallIn(SingletonComponent::class)
abstract class ResolverRepositoryModule {
    @Binds abstract fun bindResolvedSourceRepository(impl: ResolvedSourceRepositoryImpl): ResolvedSourceRepository

    @Binds abstract fun bindYoutubeCandidateRepository(impl: YoutubeCandidateRepositoryImpl): YoutubeCandidateRepository

    @Binds abstract fun bindAudioSourceRepository(impl: AudioSourceRepositoryImpl): AudioSourceRepository

    @Binds abstract fun bindYoutubeMusicSearchSource(source: HybridYoutubeMusicSearchSource): YoutubeMusicSearchSource
}

@Module
@InstallIn(SingletonComponent::class)
object ResolverScopeModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
