package com.otplesssdk.otp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.otplesssdk.otp.utils.OtpDispatcher
import com.otplesssdk.otp.utils.WhatsAppOtpHelper
import com.otplesssdk.utils.logger.SdkLogger

/**
 * Receives the WhatsApp OTP callback delivered via the PendingIntent sent during handshake.
 *
 * WhatsApp may choose to set an action (OTP_RETRIEVED / OTP_ERROR) or only attach extras.
 * We normalize the intent so our dispatcher can reliably process the payload.
 */
class WhatsAppOtpCallbackReceiver : BroadcastReceiver() {
    private companion object {
        private const val TAG = "WhatsAppOtpCallbackReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val normalizedIntent = when {
            intent.action == WhatsAppOtpHelper.ACTION_OTP_ERROR ||
                intent.hasExtra(WhatsAppOtpHelper.EXTRA_ERROR) ||
                intent.hasExtra(WhatsAppOtpHelper.EXTRA_ERROR_MESSAGE) -> {
                Intent(intent).setAction(WhatsAppOtpHelper.ACTION_OTP_ERROR)
            }

            intent.action == WhatsAppOtpHelper.ACTION_OTP_RETRIEVED ||
                intent.hasExtra(WhatsAppOtpHelper.EXTRA_CODE) -> {
                Intent(intent).setAction(WhatsAppOtpHelper.ACTION_OTP_RETRIEVED)
            }

            else -> intent
        }

        val action = normalizedIntent.action
        try {
            // Safe to call both; each handler exits early if the action doesn't match.
            runCatching {
                OtpDispatcher.handleWhatsAppOtpIntent(context, normalizedIntent)
            }.onFailure { t ->
                SdkLogger.e(
                    TAG,
                    "Failed to handle WhatsApp OTP callback (handleWhatsAppOtpIntent), action=$action",
                    t
                )
            }
            runCatching {
                OtpDispatcher.handleWhatsAppErrorIntent(context, normalizedIntent)
            }.onFailure { t ->
                SdkLogger.e(
                    TAG,
                    "Failed to handle WhatsApp OTP callback (handleWhatsAppErrorIntent), action=$action",
                    t
                )
            }
        } catch (t: Throwable) {
            // Never let a BroadcastReceiver throw and crash the host app.
            SdkLogger.e(TAG, "Unexpected failure handling WhatsApp OTP callback, action=$action", t)
        }
    }
}
