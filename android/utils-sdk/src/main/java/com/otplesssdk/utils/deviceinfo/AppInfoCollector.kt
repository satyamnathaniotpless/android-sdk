package com.otplesssdk.utils.deviceinfo

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Collects application-related information.
 */
internal object AppInfoCollector {
    fun collect(context: Context, packageInfo: PackageInfo?, referrer: String?): AppInfo {
        return AppInfo(
            appVersion = packageInfo?.versionName,
            appVersionCode = getAppVersionCode(packageInfo),
            appPackageName = context.packageName,
            appTargetSdk = getTargetSdkVersion(context),
            appMinSdk = getMinSdkVersion(context),
            installSource = getInstallSource(context),
            referrer = referrer,
            appSignatureHash = getAppSignatureHash(context),
            signingCerts = getSigningCerts(context),
            firstInstallTime = packageInfo?.firstInstallTime,
            lastUpdateTime = packageInfo?.lastUpdateTime,
            isInstantApp = getIsInstantApp(context)
        )
    }
    
    private fun getAppVersionCode(packageInfo: PackageInfo?): Long? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode?.toLong()
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getTargetSdkVersion(context: Context): Int? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_META_DATA)
            packageInfo.applicationInfo.targetSdkVersion
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getMinSdkVersion(context: Context): Int? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_META_DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                packageInfo.applicationInfo.minSdkVersion
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getInstallSource(context: Context): String? {
        return try {
            val installerPackageName = context.packageManager.getInstallerPackageName(context.packageName)
            installerPackageName ?: "unknown"
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getAppSignatureHash(context: Context): String? {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo
                val signers = if (signingInfo == null) {
                    null
                } else if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
                signers?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures?.firstOrNull()
            }
            if (signature != null) {
                val md = MessageDigest.getInstance("SHA-256")
                val digest = md.digest(signature.toByteArray())
                digest.joinToString("") { "%02x".format(it) }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getSigningCerts(context: Context): List<String>? {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo
                val certificates = signingInfo?.apkContentsSigners
                certificates?.mapNotNull { cert ->
                    try {
                        val md = MessageDigest.getInstance("SHA-256")
                        val digest = md.digest(cert.toByteArray())
                        digest.joinToString("") { "%02x".format(it) }
                    } catch (e: Exception) {
                        null
                    }
                }?.takeIf { it.isNotEmpty() }
            } else {
                @Suppress("DEPRECATION")
                val signatures = packageInfo.signatures
                signatures?.mapNotNull { signature ->
                    try {
                        val md = MessageDigest.getInstance("SHA-256")
                        val digest = md.digest(signature.toByteArray())
                        digest.joinToString("") { "%02x".format(it) }
                    } catch (e: Exception) {
                        null
                    }
                }?.takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getIsInstantApp(context: Context): Boolean? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.isInstantApp
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

internal data class AppInfo(
    val appVersion: String? = null,
    val appVersionCode: Long? = null,
    val appPackageName: String? = null,
    val appTargetSdk: Int? = null,
    val appMinSdk: Int? = null,
    val installSource: String? = null,
    val referrer: String? = null,
    val appSignatureHash: String? = null,
    val signingCerts: List<String>? = null,
    val firstInstallTime: Long? = null,
    val lastUpdateTime: Long? = null,
    val isInstantApp: Boolean? = null
)
