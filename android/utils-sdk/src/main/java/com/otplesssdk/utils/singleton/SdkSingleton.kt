package com.otplesssdk.utils.singleton

import android.content.Context

/**
 * Base singleton helper for SDK initialization.
 * Provides thread-safe singleton pattern with application context handling.
 */
abstract class SdkSingleton<T : Any> {
    @Volatile
    protected var singletonInstance: T? = null

    /**
     * Initialize the singleton instance.
     * Uses double-checked locking pattern for thread safety.
     *
     * @param context Application context (will be converted to applicationContext)
     * @param factory Factory function to create the instance
     * @return The singleton instance
     */
    protected fun initialize(
        context: Context,
        factory: (Context) -> T
    ): T {
        return singletonInstance ?: synchronized(this) {
            singletonInstance ?: factory(context.applicationContext).also { singletonInstance = it }
        }
    }

    /**
     * Get the initialized singleton instance.
     *
     * @return The singleton instance or null if not initialized
     */
    fun getInstance(): T? {
        return singletonInstance
    }

    /**
     * Clear the singleton instance (useful for testing).
     */
    fun clear() {
        synchronized(this) {
            singletonInstance = null
        }
    }
}

