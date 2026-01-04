package com.otplesssdk.otp.utils

import android.content.Context
import android.content.Intent
import com.otplesssdk.otp.callback.OtpCallback
import com.otplesssdk.otp.models.OtpConfig
import com.otplesssdk.utils.coroutines.SdkCoroutineScope

internal object OtpDispatcher {
    private const val WHATSAPP_TIMEOUT_MS = 10 * 60 * 1000L
    private val scope = SdkCoroutineScope.createMainScope(immediate = true)
    private val store = OtpSessionStore()
    private val emitter = OtpResultEmitter(store, scope)
    private val sms = SmsRetrieverHandler(store, emitter)
    private val whatsapp = WhatsAppZeroTapHandler(
        store = store,
        emitter = emitter,
        scope = scope,
        timeoutMs = WHATSAPP_TIMEOUT_MS
    )

    fun start(context: Context, config: OtpConfig, callback: OtpCallback) {
        val appContext = context.applicationContext
        val newSession = OtpSession(config, callback)
        store.replace(newSession)

        if (!newSession.smsCompleted && store.isCurrent(newSession)) {
            sms.start(appContext, newSession)
        }

        if (!newSession.whatsAppCompleted && store.isCurrent(newSession)) {
            whatsapp.startHandshake(appContext, newSession)
        }
    }

    fun stop(@Suppress("UNUSED_PARAMETER") context: Context) {
        store.clear()
    }

    fun handleSmsIntent(@Suppress("UNUSED_PARAMETER") context: Context, intent: Intent) {
        sms.handleIntent(intent)
    }

    fun handleWhatsAppOtpIntent(
        @Suppress("UNUSED_PARAMETER") context: Context,
        intent: Intent,
        senderPackage: String? = null
    ) {
        whatsapp.handleOtpIntent(intent, senderPackage)
    }

    fun handleWhatsAppErrorIntent(
        @Suppress("UNUSED_PARAMETER") context: Context,
        intent: Intent,
        senderPackage: String? = null
    ) {
        whatsapp.handleErrorIntent(intent, senderPackage)
    }
}
