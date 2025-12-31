package com.otplesssdk.sna.callback

import com.otplesssdk.sna.models.SnaResult

/**
 * Callback for SNA authentication results.
 *
 * The SDK invokes this exactly once per `authenticate()` call.
 */
interface SnaCallback {
    fun onResult(result: SnaResult)
}
