package com.otplesssdk.otp.models

sealed class OtpResult {
    data class Success(
        val otp: String,
        val source: OtpChannel,
        /**
         * SMS originating address as returned by Play services (EXTRA_SMS_ORIGINATING_ADDRESS).
         *
         * Only applicable to [OtpChannel.SMS] and may be null depending on Google Play services/device support.
         */
        val senderAddress: String? = null,
        /**
         * Package name of the application that delivered this OTP result, when available.
         *
         * For WhatsApp zero-tap this may be `com.whatsapp` or `com.whatsapp.w4b` on Android 14+.
         * On older Android versions this may be null because the platform does not reliably expose
         * the broadcast sender.
         */
        val senderPackage: String? = null
    ) : OtpResult()

    data class Error(
        val reason: OtpErrorReason,
        val source: OtpChannel,
        val errorKey: String? = null,
        val errorMessage: String? = null,
        /**
         * Package name of the application that delivered this error, when available.
         *
         * For WhatsApp zero-tap this may be `com.whatsapp` or `com.whatsapp.w4b` on Android 14+.
         */
        val senderPackage: String? = null
    ) : OtpResult()
}
