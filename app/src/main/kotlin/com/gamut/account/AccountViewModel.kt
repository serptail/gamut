package com.gamut.account

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamut.core.database.PendingSyncDao
import com.gamut.core.sync.AuthRepository
import com.gamut.core.sync.BackupPasswordException
import com.gamut.core.sync.DataExporter
import com.gamut.core.sync.ImportReport
import com.gamut.core.sync.LocalDataCleaner
import com.gamut.core.sync.SyncManager
import com.gamut.onboarding.AutoBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupFile(val path: String, val modifiedMs: Long)

data class ImportSummary(val report: ImportReport, val replaced: Boolean)

data class RestorePrompt(val file: BackupFile, val preview: ImportReport)

sealed interface Notice {
    val text: String
    data class Success(override val text: String) : Notice
    data class Info(override val text: String) : Notice
    data class Error(override val text: String) : Notice
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val auth: AuthRepository,
    private val sync: SyncManager,
    private val exporter: DataExporter,
    private val cleaner: LocalDataCleaner,
    private val store: ProfileStore,
    private val avatars: AvatarRepository,
    pending: PendingSyncDao,
) : ViewModel() {
    val userId = auth.userId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), auth.currentUserId())
    val email: StateFlow<String?> = userId.map { auth.currentEmail() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), auth.currentEmail())

    val displayName: StateFlow<String> = userId
        .flatMapLatest { uid -> store.nameOverride(uid).map { it ?: auth.defaultDisplayName() ?: "Guest" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), auth.defaultDisplayName() ?: "Guest")

    val customName: StateFlow<String> = userId
        .flatMapLatest { uid -> store.nameOverride(uid).map { it.orEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    val status = sync.status
    val lastSyncedMs = sync.lastSyncedMs
    val pendingCount = pending.observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _events = MutableSharedFlow<Notice>(extraBufferCapacity = 8)
    val events: SharedFlow<Notice> = _events.asSharedFlow()

    private val _importSummary = MutableStateFlow<ImportSummary?>(null)
    val importSummary: StateFlow<ImportSummary?> = _importSummary
    fun dismissImportSummary() {
        _importSummary.value = null
    }

    private val _restorePrompt = MutableStateFlow<RestorePrompt?>(null)
    val restorePrompt: StateFlow<RestorePrompt?> = _restorePrompt
    fun cancelRestore() {
        _restorePrompt.value = null
    }

    private val busy = AtomicBoolean(false)

    val autoBackup = store.autoBackup
    private val _backups = MutableStateFlow<List<BackupFile>>(emptyList())
    val backups: StateFlow<List<BackupFile>> = _backups

    val avatar: StateFlow<Any?> = userId.flatMapLatest { avatars.model(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        refreshBackups()
        viewModelScope.launch { userId.collect { avatars.refresh(it) } }
        viewModelScope.launch {
            sync.signedOutRemotely.collect { _events.tryEmit(Notice.Info("Signed out \u00B7 this account was signed out elsewhere or deleted")) }
        }
    }

    fun signIn(activityContext: Context) = viewModelScope.launch {
        auth.signInWithGoogle(activityContext)
            .onSuccess { _events.tryEmit(Notice.Success("Signed in")) }
            .onFailure { _events.tryEmit(Notice.Error("Sign-in failed")) }
    }

    fun syncNow() = sync.syncNow()

    fun setName(name: String) {
        val clean = name.trim().take(40).ifBlank { null }
        val uid = auth.currentUserId()
        store.setName(uid, clean)
        _events.tryEmit(Notice.Success(if (clean == null) "Using your Google name" else "Name saved"))
        if (uid != null && clean != null) viewModelScope.launch { auth.updateDisplayName(clean) }
    }

    fun setAvatar(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        avatars.set(auth.currentUserId(), resolver, uri)
            .onSuccess { ok -> _events.tryEmit(if (ok) Notice.Success("Photo updated") else Notice.Info("Saved here \u00B7 uploads when online")) }
            .onFailure { _events.tryEmit(Notice.Error("Couldn\u2019t use that image")) }
    }

    fun removeAvatar() = viewModelScope.launch { avatars.remove(auth.currentUserId()) }

    fun signOut(allDevices: Boolean, clearLocal: Boolean) = viewModelScope.launch {
        val uid = auth.currentUserId()
        auth.signOut(allDevices).onFailure { _events.tryEmit(Notice.Error("Couldn\u2019t sign out everywhere")) }
        if (clearLocal) {
            cleaner.clear()
            avatars.clearLocal()
            AutoBackup.deleteAll(appContext)
            store.setName(uid, null)
            refreshBackups()
        }
        _events.tryEmit(Notice.Info("Google disconnected"))
    }

    fun deleteAccount(clearLocal: Boolean) = viewModelScope.launch {
        val uid = auth.currentUserId()
        auth.deleteAccount()
            .onSuccess {
                if (clearLocal) {
                    cleaner.clear()
                    avatars.clearLocal()
                    AutoBackup.deleteAll(appContext)
                    store.setName(uid, null)
                    refreshBackups()
                } else {
                    sync.forgetOwner()
                }
                _events.tryEmit(Notice.Success("Account deleted"))
            }
            .onFailure { _events.tryEmit(Notice.Error("Couldn\u2019t delete account")) }
    }

    fun export(resolver: ContentResolver, uri: Uri, password: String) {
        appScope.launch {
            runCatching {
                val bytes = exporter.export(password.takeIf { it.isNotBlank() }?.toCharArray())
                (resolver.openOutputStream(uri, "wt") ?: error("No output stream")).use { it.write(bytes) }
            }.onSuccess { _events.tryEmit(Notice.Success("Backup exported")) }
                .onFailure {
                    runCatching { android.provider.DocumentsContract.deleteDocument(resolver, uri) }
                    _events.tryEmit(Notice.Error("Export failed"))
                }
        }
    }

    fun import(resolver: ContentResolver, uri: Uri, password: String, replace: Boolean) {
        if (!busy.compareAndSet(false, true)) return
        appScope.launch {
            val pw = password.takeIf { it.isNotBlank() }?.toCharArray()
            try {
                val bytes = runCatching { resolver.openInputStream(uri)!!.use { exporter.readLimited(it) } }
                    .getOrElse {
                        _events.tryEmit(Notice.Error("Couldn\u2019t read that file"))
                        return@launch
                    }
                _importSummary.value = ImportSummary(exporter.import(bytes, pw, replace), replace)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(Notice.Error(importError(e)))
            } finally {
                pw?.fill('\u0000')
                busy.set(false)
            }
        }
    }

    private fun importError(e: Exception): String = when (e) {
        is BackupPasswordException -> if (e.wrong) "Wrong password" else "This backup is encrypted \u00B7 enter its password"
        is IllegalArgumentException -> e.message ?: "Import failed"
        else -> "Import failed"
    }

    fun setAutoBackup(enabled: Boolean) {
        store.setAutoBackup(enabled)
        AutoBackup.apply(appContext, enabled)
    }

    fun backupNow() = viewModelScope.launch {
        runCatching { AutoBackup.write(appContext, exporter) }
            .onSuccess { file ->
                refreshBackups()
                _events.tryEmit(if (file == null) Notice.Info("Nothing to back up yet") else Notice.Success("Backup saved on this device"))
            }
            .onFailure { _events.tryEmit(Notice.Error("Backup failed")) }
    }

    fun requestRestore(file: BackupFile) {
        if (!busy.compareAndSet(false, true)) return
        appScope.launch {
            try {
                val bytes = File(file.path).inputStream().use { exporter.readLimited(it) }
                val preview = exporter.preview(bytes, null)
                if (preview.totalAdded == 0) {
                    _events.tryEmit(Notice.Info("Already up to date \u00B7 nothing to restore"))
                } else {
                    _restorePrompt.value = RestorePrompt(file, preview)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(Notice.Error("Couldn\u2019t read that backup"))
            } finally {
                busy.set(false)
            }
        }
    }

    fun confirmRestore() {
        val prompt = _restorePrompt.value ?: return
        _restorePrompt.value = null
        if (!busy.compareAndSet(false, true)) return
        appScope.launch {
            try {
                val bytes = File(prompt.file.path).inputStream().use { exporter.readLimited(it) }
                _importSummary.value = ImportSummary(exporter.import(bytes, null), replaced = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(Notice.Error("Restore failed"))
            } finally {
                busy.set(false)
            }
        }
    }

    fun refreshBackups() {
        viewModelScope.launch {
            _backups.value = withContext(Dispatchers.IO) {
                AutoBackup.list(appContext).map { BackupFile(it.file.absolutePath, it.lastModified) }
            }
        }
    }
}
