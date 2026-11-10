package com.gamut

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.work.Configuration
import com.gamut.account.ProfileStore
import com.gamut.core.database.DownloadIndex
import com.gamut.core.player.MusicPlayer
import com.gamut.core.sync.SyncManager
import com.gamut.feature.resolver.AndroidYtDlpEngine
import com.gamut.feature.resolver.AudioSourceRepository
import com.gamut.feature.resolver.ResolveTrackSourceUseCase
import com.gamut.feature.resolver.YtDlpUpdateWorker
import com.gamut.feature.search.ShazamSession
import com.gamut.onboarding.AutoBackup
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class GamutApplication :
    Application(),
    Configuration.Provider {
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()

    @Inject lateinit var ytDlp: AndroidYtDlpEngine

    @Inject lateinit var player: MusicPlayer

    @Inject lateinit var resolveTrackSource: ResolveTrackSourceUseCase

    @Inject lateinit var audioSources: AudioSourceRepository

    @Inject lateinit var downloadIndex: DownloadIndex

    @Inject lateinit var syncManager: SyncManager

    @Inject lateinit var profileStore: ProfileStore

    @Inject lateinit var shazamSession: ShazamSession

    override fun onCreate() {
        super.onCreate()
        player.setSourceResolver { track, forceRefresh, onProgress ->
            if (!forceRefresh) {
                downloadIndex.pathFor(track.isrc)?.let(::File)?.takeIf { it.exists() }?.let {
                    return@setSourceResolver Uri.fromFile(it).toString()
                }
            }
            val source = resolveTrackSource(track).getOrNull() ?: return@setSourceResolver null
            onProgress(0.5f)
            audioSources.audioUrl(source.youtubeVideoId, forceRefresh).getOrNull()
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            ytDlp.warmUp()
            YtDlpUpdateWorker.schedule(applicationContext)
        }
        syncManager.start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            private var lastSyncAt = 0L
            override fun onActivityStarted(activity: Activity) {
                if (started++ == 0) shazamSession.appVisible = true
                if (started == 1 && System.currentTimeMillis() - lastSyncAt > 30_000) {
                    lastSyncAt = System.currentTimeMillis()
                    syncManager.syncNow()
                }
            }
            override fun onActivityStopped(activity: Activity) {
                if (--started == 0) shazamSession.appVisible = false
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            profileStore.autoBackup.collect { AutoBackup.apply(applicationContext, it) }
        }
    }
}
