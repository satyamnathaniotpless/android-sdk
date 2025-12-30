package com.otplesssdk.otp.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import com.otplesssdk.otp.models.AppHash
import com.otplesssdk.utils.logger.SdkLogger
import java.security.MessageDigest

internal object SmsRetrieverAppHash {
    private const val TAG = "SmsRetrieverAppHash"

    fun getAppHashes(context: Context): Set<AppHash> {
        val packageName = context.packageName
        val signatures = getSignatures(context, packageName)
        if (signatures.isEmpty()) {
            return emptySet()
        }
        return signatures.mapNotNull { signature ->
            hash(packageName, signature)?.let { hashValue ->
                AppHash(packageName = packageName, hash = hashValue)
            }
        }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun getSignatures(context: Context, packageName: String): List<String> {
        return try {
            val packageManager = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = info.signingInfo ?: return emptyList()
                signingInfo.apkContentsSigners
                    ?.map { it.toCharsString() }
                    .orEmpty()
            } else {
                val info = packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                )
                info.signatures?.map { it.toCharsString() }.orEmpty()
            }
        } catch (exception: Exception) {
            SdkLogger.w(TAG, "Failed to read signatures", exception)
            emptyList()
        }
    }

    private fun hash(packageName: String, signature: String): String? {
        return try {
            val appInfo = "$packageName $signature"
            val messageDigest = MessageDigest.getInstance("SHA-256")
            val hashSignature = messageDigest.digest(appInfo.toByteArray(Charsets.UTF_8))
            val truncated = hashSignature.copyOfRange(0, 9)
            val base64 = Base64.encodeToString(
                truncated,
                Base64.NO_PADDING or Base64.NO_WRAP
            )
            if (base64.length >= 11) {
                base64.substring(0, 11)
            } else {
                base64
            }
        } catch (exception: Exception) {
            SdkLogger.w(TAG, "Failed to generate app hash", exception)
            null
        }
    }
}
