package com.gamut.core.common

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

sealed class AppFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable) : AppFailure("Network request failed", cause)
    class NoMatch : AppFailure("No verified source was found")
    class Unknown(cause: Throwable) : AppFailure("The operation failed", cause)
}

open class DispatcherProvider(
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
    val main: CoroutineDispatcher = Dispatchers.Main,
)

@Singleton
class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider()
