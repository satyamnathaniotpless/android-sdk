package com.otplesssdk.otp.models

data class OtpConfig(
    val channels: Set<OtpChannel> = setOf(OtpChannel.SMS, OtpChannel.WHATSAPP)
) {
    init {
        require(channels.isNotEmpty()) { "channels must not be empty" }
    }
}
