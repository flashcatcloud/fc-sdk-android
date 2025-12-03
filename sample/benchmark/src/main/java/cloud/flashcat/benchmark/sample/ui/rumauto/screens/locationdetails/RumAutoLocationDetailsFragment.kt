/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.benchmark.sample.ui.rumauto.screens.locationdetails

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.GridLayoutManager
import cloud.flashcat.benchmark.sample.activities.scenarios.benchmarkActivityComponent
import cloud.flashcat.benchmark.sample.navigation.args
import cloud.flashcat.benchmark.sample.network.rickandmorty.models.Location
import cloud.flashcat.benchmark.sample.ui.rumauto.screens.common.details.CharacterItem
import cloud.flashcat.benchmark.sample.ui.rumauto.screens.locationdetails.di.DaggerRumAutoLocationDetailsComponent
import cloud.flashcat.benchmark.sample.ui.rumauto.screens.locationdetails.di.RumAutoLocationDetailsComponent
import cloud.flashcat.benchmark.sample.utils.componentHolderViewModel
import cloud.flashcat.benchmark.sample.utils.recycler.applyNewItems
import cloud.flashcat.sample.benchmark.databinding.FragmentRumAutoLocationDetailsBinding
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

internal class RumAutoLocationDetailsFragment : Fragment() {

    private val location: Location by args()

    private val component: RumAutoLocationDetailsComponent by componentHolderViewModel {
        DaggerRumAutoLocationDetailsComponent.factory().create(
            deps = requireActivity().benchmarkActivityComponent,
            location = location,
            viewModelScope = viewModelScope
        )
    }

    @Inject
    lateinit var viewModel: RumAutoLocationDetailsViewModel

    @Inject
    lateinit var adapter: RumAutoLocationDetailsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        component.inject(this)

        val binding = FragmentRumAutoLocationDetailsBinding.inflate(inflater, container, false)

        @Suppress("MagicNumber")
        binding.locationDetailsRecycler.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.locationDetailsRecycler.adapter = adapter

        viewModel.state
            .onEach { state ->
                binding.locationDetailsTitle.text = state.location.name
                binding.locationDetailsType.text = state.location.type
                binding.locationDetailsDimension.text = state.location.dimension
                binding.locationDetailsCreated.text = state.location.created

                val characters = state
                    .residentsLoadingTask
                    .optionalResult
                    ?.optionalResult
                    ?.map {
                        CharacterItem(
                            character = it,
                            key = it.id.toString()
                        )
                    } ?: emptyList()

                adapter.applyNewItems(characters)
            }
            .launchIn(lifecycleScope)

        return binding.root
    }
}
