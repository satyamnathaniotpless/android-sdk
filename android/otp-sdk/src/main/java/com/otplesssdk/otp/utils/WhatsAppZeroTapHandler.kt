package com.otplesssdk.otp.utils

import android.content.Context
import android.content.Intent
import com.otplesssdk.otp.models.OtpChannel
import com.otplesssdk.otp.models.OtpErrorReason
import com.otplesssdk.utils.logger.SdkLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class WhatsAppZeroTapHandler(
    private val store: OtpSessionStore,
    private val emitter: OtpResultEmitter,
    private val scope: CoroutineScope,
    private val timeoutMs: Long
) {
    private companion object {
        private const val TAG = "WhatsAppZeroTapHandler"
    }

    fun startHandshake(context: Context, session: OtpSession) {
        try {
            val started = WhatsAppOtpHelper.sendHandshake(context)
            if (!started) {
                emitter.error(
                    session = session,
                    reason = OtpErrorReason.WHATSAPP_NOT_INSTALLED,
                    source = OtpChannel.WHATSAPP
                )
                return
            }
        } catch (exception: Exception) {
            SdkLogger.e(TAG, "WhatsApp handshake failed", exception)
            emitter.error(
                session = session,
                reason = OtpErrorReason.WHATSAPP_HANDSHAKE_FAILED,
                source = OtpChannel.WHATSAPP,
                errorMessage = exception.message
            )
            return
        }

        if (timeoutMs <= 0) return

        val timeoutJob = scope.launch {
            delay(timeoutMs)
            emitter.error(
                session = session,
                reason = OtpErrorReason.WHATSAPP_TIMEOUT,
                source = OtpChannel.WHATSAPP
            )
        }

        // Only keep the timeout if this session is still current.
        store.withCurrentSession(session) { s ->
            s.cancelWhatsAppTimeout()
            s.whatsAppTimeoutJob = timeoutJob
        } ?: timeoutJob.cancel()
    }

    fun handleOtpIntent(intent: Intent, senderPackage: String?) {
        if (intent.action != WhatsAppOtpHelper.ACTION_OTP_RETRIEVED) return

        val rawCode = intent.getStringExtra(WhatsAppOtpHelper.EXTRA_CODE).orEmpty()
        val trimmedCode = rawCode.trim()
        val currentSession = store.current() ?: return

        if (!currentSession.config.channels.contains(OtpChannel.WHATSAPP)) return
        if (currentSession.isCompleted(OtpChannel.WHATSAPP)) return

        val otp = if (OtpParser.isValidOtp(trimmedCode)) trimmedCode else OtpParser.extractOtp(rawCode)
        if (otp == null) {
            emitter.error(
                session = currentSession,
                reason = OtpErrorReason.WHATSAPP_OTP_NOT_FOUND,
                source = OtpChannel.WHATSAPP,
                senderPackage = senderPackage
            )
        } else {
            emitter.success(
                session = currentSession,
                otp = otp,
                source = OtpChannel.WHATSAPP,
                senderAddress = null,
                senderPackage = senderPackage
            )
        }
    }

    fun handleErrorIntent(intent: Intent, senderPackage: String?) {
        if (intent.action != WhatsAppOtpHelper.ACTION_OTP_ERROR) return

        val errorKey = intent.getStringExtra(WhatsAppOtpHelper.EXTRA_ERROR)
        val errorMessage = intent.getStringExtra(WhatsAppOtpHelper.EXTRA_ERROR_MESSAGE)
        val currentSession = store.current() ?: return

        if (!currentSession.config.channels.contains(OtpChannel.WHATSAPP)) return
        if (currentSession.isCompleted(OtpChannel.WHATSAPP)) return

        emitter.error(
            session = currentSession,
            reason = OtpErrorReason.WHATSAPP_OTP_ERROR,
            source = OtpChannel.WHATSAPP,
            errorKey = errorKey,
            errorMessage = errorMessage,
            senderPackage = senderPackage
        )
    }
}

