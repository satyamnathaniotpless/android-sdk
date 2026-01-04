package com.otplesssdk.utils.deviceinfo

import android.content.Context
import android.content.pm.PackageManager

internal object AppPresenceCollector {
    private const val WHATSAPP = "com.whatsapp"
    private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    private const val TELEGRAM = "org.telegram.messenger"
    private const val TRUECALLER = "com.truecaller"
    private const val VIBER = "com.viber.voip"

    fun collect(context: Context): AppPresenceInfo {
        val pm = context.packageManager
        val whatsapp = isInstalled(pm, WHATSAPP)
        val whatsappBusiness = isInstalled(pm, WHATSAPP_BUSINESS)
        return AppPresenceInfo(
            whatsapp = whatsapp,
            whatsappBusiness = whatsappBusiness,
            telegram = isInstalled(pm, TELEGRAM),
            truecaller = isInstalled(pm, TRUECALLER),
            viber = isInstalled(pm, VIBER),
            whatsappAny = whatsapp || whatsappBusiness
        )
    }

    @Suppress("DEPRECATION")
    private fun isInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}

data class AppPresenceInfo(
    val whatsapp: Boolean = false,
    val whatsappBusiness: Boolean = false,
    val whatsappAny: Boolean = false,
    val telegram: Boolean = false,
    val truecaller: Boolean = false,
    val viber: Boolean = false,
)

