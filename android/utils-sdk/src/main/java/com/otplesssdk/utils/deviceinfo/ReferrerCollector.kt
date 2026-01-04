package com.otplesssdk.utils.deviceinfo

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.otplesssdk.utils.concurrency.Once
import com.otplesssdk.utils.coroutines.SdkCoroutineScope
import com.otplesssdk.utils.logger.SdkLogger
import com.otplesssdk.utils.safe.Safe
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal object ReferrerCollector {
    private const val TAG = "ReferrerCollector"

    // Install Referrer runs via async callbacks; we keep a small process-wide cache here.
    private val startOnce = Once()

    private val job = SupervisorJob()
    private val scope = SdkCoroutineScope.createIOScope(parentJob = job)

    @Volatile
    private var cachedReferrer: String? = null

    @Volatile
    private var cachedReferrerTimestampMs: Long = 0L

    /**
     * Returns the last successfully fetched referrer, if any.
     *
     * This does not trigger fetching; [start] must be called separately (DeviceInfoCollector does this).
     */
    fun getCached(): ReferrerInfo? {
        val ref = cachedReferrer ?: return null
        return ReferrerInfo(referrer = ref, timestampMs = cachedReferrerTimestampMs)
    }

    fun start(context: Context) {
        // startOnce prevents repeated Play Store binder connections in the same process.
        if (!startOnce.tryStart()) return

        scope.launch {
            try {
                val client = InstallReferrerClient.newBuilder(context).build()
                client.startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        var success = false
                        try {
                            when (responseCode) {
                                InstallReferrerClient.InstallReferrerResponse.OK -> {
                                    val referrer = Safe.tryOrNull(TAG, "Read install referrer") {
                                        val response: ReferrerDetails = client.installReferrer
                                        response.installReferrer
                                    }
                                    if (referrer != null) {
                                        cachedReferrer = referrer
                                        cachedReferrerTimestampMs = System.currentTimeMillis()
                                        success = true
                                        SdkLogger.d(TAG, "Install referrer obtained")
                                    } else {
                                        cachedReferrer = null
                                        cachedReferrerTimestampMs = 0L
                                    }
                                }
                                InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                                    cachedReferrer = null
                                    cachedReferrerTimestampMs = 0L
                                }
                                InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                                    cachedReferrer = null
                                    cachedReferrerTimestampMs = 0L
                                }
                                else -> {
                                    cachedReferrer = null
                                    cachedReferrerTimestampMs = 0L
                                }
                            }
                        } finally {
                            // If we didn't succeed, allow future retries (DeviceInfoCollector TTL-gates retries).
                            if (!success) startOnce.reset()
                            try {
                                client.endConnection()
                            } catch (_: Exception) {
                            }
                        }
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        try {
                            client.endConnection()
                        } catch (_: Exception) {
                        }
                        // Disconnected before success -> allow a future retry.
                        startOnce.reset()
                    }
                })
            } catch (e: Exception) {
                startOnce.reset()
                cachedReferrer = null
                cachedReferrerTimestampMs = 0L
                SdkLogger.d(TAG, "Install referrer unavailable: ${e.javaClass.name}: ${e.message}")
            }
        }
    }
}

internal data class ReferrerInfo(
    val referrer: String? = null,
    val timestampMs: Long? = null,
)

