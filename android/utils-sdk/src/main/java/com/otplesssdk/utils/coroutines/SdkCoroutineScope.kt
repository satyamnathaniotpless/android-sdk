package com.otplesssdk.utils.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Factory for creating consistent coroutine scopes across SDKs.
 */
object SdkCoroutineScope {
    /**
     * Create a main thread coroutine scope with SupervisorJob.
     * Use this for UI-related operations that should run on the main thread.
     *
     * @param immediate If true, uses Dispatchers.Main.immediate for immediate execution.
     *                  If false, uses Dispatchers.Main (default).
     * @return A CoroutineScope configured for main thread execution
     */
    fun createMainScope(immediate: Boolean = false): CoroutineScope {
        val dispatcher = if (immediate) {
            Dispatchers.Main.immediate
        } else {
            Dispatchers.Main
        }
        return CoroutineScope(SupervisorJob() + dispatcher)
    }

    /**
     * Create an IO coroutine scope with SupervisorJob.
     * Use this for network or file operations.
     *
     * @return A CoroutineScope configured for IO operations
     */
    fun createIOScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}