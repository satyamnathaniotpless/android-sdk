package com.otplesssdk.otp.utils

/**
 * Holds the current in-memory OTP session for this process.
 *
 * All access is guarded by [lock] to avoid races between channels/receivers.
 */
internal class OtpSessionStore {
    private val lock = Any()
    private var session: OtpSession? = null

    fun replace(newSession: OtpSession) {
        synchronized(lock) {
            session?.clear()
            session = newSession
        }
    }

    fun clear() {
        synchronized(lock) {
            session?.clear()
            session = null
        }
    }

    fun current(): OtpSession? = synchronized(lock) { session }

    fun isCurrent(s: OtpSession): Boolean = synchronized(lock) { session === s }

    /**
     * Clears the stored session reference if [s] is still current.
     *
     * Note: does not call [OtpSession.clear]; callers should do that explicitly.
     */
    fun clearIfCurrent(s: OtpSession) {
        synchronized(lock) {
            if (session === s) {
                session = null
            }
        }
    }

    /**
     * Executes [block] while holding the lock, only if [s] is still current and not finished.
     */
    fun <T> withCurrentSession(s: OtpSession, block: (OtpSession) -> T): T? {
        synchronized(lock) {
            val cur = session
            if (cur !== s || s.finished) return null
            return block(s)
        }
    }
}

