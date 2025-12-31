package com.otplesssdk.otp

import android.content.Context
import com.otplesssdk.otp.callback.OtpCallback
import com.otplesssdk.otp.models.AppHash
import com.otplesssdk.otp.models.OtpChannel
import com.otplesssdk.otp.models.OtpConfig
import com.otplesssdk.otp.utils.OtpDispatcher
import com.otplesssdk.otp.utils.SmsRetrieverAppHash
import com.otplesssdk.utils.event.EventSender
import com.otplesssdk.utils.logger.SdkLogger
import com.otplesssdk.otp.utils.WhatsAppOtpHelper

class OtpSdk private constructor(private val context: Context) {
    companion object {
        @Volatile
        private var instance: OtpSdk? = null
        
        private const val SDK_NAME = "otp-sdk"
        private const val SDK_VERSION = "1.0.0"

        @JvmStatic
        @JvmOverloads
        fun initialize(
            context: Context,
            appId: String? = null,
            eventEndpoint: String? = null
        ): OtpSdk {
            return instance ?: synchronized(this) {
                instance ?: OtpSdk(context.applicationContext).also { 
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
                        eventName = "otp_sdk_initialized",
                        properties = emptyMap()
                    )
                }
            }
        }

        @JvmStatic
        fun getInstance(): OtpSdk? {
            return instance
        }

        @JvmStatic
        fun setLoggingEnabled(enabled: Boolean) {
            SdkLogger.setDebugEnabled(enabled)
        }

        @JvmStatic
        fun getAppHashes(context: Context): Set<AppHash> {
            val hashes = SmsRetrieverAppHash.getAppHashes(context.applicationContext)
            
            // Send event
            if (EventSender.isInitialized()) {
                EventSender.sendEvent(
                    eventName = "otp_app_hash_requested",
                    properties = mapOf(
                        "hash_count" to hashes.size,
                        "package_names" to hashes.map { it.packageName }
                    )
                )
            }
            
            return hashes
        }
    }

    fun startListening(channels: Set<OtpChannel>, callback: OtpCallback) {
        SdkLogger.d("OtpSdk", "startListening called with channels: $channels")
        
        // Send event
        EventSender.sendEvent(
            eventName = "otp_listening_started",
            properties = mapOf(
                "channels" to channels.map { it.name },
                "channel_count" to channels.size,
                "has_sms" to channels.contains(OtpChannel.SMS),
                "has_whatsapp" to channels.contains(OtpChannel.WHATSAPP)
            )
        )
        
        val config = OtpConfig(channels = channels)
        OtpDispatcher.start(context, config, callback)
    }

    fun stop() {
        SdkLogger.d("OtpSdk", "stop called")
        
        // Send event
        EventSender.sendEvent(
            eventName = "otp_listening_stopped",
            properties = emptyMap()
        )
        
        OtpDispatcher.stop(context)
    }

    fun isWhatsAppInstalled(): Boolean {
        val installed = WhatsAppOtpHelper.isWhatsAppInstalled(context)
        
        // Send event
        EventSender.sendEvent(
            eventName = "otp_whatsapp_check",
            properties = mapOf(
                "is_installed" to installed
            )
        )
        
        return installed
    }
}
