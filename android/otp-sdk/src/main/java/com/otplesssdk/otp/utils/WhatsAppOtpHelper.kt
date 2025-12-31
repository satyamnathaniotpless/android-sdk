package com.otplesssdk.otp.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.otplesssdk.otp.receiver.WhatsAppOtpCallbackReceiver

internal object WhatsAppOtpHelper {
    const val PACKAGE_WHATSAPP = "com.whatsapp"
    const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    const val ACTION_OTP_REQUESTED = "com.whatsapp.otp.OTP_REQUESTED"
    const val ACTION_OTP_RETRIEVED = "com.whatsapp.otp.OTP_RETRIEVED"
    const val ACTION_OTP_ERROR = "com.whatsapp.otp.OTP_ERROR"

    // Unique action for the PendingIntent target in the host app.
    const val ACTION_WHATSAPP_OTP_CALLBACK = "com.otplesssdk.otp.WHATSAPP_OTP_CALLBACK"

    const val EXTRA_PENDING_INTENT = "_ci_"
    const val EXTRA_CODE = "code"
    const val EXTRA_ERROR = "error"
    const val EXTRA_ERROR_MESSAGE = "error_message"

    fun isWhatsAppInstalled(context: Context): Boolean {
        return isPackageInstalled(context, PACKAGE_WHATSAPP) ||
            isPackageInstalled(context, PACKAGE_WHATSAPP_BUSINESS)
    }

    fun sendHandshake(context: Context): Boolean {
        val packages = listOf(PACKAGE_WHATSAPP, PACKAGE_WHATSAPP_BUSINESS)
        val installedPackages = packages.filter { isPackageInstalled(context, it) }
        if (installedPackages.isEmpty()) {
            return false
        }

        // WhatsApp needs to be able to attach OTP extras to the callback intent,
        // so the PendingIntent must be mutable on Android 12+.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }

        val callbackIntent = Intent(context, WhatsAppOtpCallbackReceiver::class.java).apply {
            action = ACTION_WHATSAPP_OTP_CALLBACK
            setPackage(context.packageName)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            /* requestCode = */ 1001,
            callbackIntent,
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

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
