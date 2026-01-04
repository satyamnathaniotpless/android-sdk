package com.otplesssdk.otp.utils

import com.otplesssdk.otp.models.OtpChannel
import com.otplesssdk.otp.models.OtpErrorReason
import com.otplesssdk.otp.models.OtpResult
import com.otplesssdk.utils.event.EventSender
import com.otplesssdk.utils.logger.SdkLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Centralized result dispatching + session completion bookkeeping.
 */
internal class OtpResultEmitter(
    private val store: OtpSessionStore,
    private val callbackScope: CoroutineScope
) {
    private companion object {
        private const val TAG = "OtpResultEmitter"
    }

    fun success(
        session: OtpSession,
        otp: String,
        source: OtpChannel,
        senderAddress: String?,
        senderPackage: String?
    ) {
        EventSender.sendEvent(
            eventName = "otp_received",
            properties = mapOf(
                "channel" to source.name,
                "otp_length" to otp.length,
                // For SMS this is the originating address; for WhatsApp this is always null.
                "has_sender_id" to (senderAddress != null)
            )
        )

        dispatch(
            session = session,
            result = OtpResult.Success(
                otp = otp,
                source = source,
                senderAddress = senderAddress,
                senderPackage = senderPackage
            ),
            source = source,
            completeSource = true,
            finishSession = false
        )
    }

    fun error(
        session: OtpSession,
        reason: OtpErrorReason,
        source: OtpChannel,
        errorKey: String? = null,
        errorMessage: String? = null,
        senderPackage: String? = null
    ) {
        val resolvedMessage = resolveErrorMessage(reason, errorKey, errorMessage)

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
            session = session,
            result = OtpResult.Error(
                reason = reason,
                source = source,
                errorKey = errorKey,
                errorMessage = resolvedMessage,
                senderPackage = senderPackage
            ),
            source = source,
            completeSource = true,
            finishSession = false
        )
    }

    private fun dispatch(
        session: OtpSession,
        result: OtpResult,
        source: OtpChannel,
        completeSource: Boolean,
        finishSession: Boolean
    ) {
        val (callback, shouldClear) = store.withCurrentSession(session) { current ->
            if (completeSource) {
                current.markDone(source)
                if (source == OtpChannel.WHATSAPP) {
                    current.cancelWhatsAppTimeout()
                }
            }
            val finish = finishSession || current.allDone()
            if (finish) {
                current.finished = true
                current.clear()
            }
            current.callback to finish
        } ?: return

        if (shouldClear) {
            // Clear the store reference outside the lock to avoid re-entrant locking.
            store.clearIfCurrent(session)
        }

        callbackScope.launch {
            runCatching {
                callback.onResult(result)
            }.onFailure { t ->
                // SDK callbacks should never crash the host app.
                SdkLogger.e(TAG, "OtpCallback.onResult threw", t)
            }
        }
    }

    private fun resolveErrorMessage(
        reason: OtpErrorReason,
        errorKey: String?,
        errorMessage: String?
    ): String? {
        if (!errorMessage.isNullOrBlank()) return errorMessage
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
                if (!errorKey.isNullOrBlank()) "WhatsApp OTP error: $errorKey" else "WhatsApp OTP error."
            }
        }
    }
}

