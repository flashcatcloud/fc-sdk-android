/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.benchmark.sample.activities.scenarios

import android.app.Activity
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.findNavController
import cloud.flashcat.benchmark.sample.config.BenchmarkConfig
import cloud.flashcat.benchmark.sample.di.activity.BenchmarkActivityComponent
import cloud.flashcat.benchmark.sample.navigation.NavigationGraphInitializer
import cloud.flashcat.benchmark.sample.ui.rumauto.RumAutoBottomNavBar
import cloud.flashcat.benchmark.sample.ui.rumauto.RumAutoScenarioNavigator
import cloud.flashcat.sample.benchmark.R
import cloud.flashcat.sample.benchmark.databinding.FragmentRumAutoHostBinding
import javax.inject.Inject

internal class RumAutoScenarioActivity : BaseScenarioActivity() {
    @Inject
    internal lateinit var rumAutoScenarioNavigator: RumAutoScenarioNavigator

    @Inject
    internal lateinit var navigationGraphInitializer: NavigationGraphInitializer

    @Inject
    internal lateinit var benchmarkConfig: BenchmarkConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        benchmarkActivityComponent.inject(this)

        val binding = FragmentRumAutoHostBinding.inflate(layoutInflater)

        supportActionBar?.hide()
        setContentView(binding.root)

        binding.rumAutoBottomNavbar.setContent {
            val currentTab by rumAutoScenarioNavigator.currentTab.collectAsStateWithLifecycle(null)

            currentTab?.let { tab ->
                RumAutoBottomNavBar(tab) {
                    rumAutoScenarioNavigator.openTab(it)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val navController = findNavController(R.id.nav_host_fragment)
        navigationGraphInitializer.initialize(navController, benchmarkConfig.scenario)

        rumAutoScenarioNavigator.setNavController(findNavController(R.id.nav_host_fragment))
    }
}

internal val Activity.benchmarkActivityComponent: BenchmarkActivityComponent
    get() = (this as BaseScenarioActivity).viewModel.component
