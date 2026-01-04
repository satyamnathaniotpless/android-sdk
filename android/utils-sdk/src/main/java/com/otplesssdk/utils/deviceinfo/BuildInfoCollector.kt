package com.otplesssdk.utils.deviceinfo

import android.os.Build

internal object BuildInfoCollector {
    fun collect(): BuildInfo {
        return BuildInfo(
            buildId = Build.ID,
            buildType = Build.TYPE,
            buildTags = Build.TAGS,
            buildHost = Build.HOST,
            buildUser = Build.USER
        )
    }
}

internal data class BuildInfo(
    val buildId: String? = null,
    val buildType: String? = null,
    val buildTags: String? = null,
    val buildHost: String? = null,
    val buildUser: String? = null
)
