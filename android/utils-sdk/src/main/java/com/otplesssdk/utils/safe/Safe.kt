package com.otplesssdk.utils.safe

import com.otplesssdk.utils.logger.SdkLogger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Shared safe-execution helper.
 * Keeps collectors consistent: never throw, log only when enabled.
 */
internal object Safe {
    inline fun <T> tryOrNull(tag: String, what: String, block: () -> T): T? {
        return try {
            block()
        } catch (e: Exception) {
            SdkLogger.w(tag, "$what (${e.javaClass.name}: ${e.message})", e)
            null
        }
    }

    suspend inline fun <T> withTimeoutOrNull(
        tag: String,
        what: String,
        timeoutMs: Long,
        crossinline block: suspend () -> T
    ): T? {
        return try {
            withTimeout(timeoutMs) { block() }
        } catch (e: TimeoutCancellationException) {
            SdkLogger.w(tag, "$what (timeout after ${timeoutMs}ms)")
            null
        } catch (e: Exception) {
            SdkLogger.w(tag, "$what (${e.javaClass.name}: ${e.message})", e)
            null
        }
    }
}

