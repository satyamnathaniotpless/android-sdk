package com.otplesssdk.utils.deviceinfo

import android.os.Build

internal object OsInfoCollector {
    fun collect(): OsInfo {
        return OsInfo(
            version = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            codename = Build.VERSION.CODENAME.takeIf { it.isNotEmpty() && it != "REL" }
        )
    }
}

internal data class OsInfo(
    val version: String,
    val apiLevel: Int,
    val codename: String? = null
)

