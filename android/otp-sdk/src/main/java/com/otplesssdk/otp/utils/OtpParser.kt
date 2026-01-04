package com.otplesssdk.otp.utils

import com.otplesssdk.otp.models.OtpConfig

internal object OtpParser {
    // Keep parsing intentionally simple:
    // - Match either 6-digit or 4-digit OTP (in that order)
    // - Return the first match from the start of the message
    // - Digits only
    private val OTP_4_OR_6 = Regex("\\b(\\d{6}|\\d{4})\\b")

    fun extractOtp(message: String): String? {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        return OTP_4_OR_6.find(trimmed)?.groupValues?.getOrNull(1)
    }

    fun isValidOtp(value: String): Boolean {
        val otp = value.trim()
        if (otp.isEmpty() || otp.any { !it.isDigit() }) {
            return false
        }
        // Only accept common OTP lengths: 4 or 6 digits.
        return otp.length == 4 || otp.length == 6
    }
}