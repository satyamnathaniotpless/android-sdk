package com.otplesssdk.otp.models

sealed class OtpResult {
    data class Success(
        val otp: String,
        val source: OtpChannel,
        val senderId: String? = null
    ) : OtpResult()

    data class Error(
        val reason: OtpErrorReason,
        val source: OtpChannel,
        val errorKey: String? = null,
        val errorMessage: String? = null
    ) : OtpResult()
}
