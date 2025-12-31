package com.sna.sdk.testapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.otplesssdk.sna.SNASdk
import com.otplesssdk.sna.callback.SnaCallback
import com.otplesssdk.sna.models.SnaResult
import com.otplesssdk.otp.OtpSdk
import com.otplesssdk.otp.callback.OtpCallback
import com.otplesssdk.otp.models.OtpResult
import com.otplesssdk.otp.models.OtpChannel
import com.otplesssdk.utils.deviceinfo.DeviceInfoCollector
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    
    private lateinit var resultText: TextView
    private val PERMISSION_REQUEST_CODE = 1001
    private lateinit var urlInput: EditText

    private val DEVICE_INFO_LOG_TAG = "DeviceInfoJson"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable SDK logging for debugging
        SNASdk.setLoggingEnabled(true)
        OtpSdk.setLoggingEnabled(true)
        
        // Initialize SDK
        SNASdk.initialize(applicationContext)
        OtpSdk.initialize(applicationContext)
        
        // Create UI programmatically (no layout files needed)
        createUI()
        
        // Request permission
        requestPermissionIfNeeded()

        // Auto-print device info once on startup to make it easy to verify via Logcat on real devices.
        // (The button still exists for manual re-checks.)
        resultText.post { printDeviceInfoJson() }

        // Also log GAID debug info (success vs limit-ad-tracking vs exception).
        debugGaid()
    }
    
    private fun createUI() {
        val scrollView = ScrollView(this)
        val mainLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        val title = TextView(this).apply {
            text = "SNA SDK Test App"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }

        urlInput = EditText(this).apply {
            hint = "SNA URL (http:// or https://)"
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText("http://partnerapi.jio.com/v2/adv/smv?client_id=l7xx632314bada7544c39bd06eedcc85014f&redirect_uri=https://sna.otpless.app/sna/callback/jio&app_partner_code=DOM_Mi_as-otpless_Meesho_97093&state=3ccb558b6c974101bh5yfab9b2d38a2a")
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }

        resultText = TextView(this).apply {
            text = "Click buttons to test SDK functions\n\n"
            textSize = 14f
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFFF5F5F5.toInt())
        }
        
        val btnSimInfo = Button(this).apply {
            text = "Get SIM Info"
            setOnClickListener { testSimInfo() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }
        
        val btnMobileData = Button(this).apply {
            text = "Check Mobile Data Status"
            setOnClickListener { testMobileData() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }

        val btnDeviceInfo = Button(this).apply {
            text = "Print Device Info JSON"
            setOnClickListener { printDeviceInfoJson() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }

        val btnSna = Button(this).apply {
            text = "Run SNA URL (Cellular + Trace)"
            setOnClickListener { testSnaUrl() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }

        val btnOtpStart = Button(this).apply {
            text = "Start OTP Listener (SMS + WhatsApp)"
            setOnClickListener { testOtpListener() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }

        val btnOtpStop = Button(this).apply {
            text = "Stop OTP Listener"
            setOnClickListener { stopOtpListener() }
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
        }
        
        mainLayout.addView(title)
        mainLayout.addView(urlInput)
        mainLayout.addView(resultText)
        mainLayout.addView(btnSimInfo)
        mainLayout.addView(btnMobileData)
        mainLayout.addView(btnDeviceInfo)
        mainLayout.addView(btnSna)
        mainLayout.addView(btnOtpStart)
        mainLayout.addView(btnOtpStop)
        
        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }
    
    private fun requestPermissionIfNeeded() {
        // For testing "all SIM/eSIM" enumeration, many devices require phone-state runtime permission(s).
        val permissionsToRequest = mutableListOf(Manifest.permission.READ_PHONE_STATE)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            permissionsToRequest.add(Manifest.permission.READ_BASIC_PHONE_STATE)
        }

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            appendResult("✅ Phone-state permissions already granted (subscription enumeration enabled)\n\n")
            return
        }

        if (missing.any { ActivityCompat.shouldShowRequestPermissionRationale(this, it) }) {
            appendResult(
                "ℹ️ Permission needed to enumerate SIM/eSIM subscriptions.\n" +
                    "We only read carrier/operator + network capability fields; no phone numbers.\n\n"
            )
        }

        ActivityCompat.requestPermissions(
            this,
            missing.toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val grantedAll = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (grantedAll) {
                appendResult("✅ Permission granted! Re-printing device info with subscription data...\n\n")
                DeviceInfoCollector.clearCache()
                printDeviceInfoJson()
            } else {
                appendResult("❌ Permission denied. SIM/eSIM subscription enumeration may be unavailable.\n\n")
            }
        }
    }
    
    private fun testSimInfo() {
        val sdk = SNASdk.getInstance()
        if (sdk == null) {
            appendResult("❌ SDK not initialized\n\n")
            return
        }
        
        val info = sdk.getSimNetworkInfo()
        appendResult("""
            📱 SIM Info:
            MCC: ${info.mcc ?: "N/A"}
            MNC: ${info.mnc ?: "N/A"}
            Network Name: ${info.networkName ?: "N/A"}
            Mobile Data Enabled: ${info.isMobileDataEnabled}
            
        """.trimIndent())
    }
    
    private fun testMobileData() {
        val sdk = SNASdk.getInstance()
        if (sdk == null) {
            appendResult("❌ SDK not initialized\n\n")
            return
        }
        
        val isEnabled = sdk.isMobileDataEnabled()
        appendResult(
            "📶 Mobile Data Status (conservative): ${if (isEnabled) "ENABLED ✅" else "UNKNOWN/DISABLED ❌"}\n\n"
        )
    }

    private fun printDeviceInfoJson() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val inner = DeviceInfoCollector.getDeviceInfoJson(
                        context = applicationContext,
                        sdkVersion = "testapp",
                        sdkName = "sna-sdk-testapp"
                    )

                    val wrapped = "{\"device_info\":{${inner}}}"
                    val pretty = try {
                        JSONObject(wrapped).toString(2)
                    } catch (_: Exception) {
                        // If JSON parsing fails for any reason, still show the raw string.
                        wrapped
                    }

                    Pair(pretty, wrapped)
                }

                appendResult("🧾 Device Info JSON (pretty):\n${result.first}\n\n")
                appendResult("✅ Logged to Logcat with tag: $DEVICE_INFO_LOG_TAG\n\n")

                // Also log raw JSON (chunked to avoid Logcat truncation).
                withContext(Dispatchers.IO) {
                    logLong(DEVICE_INFO_LOG_TAG, result.second)
                }
            } catch (e: Exception) {
                appendResult("❌ Failed to collect device info: ${e.message}\n\n")
                Log.e(DEVICE_INFO_LOG_TAG, "Failed to collect device info JSON", e)
            }
        }
    }

    private fun logLong(tag: String, message: String) {
        val chunkSize = 3500
        if (message.length <= chunkSize) {
            Log.i(tag, message)
            return
        }
        var i = 0
        var part = 1
        while (i < message.length) {
            val end = (i + chunkSize).coerceAtMost(message.length)
            Log.i(tag, "part=$part ${message.substring(i, end)}")
            i = end
            part++
        }
    }

    private fun debugGaid() {
        lifecycleScope.launch {
            try {
                val (id, isLimited) = withContext(Dispatchers.IO) {
                    val info = AdvertisingIdClient.getAdvertisingIdInfo(applicationContext)
                    info.id to info.isLimitAdTrackingEnabled
                }
                // If you ever need to touch UI here, switch to Dispatchers.Main.
                Log.i("GaidDebug", "AdvertisingIdClient: id=$id, isLimitAdTrackingEnabled=$isLimited")
            } catch (t: Throwable) {
                Log.e("GaidDebug", "AdvertisingIdClient failed: ${t.javaClass.name}: ${t.message}", t)
            }
        }
    }

    private fun testSnaUrl() {
        val sdk = SNASdk.getInstance()
        if (sdk == null) {
            appendResult("❌ SDK not initialized\n\n")
            return
        }

        val url = urlInput.text?.toString()?.trim().orEmpty()
        if (url.isBlank()) {
            appendResult("❌ Please enter a URL\n\n")
            return
        }

        appendResult("🌐 Starting SNA call over cellular...\n$url\n\n")

        sdk.authenticate(
            url = url,
            timeoutSeconds = 5,
            callback = object : SnaCallback {
                override fun onResult(result: SnaResult) {
                    when (result) {
                        is SnaResult.Success -> {
                            appendResult(
                                "✅ SNA Success: ${result.response.httpCode}\n" +
                                    "Final: ${result.response.finalUrl}\n" +
                                    "Total: ${result.timings.totalMs}ms\n" +
                                    (result.timings.cellularAcquireMs?.let { "Cellular acquire: ${it}ms\n" } ?: "") +
                                    (result.timings.processBindMs?.let { "Process bind: ${it}ms\n" } ?: "") +
                                    "\n"
                            )

                            if (result.response.body != null) {
                                appendResult("Final body (truncated=${result.response.bodyTruncated}):\n${result.response.body}\n\n")
                            }
                            if (result.redirects.isNotEmpty()) {
                                appendResult("Redirect trace:\n")
                                result.redirects.forEachIndexed { index, hop ->
                                    appendResult(
                                        "${index + 1}. ${hop.httpCode ?: "-"} ${hop.url} " +
                                            "(${hop.durationMs}ms" +
                                            (hop.dnsMs?.let { ", dns=${it}ms" } ?: "") +
                                            (hop.connectMs?.let { ", connect=${it}ms" } ?: "") +
                                            (hop.tlsMs?.let { ", tls=${it}ms" } ?: "") +
                                            ")\n"
                                    )
                                }
                                appendResult("\n")
                            }
                        }

                        is SnaResult.Failure -> {
                            appendResult(
                                "❌ SNA Failure: ${result.reason}\n" +
                                    "${result.detail ?: ""}\n" +
                                    "Total: ${result.timings.totalMs}ms\n" +
                                    (result.timings.cellularAcquireMs?.let { "Cellular acquire: ${it}ms\n" } ?: "") +
                                    (result.timings.processBindMs?.let { "Process bind: ${it}ms\n" } ?: "") +
                                    "\n"
                            )
                            val finalResponse = result.response
                            if (finalResponse != null) {
                                appendResult("Final URL: ${finalResponse.finalUrl} (${finalResponse.httpCode})\n\n")
                            }
                            if (result.redirects.isNotEmpty()) {
                                appendResult("Trace before failure:\n")
                                result.redirects.forEachIndexed { index, hop ->
                                    appendResult("${index + 1}. ${hop.httpCode ?: "-"} ${hop.url} (${hop.durationMs}ms)\n")
                                }
                                appendResult("\n")
                            }
                        }
                    }
                }
            }
        )
    }

    private fun testOtpListener() {
        val sdk = OtpSdk.getInstance()
        if (sdk == null) {
            appendResult("❌ OTP SDK not initialized\n\n")
            return
        }

        val channels = setOf(OtpChannel.SMS, OtpChannel.WHATSAPP)

        appendResult(
            "🔐 Starting OTP listener. " +
                "WhatsApp installed: ${sdk.isWhatsAppInstalled()}\n\n"
        )
        val appHashes = OtpSdk.getAppHashes(applicationContext)
        if (appHashes.isNotEmpty()) {
            appendResult("🔑 SMS App Hashes:\n")
            appHashes.forEach { hash ->
                appendResult("${hash.packageName}: ${hash.hash}\n")
            }
            appendResult("\n")
        } else {
            appendResult("⚠️ No SMS app hashes found\n\n")
        }

        sdk.startListening(
            channels = channels,
            callback = object : OtpCallback {
                override fun onResult(result: OtpResult) {
                    when (result) {
                        is OtpResult.Success -> {
                            val senderInfo = result.senderId?.let { " SenderId=$it" }.orEmpty()
                            appendResult("✅ OTP (${result.source}): ${result.otp}$senderInfo\n\n")
                        }
                        is OtpResult.Error -> {
                            val details = buildString {
                                result.errorKey?.let { append("Key=$it") }
                                result.errorMessage?.let {
                                    if (isNotEmpty()) append(" | ")
                                    append("Message=$it")
                                }
                            }
                            appendResult(
                                "❌ OTP Error (${result.source}): ${result.reason}\n" +
                                    (if (details.isNotEmpty()) "$details\n" else "") +
                                    "\n"
                            )
                        }
                    }
                }
            }
        )
    }

    private fun stopOtpListener() {
        val sdk = OtpSdk.getInstance()
        if (sdk == null) {
            appendResult("❌ OTP SDK not initialized\n\n")
            return
        }
        sdk.stop()
        appendResult("🛑 OTP listener stopped\n\n")
    }
    
    private fun appendResult(text: String) {
        resultText.append(text)
        // Auto-scroll to bottom
        val sv = resultText.parent as? ScrollView
        sv?.post {
            sv.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }
}
