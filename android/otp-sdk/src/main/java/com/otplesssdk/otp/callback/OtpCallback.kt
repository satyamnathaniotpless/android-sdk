package com.otplesssdk.otp.callback

import com.otplesssdk.otp.models.OtpResult

/**
 * Callback for OTP listener results.
 */
interface OtpCallback {
    fun onResult(result: OtpResult)
}
