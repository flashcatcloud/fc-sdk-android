/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.benchmark.sample.ui.rumauto.screens.locationdetails.di

import cloud.flashcat.benchmark.sample.di.common.DispatchersModule
import cloud.flashcat.benchmark.sample.network.rickandmorty.RickAndMortyNetworkService
import cloud.flashcat.benchmark.sample.network.rickandmorty.models.Location
import cloud.flashcat.benchmark.sample.ui.rumauto.RumAutoScenarioNavigator
import cloud.flashcat.benchmark.sample.ui.rumauto.screens.locationdetails.RumAutoLocationDetailsFragment
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.CoroutineScope
import javax.inject.Scope

internal interface RumAutoLocationDetailsComponentDependencies {
    val rickAndMortyNetworkService: RickAndMortyNetworkService
    val rumAutoScenarioNavigator: RumAutoScenarioNavigator
}

@Scope
internal annotation class RumAutoLocationDetailsScope

@RumAutoLocationDetailsScope
@Component(
    dependencies = [
        RumAutoLocationDetailsComponentDependencies::class
    ],
    modules = [
        DispatchersModule::class
    ]
)
internal interface RumAutoLocationDetailsComponent {
    @Component.Factory
    interface Factory {
        fun create(
            deps: RumAutoLocationDetailsComponentDependencies,
            @BindsInstance viewModelScope: CoroutineScope,
            @BindsInstance location: Location
        ): RumAutoLocationDetailsComponent
    }

    fun inject(fragment: RumAutoLocationDetailsFragment)
}
