package com.sna.sdk.testapp

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.otplesssdk.utils.deviceinfo.DeviceInfoCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DeviceInfoFragment : Fragment() {
    private val tag = "DeviceInfoJson"
    private lateinit var permStatus: TextView
    private lateinit var logView: TextView
    private lateinit var log: UiLog
    private var lastRawJson: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_device_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        permStatus = view.findViewById(R.id.permStatus)
        logView = view.findViewById(R.id.log)
        log = UiLog(logView)

        view.findViewById<MaterialButton>(R.id.btnRequestPerm).setOnClickListener {
            (activity as? MainActivity)?.requestPhonePermissionsIfNeeded()
            refreshPermissionStatus()
        }
        view.findViewById<MaterialButton>(R.id.btnWarmUp).setOnClickListener {
            DeviceInfoCollector.warmUp(requireContext().applicationContext)
            log.append("🔥 DeviceInfoCollector warmUp() triggered\n\n")
        }
        view.findViewById<MaterialButton>(R.id.btnClearCache).setOnClickListener {
            DeviceInfoCollector.clearCache()
            log.append("🧹 DeviceInfoCollector cache cleared\n\n")
        }
        view.findViewById<MaterialButton>(R.id.btnClearLog).setOnClickListener { log.clear() }
        view.findViewById<MaterialButton>(R.id.btnCollect).setOnClickListener { collect() }
        view.findViewById<MaterialButton>(R.id.btnNetwork).setOnClickListener { printNetworkInfo() }
        view.findViewById<MaterialButton>(R.id.btnIdentifiers).setOnClickListener { printIdentifiers() }
        view.findViewById<MaterialButton>(R.id.btnPresence).setOnClickListener { printAppPresence() }
        view.findViewById<MaterialButton>(R.id.btnCopy).setOnClickListener {
            val raw = lastRawJson
            if (raw.isNullOrBlank()) {
                log.append("⚠️ Nothing to copy yet (collect JSON first)\n\n")
            } else {
                val cm = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("device_info_json", raw))
                log.append("📋 Copied JSON to clipboard\n\n")
            }
        }

        refreshPermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }

    private fun refreshPermissionStatus() {
        permStatus.text = buildString {
            append("Permissions:\n")
            append(Permissions.describe(requireContext()))
        }
    }

    private fun collect() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val (pretty, raw) = withContext(Dispatchers.IO) {
                    val inner = DeviceInfoCollector.getDeviceInfoJson(
                        context = requireContext().applicationContext,
                        sdkVersion = "testapp",
                        sdkName = "sna-sdk-testapp"
                    )
                    val wrapped = "{\"device_info\":{${inner}}}"
                    val prettyPrinted = try {
                        JSONObject(wrapped).toString(2)
                    } catch (_: Exception) {
                        wrapped
                    }
                    prettyPrinted to wrapped
                }
                lastRawJson = raw

                log.append("🧾 Device Info JSON:\n")
                log.append(pretty)
                log.append("\n\n")

                // Also log the raw JSON to logcat (chunked by logcat itself).
                Log.i(tag, raw)
            } catch (e: Exception) {
                log.append("❌ Device info collection failed: ${e.javaClass.name}: ${e.message}\n\n")
            }
        }
    }

    private fun printNetworkInfo() {
        val info = DeviceInfoCollector.getNetworkInfo(requireContext().applicationContext)
        log.append("🌐 NetworkInfo:\n")
        log.append(info.toString())
        log.append("\n\n")
    }

    private fun printIdentifiers() {
        viewLifecycleOwner.lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                DeviceInfoCollector.getIdentifiersInfo(requireContext().applicationContext)
            }
            log.append("🪪 IdentifiersInfo:\n")
            log.append(info.toString())
            log.append("\n\n")
        }
    }

    private fun printAppPresence() {
        val info = DeviceInfoCollector.getAppPresenceInfo(requireContext().applicationContext)
        log.append("📦 AppPresenceInfo:\n")
        log.append(info.toString())
        log.append("\n\n")
    }
}

