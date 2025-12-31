package com.otplesssdk.utils.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/**
 * Factory for creating consistent coroutine scopes across SDKs.
 */
object SdkCoroutineScope {
    /**
     * Create a main thread coroutine scope with SupervisorJob.
     * Use this for UI-related operations that should run on the main thread.
     *
     * IMPORTANT: The returned scope is not lifecycle-aware by itself. Prefer passing a [parentJob]
     * tied to your component lifecycle (or call `scope.cancel()` when the work is no longer needed)
     * to avoid leaking coroutines after the component is destroyed.
     *
     * @param parentJob Optional parent job that controls scope lifecycle/cancellation.
     * @param immediate If true, uses Dispatchers.Main.immediate for immediate execution.
     *                  If false, uses Dispatchers.Main (default).
     * @return A CoroutineScope configured for main thread execution
     */
    @JvmStatic
    fun createMainScope(parentJob: Job? = null, immediate: Boolean = false): CoroutineScope {
        val dispatcher = if (immediate) {
            Dispatchers.Main.immediate
        } else {
            Dispatchers.Main
        }
        return CoroutineScope(SupervisorJob(parentJob) + dispatcher)
    }

    /**
     * Create an IO coroutine scope with SupervisorJob.
     * Use this for network or file operations.
     *
     * IMPORTANT: The returned scope is not lifecycle-aware by itself. Prefer passing a [parentJob]
     * tied to your component lifecycle (or call `scope.cancel()` when the work is no longer needed)
     * to avoid leaking coroutines after the component is destroyed.
     *
     * @param parentJob Optional parent job that controls scope lifecycle/cancellation.
     * @return A CoroutineScope configured for IO operations
     */
    @JvmStatic
    fun createIOScope(parentJob: Job? = null): CoroutineScope {
        return CoroutineScope(SupervisorJob(parentJob) + Dispatchers.IO)
    }
}