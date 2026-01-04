package com.sna.sdk.testapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal object Permissions {
    fun hasReadPhoneState(context: Context): Boolean {
        val hasReadPhone =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val hasReadBasic =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_BASIC_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
        return hasReadPhone || hasReadBasic
    }

    fun describe(context: Context): String {
        val readPhone = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val readBasic =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_BASIC_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
            } else false
        return buildString {
            append("READ_PHONE_STATE="); append(if (readPhone) "GRANTED" else "DENIED"); append('\n')
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                append("READ_BASIC_PHONE_STATE="); append(if (readBasic) "GRANTED" else "DENIED"); append('\n')
            }
        }
    }
}

