package com.otplesssdk.utils.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Coroutine bridge for OkHttp calls.
 *
 * This is intentionally lightweight so SDK modules can share the same, correct cancellation behavior:
 * - Cancelling the coroutine cancels the underlying OkHttp call.
 * - If the coroutine is already cancelled, the response is closed immediately.
 */
public suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isActive) return
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) {
                    response.close()
                    return
                }
                continuation.resume(response)
            }
        })
    }

