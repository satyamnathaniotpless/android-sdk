package com.otplesssdk.otp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.otplesssdk.otp.utils.OtpDispatcher
import com.otplesssdk.otp.utils.WhatsAppOtpHelper
import com.otplesssdk.utils.coroutines.SdkCoroutineScope
import com.otplesssdk.utils.event.EventSender
import com.otplesssdk.utils.logger.SdkLogger
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WhatsAppOtpErrorReceiver : BroadcastReceiver() {
    private companion object {
        private const val TAG = "WhatsAppOtpErrorReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Validate action first; ignore unrelated broadcasts.
        if (intent.action != WhatsAppOtpHelper.ACTION_OTP_ERROR) {
            return
        }

        // Process asynchronously to avoid ANR/crashes if dispatcher does any long-running work.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val safeIntent = Intent(intent)
        val senderPackage = if (Build.VERSION.SDK_INT >= 34) {
            @Suppress("NewApi")
            sentFromPackage
        } else {
            null
        }
        val receiverJob = SupervisorJob()
        val ioScope = SdkCoroutineScope.createIOScope(parentJob = receiverJob)

        try {
            ioScope.launch {
                try {
                    // Validate expected payload; ignore malformed broadcasts.
                    val hasAnyErrorExtra =
                        safeIntent.hasExtra(WhatsAppOtpHelper.EXTRA_ERROR) ||
                            safeIntent.hasExtra(WhatsAppOtpHelper.EXTRA_ERROR_MESSAGE)
                    if (!hasAnyErrorExtra) {
                        SdkLogger.w(TAG, "Ignored WhatsApp error broadcast: missing error extras")
                        return@launch
                    }
                    OtpDispatcher.handleWhatsAppErrorIntent(
                        appContext,
                        safeIntent,
                        senderPackage = senderPackage
                    )
                } catch (t: Throwable) {
                    SdkLogger.e(TAG, "Failed to handle WhatsApp OTP error intent", t)
                    EventSender.sendEvent(
                        eventName = "otp_receiver_exception",
                        properties = mapOf(
                            "receiver" to TAG,
                            "action" to (safeIntent.action ?: ""),
                            "exception" to t.javaClass.name,
                            "message" to (t.message ?: "")
                        )
                    )
                } finally {
                    pendingResult.finish()
                    // Ensure no work outlives this broadcast processing.
                    receiverJob.cancel()
                }
            }
        } catch (t: Throwable) {
            // If we fail to schedule background work, don't crash the app and always finish().
            SdkLogger.e(TAG, "Failed to schedule WhatsApp OTP error handling", t)
            EventSender.sendEvent(
                eventName = "otp_receiver_exception",
                properties = mapOf(
                    "receiver" to TAG,
                    "action" to (safeIntent.action ?: ""),
                    "exception" to t.javaClass.name,
                    "message" to (t.message ?: "")
                )
            )
            pendingResult.finish()
            receiverJob.cancel()
        }
    }
}
