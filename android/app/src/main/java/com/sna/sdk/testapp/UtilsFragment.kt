package com.sna.sdk.testapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.otplesssdk.utils.event.EventSender
import com.otplesssdk.utils.ids.SessionIdManager
import com.otplesssdk.utils.network.ApiClient
import com.otplesssdk.utils.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

class UtilsFragment : Fragment() {
    private lateinit var log: UiLog
    private lateinit var logView: TextView
    private lateinit var lastHeadersView: TextView

    private lateinit var inputEventEndpoint: TextInputEditText
    private lateinit var inputAppId: TextInputEditText
    private lateinit var inputSdkName: TextInputEditText
    private lateinit var inputSdkVersion: TextInputEditText
    private lateinit var inputEventName: TextInputEditText
    private lateinit var inputEventProps: TextInputEditText
    private lateinit var inputUserId: TextInputEditText
    private lateinit var inputAsid: TextInputEditText
    private lateinit var inputRequestId: TextInputEditText
    private lateinit var inputState: TextInputEditText

    private lateinit var inputUrl: TextInputEditText
    private lateinit var inputTimeoutMs: TextInputEditText
    private lateinit var inputMaxBodyKb: TextInputEditText
    private lateinit var inputHeaderName: TextInputEditText
    private lateinit var inputHeaderValue: TextInputEditText

    private var lastEffectiveRequestHeaders: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_utils, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        logView = view.findViewById(R.id.log)
        lastHeadersView = view.findViewById(R.id.lastHeaders)
        log = UiLog(logView)

        inputEventEndpoint = view.findViewById(R.id.eventEndpoint)
        inputAppId = view.findViewById(R.id.appId)
        inputSdkName = view.findViewById(R.id.sdkName)
        inputSdkVersion = view.findViewById(R.id.sdkVersion)
        inputEventName = view.findViewById(R.id.eventName)
        inputEventProps = view.findViewById(R.id.eventProps)
        inputUserId = view.findViewById(R.id.userId)
        inputAsid = view.findViewById(R.id.asid)
        inputRequestId = view.findViewById(R.id.requestId)
        inputState = view.findViewById(R.id.state)

        inputUrl = view.findViewById(R.id.httpUrl)
        inputTimeoutMs = view.findViewById(R.id.timeoutMs)
        inputMaxBodyKb = view.findViewById(R.id.maxBodyKb)
        inputHeaderName = view.findViewById(R.id.headerName)
        inputHeaderValue = view.findViewById(R.id.headerValue)

        inputSdkName.setText(EventSender.sdkName ?: "core-sdk-testapp")
        inputSdkVersion.setText(EventSender.sdkVersion ?: "testapp")
        inputTimeoutMs.setText("10000")
        inputMaxBodyKb.setText("64")
        inputEventName.setText("test_event")
        if (inputEventProps.text.isNullOrBlank()) {
            inputEventProps.setText("""{"from":"sample_app","note":"edit me"}""")
        }

        view.findViewById<MaterialButton>(R.id.btnClearLog).setOnClickListener { log.clear() }
        view.findViewById<MaterialButton>(R.id.btnCopyLog).setOnClickListener {
            log.copyToClipboard(requireContext(), "utils_log")
            log.append("📋 Copied log\n\n")
        }
        view.findViewById<MaterialButton>(R.id.btnCopyHeaders).setOnClickListener {
            val headers = lastEffectiveRequestHeaders
            if (headers.isNullOrBlank()) {
                log.append("⚠️ No request headers captured yet (run request first)\n\n")
            } else {
                UiLog(lastHeadersView).copyToClipboard(requireContext(), "effective_request_headers")
                log.append("📋 Copied captured request headers\n\n")
            }
        }

        view.findViewById<MaterialButton>(R.id.btnInitEventSender).setOnClickListener { initEventSender() }
        view.findViewById<MaterialButton>(R.id.btnSendEvent).setOnClickListener { sendEvent() }

        view.findViewById<MaterialButton>(R.id.btnPutHeader).setOnClickListener { putCustomHeader() }
        view.findViewById<MaterialButton>(R.id.btnClearHeaders).setOnClickListener { clearCustomHeaders() }
        view.findViewById<MaterialButton>(R.id.btnRunRequest).setOnClickListener { runRequest() }
        view.findViewById<MaterialButton>(R.id.btnRunApiClient).setOnClickListener { runApiClient() }

        refreshIds()
        refreshHeadersVisibility()
    }

    private fun refreshIds() {
        val ctx = requireContext().applicationContext
        val inid = SessionIdManager.getInId(ctx)
        val tsid = SessionIdManager.getTsId(ctx)
        log.append("IDs: inid=$inid tsid=$tsid\n\n")
    }

    private fun initEventSender() {
        val endpoint = inputEventEndpoint.text?.toString()?.trim().orEmpty()
        val appId = inputAppId.text?.toString()?.trim().orEmpty()
        val sdkName = inputSdkName.text?.toString()?.trim().orEmpty()
        val sdkVersion = inputSdkVersion.text?.toString()?.trim().orEmpty()

        EventSender.initialize(
            context = requireContext().applicationContext,
            sdkName = sdkName.ifBlank { null },
            sdkVersion = sdkVersion.ifBlank { null },
            appId = appId.ifBlank { null },
            eventEndpoint = endpoint.ifBlank { null },
        )

        log.append("✅ EventSender initialized\n")
        log.append("isInitialized=${EventSender.isInitialized()}\n")
        log.append("sdkName=${EventSender.sdkName}\n")
        log.append("sdkVersion=${EventSender.sdkVersion}\n")
        log.append("endpoint=${EventSender.eventEndpoint}\n")
        log.append("inid=${EventSender.getInId()}\n")
        log.append("tsid=${EventSender.getTsId()}\n\n")
    }

    private fun sendEvent() {
        val name = inputEventName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            log.append("❌ Event name is required\n\n")
            return
        }

        val props = parsePropsJson(inputEventProps.text?.toString())
        val userId = inputUserId.text?.toString()?.trim().orEmpty().ifBlank { null }
        val asid = inputAsid.text?.toString()?.trim().orEmpty().ifBlank { null }
        val requestId = inputRequestId.text?.toString()?.trim().orEmpty().ifBlank { null }
        val state = inputState.text?.toString()?.trim().orEmpty().ifBlank { null }

        EventSender.sendEvent(
            eventName = name,
            properties = props,
            requestId = requestId,
            asid = asid,
            state = state,
            userId = userId,
        )

        log.append("🚀 sendEvent queued\n")
        log.append("name=$name props=${props.keys}\n")
        log.append("userId=$userId asid=$asid requestId=$requestId state=$state\n\n")
    }

    private fun putCustomHeader() {
        val name = inputHeaderName.text?.toString()?.trim().orEmpty()
        val value = inputHeaderValue.text?.toString()?.trim()
        if (name.isBlank()) {
            log.append("❌ Header name is required\n\n")
            return
        }
        HttpClient.putCustomHeader(name, value)
        log.append("✅ Custom header updated: $name=${value ?: "<null>"}\n\n")
    }

    private fun clearCustomHeaders() {
        HttpClient.clearCustomHeaders()
        log.append("🧹 Cleared custom headers\n\n")
    }

    private fun runRequest() {
        val url = inputUrl.text?.toString()?.trim().orEmpty()
        if (url.isBlank()) {
            log.append("❌ URL is required\n\n")
            return
        }

        val timeoutMs = inputTimeoutMs.text?.toString()?.trim()?.toLongOrNull()?.coerceAtLeast(1) ?: 10_000L
        val maxBodyBytes = (inputMaxBodyKb.text?.toString()?.trim()?.toLongOrNull()?.coerceAtLeast(0) ?: 64L) * 1024L

        log.append("🌐 Running request\n")
        log.append("url=$url timeoutMs=$timeoutMs maxBodyBytes=$maxBodyBytes\n\n")

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val capture = StringBuilder()
                val client = HttpClient.newBuilder()
                    .callTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .writeTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    // Captures the effective request headers after HttpClient's common headers interceptor.
                    .addInterceptor { chain ->
                        val req = chain.request()
                        capture.append("Effective request headers:\n")
                        for (name in req.headers.names()) {
                            capture.append(name).append(": ").append(req.header(name)).append('\n')
                        }
                        chain.proceed(req)
                    }
                    .build()

                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                response.use { r ->
                    val body = r.body?.source()?.let { source ->
                        val buffer = okio.Buffer()
                        source.request(maxBodyBytes + 1)
                        val toRead = minOf(source.buffer.size, maxBodyBytes + 1)
                        source.read(buffer, toRead)
                        val truncated = buffer.size > maxBodyBytes
                        if (truncated) {
                            val prefix = okio.Buffer()
                            buffer.read(prefix, maxBodyBytes)
                            prefix.readUtf8() + "\n…(truncated)…"
                        } else {
                            buffer.readUtf8()
                        }
                    }
                    Triple(
                        capture.toString(),
                        "http=${r.code} contentType=${r.body?.contentType()}\n",
                        body
                    )
                }
            }

            lastEffectiveRequestHeaders = result.first
            lastHeadersView.text = result.first
            lastHeadersView.isVisible = true

            log.append(result.second)
            if (!result.third.isNullOrBlank()) {
                log.append("Body:\n")
                log.append(result.third!!)
                log.append("\n\n")
            } else {
                log.append("Body: <empty>\n\n")
            }
        }
    }

    private fun runApiClient() {
        val url = inputUrl.text?.toString()?.trim().orEmpty()
        if (url.isBlank()) {
            log.append("❌ URL is required\n\n")
            return
        }

        val timeoutMs = inputTimeoutMs.text?.toString()?.trim()?.toLongOrNull()?.coerceAtLeast(1) ?: 10_000L
        val maxBodyBytes = (inputMaxBodyKb.text?.toString()?.trim()?.toLongOrNull()?.coerceAtLeast(0) ?: 64L) * 1024L

        log.append("🌐 ApiClient.execute()\n")
        log.append("url=$url timeoutMs=$timeoutMs maxBodyBytes=$maxBodyBytes\n\n")

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).get().build()
                ApiClient.execute(
                    request = request,
                    policy = ApiClient.NetworkPolicy(timeoutMs = timeoutMs, maxBodyBytes = maxBodyBytes)
                )
            }

            when (result) {
                is ApiClient.ApiResult.Success -> {
                    log.append("✅ SUCCESS http=${result.code} contentType=${result.contentType}\n")
                    log.append("headers=${result.headers.size}\n")
                    result.body?.let {
                        log.append("\nBody:\n")
                        log.append(it)
                        log.append("\n")
                    }
                    log.append("\n")
                }

                is ApiClient.ApiResult.Failure -> {
                    log.append("❌ FAILURE error=${result.error}\n")
                    result.code?.let { log.append("http=$it\n") }
                    log.append("headers=${result.headers.size}\n")
                    result.bodySnippet?.let {
                        log.append("\nBodySnippet:\n")
                        log.append(it)
                        log.append("\n")
                    }
                    log.append("\n")
                }
            }
        }
    }

    private fun refreshHeadersVisibility() {
        lastHeadersView.isVisible = !lastHeadersView.text.isNullOrBlank()
    }

    private fun parsePropsJson(raw: String?): Map<String, Any?> {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(text)
            val out = LinkedHashMap<String, Any?>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k] = obj.opt(k)
            }
            out
        } catch (_: Exception) {
            log.append("⚠️ Properties JSON invalid; sending empty properties\n\n")
            emptyMap()
        }
    }
}

