package com.otplesssdk.otp.models

/**
 * Represents an app hash for SMS Retriever API.
 * Contains the package name and the corresponding hash string.
 */
data class AppHash(
    val packageName: String,
    val hash: String
)

