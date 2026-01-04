package com.otplesssdk.authcore

import android.content.Context
import com.otplesssdk.otp.OtpSdk
import com.otplesssdk.sna.SNASdk
import com.otplesssdk.utils.event.EventSender

/**
 * Auth Core SDK (framework).
 *
 * This is an orchestration layer on top of:
 * - utils-sdk (events/IDs/network helpers)
 * - otp-sdk (OTP auto-read)
 * - sna-sdk (SIM Network Authentication)
 *
 * Exact auth flow logic will be added later. For now, this class provides:
 * - initialize(appId mandatory) which also propagates hardcoded shared config to underlying SDKs
 * - initiateAuth() stub
 * - verifyAuth() stub
 */
class AuthCoreSdk private constructor(
    private val context: Context,
    val appId: String
) {
    companion object {
        @Volatile
        private var instance: AuthCoreSdk? = null

        /**
         * Hardcoded defaults for this SDK.
         *
         * NOTE: Replace with the real endpoints/values once final auth flow is shared.
         */
        private object Defaults {
            // Used by utils-sdk EventSender (and therefore by all SDKs that emit events).
            const val EVENT_ENDPOINT: String = "https://example.com/events"
        }

        /**
         * Initialize Auth Core SDK.
         *
         * @param context Application context
         * @param appId Mandatory app ID (propagated to composed SDKs for headers/events)
         */
        @JvmStatic
        fun initialize(
            context: Context,
            appId: String
        ): AuthCoreSdk {
            val normalizedAppId = appId.trim()
            require(normalizedAppId.isNotEmpty()) { "appId is required" }

            return instance ?: synchronized(this) {
                instance ?: AuthCoreSdk(context.applicationContext, normalizedAppId).also { sdk ->
                    instance = sdk

                    // Initialize shared event sender with hardcoded endpoint.
                    EventSender.initialize(
                        context = sdk.context,
                        appId = normalizedAppId,
                        eventEndpoint = Defaults.EVENT_ENDPOINT
                    )

                    // Initialize underlying SDKs with the same appId + event endpoint.
                    // These initializers are idempotent and safe to call multiple times.
                    OtpSdk.initialize(
                        context = sdk.context,
                        appId = normalizedAppId,
                        eventEndpoint = Defaults.EVENT_ENDPOINT
                    )
                    SNASdk.initialize(
                        context = sdk.context,
                        appId = normalizedAppId,
                        eventEndpoint = Defaults.EVENT_ENDPOINT
                    )
                }
            }
        }

        @JvmStatic
        fun getInstance(): AuthCoreSdk? = instance
    }

    /**
     * Start an auth flow (stub).
     *
     * TODO: Replace with real flow once final requirements are shared.
     */
    fun initiateAuth() {
        TODO("Auth flow not implemented yet")
    }

    /**
     * Verify an auth step / OTP (stub).
     *
     * TODO: Replace with real flow once final requirements are shared.
     */
    fun verifyAuth() {
        TODO("Auth flow not implemented yet")
    }
}