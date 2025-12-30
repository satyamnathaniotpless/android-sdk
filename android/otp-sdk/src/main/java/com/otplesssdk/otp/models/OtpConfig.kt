package com.otplesssdk.otp.models

data class OtpConfig(
    val otpLength: Int = DEFAULT_OTP_LENGTH,
    val channels: Set<OtpChannel> = setOf(OtpChannel.SMS, OtpChannel.WHATSAPP),
    val whatsAppTimeoutMs: Long = DEFAULT_WHATSAPP_TIMEOUT_MS,
    val allowVariableLength: Boolean = false,
    val minOtpLength: Int = DEFAULT_MIN_OTP_LENGTH,
    val maxOtpLength: Int = DEFAULT_MAX_OTP_LENGTH,
    val otpRegexes: List<String> = emptyList(),
    val otpKeywords: List<String> = DEFAULT_KEYWORDS,
    val storedResultMaxAgeMs: Long = DEFAULT_STORED_RESULT_MAX_AGE_MS
) {
    init {
        require(otpLength > 0) { "otpLength must be > 0" }
        require(minOtpLength > 0) { "minOtpLength must be > 0" }
        require(maxOtpLength > 0) { "maxOtpLength must be > 0" }
        require(minOtpLength <= maxOtpLength) { "minOtpLength must be <= maxOtpLength" }
        for (pattern in otpRegexes) {
            require(pattern.isNotBlank()) { "otpRegexes must not contain blank patterns" }
            Regex(pattern)
        }
        require(whatsAppTimeoutMs >= 0) { "whatsAppTimeoutMs must be >= 0" }
        require(storedResultMaxAgeMs >= 0) { "storedResultMaxAgeMs must be >= 0" }
        require(channels.isNotEmpty()) { "channels must not be empty" }
    }

    internal fun lengthRange(): IntRange {
        return if (allowVariableLength) {
            minOtpLength..maxOtpLength
        } else {
            otpLength..otpLength
        }
    }

    companion object {
        const val DEFAULT_OTP_LENGTH = 6
        const val DEFAULT_MIN_OTP_LENGTH = 4
        const val DEFAULT_MAX_OTP_LENGTH = 8
        const val DEFAULT_WHATSAPP_TIMEOUT_MS = 10 * 60 * 1000L
        const val DEFAULT_STORED_RESULT_MAX_AGE_MS = 0L
        val DEFAULT_KEYWORDS = listOf("otp", "code", "passcode", "verification")
    }
}
