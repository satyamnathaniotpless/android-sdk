package com.otplesssdk.otp.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.otplesssdk.utils.deviceinfo.DeviceInfoCollector

internal object WhatsAppOtpHelper {
    const val PACKAGE_WHATSAPP = "com.whatsapp"
    const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    const val ACTION_OTP_REQUESTED = "com.whatsapp.otp.OTP_REQUESTED"
    const val ACTION_OTP_RETRIEVED = "com.whatsapp.otp.OTP_RETRIEVED"
    const val ACTION_OTP_ERROR = "com.whatsapp.otp.OTP_ERROR"

    const val EXTRA_PENDING_INTENT = "_ci_"
    const val EXTRA_CODE = "code"
    const val EXTRA_ERROR = "error"
    const val EXTRA_ERROR_MESSAGE = "error_message"

    fun isWhatsAppInstalled(context: Context): Boolean {
        return DeviceInfoCollector.getAppPresenceInfo(context.applicationContext).whatsappAny
    }

    fun sendHandshake(context: Context): Boolean {
        val presence = DeviceInfoCollector.getAppPresenceInfo(context.applicationContext)
        val installedPackages = buildList {
            if (presence.whatsapp) add(PACKAGE_WHATSAPP)
            if (presence.whatsappBusiness) add(PACKAGE_WHATSAPP_BUSINESS)
        }
        if (installedPackages.isEmpty()) {
            return false
        }

        // Per Meta's "without SDK" guidance, WhatsApp uses the PendingIntent creator package
        // for eligibility checks. We do not rely on it for delivery; WhatsApp broadcasts OTP_RETRIEVED/OTP_ERROR.
        // On Android 12+ we must explicitly declare mutability; immutable is sufficient and safer here.
        val flags = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }) or PendingIntent.FLAG_UPDATE_CURRENT

        val pendingIntent = PendingIntent.getActivity(
            context,
            /* requestCode = */ 0,
            Intent(),
            flags
        )

        for (packageName in installedPackages) {
            val intentToWhatsApp = Intent().apply {
                setPackage(packageName)
                action = ACTION_OTP_REQUESTED
                val extras = Bundle()
                extras.putParcelable(EXTRA_PENDING_INTENT, pendingIntent)
                putExtras(extras)
            }
            context.sendBroadcast(intentToWhatsApp)
        }
        return true
    }

    fun isValidCreatorPackage(packageName: String?): Boolean {
        return packageName == PACKAGE_WHATSAPP || packageName == PACKAGE_WHATSAPP_BUSINESS
    }
}
