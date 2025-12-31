package com.otplesssdk.otp.utils

import com.otplesssdk.otp.models.OtpConfig
import com.otplesssdk.utils.logger.SdkLogger
import java.util.regex.PatternSyntaxException

internal object OtpParser {
    private const val TAG = "OtpParser"

    fun extractOtp(message: String, config: OtpConfig): String? {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            return null
        }

        val lengthRange = config.lengthRange()
        val regexes = buildRegexes(config, lengthRange)

        for (regex in regexes) {
            val match = regex.find(trimmed) ?: continue
            val candidate = if (match.groupValues.size > 1) {
                match.groupValues[1]
            } else {
                match.value
            }
            if (isValidOtp(candidate, config)) {
                return candidate
            }
            val digitsOnly = candidate.filter { it.isDigit() }
            if (digitsOnly.isNotEmpty() && isValidOtp(digitsOnly, config)) {
                return digitsOnly
            }
        }

        return null
    }

    fun isValidOtp(value: String, config: OtpConfig): Boolean {
        val otp = value.trim()
        if (otp.isEmpty() || otp.any { !it.isDigit() }) {
            return false
        }
        val lengthRange = config.lengthRange()
        return otp.length in lengthRange
    }

    private fun buildRegexes(config: OtpConfig, lengthRange: IntRange): List<Regex> {
        val patterns = mutableListOf<Regex>()

        for (pattern in config.otpRegexes) {
            try {
                patterns.add(Regex(pattern))
            } catch (exception: PatternSyntaxException) {
                SdkLogger.w(TAG, "Invalid OTP regex pattern skipped: $pattern", exception)
            }
        }

        val lengthPattern = if (lengthRange.first == lengthRange.last) {
            "{${lengthRange.first}}"
        } else {
            "{${lengthRange.first},${lengthRange.last}}"
        }

        val keywords = config.otpKeywords
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        for (keyword in keywords) {
                val escaped = Regex.escape(keyword)
                val pattern = "(?i)\\b$escaped\\b(?:\\s+is)?[:\\s-]*([0-9]$lengthPattern)\\b"
                patterns.add(Regex(pattern))
            }

        patterns.add(Regex("\\b\\d$lengthPattern\\b"))

        return patterns
    }
}
