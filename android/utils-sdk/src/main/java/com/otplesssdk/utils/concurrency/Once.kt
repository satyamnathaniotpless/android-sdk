package com.otplesssdk.utils.concurrency

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Simple "run once" helper.
 *
 * Useful for idempotent start() methods that should only execute once per process.
 */
internal class Once {
    private val started = AtomicBoolean(false)

    fun tryStart(): Boolean = started.compareAndSet(false, true)

    fun reset() {
        started.set(false)
    }
}

