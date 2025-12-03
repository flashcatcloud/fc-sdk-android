/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.benchmark.sample.ui.rumauto.screens.common.details

import com.bumptech.glide.Glide
import cloud.flashcat.benchmark.sample.network.rickandmorty.models.Character
import cloud.flashcat.benchmark.sample.utils.recycler.BaseRecyclerViewItem
import cloud.flashcat.sample.benchmark.databinding.ItemCharacterBinding
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding

internal data class CharacterItem(
    val character: Character,
    override val key: String
) : BaseRecyclerViewItem

internal fun characterItemDelegate(onClick: (Character) -> Unit) =
    adapterDelegateViewBinding<CharacterItem, BaseRecyclerViewItem, ItemCharacterBinding>(
        { layoutInflater, root -> ItemCharacterBinding.inflate(layoutInflater, root, false) }
    ) {
        bind {
            Glide.with(binding.root.context)
                .load(item.character.image)
                .into(binding.characterImage)

            binding.characterName.text = item.character.name
            binding.root.setOnClickListener {
                onClick(item.character)
            }
        }
    }
