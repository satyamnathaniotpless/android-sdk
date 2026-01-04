package com.sna.sdk.testapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.otplesssdk.otp.OtpSdk
import com.otplesssdk.sna.SNASdk
import com.otplesssdk.utils.deviceinfo.DeviceInfoCollector

class MainActivity : AppCompatActivity() {
    companion object {
        const val PHONE_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Enable SDK logging + init SDKs once for the whole test app.
        SNASdk.setLoggingEnabled(true)
        OtpSdk.setLoggingEnabled(true)
        SNASdk.initialize(applicationContext)
        OtpSdk.initialize(applicationContext)

        // Warm up device info collection (includes best-effort referrer collection).
        DeviceInfoCollector.warmUp(applicationContext)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val pager = findViewById<ViewPager2>(R.id.pager)
        val tabs = findViewById<TabLayout>(R.id.tabs)

        pager.adapter = MainPagerAdapter(this)

        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_sna)
                1 -> getString(R.string.tab_otp)
                2 -> getString(R.string.tab_device_info)
                else -> getString(R.string.tab_utils)
            }
        }.attach()
    }

    fun requestPhonePermissionsIfNeeded() {
        val permissionsToRequest = mutableListOf(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) permissionsToRequest.add(Manifest.permission.READ_BASIC_PHONE_STATE)

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return

        ActivityCompat.requestPermissions(
            this,
            missing.toTypedArray(),
            PHONE_PERMISSION_REQUEST_CODE
        )
    }
}
