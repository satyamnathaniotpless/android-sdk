package com.otplesssdk.sna

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import com.otplesssdk.sna.models.FailureReason
import com.otplesssdk.sna.models.SimNetworkInfo
import com.otplesssdk.sna.models.SnaResult
import com.otplesssdk.sna.models.SnaTimings
import com.otplesssdk.sna.utils.NetworkUtils
import com.otplesssdk.sna.utils.SimUtils
import com.otplesssdk.sna.utils.SnaConfig
import com.otplesssdk.sna.utils.SnaUrlHandler
import com.otplesssdk.utils.event.EventSender
import com.otplesssdk.utils.logger.SdkLogger
import kotlinx.coroutines.runBlocking

/**
 * Main SDK class for SIM and network information
 * 
 * This SDK provides:
 * - SIM and network information (MCC, MNC, mobile data status)
 * - Mobile data network status check
 * - SNA authentication via URL over cellular
 */
class SNASdk private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var instance: SNASdk? = null

        private const val TAG = "SNASdk"

        private data class EventSenderConfig(
            val appId: String?,
            val eventEndpoint: String?
        )

        /**
         * Tracks EventSender configuration that this SDK has already applied.
         * Used to detect re-initialization calls with different non-null parameters.
         */
        @Volatile
        private var appliedEventSenderConfig: EventSenderConfig? = null

        /**
         * Tracks an EventSender config currently scheduled to be applied outside the init lock.
         * This prevents a race where we "record" a config change but never actually apply it.
         */
        @Volatile
        private var pendingEventSenderConfig: EventSenderConfig? = null
        
        private const val SDK_NAME = "sna-sdk"
        private const val SDK_VERSION = "1.0.0"
        
        /**
         * Initialize the SDK with application context
         * 
         * @param context Application context
         * @param appId Optional app ID for event tracking
         * @param eventEndpoint Optional event endpoint URL for event tracking
         * @return SNASdk instance
         */
        @JvmStatic
        @JvmOverloads
        fun initialize(
            context: Context,
            appId: String? = null,
            eventEndpoint: String? = null
        ): SNASdk {
            val appContext = context.applicationContext
            val normalizedAppId = appId?.takeIf { it.isNotBlank() }
            val normalizedEndpoint = eventEndpoint?.takeIf { it.isNotBlank() }

            var sdk: SNASdk
            var shouldSendInitEvent = false
            var configToApply: EventSenderConfig? = null

            synchronized(this) {
                val existing = instance
                if (existing == null) {
                    sdk = SNASdk(appContext).also { instance = it }
                    shouldSendInitEvent = true
                } else {
                    sdk = existing
                }

                val currentConfig = pendingEventSenderConfig ?: appliedEventSenderConfig
                val isFirstEventSenderInit = currentConfig == null
                val wantsUpdateWithDifferentNonNullParams =
                    (normalizedAppId != null && normalizedAppId != currentConfig?.appId) ||
                    (normalizedEndpoint != null && normalizedEndpoint != currentConfig?.eventEndpoint)

                if (isFirstEventSenderInit || wantsUpdateWithDifferentNonNullParams) {
                    if (wantsUpdateWithDifferentNonNullParams) {
                        SdkLogger.w(
                            TAG,
                            "initialize() called again with different non-null parameters; reinitializing EventSender. " +
                                "appId: ${currentConfig?.appId} -> $normalizedAppId, " +
                                "eventEndpoint: ${currentConfig?.eventEndpoint} -> $normalizedEndpoint"
                        )
                    }

                    val mergedConfig = EventSenderConfig(
                        appId = normalizedAppId ?: currentConfig?.appId,
                        eventEndpoint = normalizedEndpoint ?: currentConfig?.eventEndpoint
                    )

                    // Coalesce concurrent updates: if the same config is already pending, no need to schedule again.
                    if (pendingEventSenderConfig != mergedConfig) {
                        pendingEventSenderConfig = mergedConfig
                        configToApply = mergedConfig
                    }
                }
            }

            // Potentially slow work should happen outside the synchronized block.
            configToApply?.let { cfg ->
                EventSender.initialize(
                    context = appContext,
                    sdkName = SDK_NAME,
                    sdkVersion = SDK_VERSION,
                    appId = cfg.appId,
                    eventEndpoint = cfg.eventEndpoint
                )
                synchronized(this) {
                    if (pendingEventSenderConfig == cfg) {
                        appliedEventSenderConfig = cfg
                        pendingEventSenderConfig = null
                    }
                }
            }

            if (shouldSendInitEvent) {
                EventSender.sendEvent(
                    eventName = "sna_sdk_initialized",
                    properties = emptyMap()
                )
            }

            return sdk
        }
        
        /**
         * Get the initialized SDK instance
         * 
         * @return SNASdk instance or null if not initialized
         */
        @JvmStatic
        fun getInstance(): SNASdk? {
            return instance
        }

        /**
         * Enable or disable debug logging for the SDK.
         */
        @JvmStatic
        fun setLoggingEnabled(enabled: Boolean) {
            SdkLogger.setEnabled(enabled)
        }
    }

    /**
     * Get SIM and network details
     * 
     * @return SimNetworkInfo containing MCC, MNC, and mobile data status
     */
    fun getSimNetworkInfo(): SimNetworkInfo {
        SdkLogger.d("SNASdk", "getSimNetworkInfo() called")
        val info = SimUtils.getSimNetworkInfo(context)
        SdkLogger.d("SNASdk", "getSimNetworkInfo() result - MCC: ${info.mcc}, MNC: ${info.mnc}, Mobile Data: ${info.isMobileDataEnabled}")
        
        // Send event
        EventSender.sendEvent(
            eventName = "sna_sim_network_info_requested",
            properties = mapOf(
                "mcc" to info.mcc,
                "mnc" to info.mnc,
                "network_name" to info.networkName,
                "is_mobile_data_enabled" to info.isMobileDataEnabled
            )
        )
        
        return info
    }
    
    /**
     * Check if mobile data network is currently enabled
     * 
     * @return true if mobile data is ON, false if OFF
     */
    fun isMobileDataEnabled(): Boolean {
        SdkLogger.d("SNASdk", "isMobileDataEnabled() called")
        val enabled = NetworkUtils.isMobileDataAvailable(context)
        SdkLogger.d("SNASdk", "isMobileDataEnabled() result: $enabled")
        
        // Send event
        EventSender.sendEvent(
            eventName = "sna_mobile_data_check",
            properties = mapOf(
                "is_enabled" to enabled
            )
        )
        
        return enabled
    }

    /**
     * Run SNA authentication over cellular.
     *
     * This is a suspending API: the caller controls the threading model.
     * - If called from Main, it will suspend while work runs on IO and resume on Main.
     * - If called from a background coroutine, it will resume on that background context.
     */
    suspend fun authenticate(
        url: String,
        timeoutSeconds: Long = SnaConfig.DEFAULT_TIMEOUT_SECONDS
    ): SnaResult {
        SdkLogger.d("SNASdk", "authenticate(url, timeoutSeconds) called")

        // Send authenticate started event
        EventSender.sendEvent(
            eventName = "sna_authenticate_started",
            properties = mapOf(
                "url" to url,
                "timeout_seconds" to timeoutSeconds
            )
        )

        val startMs = SystemClock.elapsedRealtime()
        return try {
            val result: SnaResult = SnaUrlHandler.execute(
                context = context,
                urlString = url,
                timeoutSeconds = timeoutSeconds
            )

            // Send success or failure event based on result
            when (result) {
                is SnaResult.Success -> {
                    EventSender.sendEvent(
                        eventName = "sna_authenticate_success",
                        properties = buildSuccessEventProperties(result, url)
                    )
                }

                is SnaResult.Failure -> {
                    EventSender.sendEvent(
                        eventName = "sna_authenticate_failure",
                        properties = buildFailureEventProperties(result, url)
                    )
                }
            }

            result
        } catch (e: Exception) {
            val failure = SnaResult.Failure(
                reason = FailureReason.UNKNOWN_ERROR,
                detail = e.message ?: "Unknown error",
                timings = SnaTimings(totalMs = SystemClock.elapsedRealtime() - startMs)
            )

            EventSender.sendEvent(
                eventName = "sna_authenticate_failure",
                properties = buildFailureEventProperties(failure, url)
            )
            SdkLogger.e("SNASdk", "authenticate() failed with exception", e)
            failure
        }
    }

    /**
     * Blocking wrapper for Java/non-coroutine callers.
     *
     * IMPORTANT: This blocks the calling thread. Do not call from the main thread.
     */
    @JvmOverloads
    fun authenticateBlocking(
        url: String,
        timeoutSeconds: Long = SnaConfig.DEFAULT_TIMEOUT_SECONDS
    ): SnaResult {
        check(Looper.myLooper() != Looper.getMainLooper()) {
            "authenticateBlocking() must not be called on the main thread. Use authenticate() from a coroutine instead."
        }
        return runBlocking {
            authenticate(url = url, timeoutSeconds = timeoutSeconds)
        }
    }
    
    /**
     * Build event properties for successful authentication
     */
    private fun buildSuccessEventProperties(result: SnaResult.Success, url: String): Map<String, Any?> {
        val props = mutableMapOf<String, Any?>(
            "url" to url,
            "http_code" to result.response.httpCode,
            "redirect_count" to result.redirects.size,
            "total_ms" to result.timings.totalMs,
            "final_url" to result.response.finalUrl,
            "has_response_body" to (result.response.body != null),
            "response_body_truncated" to result.response.bodyTruncated
        )
        
        result.timings.cellularAcquireMs?.let { props["cellular_acquire_ms"] = it }
        result.timings.processBindMs?.let { props["process_bind_ms"] = it }
        
        return props
    }
    
    /**
     * Build event properties for failed authentication
     */
    private fun buildFailureEventProperties(result: SnaResult.Failure, url: String): Map<String, Any?> {
        val props = mutableMapOf<String, Any?>(
            "url" to url,
            "failure_reason" to result.reason.name,
            "redirect_count" to result.redirects.size,
            "total_ms" to result.timings.totalMs
        )
        
        result.detail?.let { props["failure_detail"] = it }
        result.timings.cellularAcquireMs?.let { props["cellular_acquire_ms"] = it }
        result.timings.processBindMs?.let { props["process_bind_ms"] = it }
        result.response?.httpCode?.let { props["http_code"] = it }
        result.response?.finalUrl?.let { props["final_url"] = it }
        
        return props
    }
}
