package com.otplesssdk.utils.ids

import android.content.Context

/**
 * Utility for managing:
 * - inid: install id (persists until app is uninstalled)
 * - tsid: transient session id (persists for the current app process/session)
 */
object SessionIdManager {
    private const val PREFS_NAME = "otpless_sdk_ids"
    private const val KEY_INID = "otpless_inid"

    @Volatile
    private var cachedInId: String? = null

    @Volatile
    private var cachedTsId: String? = null

    private val lock = Any()

    fun getInId(context: Context): String {
        cachedInId?.let { return it }
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        synchronized(lock) {
            cachedInId?.let { return it }
            val stored = prefs.getString(KEY_INID, null)
            if (!stored.isNullOrBlank()) {
                cachedInId = stored
                return stored
            }
            val newId = UuidUtil.random()
            cachedInId = newId
            prefs.edit().putString(KEY_INID, newId).apply()
            return newId
        }
    }

    fun getTsId(context: Context): String {
        context.applicationContext
        cachedTsId?.let { return it }
        synchronized(lock) {
            cachedTsId?.let { return it }
            val newId = UuidUtil.random()
            cachedTsId = newId
            return newId
        }
    }
}
