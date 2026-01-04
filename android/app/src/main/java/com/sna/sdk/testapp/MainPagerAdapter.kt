package com.sna.sdk.testapp

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

internal class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SnaFragment()
            1 -> OtpFragment()
            2 -> DeviceInfoFragment()
            else -> UtilsFragment()
        }
    }
}

