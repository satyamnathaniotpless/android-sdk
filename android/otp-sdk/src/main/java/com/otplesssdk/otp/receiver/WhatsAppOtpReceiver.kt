package com.otplesssdk.otp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.otplesssdk.otp.utils.OtpDispatcher
import com.otplesssdk.otp.utils.WhatsAppOtpHelper
import com.otplesssdk.utils.logger.SdkLogger

class WhatsAppOtpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) {
            SdkLogger.w(TAG, "Ignored WhatsApp OTP broadcast: intent is null")
            return
        }
        if (intent.action != WhatsAppOtpHelper.ACTION_OTP_RETRIEVED) {
            // Ignore unrelated broadcasts.
            return
        }
        val safeContext = context ?: run {
            SdkLogger.w(TAG, "Ignored WhatsApp OTP broadcast: context is null")
            return
        }
        try {
            val senderPackage = if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("NewApi")
                sentFromPackage
            } else {
                null
            }
            OtpDispatcher.handleWhatsAppOtpIntent(safeContext, intent, senderPackage = senderPackage)
        } catch (t: Throwable) {
            SdkLogger.e(TAG, "Failed handling WhatsApp OTP broadcast", t)
        }
    }

    private companion object {
        private const val TAG = "WhatsAppOtpReceiver"
    }
}
