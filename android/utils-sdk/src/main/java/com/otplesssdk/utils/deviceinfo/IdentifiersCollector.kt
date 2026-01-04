package com.otplesssdk.utils.deviceinfo

import android.content.Context
import android.media.MediaDrm
import android.os.Build
import android.os.Looper
import android.provider.Settings
import com.otplesssdk.utils.safe.Safe
import java.util.UUID

internal object IdentifiersCollector {
    /**
     * Collects identifiers best-effort.
     *
     * Notes:
     * - GAID is intentionally not fetched on the main thread (can block via IPC -> ANR risk).
     * - ANDROID_ID does not require a permission but is not guaranteed to be stable across reinstalls/devices.
     * - DRM ID can be unavailable depending on OEM/DRM availability.
     */
    fun collect(context: Context): IdentifiersInfo {
        return IdentifiersInfo(
            gaid = getGaid(context),
            drmId = getDrmId(),
            androidId = getAndroidId(context)
        )
    }

    internal fun getGaid(context: Context): String? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return null
        }
        return Safe.tryOrNull("IdentifiersCollector", "gaid") {
            // Reflection keeps this module independent of an explicit Play Services Ads Identifier dependency.
            val advertisingIdClientClass = Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient")
            val getAdvertisingIdInfoMethod =
                advertisingIdClientClass.getMethod("getAdvertisingIdInfo", Context::class.java)
            val advertisingIdInfo = getAdvertisingIdInfoMethod.invoke(null, context) ?: return@tryOrNull null

            val limitAdTrackingMethod = advertisingIdInfo.javaClass.getMethod("isLimitAdTrackingEnabled")
            val isLimited = limitAdTrackingMethod.invoke(advertisingIdInfo) as? Boolean
            // Respect user opt-out / limit ad tracking.
            if (isLimited != false) return@tryOrNull null

            val getIdMethod = advertisingIdInfo.javaClass.getMethod("getId")
            getIdMethod.invoke(advertisingIdInfo) as? String
        }
    }

    internal fun getAndroidId(context: Context): String? {
        return try {
            Settings.Secure.getString(context.applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (_: Exception) {
            null
        }
    }

    private fun getDrmId(): String? {
        // Widevine is the most common DRM scheme on Android devices.
        val widevineUuid = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
        var mediaDrm: MediaDrm? = null

        return try {
            mediaDrm = MediaDrm(widevineUuid)
            val deviceIdBytes = mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            deviceIdBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        } finally {
            try {
                @Suppress("DEPRECATION")
                mediaDrm?.release()
            } catch (t: Throwable) {
            }
        }
    }
}

data class IdentifiersInfo(
    val gaid: String? = null,
    val drmId: String? = null,
    val androidId: String? = null,
)
