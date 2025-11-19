/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://flashcat.cloud/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.flashcat.rum.sdk.integration.rum

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.flashcat.rum.sdk.integration.R

internal class KioskTrackedPlaygroundActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.kiosk_tracked_layout)

        val backButton: Button = findViewById(R.id.kiosk_back_button)
        backButton.setOnClickListener {
            finish()
        }
    }
}
