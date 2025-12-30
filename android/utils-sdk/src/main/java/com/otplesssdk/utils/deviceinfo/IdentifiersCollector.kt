package com.otplesssdk.utils.deviceinfo

import android.content.Context
import android.media.MediaDrm
import android.os.Build
import android.os.Looper
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.otplesssdk.utils.coroutines.SdkCoroutineScope
import com.otplesssdk.utils.logger.SdkLogger
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Collects device identifiers (GAID, DRM ID).
 */
internal object IdentifiersCollector {
    private const val TAG = "IdentifiersCollector"

    // Cached install referrer (collected in background)
    @Volatile
    private var cachedReferrer: String? = null

    fun collect(context: Context): IdentifiersInfo {
        return IdentifiersInfo(
            gaid = getGaid(context),
            drmId = getDrmId()
        )
    }

    /**
     * Get cached install referrer.
     */
    fun getReferrer(): String? {
        return cachedReferrer
    }

    /**
     * Start collecting install referrer in the background (non-blocking).
     */
    fun startReferrerCollection(context: Context) {
        val scope = SdkCoroutineScope.createIOScope()
        scope.launch {
            try {
                val referrerClient = InstallReferrerClient.newBuilder(context).build()
                referrerClient.startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        try {
                            when (responseCode) {
                                InstallReferrerClient.InstallReferrerResponse.OK -> {
                                    try {
                                        val response: ReferrerDetails = referrerClient.installReferrer
                                        val referrerUrl = response.installReferrer
                                        cachedReferrer = referrerUrl
                                        SdkLogger.d(TAG, "Install referrer obtained: $referrerUrl")
                                    } catch (e: Exception) {
                                        SdkLogger.w(TAG, "Failed to get install referrer: ${e.message}")
                                        cachedReferrer = null
                                    }
                                }
                                InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                                    SdkLogger.d(TAG, "Install Referrer API not supported")
                                    cachedReferrer = null
                                }
                                InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                                    SdkLogger.w(TAG, "Install Referrer service unavailable")
                                    cachedReferrer = null
                                }
                                else -> {
                                    SdkLogger.w(TAG, "Install Referrer response code: $responseCode")
                                    cachedReferrer = null
                                }
                            }
                        } finally {
                            try {
                                referrerClient.endConnection()
                            } catch (_: Exception) {
                            }
                        }
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        SdkLogger.d(TAG, "Install Referrer service disconnected")
                        try {
                            referrerClient.endConnection()
                        } catch (_: Exception) {
                        }
                    }
                })
            } catch (e: Exception) {
                SdkLogger.w(TAG, "Install Referrer not available: ${e.message}")
                cachedReferrer = null
            }
        }
    }

    /**
     * Get Google Advertising ID (GAID).
     * Returns null if Google Play Services is not available or if there's an error.
     */
    internal fun getGaid(context: Context): String? {
        // AdvertisingIdClient may throw if called on the main thread (common cause of GAID=null in sync flows).
        if (Looper.myLooper() == Looper.getMainLooper()) {
            SdkLogger.w(TAG, "GAID requested on main thread; returning null to avoid potential deadlock.")
            return null
        }
        return try {
            // Use reflection to avoid adding dependency if not needed
            val advertisingIdClientClass = Class.forName("com.google.android.gms.ads.identifier.AdvertisingIdClient")
            val getAdvertisingIdInfoMethod = advertisingIdClientClass.getMethod("getAdvertisingIdInfo", Context::class.java)
            val advertisingIdInfo = getAdvertisingIdInfoMethod.invoke(null, context)
            if (advertisingIdInfo == null) {
                return null
            }
            val limitAdTrackingMethod = advertisingIdInfo.javaClass.getMethod("isLimitAdTrackingEnabled")
            val isLimited = limitAdTrackingMethod.invoke(advertisingIdInfo) as? Boolean
            if (isLimited != false) {
                return null
            }
            val getIdMethod = advertisingIdInfo.javaClass.getMethod("getId")
            getIdMethod.invoke(advertisingIdInfo) as? String
        } catch (e: Exception) {
            // Google Play Services not available or reflection failed
            SdkLogger.d(TAG, "GAID unavailable: ${e.javaClass.name}: ${e.message}")
            null
        }
    }

    /**
     * Get MediaDrm ID (DRM ID).
     * Returns null if MediaDrm is not available or if there's an error.
     */
    private fun getDrmId(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                // Use Widevine DRM (most common)
                val widevineUuid = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
                val mediaDrm = MediaDrm(widevineUuid)
                val deviceIdBytes = mediaDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
                mediaDrm.release()
                // Convert bytes to hex string
                deviceIdBytes?.joinToString("") { "%02x".format(it) }
            } else {
                null
            }
        } catch (e: Exception) {
            // MediaDrm not available or error occurred
            null
        }
    }
}

internal data class IdentifiersInfo(
    val gaid: String? = null,
    val drmId: String? = null
)
