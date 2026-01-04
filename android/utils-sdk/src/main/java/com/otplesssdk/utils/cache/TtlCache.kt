package com.otplesssdk.utils.cache

/**
 * Minimal in-memory TTL cache for a single value.
 *
 * - Thread-safe via volatile fields (best-effort; updates are atomic enough for this use-case).
 * - If [ttlMs] <= 0, values are treated as always fresh.
 */
internal class TtlCache<T>(private val ttlMs: Long) {
    @Volatile
    private var value: T? = null

    @Volatile
    private var tsMs: Long = 0L

    fun getFresh(nowMs: Long = System.currentTimeMillis()): T? {
        val v = value ?: return null
        if (ttlMs <= 0) return v
        if (tsMs <= 0L) return null
        return if (nowMs - tsMs <= ttlMs) v else null
    }

    fun getStale(): T? = value

    fun timestampMs(): Long = tsMs

    fun set(v: T?, nowMs: Long = System.currentTimeMillis()) {
        value = v
        tsMs = if (v == null) 0L else nowMs
    }

    fun clear() {
        value = null
        tsMs = 0L
    }
}

