/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

@file:Suppress("DEPRECATION")

package cloud.flashcat.android.sdk.integration.rum

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import cloud.flashcat.android.Flashcat
import cloud.flashcat.android.rum.Rum
import cloud.flashcat.android.rum.tracking.FragmentViewTrackingStrategy
import cloud.flashcat.android.sdk.integration.R
import cloud.flashcat.android.sdk.integration.RuntimeConfig
import cloud.flashcat.android.sdk.utils.getTrackingConsent

internal class FragmentTrackingPlaygroundActivity : AppCompatActivity() {
    lateinit var viewPager: ViewPager
    lateinit var btnNext: Button
    lateinit var btnLast: Button

    @Suppress("CheckInternal")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = RuntimeConfig.configBuilder()
            .build()
        val trackingConsent = intent.getTrackingConsent()

        Flashcat.setVerbosity(Log.VERBOSE)
        val sdkCore = Flashcat.initialize(this, config, trackingConsent)
        checkNotNull(sdkCore)

        val rumConfig = RuntimeConfig.rumConfigBuilder()
            .trackUserInteractions()
            .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
            .useViewTrackingStrategy(FragmentViewTrackingStrategy(true))
            .build()
        Rum.enable(rumConfig, sdkCore)

        setContentView(R.layout.fragment_tracking_layout)
        viewPager = findViewById(R.id.pager)
        btnNext = findViewById(R.id.btn_next)
        btnLast = findViewById(R.id.btn_last)
        viewPager.apply {
            adapter = ViewPagerAdapter(supportFragmentManager)
        }
        btnNext.setOnClickListener {
            viewPager.setCurrentItem(viewPager.currentItem + 1, true)
        }
        btnLast.setOnClickListener {
            viewPager.setCurrentItem(viewPager.currentItem - 1, true)
        }

        // attach the fragment view tracking strategy
    }

    internal inner class ViewPagerAdapter(fragmentManager: FragmentManager) :
        FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getItem(position: Int): Fragment {
            return when (position) {
                0 -> FragmentA()
                1 -> FragmentB()
                else -> FragmentC()
            }.apply {
                val args = Bundle().apply {
                    putString("fragmentClassName", this::class.java.simpleName)
                    putInt("adapterPosition", position)
                }
                arguments = args
            }
        }

        override fun getCount(): Int {
            return 3
        }
    }
}
