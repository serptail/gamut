package com.gamut.update

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.gamut.BuildConfig
import com.gamut.settings.UpdateCheckStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UpdatePhase { READY, NEEDS_PERMISSION, DOWNLOADING, INSTALLING, FAILED }

data class UpdateUiState(
    val checking: Boolean = true,
    val required: UpdateInfo? = null,
    val phase: UpdatePhase = UpdatePhase.READY,
    val progress: Float = 0f,
    val error: String? = null,
    val skipped: Boolean = false,
    val optional: Boolean = false,
    val waiting: Boolean = false,
)

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val checker = UpdateChecker(context)
    private val installer = UpdateInstaller(context)
    private val workManager = WorkManager.getInstance(context)

    private val _state = MutableStateFlow(UpdateUiState(checking = checker.enabled))
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var updateJob: Job? = null
    private var watchJob: Job? = null
    private var watchedCode = 0
    private var expectedWork: UUID? = null
    private var installedWork: UUID? = null
    private var lastCheckAt = 0L

    init {
        viewModelScope.launch {
            delay(GRACE_MS)
            _state.update { if (it.checking) it.copy(checking = false) else it }
        }
        viewModelScope.launch {
            UpdateStatusBus.failures.collect { message ->
                updateJob?.cancel()
                _state.update { it.copy(phase = UpdatePhase.FAILED, error = message) }
            }
        }
    }

    fun onStart() {
        check()
        val s = _state.value
        if (s.required != null && !s.skipped && s.phase == UpdatePhase.NEEDS_PERMISSION && installer.canInstall()) startUpdate()
    }

    fun recheckNow() {
        lastCheckAt = 0L
        check()
    }

    private fun check() {
        if (!checker.enabled) {
            _state.update { it.copy(checking = false) }
            return
        }
        if (checkJob?.isActive == true) return
        val now = SystemClock.elapsedRealtime()
        if (lastCheckAt != 0L && now - lastCheckAt < RECHECK_MS && _state.value.required == null) return
        checkJob = viewModelScope.launch {
            val latest = checker.latest()
            lastCheckAt = SystemClock.elapsedRealtime()
            Log.i("AppUpdate", "installed ${BuildConfig.VERSION_CODE}, latest ${latest?.versionCode}, minimum ${latest?.minVersionCode}")
            applyLatest(latest, manual = false)
        }
    }

    suspend fun checkNow(): UpdateCheckStatus {
        if (!checker.enabled) return UpdateCheckStatus.Unavailable
        checkJob?.join()
        val latest = checker.latest(allowCache = false) ?: return UpdateCheckStatus.Failed
        lastCheckAt = SystemClock.elapsedRealtime()
        applyLatest(latest, manual = true)
        return if (BuildConfig.VERSION_CODE < latest.versionCode) UpdateCheckStatus.Found else UpdateCheckStatus.UpToDate
    }

    private fun applyLatest(latest: UpdateInfo?, manual: Boolean) {
        val forced = latest?.takeIf { BuildConfig.VERSION_CODE < it.minVersionCode }
        val newer = latest?.takeIf { BuildConfig.VERSION_CODE < it.versionCode }
        val target = if (manual) newer else forced
        _state.update { s ->
            when {
                target == null && !manual && s.optional -> s.copy(checking = false)
                target == null -> s.copy(checking = false, required = null, optional = false, phase = UpdatePhase.READY, error = null, skipped = false)
                s.required?.versionCode == target.versionCode ->
                    s.copy(checking = false, optional = forced == null, skipped = if (manual) false else s.skipped)
                else -> s.copy(checking = false, required = target, optional = forced == null, phase = UpdatePhase.READY, progress = 0f, error = null, skipped = false)
            }
        }
        if (target != null && watchedCode != target.versionCode) {
            watchedCode = target.versionCode
            expectedWork = null
            watch(target)
        }
    }

    fun skip() {
        _state.update { it.copy(skipped = true) }
    }

    fun startUpdate() {
        val info = _state.value.required ?: return
        val phase = _state.value.phase
        if (phase == UpdatePhase.DOWNLOADING || phase == UpdatePhase.INSTALLING) return
        if (!installer.canInstall()) {
            _state.update { it.copy(phase = UpdatePhase.NEEDS_PERMISSION, error = null) }
            installer.openInstallSettings()
            return
        }
        _state.update { it.copy(phase = UpdatePhase.DOWNLOADING, progress = 0f, error = null, waiting = false) }
        expectedWork = UpdateDownloadWorker.enqueue(context, info)
    }

    fun cancelUpdate() {
        updateJob?.cancel()
        _state.value.required?.let { workManager.cancelUniqueWork(UpdateDownloadWorker.nameFor(it)) }
        expectedWork = null
        installedWork = null
        installer.abandonSessions()
        _state.update { it.copy(phase = UpdatePhase.READY, progress = 0f, error = null, waiting = false) }
    }

    private fun watch(info: UpdateInfo) {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            var sawRunning = false
            workManager.getWorkInfosForUniqueWorkFlow(UpdateDownloadWorker.nameFor(info)).collect { list ->
                val work = list.firstOrNull { expectedWork == null || it.id == expectedWork } ?: return@collect
                when (work.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING -> {
                        if (expectedWork == null) {
                            workManager.cancelUniqueWork(UpdateDownloadWorker.nameFor(info))
                            return@collect
                        }
                        sawRunning = true
                        val p = work.progress.getFloat(UpdateDownloadWorker.K_PROGRESS, -1f)
                        val waiting = work.state != WorkInfo.State.RUNNING
                        _state.update {
                            if (it.phase == UpdatePhase.INSTALLING) {
                                it
                            } else {
                                it.copy(phase = UpdatePhase.DOWNLOADING, error = null, waiting = waiting, progress = if (p >= 0f) p else it.progress)
                            }
                        }
                    }

                    WorkInfo.State.SUCCEEDED ->
                        if ((sawRunning || expectedWork != null) && installedWork != work.id) {
                            installedWork = work.id
                            install(info)
                        }

                    WorkInfo.State.FAILED ->
                        if (sawRunning || expectedWork != null) {
                            val message = work.outputData.getString(UpdateDownloadWorker.K_ERROR) ?: "The download failed."
                            _state.update { it.copy(phase = UpdatePhase.FAILED, error = message) }
                        }

                    WorkInfo.State.CANCELLED -> Unit
                }
            }
        }
    }

    private fun install(info: UpdateInfo) {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            _state.update { it.copy(phase = UpdatePhase.INSTALLING, progress = 1f, error = null) }
            try {
                val apk = installer.download(info) { }
                installer.signerProblem(apk)?.let { throw IllegalStateException(it) }
                installer.install(apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = UpdatePhase.FAILED, error = e.message ?: "The update failed.") }
            }
        }
    }

    companion object {
        const val GRACE_MS = 3_500L
        private const val RECHECK_MS = 15 * 60_000L
    }
}