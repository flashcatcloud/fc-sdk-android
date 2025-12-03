/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.benchmark.sample.activities.scenarios

import android.os.Bundle
import androidx.navigation.findNavController
import cloud.flashcat.benchmark.sample.config.BenchmarkConfig
import cloud.flashcat.benchmark.sample.navigation.NavigationGraphInitializer
import cloud.flashcat.sample.benchmark.R
import javax.inject.Inject

internal class DefaultScenarioActivity : BaseScenarioActivity() {

    @Inject
    internal lateinit var config: BenchmarkConfig

    @Inject
    internal lateinit var navigationGraphInitializer: NavigationGraphInitializer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        benchmarkActivityComponent.inject(this)

        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        val navController = findNavController(R.id.nav_host_fragment)

        navigationGraphInitializer.initialize(navController, config.scenario)
    }
}
