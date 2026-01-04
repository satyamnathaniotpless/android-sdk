package com.otplesssdk.utils.deviceinfo

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.otplesssdk.utils.concurrency.SingleFlight
import com.otplesssdk.utils.coroutines.SdkCoroutineScope
import com.otplesssdk.utils.ids.UuidUtil
import com.otplesssdk.utils.logger.SdkLogger
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest

internal object IntegrityCollector {
    private const val TAG = "IntegrityCollector"

    // Mutable, process-wide cache: Play Integrity returns asynchronously via Play Core Task callbacks.
    @Volatile
    var cloudProjectNumber: Long? = null

    private const val FLIGHT_KEY = "integrity_token"

    private val job = SupervisorJob()
    private val scope = SdkCoroutineScope.createIOScope(parentJob = job)

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedTokenTimestampMs: Long = 0L

    /**
     * Returns the last successfully fetched token, if any.
     *
     * This does not trigger a refresh; [DeviceInfoCollector] decides when to refresh based on TTL.
     */
    fun getCached(): IntegrityInfo? {
        val token = cachedToken ?: return null
        return IntegrityInfo(token = token, tokenTimestampMs = cachedTokenTimestampMs, error = null)
    }

    fun refreshAsync(appContext: Context) {
        if (!SingleFlight.enter(FLIGHT_KEY)) return

        scope.launch {
            try {
                val integrityManager = IntegrityManagerFactory.create(appContext)
                val nonce = generateNonce(appContext)

                val builder = IntegrityTokenRequest.builder().setNonce(nonce)
                cloudProjectNumber?.let { builder.setCloudProjectNumber(it) }
                val request = builder.build()

                val task = integrityManager.requestIntegrityToken(request)
                task.addOnSuccessListener { response ->
                    cachedToken = response.token()
                    cachedTokenTimestampMs = System.currentTimeMillis()
                    SingleFlight.leave(FLIGHT_KEY)
                }.addOnFailureListener { t ->
                    SdkLogger.d(TAG, "Play Integrity token request failed: ${t.javaClass.name}: ${t.message}")
                    SingleFlight.leave(FLIGHT_KEY)
                }.addOnCompleteListener {
                    // Safety: avoid leaving the single-flight gate stuck if listeners behave unexpectedly.
                    SingleFlight.leave(FLIGHT_KEY)
                }
            } catch (t: Throwable) {
                SdkLogger.d(TAG, "Play Integrity unavailable: ${t.javaClass.name}: ${t.message}")
                SingleFlight.leave(FLIGHT_KEY)
            }
        }
    }

    private fun generateNonce(context: Context): String {
        val raw = buildString {
            append(context.packageName)
            append('|')
            append(System.currentTimeMillis())
            append('|')
            append(UuidUtil.random())
        }
        return sha256Hex(raw)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

internal data class IntegrityInfo(
    val token: String? = null,
    val tokenTimestampMs: Long? = null,
    val error: String? = null,
)

