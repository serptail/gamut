package com.gamut.core.sync

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton
import kotlinx.serialization.json.Json

object SyncConfig {
    val enabled: Boolean get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
}

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides
    @Singleton
    fun supabase(@ApplicationContext context: Context): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL.ifBlank { "https://unconfigured.invalid" },
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "unconfigured" },
    ) {
        defaultSerializer = KotlinXSerializer(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = true
            },
        )
        install(Auth) { sessionManager = EncryptedSessionManager(context) }
        install(Postgrest)
        install(Functions)
        install(Storage)
    }
}
