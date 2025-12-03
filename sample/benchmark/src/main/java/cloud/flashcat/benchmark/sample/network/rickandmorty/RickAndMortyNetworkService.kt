/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 * Modified 2025 by FlashCat, Inc.
 */

package cloud.flashcat.benchmark.sample.network.rickandmorty

import cloud.flashcat.benchmark.sample.network.KtorHttpResponse
import cloud.flashcat.benchmark.sample.network.rickandmorty.models.Character
import cloud.flashcat.benchmark.sample.network.rickandmorty.models.CharacterResponse
import cloud.flashcat.benchmark.sample.network.rickandmorty.models.Episode
import cloud.flashcat.benchmark.sample.network.rickandmorty.models.EpisodeResponse
import cloud.flashcat.benchmark.sample.network.rickandmorty.models.Location
import cloud.flashcat.benchmark.sample.network.rickandmorty.models.LocationResponse

internal interface RickAndMortyNetworkService {
    suspend fun getCharacter(id: Int): KtorHttpResponse<Character>
    suspend fun getCharacters(nextPageUrl: String?): KtorHttpResponse<CharacterResponse>
    suspend fun getCharacters(ids: List<String>): KtorHttpResponse<List<Character>>

    suspend fun getLocation(id: Int): KtorHttpResponse<Location>
    suspend fun getLocations(nextPageUrl: String?): KtorHttpResponse<LocationResponse>

    suspend fun getEpisode(id: Int): KtorHttpResponse<Episode>
    suspend fun getEpisodes(ids: List<String>): KtorHttpResponse<List<Episode>>
    suspend fun getEpisodes(nextPageUrl: String?): KtorHttpResponse<EpisodeResponse>
}
