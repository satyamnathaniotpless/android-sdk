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

    private fun ensureCurrentHop(startNs: Long): HopBuilder {
        synchronized(lock) {
            val current = hops.lastOrNull { it.endNs == null }
            if (current != null) return current
            return HopBuilder(url = "", startNs = startNs).also { hops.add(it) }
        }
    }

    private fun currentHopOrNull(): HopBuilder? {
        synchronized(lock) {
            return hops.lastOrNull { it.endNs == null }
        }
    }

    override fun requestHeadersStart(call: Call) {
        val now = System.nanoTime()
        // Start a hop if we haven't seen earlier events (DNS/connect) for this request.
        ensureCurrentHop(now)
    }

    override fun dnsStart(call: Call, domainName: String) {
        val now = System.nanoTime()
        val hop = ensureCurrentHop(now)
        if (hop.url.isBlank()) hop.url = domainName
        hop.dnsStartNs = now
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        val hop = currentHopOrNull() ?: return
        val start = hop.dnsStartNs ?: return
        hop.dnsNs = System.nanoTime() - start
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        val now = System.nanoTime()
        val hop = ensureCurrentHop(now)
        if (hop.url.isBlank()) hop.url = inetSocketAddress.hostString
        hop.connectStartNs = now
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: okhttp3.Protocol?
    ) {
        val hop = currentHopOrNull() ?: return
        val start = hop.connectStartNs ?: return
        hop.connectNs = System.nanoTime() - start
    }

    override fun secureConnectStart(call: Call) {
        val now = System.nanoTime()
        ensureCurrentHop(now).tlsStartNs = now
    }

    override fun secureConnectEnd(call: Call, handshake: okhttp3.Handshake?) {
        val hop = currentHopOrNull() ?: return
        val start = hop.tlsStartNs ?: return
        hop.tlsNs = System.nanoTime() - start
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        val hop = currentHopOrNull() ?: return
        hop.url = response.request.url.toString()
        hop.httpCode = response.code
        hop.endNs = System.nanoTime()
    }

    override fun callFailed(call: Call, ioe: IOException) {
        val hop = currentHopOrNull() ?: return
        if (hop.url.isBlank()) hop.url = call.request().url.toString()
        hop.endNs = System.nanoTime()
    }
}
