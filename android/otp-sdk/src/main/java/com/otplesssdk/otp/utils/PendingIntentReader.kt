package com.otplesssdk.otp.utils

import android.app.PendingIntent
import android.content.Intent
import android.os.BadParcelableException
import android.os.Build

internal object PendingIntentReader {
    fun getPendingIntent(intent: Intent): PendingIntent? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    WhatsAppOtpHelper.EXTRA_PENDING_INTENT,
                    PendingIntent::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(WhatsAppOtpHelper.EXTRA_PENDING_INTENT)
            }
        } catch (_: BadParcelableException) {
            null
        }
    }
}
