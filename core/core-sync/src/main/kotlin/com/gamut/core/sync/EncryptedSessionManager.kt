package com.gamut.core.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EncryptedSessionManager(private val context: Context) : SessionManager {
    private val json = Json { ignoreUnknownKeys = true }

    private fun open(): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val prefs: SharedPreferences by lazy {
        try {
            open()
        } catch (e: Exception) {
            context.deleteSharedPreferences(FILE)
            open()
        }
    }

    override suspend fun saveSession(session: UserSession) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY, json.encodeToString(session)).commit()
        Unit
    }

    override suspend fun loadSession(): UserSession? = withContext(Dispatchers.IO) {
        runCatching { prefs.getString(KEY, null)?.let { json.decodeFromString<UserSession>(it) } }.getOrNull()
    }

    override suspend fun deleteSession() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY).commit()
        Unit
    }

    private companion object {
        const val FILE = "gamut_session"
        const val KEY = "session"
    }
}
