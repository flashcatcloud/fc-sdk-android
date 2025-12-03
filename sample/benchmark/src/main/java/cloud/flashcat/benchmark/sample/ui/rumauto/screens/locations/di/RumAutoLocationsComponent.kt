/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.benchmark.sample.ui.rumauto.screens.locations.di

import cloud.flashcat.benchmark.sample.di.common.DispatchersModule
import cloud.flashcat.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import cloud.flashcat.benchmark.sample.ui.rumauto.RumAutoScenarioNavigator
import cloud.flashcat.benchmark.sample.ui.rumauto.screens.locations.RumAutoLocationsFragment
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.CoroutineScope
import javax.inject.Scope

internal interface RumAutoLocationsComponentDependencies {
    val rickAndMortyNetworkService: RickAndMortyNetworkService
    val rumAutoScenarioNavigator: RumAutoScenarioNavigator
}

@Scope
internal annotation class RumAutoLocationsScope

@RumAutoLocationsScope
@Component(
    dependencies = [
        RumAutoLocationsComponentDependencies::class
    ],
    modules = [
        DispatchersModule::class
    ]
)
internal interface RumAutoLocationsComponent {
    @Component.Factory
    interface Factory {
        fun create(
            deps: RumAutoLocationsComponentDependencies,
            @BindsInstance viewModelScope: CoroutineScope
        ): RumAutoLocationsComponent
    }

    fun inject(fragment: RumAutoLocationsFragment)
}
