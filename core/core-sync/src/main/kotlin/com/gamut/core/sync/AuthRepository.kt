package com.gamut.core.sync

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.functions.functions
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

@Singleton
class AuthRepository @Inject constructor(private val supabase: SupabaseClient) {
    val userId: Flow<String?> = supabase.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> status.session.user?.id
            is SessionStatus.NotAuthenticated -> null
            else -> supabase.auth.currentUserOrNull()?.id
        }
    }.distinctUntilChanged()

    val authState: Flow<Boolean?> = supabase.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> true
            is SessionStatus.NotAuthenticated -> false
            else -> if (supabase.auth.currentUserOrNull() != null) true else null
        }
    }.distinctUntilChanged()

    fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id
    fun currentEmail(): String? = supabase.auth.currentUserOrNull()?.email

    fun defaultDisplayName(): String? {
        val user = supabase.auth.currentUserOrNull() ?: return null
        fun meta(key: String) = (user.userMetadata?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        return meta("display_name") ?: meta("full_name") ?: meta("name") ?: user.email?.substringBefore('@')
    }

    suspend fun updateDisplayName(name: String): Result<Unit> = runCatching {
        supabase.auth.updateUser { data { put("display_name", name) } }
        Unit
    }

    suspend fun signInWithGoogle(activityContext: Context): Result<Unit> = runCatching {
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256").digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(hashedNonce).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        val googleToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        supabase.auth.signInWith(IDToken) {
            idToken = googleToken
            provider = Google
            nonce = rawNonce
        }
    }

    suspend fun signOut(allDevices: Boolean): Result<Unit> {
        val remote = runCatching { supabase.auth.signOut(if (allDevices) SignOutScope.GLOBAL else SignOutScope.LOCAL) }
        if (remote.isFailure) runCatching { supabase.auth.clearSession() }
        return if (allDevices) remote else Result.success(Unit)
    }

    suspend fun deleteAccount(): Result<Unit> = runCatching {
        supabase.functions.invoke("delete-account", buildJsonObject { })
        runCatching { supabase.auth.signOut(SignOutScope.LOCAL) }
        Unit
    }
}
