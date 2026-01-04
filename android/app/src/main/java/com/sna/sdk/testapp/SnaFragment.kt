package com.sna.sdk.testapp

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.otplesssdk.sna.SNASdk
import com.otplesssdk.sna.models.SnaResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class SnaFragment : Fragment() {
    private lateinit var log: UiLog
    private lateinit var urlInput: TextInputEditText
    private lateinit var timeoutInput: TextInputEditText
    private lateinit var freshStateSwitch: SwitchMaterial

    // Default URL: user can edit; we refresh state each run if enabled.
    private val defaultAirtelUrl: String =
        "http://api-csp.airtel.in:8443/gateway/airtel-authentication-solution/authorize/OTPLESS_SO_OL7W6ko21mbaqVoIvIj3" +
            "?redirect_uri=https%3A%2F%2Fsna.otpless.tech%2Fsna%2Fcallback%2Fairtel" +
            "&state=6b9f0c5e-3a71-4d82-8f4c-1e7a2d5c9b63" +
            "&sub_acc_id=53edd94f-719d-44e3-a8e7-ee9a5f6fdff1"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_sna, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        urlInput = view.findViewById(R.id.snaUrl)
        timeoutInput = view.findViewById(R.id.timeoutSeconds)
        freshStateSwitch = view.findViewById(R.id.switchFreshState)
        log = UiLog(view.findViewById<TextView>(R.id.log))

        freshStateSwitch.isChecked = true
        timeoutInput.setText("30")
        if (urlInput.text.isNullOrBlank()) {
            urlInput.setText(withFreshState(defaultAirtelUrl))
        }

        view.findViewById<MaterialButton>(R.id.btnClearLog).setOnClickListener {
            log.clear()
        }
        view.findViewById<MaterialButton>(R.id.btnCopyUrl).setOnClickListener {
            val url = urlInput.text?.toString().orEmpty()
            copyText(requireContext(), label = "sna_url", text = url)
            log.append("📋 URL copied\n\n")
        }
        view.findViewById<MaterialButton>(R.id.btnCopyLog).setOnClickListener {
            log.copyToClipboard(requireContext(), "sna_log")
            log.append("📋 Log copied\n\n")
        }

        view.findViewById<MaterialButton>(R.id.btnRunSna).setOnClickListener {
            runSna()
        }
        view.findViewById<MaterialButton>(R.id.btnRunSnaBlocking).setOnClickListener {
            runSnaBlocking()
        }

        view.findViewById<MaterialButton>(R.id.btnSimInfo).setOnClickListener {
            printSimInfo()
        }
        view.findViewById<MaterialButton>(R.id.btnMobileData).setOnClickListener {
            printMobileData()
        }
    }

    private fun runSna() {
        val sdk = SNASdk.getInstance()
        if (sdk == null) {
            log.append("❌ SNASdk not initialized\n\n")
            return
        }

        val rawUrl = urlInput.text?.toString()?.trim().orEmpty().ifBlank { defaultAirtelUrl }
        val url = if (freshStateSwitch.isChecked) withFreshState(rawUrl) else normalizeUrl(rawUrl)
        urlInput.setText(url)

        val timeoutSeconds = timeoutInput.text?.toString()?.toLongOrNull()?.coerceAtLeast(1) ?: 30L

        log.append("🌐 Starting SNA\n")
        log.append("timeout=${timeoutSeconds}s\n")
        log.append("$url\n\n")

        viewLifecycleOwner.lifecycleScope.launch {
            val result = sdk.authenticate(
                url = url,
                timeoutSeconds = timeoutSeconds,
            )

            logSnaResult(result)
        }
    }

    private fun runSnaBlocking() {
        val sdk = SNASdk.getInstance()
        if (sdk == null) {
            log.append("❌ SNASdk not initialized\n\n")
            return
        }

        val rawUrl = urlInput.text?.toString()?.trim().orEmpty().ifBlank { defaultAirtelUrl }
        val url = if (freshStateSwitch.isChecked) withFreshState(rawUrl) else normalizeUrl(rawUrl)
        urlInput.setText(url)

        val timeoutSeconds = timeoutInput.text?.toString()?.toLongOrNull()?.coerceAtLeast(1) ?: 30L

        log.append("🌐 Starting SNA (blocking wrapper)\n")
        log.append("timeout=${timeoutSeconds}s\n")
        log.append("$url\n\n")

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                sdk.authenticateBlocking(url = url, timeoutSeconds = timeoutSeconds)
            }
            logSnaResult(result)
        }
    }

    private fun printSimInfo() {
        val sdk = SNASdk.getInstance()
        if (sdk == null) {
            log.append("❌ SNASdk not initialized\n\n")
            return
        }
        val info = sdk.getSimNetworkInfo()
        log.append("📶 SIM/Network info:\n")
        log.append("mcc=${info.mcc} mnc=${info.mnc}\n")
        log.append("networkName=${info.networkName}\n")
        log.append("mobileDataEnabled(best-effort)=${info.isMobileDataEnabled}\n\n")
    }

    private fun printMobileData() {
        val sdk = SNASdk.getInstance()
        if (sdk == null) {
            log.append("❌ SNASdk not initialized\n\n")
            return
        }
        val enabled = sdk.isMobileDataEnabled()
        log.append("📡 Mobile data available=${enabled}\n\n")
    }

    private fun logSnaResult(result: SnaResult) {
        when (result) {
            is SnaResult.Success -> {
                log.append("✅ SUCCESS http=${result.response.httpCode}\n")
                log.append("final=${result.response.finalUrl}\n")
                log.append("totalMs=${result.timings.totalMs}\n")
                result.timings.cellularAcquireMs?.let { log.append("cellularAcquireMs=$it\n") }
                result.timings.processBindMs?.let { log.append("processBindMs=$it\n") }
                log.append("headers=${result.response.headers.size} contentType=${result.response.contentType}\n")
                if (result.redirects.isNotEmpty()) {
                    log.append("\nRedirects:\n")
                    result.redirects.forEachIndexed { i, hop ->
                        log.append(
                            "${i + 1}. ${hop.httpCode ?: "-"} ${hop.url} " +
                                "(${hop.durationMs}ms" +
                                (hop.dnsMs?.let { ", dns=${it}ms" } ?: "") +
                                (hop.connectMs?.let { ", connect=${it}ms" } ?: "") +
                                (hop.tlsMs?.let { ", tls=${it}ms" } ?: "") +
                                ")\n"
                        )
                    }
                }
                result.response.body?.let {
                    log.append("\nBody (truncated=${result.response.bodyTruncated}):\n")
                    log.append(it)
                    log.append("\n")
                }
                log.append("\n")
            }

            is SnaResult.Failure -> {
                log.append("❌ FAILURE reason=${result.reason}\n")
                result.detail?.let { log.append("detail=$it\n") }
                log.append("totalMs=${result.timings.totalMs}\n")
                result.timings.cellularAcquireMs?.let { log.append("cellularAcquireMs=$it\n") }
                result.timings.processBindMs?.let { log.append("processBindMs=$it\n") }
                result.response?.let { resp ->
                    log.append("final=${resp.finalUrl} http=${resp.httpCode}\n")
                    log.append("headers=${resp.headers.size} contentType=${resp.contentType}\n")
                }
                if (result.redirects.isNotEmpty()) {
                    log.append("\nTrace:\n")
                    result.redirects.forEachIndexed { i, hop ->
                        log.append("${i + 1}. ${hop.httpCode ?: "-"} ${hop.url} (${hop.durationMs}ms)\n")
                    }
                }
                log.append("\n")
            }
        }
    }

    private fun normalizeUrl(input: String): String {
        // Accept documentation-safe prefix.
        return input.trim().replaceFirst("httpxx://", "http://")
    }

    private fun withFreshState(inputUrl: String): String {
        val normalized = normalizeUrl(inputUrl)
        val newState = UUID.randomUUID().toString()

        return try {
            val uri = Uri.parse(normalized)
            val builder = uri.buildUpon().clearQuery()
            for (name in uri.queryParameterNames) {
                if (name.equals("state", ignoreCase = true)) continue
                for (v in uri.getQueryParameters(name)) {
                    builder.appendQueryParameter(name, v)
                }
            }
            builder.appendQueryParameter("state", newState)
            builder.build().toString()
        } catch (_: Exception) {
            if (normalized.contains("state=", ignoreCase = true)) {
                normalized.replace(
                    Regex("([?&])state=[^&]*", RegexOption.IGNORE_CASE),
                    "$1state=$newState"
                )
            } else {
                normalized + (if (normalized.contains("?")) "&" else "?") + "state=$newState"
            }
        }
    }

    private fun copyText(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

