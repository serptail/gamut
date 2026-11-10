package com.gamut.onboarding

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gamut.account.ProfileStore
import com.gamut.core.sync.AuthRepository
import com.gamut.core.sync.SyncConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Gate { Loading, Welcome, Ready }

data class WelcomeUi(val busy: Boolean = false, val error: String? = null)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val auth: AuthRepository,
    private val store: OnboardingStore,
    private val profile: ProfileStore,
) : ViewModel() {
    val gate: StateFlow<Gate> = combine(store.localMode, auth.authState) { local, signedIn ->
        android.util.Log.d("GamutGate", "local=$local signedIn=$signedIn")
        when {
            local || !SyncConfig.enabled -> Gate.Ready
            signedIn == true -> Gate.Ready
            signedIn == false -> Gate.Welcome
            else -> Gate.Loading
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        if (store.localMode.value || !SyncConfig.enabled) Gate.Ready else Gate.Loading,
    )

    private val _ui = MutableStateFlow(WelcomeUi())
    val ui: StateFlow<WelcomeUi> = _ui

    init {
        viewModelScope.launch(Dispatchers.IO) {
            combine(store.localMode, auth.authState, profile.autoBackup) { local, signedIn, enabled -> enabled && (local || !SyncConfig.enabled) && signedIn != true }
                .distinctUntilChanged()
                .collect { if (it) AutoBackup.schedule(appContext) else AutoBackup.cancel(appContext) }
        }
    }

    fun continueLocally() = store.chooseLocal()

    fun signIn(activityContext: Context) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.value = WelcomeUi(busy = true)
            auth.signInWithGoogle(activityContext).fold(
                onSuccess = { _ui.value = WelcomeUi() },
                onFailure = {
                    _ui.value = WelcomeUi(
                        error = if (it is GetCredentialCancellationException) {
                            null
                        } else {
                            "Couldn\u2019t sign in. Check your connection and try again."
                        },
                    )
                },
            )
        }
    }
}
