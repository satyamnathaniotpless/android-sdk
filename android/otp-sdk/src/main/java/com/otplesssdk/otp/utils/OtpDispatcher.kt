package com.otplesssdk.otp.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.otplesssdk.otp.callback.OtpCallback
import com.otplesssdk.otp.models.OtpConfig
import com.otplesssdk.otp.models.OtpErrorReason
import com.otplesssdk.otp.models.OtpResult
import com.otplesssdk.otp.models.OtpChannel
import com.otplesssdk.utils.coroutines.SdkCoroutineScope
import com.otplesssdk.utils.event.EventSender
import com.otplesssdk.utils.logger.SdkLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal object OtpDispatcher {
    private const val TAG = "OtpDispatcher"
    private val lock = Any()
    private val scope = SdkCoroutineScope.createMainScope(immediate = true)
    private var session: OtpSession? = null

    fun start(context: Context, config: OtpConfig, callback: OtpCallback) {
        val appContext = context.applicationContext
        val newSession = OtpSession(config, callback)
        synchronized(lock) {
            session?.clear()
            session = newSession
        }

        OtpStorage.setStorageConfig(appContext, config.storedResultMaxAgeMs)
        OtpStorage.consumeStored(appContext, config.storedResultMaxAgeMs)?.let { stored ->
            dispatchStoredResult(newSession, stored)
        }

        val shouldStartSms = synchronized(lock) {
            session === newSession && !newSession.smsCompleted
        }
        if (shouldStartSms) {
            startSmsRetriever(appContext, newSession)
        }

        val shouldStartWhatsApp = synchronized(lock) {
            session === newSession && !newSession.whatsAppCompleted
        }
        if (shouldStartWhatsApp) {
            startWhatsAppHandshake(appContext, config, newSession)
        }
    }

    fun stop(context: Context) {
        synchronized(lock) {
            session?.clear()
            session = null
        }
        OtpStorage.setStorageConfig(context.applicationContext, 0L)
    }

    private fun dispatchStoredResult(
        currentSession: OtpSession,
        stored: OtpStorage.StoredOtp
    ) {
        if (!currentSession.config.channels.contains(stored.channel)) {
            return
        }
        when (stored.type) {
            OtpStorage.StoredType.SMS_MESSAGE -> {
                val message = stored.payload.orEmpty()
                val otp = OtpParser.extractOtp(message, currentSession.config)
                if (otp == null) {
                    dispatchError(
                        currentSession,
                        OtpErrorReason.SMS_OTP_NOT_FOUND,
                        OtpChannel.SMS
                    )
                } else {
                    dispatchSuccess(
                        currentSession,
                        otp,
                        OtpChannel.SMS,
                        senderId = stored.senderId
                    )
                }
            }
            OtpStorage.StoredType.WHATSAPP_CODE -> {
                val rawCode = stored.payload.orEmpty()
                val trimmedCode = rawCode.trim()
                val otp = if (OtpParser.isValidOtp(trimmedCode, currentSession.config)) {
                    trimmedCode
                } else {
                    OtpParser.extractOtp(rawCode, currentSession.config)
                }
                if (otp == null) {
                    dispatchError(
                        currentSession,
                        OtpErrorReason.WHATSAPP_OTP_NOT_FOUND,
                        OtpChannel.WHATSAPP
                    )
                } else {
                    dispatchSuccess(
                        currentSession,
                        otp,
                        OtpChannel.WHATSAPP,
                        senderId = null
                    )
                }
            }
            OtpStorage.StoredType.WHATSAPP_ERROR -> {
                dispatchError(
                    currentSession,
                    OtpErrorReason.WHATSAPP_OTP_ERROR,
                    OtpChannel.WHATSAPP,
                    errorKey = stored.errorKey,
                    errorMessage = stored.errorMessage
                )
            }
        }
    }

    fun handleSmsIntent(context: Context, intent: Intent) {
        if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) {
            return
        }
        val extras = intent.extras ?: return
        val status = extras.get(SmsRetriever.EXTRA_STATUS) as? Status ?: return
        val currentSession = synchronized(lock) { session }

        when (status.statusCode) {
            CommonStatusCodes.SUCCESS -> {
                val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE)
                val senderId = extras.getString(SmsRetriever.EXTRA_SMS_ORIGINATING_ADDRESS)
                if (message.isNullOrBlank()) {
                    currentSession?.let {
                        if (it.config.channels.contains(OtpChannel.SMS)) {
                            dispatchError(
                                it,
                                OtpErrorReason.SMS_OTP_NOT_FOUND,
                                OtpChannel.SMS
                            )
                        }
                    }
                    return
                }

                if (currentSession == null) {
                    OtpStorage.storeSmsMessage(
                        context.applicationContext,
                        message,
                        senderId
                    )
                    return
                }
                if (!currentSession.config.channels.contains(OtpChannel.SMS)) {
                    return
                }
                if (currentSession.isCompleted(OtpChannel.SMS)) {
                    return
                }

                val otp = OtpParser.extractOtp(message, currentSession.config)
                if (otp == null) {
                    dispatchError(
                        currentSession,
                        OtpErrorReason.SMS_OTP_NOT_FOUND,
                        OtpChannel.SMS
                    )
                } else {
                    dispatchSuccess(
                        currentSession,
                        otp,
                        OtpChannel.SMS,
                        senderId = senderId
                    )
                }
            }
            CommonStatusCodes.TIMEOUT -> {
                currentSession?.let {
                    if (it.config.channels.contains(OtpChannel.SMS) && !it.isCompleted(OtpChannel.SMS)) {
                        dispatchError(
                            it,
                            OtpErrorReason.SMS_TIMEOUT,
                            OtpChannel.SMS
                        )
                    }
                }
            }
            else -> {
                currentSession?.let {
                    if (it.config.channels.contains(OtpChannel.SMS) && !it.isCompleted(OtpChannel.SMS)) {
                        dispatchError(
                            it,
                            OtpErrorReason.SMS_RETRIEVER_ERROR,
                            OtpChannel.SMS,
                            errorMessage = "statusCode=${status.statusCode}"
                        )
                    }
                }
            }
        }
    }

    fun handleWhatsAppOtpIntent(context: Context, intent: Intent) {
        if (intent.action != WhatsAppOtpHelper.ACTION_OTP_RETRIEVED) {
            return
        }
        val pendingIntent = PendingIntentReader.getPendingIntent(intent)
        val creatorPackage = pendingIntent?.creatorPackage
        if (!WhatsAppOtpHelper.isValidCreatorPackage(creatorPackage)) {
            SdkLogger.w(TAG, "Ignored WhatsApp OTP broadcast from $creatorPackage")
            return
        }

        val rawCode = intent.getStringExtra(WhatsAppOtpHelper.EXTRA_CODE).orEmpty()
        val trimmedCode = rawCode.trim()
        val currentSession = synchronized(lock) { session }

        if (currentSession == null) {
            if (trimmedCode.isNotEmpty()) {
                OtpStorage.storeWhatsAppCode(context.applicationContext, trimmedCode)
            }
            return
        }
        if (!currentSession.config.channels.contains(OtpChannel.WHATSAPP)) {
            return
        }
        if (currentSession.isCompleted(OtpChannel.WHATSAPP)) {
            return
        }

        val otp = if (OtpParser.isValidOtp(trimmedCode, currentSession.config)) {
            trimmedCode
        } else {
            OtpParser.extractOtp(rawCode, currentSession.config)
        }

        if (otp == null) {
            dispatchError(
                currentSession,
                OtpErrorReason.WHATSAPP_OTP_NOT_FOUND,
                OtpChannel.WHATSAPP
            )
        } else {
            dispatchSuccess(
                currentSession,
                otp,
                OtpChannel.WHATSAPP,
                senderId = null
            )
        }
    }

    fun handleWhatsAppErrorIntent(context: Context, intent: Intent) {
        handleWhatsAppErrorIntent(context, intent, pendingIntent = null)
    }

    fun handleWhatsAppErrorIntent(context: Context, intent: Intent, pendingIntent: PendingIntent?) {
        if (intent.action != WhatsAppOtpHelper.ACTION_OTP_ERROR) {
            return
        }
        val resolvedPendingIntent = pendingIntent ?: PendingIntentReader.getPendingIntent(intent)
        val creatorPackage = resolvedPendingIntent?.creatorPackage
        if (!WhatsAppOtpHelper.isValidCreatorPackage(creatorPackage)) {
            SdkLogger.w(TAG, "Ignored WhatsApp error broadcast from $creatorPackage")
            return
        }

        val errorKey = intent.getStringExtra(WhatsAppOtpHelper.EXTRA_ERROR)
        val errorMessage = intent.getStringExtra(WhatsAppOtpHelper.EXTRA_ERROR_MESSAGE)
        val currentSession = synchronized(lock) { session }

        if (currentSession == null) {
            OtpStorage.storeWhatsAppError(
                context.applicationContext,
                errorKey,
                errorMessage
            )
            return
        }
        if (!currentSession.config.channels.contains(OtpChannel.WHATSAPP)) {
            return
        }
        if (currentSession.isCompleted(OtpChannel.WHATSAPP)) {
            return
        }

        dispatchError(
            currentSession,
            OtpErrorReason.WHATSAPP_OTP_ERROR,
            OtpChannel.WHATSAPP,
            errorKey = errorKey,
            errorMessage = errorMessage
        )
    }

    private fun startSmsRetriever(context: Context, currentSession: OtpSession) {
        try {
            val availability = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context)
            if (availability != ConnectionResult.SUCCESS) {
                dispatchError(
                    currentSession,
                    OtpErrorReason.SMS_PLAY_SERVICES_UNAVAILABLE,
                    OtpChannel.SMS,
                    errorMessage = GoogleApiAvailability.getInstance().getErrorString(availability)
                )
                return
            }
            SmsRetriever.getClient(context).startSmsRetriever()
                .addOnSuccessListener {
                    SdkLogger.d(TAG, "SMS Retriever started")
                }
                .addOnFailureListener { exception ->
                    SdkLogger.w(TAG, "SMS Retriever failed to start", exception)
                    dispatchError(
                        currentSession,
                        OtpErrorReason.SMS_START_FAILED,
                        OtpChannel.SMS,
                        errorMessage = exception.message
                    )
                }
        } catch (exception: Exception) {
            SdkLogger.e(TAG, "SMS Retriever start exception", exception)
            dispatchError(
                currentSession,
                OtpErrorReason.SMS_START_FAILED,
                OtpChannel.SMS,
                errorMessage = exception.message
            )
        }
    }

    private fun startWhatsAppHandshake(context: Context, config: OtpConfig, currentSession: OtpSession) {
        try {
            val started = WhatsAppOtpHelper.sendHandshake(context)
            if (!started) {
                dispatchError(
                    currentSession,
                    OtpErrorReason.WHATSAPP_NOT_INSTALLED,
                    OtpChannel.WHATSAPP
                )
                return
            }
        } catch (exception: Exception) {
            SdkLogger.e(TAG, "WhatsApp handshake failed", exception)
            dispatchError(
                currentSession,
                OtpErrorReason.WHATSAPP_HANDSHAKE_FAILED,
                OtpChannel.WHATSAPP,
                errorMessage = exception.message
            )
            return
        }

        if (config.whatsAppTimeoutMs > 0) {
            val timeoutJob = scope.launch {
                delay(config.whatsAppTimeoutMs)
                dispatchError(
                    currentSession,
                    OtpErrorReason.WHATSAPP_TIMEOUT,
                    OtpChannel.WHATSAPP
                )
            }
            synchronized(lock) {
                if (session !== currentSession || currentSession.finished) {
                    timeoutJob.cancel()
                    return
                }
                currentSession.cancelWhatsAppTimeout()
                currentSession.whatsAppTimeoutJob = timeoutJob
            }
        }
    }

    private fun dispatchSuccess(
        currentSession: OtpSession,
        otp: String,
        source: OtpChannel,
        senderId: String?
    ) {
        // Send event
        EventSender.sendEvent(
            eventName = "otp_received",
            properties = mapOf(
                "channel" to source.name,
                "otp_length" to otp.length,
                "has_sender_id" to (senderId != null)
            )
        )
        
        dispatch(
            currentSession,
            OtpResult.Success(
                otp = otp,
                source = source,
                senderId = senderId
            ),
            source,
            completeSource = true,
            finishSession = false
        )
    }

    private fun dispatchError(
        currentSession: OtpSession,
        reason: OtpErrorReason,
        source: OtpChannel,
        errorKey: String? = null,
        errorMessage: String? = null
    ) {
        val resolvedMessage = resolveErrorMessage(reason, errorKey, errorMessage)
        
        // Send event
        val errorProps = mutableMapOf<String, Any?>(
            "channel" to source.name,
            "error_reason" to reason.name
        )
        errorKey?.let { errorProps["error_key"] = it }
        resolvedMessage?.let { errorProps["error_message"] = it }
        
        EventSender.sendEvent(
            eventName = "otp_error",
            properties = errorProps
        )
        
        dispatch(
            currentSession,
            OtpResult.Error(
                reason = reason,
                source = source,
                errorKey = errorKey,
                errorMessage = resolvedMessage
            ),
            source,
            completeSource = true,
            finishSession = false
        )
    }

    private fun resolveErrorMessage(
        reason: OtpErrorReason,
        errorKey: String?,
        errorMessage: String?
    ): String? {
        if (!errorMessage.isNullOrBlank()) {
            return errorMessage
        }
        return when (reason) {
            OtpErrorReason.SMS_START_FAILED -> "Failed to start SMS Retriever."
            OtpErrorReason.SMS_TIMEOUT -> "SMS Retriever timed out."
            OtpErrorReason.SMS_OTP_NOT_FOUND -> "No OTP found in the SMS message."
            OtpErrorReason.SMS_RETRIEVER_ERROR -> "SMS Retriever returned an error."
            OtpErrorReason.SMS_PLAY_SERVICES_UNAVAILABLE -> "Google Play services unavailable."
            OtpErrorReason.WHATSAPP_NOT_INSTALLED -> "WhatsApp is not installed."
            OtpErrorReason.WHATSAPP_HANDSHAKE_FAILED -> "Failed to initiate WhatsApp handshake."
            OtpErrorReason.WHATSAPP_TIMEOUT -> "WhatsApp OTP request timed out."
            OtpErrorReason.WHATSAPP_OTP_NOT_FOUND -> "No OTP found in the WhatsApp payload."
            OtpErrorReason.WHATSAPP_OTP_ERROR -> {
                if (!errorKey.isNullOrBlank()) {
                    "WhatsApp OTP error: $errorKey"
                } else {
                    "WhatsApp OTP error."
                }
            }
        }
    }

    private fun dispatch(
        currentSession: OtpSession,
        result: OtpResult,
        source: OtpChannel,
        completeSource: Boolean,
        finishSession: Boolean
    ) {
        val callback: OtpCallback
        synchronized(lock) {
            if (session !== currentSession || currentSession.finished) {
                return
            }
            if (completeSource) {
                currentSession.markDone(source)
                if (source == OtpChannel.WHATSAPP) {
                    currentSession.cancelWhatsAppTimeout()
                }
            }
            if (finishSession || currentSession.allDone()) {
                currentSession.finished = true
                currentSession.clear()
                session = null
            }
            callback = currentSession.callback
        }

        scope.launch {
            callback.onResult(result)
        }
    }

    private class OtpSession(
        val config: OtpConfig,
        val callback: OtpCallback
    ) {
        var smsCompleted: Boolean = !config.channels.contains(OtpChannel.SMS)
        var whatsAppCompleted: Boolean = !config.channels.contains(OtpChannel.WHATSAPP)
        var finished: Boolean = false
        var whatsAppTimeoutJob: Job? = null

        fun markDone(source: OtpChannel) {
            when (source) {
                OtpChannel.SMS -> smsCompleted = true
                OtpChannel.WHATSAPP -> whatsAppCompleted = true
            }
        }

        fun isCompleted(source: OtpChannel): Boolean {
            return when (source) {
                OtpChannel.SMS -> smsCompleted
                OtpChannel.WHATSAPP -> whatsAppCompleted
            }
        }

        fun allDone(): Boolean {
            return smsCompleted && whatsAppCompleted
        }

        fun clear() {
            cancelWhatsAppTimeout()
        }

        fun cancelWhatsAppTimeout() {
            whatsAppTimeoutJob?.cancel()
            whatsAppTimeoutJob = null
        }
    }
}
