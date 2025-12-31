package com.otplesssdk.otp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.otplesssdk.otp.utils.OtpDispatcher
import com.otplesssdk.utils.logger.SdkLogger

class SmsRetrieverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) {
            SdkLogger.w(TAG, "Ignored SMS Retriever broadcast: intent is null")
            return
        }
        if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) {
            SdkLogger.w(TAG, "Ignored SMS Retriever broadcast: unexpected action=${intent.action}")
            return
        }

        val safeContext = context ?: run {
            SdkLogger.w(TAG, "Ignored SMS Retriever broadcast: context is null")
            return
        }

        try {
            OtpDispatcher.handleSmsIntent(safeContext, intent)
        } catch (exception: Exception) {
            SdkLogger.e(TAG, "Failed handling SMS Retriever broadcast", exception)
        }
    }

    private companion object {
        private const val TAG = "SmsRetrieverReceiver"
    }
}
