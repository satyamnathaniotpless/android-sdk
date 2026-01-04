package com.sna.sdk.testapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.otplesssdk.otp.OtpSdk
import com.otplesssdk.otp.callback.OtpCallback
import com.otplesssdk.otp.models.OtpChannel
import com.otplesssdk.otp.models.OtpResult

class OtpFragment : Fragment() {
    private lateinit var log: UiLog
    private lateinit var checkSms: MaterialCheckBox
    private lateinit var checkWhatsapp: MaterialCheckBox

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_otp, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        log = UiLog(view.findViewById<TextView>(R.id.log))
        checkSms = view.findViewById(R.id.checkSms)
        checkWhatsapp = view.findViewById(R.id.checkWhatsapp)

        view.findViewById<MaterialButton>(R.id.btnClearLog).setOnClickListener { log.clear() }
        view.findViewById<MaterialButton>(R.id.btnCopyLog).setOnClickListener {
            log.copyToClipboard(requireContext(), "otp_log")
            log.append("📋 Log copied\n\n")
        }
        view.findViewById<MaterialButton>(R.id.btnCheckWhatsapp).setOnClickListener { checkWhatsApp() }
        view.findViewById<MaterialButton>(R.id.btnPrintHashes).setOnClickListener { printHashes() }
        view.findViewById<MaterialButton>(R.id.btnStart).setOnClickListener { startListening() }
        view.findViewById<MaterialButton>(R.id.btnStop).setOnClickListener { stopListening() }
    }

    private fun checkWhatsApp() {
        val sdk = OtpSdk.getInstance()
        if (sdk == null) {
            log.append("❌ OtpSdk not initialized\n\n")
            return
        }
        val installed = sdk.isWhatsAppInstalled()
        log.append("📦 WhatsApp installed=$installed\n\n")
    }

    private fun printHashes() {
        val hashes = OtpSdk.getAppHashes(requireContext().applicationContext)
        if (hashes.isEmpty()) {
            log.append("⚠️ No SMS app hashes found\n\n")
            return
        }
        log.append("🔑 SMS App Hashes:\n")
        hashes.forEach { h -> log.append("${h.packageName}: ${h.hash}\n") }
        log.append("\n")
    }

    private fun startListening() {
        val sdk = OtpSdk.getInstance()
        if (sdk == null) {
            log.append("❌ OtpSdk not initialized\n\n")
            return
        }

        val channels = buildSet {
            if (checkSms.isChecked) add(OtpChannel.SMS)
            if (checkWhatsapp.isChecked) add(OtpChannel.WHATSAPP)
        }

        if (channels.isEmpty()) {
            log.append("❌ Select at least one channel\n\n")
            return
        }

        log.append("🔐 Starting OTP listener\n")
        log.append("channels=$channels\n")
        log.append("WhatsApp installed=${sdk.isWhatsAppInstalled()}\n\n")

        sdk.startListening(
            channels = channels,
            callback = object : OtpCallback {
                override fun onResult(result: OtpResult) {
                    activity?.runOnUiThread {
                        when (result) {
                            is OtpResult.Success -> {
                                val senderInfo = result.senderAddress?.let { " senderAddress=$it" }.orEmpty()
                                val senderPkg = result.senderPackage?.let { " senderPackage=$it" }.orEmpty()
                                log.append("✅ OTP (${result.source}): ${result.otp}$senderInfo$senderPkg\n\n")
                            }

                            is OtpResult.Error -> {
                                val details = buildString {
                                    result.errorKey?.let { append("key=$it") }
                                    result.errorMessage?.let {
                                        if (isNotEmpty()) append(" | ")
                                        append("message=$it")
                                    }
                                }
                                val senderPkg = result.senderPackage?.let { " senderPackage=$it" }.orEmpty()
                                log.append("❌ OTP Error (${result.source}): ${result.reason}$senderPkg\n")
                                if (details.isNotEmpty()) log.append("$details\n")
                                log.append("\n")
                            }
                        }
                    }
                }
            }
        )
    }

    private fun stopListening() {
        val sdk = OtpSdk.getInstance()
        if (sdk == null) {
            log.append("❌ OtpSdk not initialized\n\n")
            return
        }
        sdk.stop()
        log.append("🛑 OTP listener stopped\n\n")
    }
}

