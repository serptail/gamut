package com.gamut.core.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.gamut.core.model.RepeatMode
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class GamutMediaSessionService : MediaLibraryService() {
    @Inject lateinit var musicPlayer: MusicPlayer

    @Inject lateinit var browseTree: BrowseTreeProvider
    private var session: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val repeatCommand = SessionCommand(ACTION_REPEAT, Bundle.EMPTY)
    private val shuffleCommand = SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY)

    override fun onCreate() {
        super.onCreate()
        val player = musicPlayer as Media3MusicPlayer

        val icon = listOf("ic_stat_gamut", "ic_gamut_mark_white")
            .map { resources.getIdentifier(it, "drawable", packageName) }
            .firstOrNull { it != 0 } ?: 0
        if (icon != 0) {
            setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().also { it.setSmallIcon(icon) })
        }

        val builder = MediaLibrarySession.Builder(this, player.mediaSessionPlayer, LibraryCallback())
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setSessionActivity(
                PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
        }
        session = builder.build()
        refreshButtons()
        scope.launch {
            combine(musicPlayer.repeatMode, musicPlayer.shuffleEnabled) { _, _ -> }.collect { refreshButtons() }
        }
        player.onSessionPlayerChanged = { session?.player = it }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    @Suppress("DEPRECATION")
    private fun refreshButtons() {
        val shuffleOn = musicPlayer.shuffleEnabled.value
        val repeat = musicPlayer.repeatMode.value
        val shuffleButton = CommandButton.Builder()
            .setDisplayName(if (shuffleOn) "Shuffle on" else "Shuffle off")
            .setSessionCommand(shuffleCommand)
            .setIconResId(if (shuffleOn) R.drawable.gamut_ic_shuffle_on else R.drawable.gamut_ic_shuffle_off)
            .build()
        val repeatButton = CommandButton.Builder()
            .setDisplayName(
                when (repeat) {
                    RepeatMode.OFF -> "Repeat off"
                    RepeatMode.ALL -> "Repeat all"
                    RepeatMode.ONE -> "Repeat one"
                },
            )
            .setSessionCommand(repeatCommand)
            .setIconResId(
                when (repeat) {
                    RepeatMode.OFF -> R.drawable.gamut_ic_repeat_off
                    RepeatMode.ALL -> R.drawable.gamut_ic_repeat_all
                    RepeatMode.ONE -> R.drawable.gamut_ic_repeat_one
                },
            )
            .build()
        session?.setCustomLayout(ImmutableList.of(shuffleButton, repeatButton))
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(repeatCommand)
                .add(shuffleCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SHUFFLE -> musicPlayer.setShuffle(!musicPlayer.shuffleEnabled.value)

                ACTION_REPEAT -> musicPlayer.setRepeatMode(
                    when (musicPlayer.repeatMode.value) {
                        RepeatMode.OFF -> RepeatMode.ALL
                        RepeatMode.ALL -> RepeatMode.ONE
                        RepeatMode.ONE -> RepeatMode.OFF
                    },
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = folderItem(BrowseTreeProvider.ROOT, "Gamut", null, null)
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val entries = browseTree.children(parentId)
            Log.d(TAG, "children($parentId) page=$page size=$pageSize -> ${entries.size} entries")
            val items = entries.pageOf(page, pageSize).map(::toItem)
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            val entry = browseTree.item(mediaId)
            if (entry == null) {
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            } else {
                LibraryResult.ofItem(toItem(entry), null)
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = future {
            session.notifySearchResultChanged(browser, query, browseTree.search(query).size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            val items = browseTree.search(query).pageOf(page, pageSize).map(::toItem)
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = future {
            val resolved = mediaItems.mapNotNull { item ->
                val query = item.requestMetadata.searchQuery
                if (item.mediaId.isEmpty() && query != null) browseTree.search(query).firstOrNull()?.let(::toItem) else item
            }
            val first = resolved.firstOrNull { it.mediaId.isNotEmpty() }
            val queue = first?.let { browseTree.queueFor(it.mediaId) }
            if (queue != null && queue.tracks.isNotEmpty()) {
                (musicPlayer as Media3MusicPlayer).prepareExternalQueue(queue.tracks, queue.startIndex)
            }
            resolved.toMutableList()
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            val queue = browseTree.resumption()
            if (queue == null || queue.tracks.isEmpty()) throw UnsupportedOperationException("nothing to resume")
            (musicPlayer as Media3MusicPlayer).prepareExternalQueue(queue.tracks, queue.startIndex)
            val track = queue.tracks[queue.startIndex.coerceIn(0, queue.tracks.lastIndex)]
            MediaSession.MediaItemsWithStartPosition(listOf(songItem(track.isrc, track)), 0, 0L)
        }
    }

    private fun toItem(entry: BrowseEntry): MediaItem = when (entry) {
        is BrowseEntry.Folder -> folderItem(entry.id, entry.title, entry.subtitle, entry.artUrl)
        is BrowseEntry.Song -> songItem(entry.id, entry.track)
    }

    private fun folderItem(id: String, title: String, subtitle: String?, artUrl: String?): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(artUrl?.let(Uri::parse))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build(),
        )
        .build()

    private fun songItem(id: String, track: com.gamut.core.model.Track): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(track.albumArtUrl?.let(Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build(),
        )
        .build()

    private fun <T> List<T>.pageOf(page: Int, pageSize: Int): List<T> {
        val from = (page.toLong() * pageSize).coerceAtMost(size.toLong()).toInt()
        return drop(from).take(pageSize)
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        val job = scope.launch {
            try {
                result.set(block())
            } catch (e: CancellationException) {
                result.cancel(false)
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "Library callback failed", e)
                result.setException(e)
            }
        }
        result.addListener({ if (result.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
        return result
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired || (musicPlayer as Media3MusicPlayer).keepForeground)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        (musicPlayer as Media3MusicPlayer).onSessionPlayerChanged = null
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }

    private companion object {
        const val ACTION_REPEAT = "gamut.action.REPEAT"
        const val ACTION_SHUFFLE = "gamut.action.SHUFFLE"
        const val TAG = "GamutAuto"
    }
}
