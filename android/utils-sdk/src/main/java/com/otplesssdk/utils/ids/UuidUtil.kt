package com.otplesssdk.utils.ids

import java.util.UUID

internal object UuidUtil {
    fun random(): String = UUID.randomUUID().toString()
}

