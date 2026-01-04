package com.otplesssdk.utils.concurrency

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Simple keyed "single-flight" gate:
 * - enter(key) returns true only for the first concurrent caller
 * - others return false until leave(key) is called
 *
 * This is useful to avoid launching duplicate background refresh work.
 */
internal object SingleFlight {
    private val inFlight = ConcurrentHashMap<String, AtomicBoolean>()

    fun enter(key: String): Boolean {
        val flag = inFlight.getOrPut(key) { AtomicBoolean(false) }
        return flag.compareAndSet(false, true)
    }

    fun leave(key: String) {
        inFlight[key]?.set(false)
    }
}

