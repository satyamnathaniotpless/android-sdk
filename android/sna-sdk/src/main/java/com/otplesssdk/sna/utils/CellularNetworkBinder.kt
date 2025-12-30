package com.otplesssdk.sna.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.otplesssdk.utils.logger.SdkLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

internal object CellularNetworkBinder {
    private const val TAG = "SNASdk:CellularBinder"

    /**
     * Requests a cellular Network and keeps the request alive until [release] is invoked.
     *
     * Important: On Android, unregistering the NetworkCallback releases the request and
     * may cause the network to be torn down while you're still using it.
     */
    suspend fun acquireCellularNetwork(context: Context, timeoutMs: Int): Pair<Network, () -> Unit>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null

        SdkLogger.d(TAG, "Requesting cellular network (timeoutMs=$timeoutMs)")

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        return withTimeoutOrNull(timeoutMs.toLong().coerceAtLeast(1)) {
            suspendCancellableCoroutine { continuation ->
                var callbackRef: ConnectivityManager.NetworkCallback? = null
                val resumed = AtomicBoolean(false)

                fun release() {
                    val cb = callbackRef ?: return
                    callbackRef = null
                    try {
                        cm.unregisterNetworkCallback(cb)
                        SdkLogger.d(TAG, "Network callback unregistered (request released)")
                    } catch (e: Exception) {
                        SdkLogger.w(TAG, "Error unregistering network callback", e)
                    }
                }

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        SdkLogger.d(TAG, "Cellular network available: $network")
                        if (resumed.compareAndSet(false, true)) {
                            continuation.resume(Pair(network, ::release))
                        }
                    }

                    override fun onUnavailable() {
                        SdkLogger.w(TAG, "Cellular network request unavailable")
                        release()
                        if (resumed.compareAndSet(false, true)) {
                            continuation.resume(null)
                        }
                    }

                    override fun onLost(network: Network) {
                        SdkLogger.w(TAG, "Cellular network lost: $network")
                    }
                }

                callbackRef = callback

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        cm.requestNetwork(request, callback, timeoutMs)
                    } else {
                        cm.requestNetwork(request, callback)
                    }
                } catch (e: Exception) {
                    SdkLogger.e(TAG, "Failed to request cellular network", e)
                    release()
                    if (resumed.compareAndSet(false, true)) {
                        continuation.resume(null)
                    }
                    return@suspendCancellableCoroutine
                }

                continuation.invokeOnCancellation {
                    SdkLogger.d(TAG, "Cellular network request cancelled; releasing")
                    release()
                }
            }
        }
    }

    fun bindProcessToNetwork(context: Context, network: Network?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return try {
            SdkLogger.d(TAG, "Binding process to network: ${network ?: "null"}")
            cm.bindProcessToNetwork(network)
        } catch (e: Exception) {
            SdkLogger.w(TAG, "Failed to bind process to network", e)
            false
        }
    }
}
