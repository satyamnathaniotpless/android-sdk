package com.otplesssdk.otp.utils

import android.content.Context
import com.otplesssdk.otp.models.OtpChannel

internal object OtpStorage {
    private const val PREFS = "sna_otp_storage"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MAX_AGE_MS = "max_age_ms"
    private const val KEY_TYPE = "type"
    private const val KEY_SOURCE = "source"
    private const val KEY_PAYLOAD = "payload"
    private const val KEY_SENDER_ID = "sender_id"
    private const val KEY_ERROR_KEY = "error_key"
    private const val KEY_ERROR_MESSAGE = "error_message"
    private const val KEY_TIMESTAMP = "timestamp"

    internal enum class StoredType(val id: String) {
        SMS_MESSAGE("sms_message"),
        WHATSAPP_CODE("whatsapp_code"),
        WHATSAPP_ERROR("whatsapp_error")
    }

    data class StoredOtp(
        val channel: OtpChannel,
        val type: StoredType,
        val payload: String?,
        val senderId: String?,
        val errorKey: String?,
        val errorMessage: String?,
        val timestampMs: Long
    )

    fun setStorageConfig(context: Context, maxAgeMs: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = maxAgeMs > 0
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putLong(KEY_MAX_AGE_MS, maxAgeMs)
            .apply()
        if (!enabled) {
            clear(context)
        }
    }

    fun storeSmsMessage(context: Context, message: String, senderId: String?) {
        store(
            context,
            type = StoredType.SMS_MESSAGE,
            channel = OtpChannel.SMS,
            payload = message,
            senderId = senderId,
            errorKey = null,
            errorMessage = null
        )
    }

    fun storeWhatsAppCode(context: Context, code: String) {
        store(
            context,
            type = StoredType.WHATSAPP_CODE,
            channel = OtpChannel.WHATSAPP,
            payload = code,
            senderId = null,
            errorKey = null,
            errorMessage = null
        )
    }

    fun storeWhatsAppError(context: Context, errorKey: String?, errorMessage: String?) {
        store(
            context,
            type = StoredType.WHATSAPP_ERROR,
            channel = OtpChannel.WHATSAPP,
            payload = null,
            senderId = null,
            errorKey = errorKey,
            errorMessage = errorMessage
        )
    }

    fun consumeStored(context: Context, maxAgeMs: Long): StoredOtp? {
        if (maxAgeMs <= 0) {
            clear(context)
            return null
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
        if (timestamp <= 0L) {
            clear(context)
            return null
        }
        val now = System.currentTimeMillis()
        if (now - timestamp > maxAgeMs) {
            clear(context)
            return null
        }

        val typeId = prefs.getString(KEY_TYPE, null)
        val channelName = prefs.getString(KEY_SOURCE, null)
        if (typeId.isNullOrEmpty() || channelName.isNullOrEmpty()) {
            clear(context)
            return null
        }
        val channel = runCatching { OtpChannel.valueOf(channelName) }.getOrNull()
        val type = StoredType.values().firstOrNull { it.id == typeId }
        if (channel == null || type == null) {
            clear(context)
            return null
        }
        val payload = prefs.getString(KEY_PAYLOAD, null)
        val senderId = prefs.getString(KEY_SENDER_ID, null)
        val errorKey = prefs.getString(KEY_ERROR_KEY, null)
        val errorMessage = prefs.getString(KEY_ERROR_MESSAGE, null)

        clear(context)
        return StoredOtp(
            channel = channel,
            type = type,
            payload = payload,
            senderId = senderId,
            errorKey = errorKey,
            errorMessage = errorMessage,
            timestampMs = timestamp
        )
    }

    fun isStorageEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, false) && prefs.getLong(KEY_MAX_AGE_MS, 0L) > 0
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_TYPE)
            .remove(KEY_SOURCE)
            .remove(KEY_PAYLOAD)
            .remove(KEY_SENDER_ID)
            .remove(KEY_ERROR_KEY)
            .remove(KEY_ERROR_MESSAGE)
            .remove(KEY_TIMESTAMP)
            .apply()
    }

    private fun store(
        context: Context,
        type: StoredType,
        channel: OtpChannel,
        payload: String?,
        senderId: String?,
        errorKey: String?,
        errorMessage: String?
    ) {
        if (!isStorageEnabled(context)) {
            return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TYPE, type.id)
            .putString(KEY_SOURCE, channel.name)
            .putString(KEY_PAYLOAD, payload)
            .putString(KEY_SENDER_ID, senderId)
            .putString(KEY_ERROR_KEY, errorKey)
            .putString(KEY_ERROR_MESSAGE, errorMessage)
            .putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }
}
