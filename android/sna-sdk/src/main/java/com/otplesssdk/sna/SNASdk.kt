package com.otplesssdk.sna

import android.content.Context
import android.os.SystemClock
import com.otplesssdk.sna.callback.SnaCallback
import com.otplesssdk.sna.models.FailureReason
import com.otplesssdk.sna.models.SimNetworkInfo
import com.otplesssdk.sna.models.SnaResult
import com.otplesssdk.sna.models.SnaTimings
import com.otplesssdk.sna.utils.NetworkUtils
import com.otplesssdk.sna.utils.SimUtils
import com.otplesssdk.sna.utils.SnaConfig
import com.otplesssdk.sna.utils.SnaUrlHandler
import com.otplesssdk.utils.coroutines.SdkCoroutineScope
import com.otplesssdk.utils.event.EventSender
import com.otplesssdk.utils.logger.SdkLogger
import kotlinx.coroutines.launch

/**
 * Main SDK class for SIM and network information
 * 
 * This SDK provides:
 * - SIM and network information (MCC, MNC, mobile data status)
 * - Mobile data network status check
 * - SNA authentication via URL over cellular
 */
class SNASdk private constructor(private val context: Context) {
    private val scope = SdkCoroutineScope.createMainScope(immediate = false)

    companion object {
        @Volatile
        private var instance: SNASdk? = null
        
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
            return instance ?: synchronized(this) {
                instance ?: SNASdk(context.applicationContext).also { 
                    instance = it
                    // Initialize EventSender
                    EventSender.initialize(
                        context = context.applicationContext,
                        sdkName = SDK_NAME,
                        sdkVersion = SDK_VERSION,
                        appId = appId,
                        eventEndpoint = eventEndpoint
                    )
                    // Send initialization event
                    EventSender.sendEvent(
                        eventName = "sna_sdk_initialized",
                        properties = emptyMap()
                    )
                }
            }
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
            SdkLogger.setDebugEnabled(enabled)
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

    fun authenticate(
        url: String,
        callback: SnaCallback
    ) {
        SdkLogger.d("SNASdk", "authenticate(url, callback) called")
        authenticate(
            url = url,
            timeoutSeconds = SnaConfig.DEFAULT_TIMEOUT_SECONDS,
            callback = callback
        )
    }

    fun authenticate(
        url: String,
        timeoutSeconds: Long,
        callback: SnaCallback
    ) {
        SdkLogger.d("SNASdk", "authenticate(url, timeoutSeconds, callback) called")
        authenticate(
            url = url,
            timeoutSeconds = timeoutSeconds,
            callback = callback
        )
    }

    fun authenticate(
        url: String,
        timeoutSeconds: Long = SnaConfig.DEFAULT_TIMEOUT_SECONDS,
        callback: SnaCallback
    ) {
        SdkLogger.d("SNASdk", "authenticate(url, timeoutSeconds, callback) called")
        
        // Redact URL for event (base URL only, no query params)
        val redactedUrl = redactUrl(url)
        
        // Send authenticate started event
        EventSender.sendEvent(
            eventName = "sna_authenticate_started",
            properties = mapOf(
                "url" to redactedUrl,
                "timeout_seconds" to timeoutSeconds
            )
        )
        
        scope.launch {
            val startMs = SystemClock.elapsedRealtime()
            try {
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
                            properties = buildSuccessEventProperties(result, redactedUrl)
                        )
                    }

                    is SnaResult.Failure -> {
                        EventSender.sendEvent(
                            eventName = "sna_authenticate_failure",
                            properties = buildFailureEventProperties(result, redactedUrl)
                        )
                    }
                }

                callback.onResult(result)
            } catch (e: Exception) {
                val failure = SnaResult.Failure(
                    reason = FailureReason.UNKNOWN_ERROR,
                    detail = e.message ?: "Unknown error",
                    timings = SnaTimings(totalMs = SystemClock.elapsedRealtime() - startMs)
                )

                EventSender.sendEvent(
                    eventName = "sna_authenticate_failure",
                    properties = buildFailureEventProperties(failure, redactedUrl)
                )
                SdkLogger.e("SNASdk", "authenticate() failed with exception", e)
                callback.onResult(failure)
            }
        }
    }
    
    /**
     * Redact URL to base URL only (remove query params and fragments)
     */
    private fun redactUrl(url: String): String {
        val noFragment = url.substringBefore('#')
        val base = noFragment.substringBefore('?')
        return if (noFragment.contains('?')) "$base?…" else base
    }
    
    /**
     * Build event properties for successful authentication
     */
    private fun buildSuccessEventProperties(result: SnaResult.Success, redactedUrl: String): Map<String, Any?> {
        val props = mutableMapOf<String, Any?>(
            "http_code" to result.response.httpCode,
            "redirect_count" to result.redirects.size,
            "total_ms" to result.timings.totalMs,
            "final_url" to redactUrl(result.response.finalUrl),
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
    private fun buildFailureEventProperties(result: SnaResult.Failure, redactedUrl: String): Map<String, Any?> {
        val props = mutableMapOf<String, Any?>(
            "failure_reason" to result.reason.name,
            "redirect_count" to result.redirects.size,
            "total_ms" to result.timings.totalMs
        )
        
        result.detail?.let { props["failure_detail"] = it }
        result.timings.cellularAcquireMs?.let { props["cellular_acquire_ms"] = it }
        result.timings.processBindMs?.let { props["process_bind_ms"] = it }
        result.response?.httpCode?.let { props["http_code"] = it }
        result.response?.finalUrl?.let { props["final_url"] = redactUrl(it) }
        
        return props
    }
}
