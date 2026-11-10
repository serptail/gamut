package com.gamut.core.database

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SyncDaoModule {
    @Provides fun pendingSyncDao(db: GamutDatabase): PendingSyncDao = db.pendingSyncDao()

    @Provides fun syncStateDao(db: GamutDatabase): SyncStateDao = db.syncStateDao()

    @Provides fun followDao(db: GamutDatabase): FollowDao = db.followDao()
}
