package com.otplesssdk.otp.utils

import com.otplesssdk.otp.callback.OtpCallback
import com.otplesssdk.otp.models.OtpChannel
import com.otplesssdk.otp.models.OtpConfig
import kotlinx.coroutines.Job

internal class OtpSession(
    val config: OtpConfig,
    val callback: OtpCallback
) {
    var smsCompleted: Boolean = !config.channels.contains(OtpChannel.SMS)
    var whatsAppCompleted: Boolean = !config.channels.contains(OtpChannel.WHATSAPP)
    var finished: Boolean = false
    var whatsAppTimeoutJob: Job? = null

    fun markDone(source: OtpChannel) {
        when (source) {
            OtpChannel.SMS -> smsCompleted = true
            OtpChannel.WHATSAPP -> whatsAppCompleted = true
        }
    }

    fun isCompleted(source: OtpChannel): Boolean {
        return when (source) {
            OtpChannel.SMS -> smsCompleted
            OtpChannel.WHATSAPP -> whatsAppCompleted
        }
    }

    fun allDone(): Boolean = smsCompleted && whatsAppCompleted

    fun clear() {
        cancelWhatsAppTimeout()
    }

    fun cancelWhatsAppTimeout() {
        whatsAppTimeoutJob?.cancel()
        whatsAppTimeoutJob = null
    }
}

