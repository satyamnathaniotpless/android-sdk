package com.otplesssdk.otp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.otplesssdk.otp.utils.OtpDispatcher

class SmsRetrieverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        OtpDispatcher.handleSmsIntent(context, intent)
    }
}
