package com.otplesssdk.sna.utils

import com.otplesssdk.sna.models.SnaRedirectHop
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

internal class SnaRedirectTracer : EventListener() {
    private data class HopBuilder(
        var url: String,
        val startNs: Long,
        var endNs: Long? = null,
        var httpCode: Int? = null,
        var dnsStartNs: Long? = null,
        var dnsNs: Long? = null,
        var connectStartNs: Long? = null,
        var connectNs: Long? = null,
        var tlsStartNs: Long? = null,
        var tlsNs: Long? = null
    )

    private val lock = Any()
    private val hops = ArrayList<HopBuilder>()

    fun snapshot(): List<SnaRedirectHop> {
        synchronized(lock) {
            return hops.map { hop ->
                val endNs = hop.endNs ?: hop.startNs
                SnaRedirectHop(
                    url = hop.url,
                    httpCode = hop.httpCode,
                    durationMs = TimeUnit.NANOSECONDS.toMillis(endNs - hop.startNs),
                    dnsMs = hop.dnsNs?.let { TimeUnit.NANOSECONDS.toMillis(it) },
                    connectMs = hop.connectNs?.let { TimeUnit.NANOSECONDS.toMillis(it) },
                    tlsMs = hop.tlsNs?.let { TimeUnit.NANOSECONDS.toMillis(it) }
                )
            }
        }
    }

    // NOTE: All hop list/field mutations must happen under `lock` to avoid races between OkHttp callbacks.
    private fun ensureCurrentHopLocked(startNs: Long): HopBuilder {
        val current = hops.lastOrNull { it.endNs == null }
        if (current != null) return current
        return HopBuilder(url = "", startNs = startNs).also { hops.add(it) }
    }

    private fun currentHopOrNullLocked(): HopBuilder? = hops.lastOrNull { it.endNs == null }

    private fun ensureCurrentHop(startNs: Long): HopBuilder =
        synchronized(lock) { ensureCurrentHopLocked(startNs) }

    private fun currentHopOrNull(): HopBuilder? =
        synchronized(lock) { currentHopOrNullLocked() }

    override fun requestHeadersStart(call: Call) {
        val now = System.nanoTime()
        // Start a hop if we haven't seen earlier events (DNS/connect) for this request.
        ensureCurrentHop(now)
    }

    override fun dnsStart(call: Call, domainName: String) {
        val now = System.nanoTime()
        synchronized(lock) {
            val hop = ensureCurrentHopLocked(now)
            if (hop.url.isBlank()) hop.url = domainName
            hop.dnsStartNs = now
        }
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        val now = System.nanoTime()
        synchronized(lock) {
            val hop = currentHopOrNullLocked() ?: return
            val start = hop.dnsStartNs ?: return
            hop.dnsNs = now - start
        }
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        val now = System.nanoTime()
        synchronized(lock) {
            val hop = ensureCurrentHopLocked(now)
            if (hop.url.isBlank()) hop.url = inetSocketAddress.hostString
            hop.connectStartNs = now
        }
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: okhttp3.Protocol?
    ) {
        val now = System.nanoTime()
        synchronized(lock) {
            val hop = currentHopOrNullLocked() ?: return
            val start = hop.connectStartNs ?: return
            hop.connectNs = now - start
        }
    }

    override fun secureConnectStart(call: Call) {
        val now = System.nanoTime()
        synchronized(lock) {
            ensureCurrentHopLocked(now).tlsStartNs = now
        }
    }

    override fun secureConnectEnd(call: Call, handshake: okhttp3.Handshake?) {
        val hop = synchronized(lock) { currentHopOrNullLocked() } ?: return
        synchronized(hop) {
            val start = hop.tlsStartNs ?: return
            val now = System.nanoTime()
            hop.tlsNs = now - start
        }
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        val hop = currentHopOrNull() ?: return
        val url = response.request.url.toString()
        val code = response.code
        synchronized(lock) {
            if (hop.endNs != null) return
            val now = System.nanoTime()
            hop.url = url
            hop.httpCode = code
            hop.endNs = now
        }
    }

    override fun callFailed(call: Call, ioe: IOException) {
        val now = System.nanoTime()
        val url = call.request().url.toString()
        synchronized(lock) {
            val hop = hops.lastOrNull { it.endNs == null } ?: return
            if (hop.url.isBlank()) hop.url = url
            hop.endNs = now
        }
    }
}
