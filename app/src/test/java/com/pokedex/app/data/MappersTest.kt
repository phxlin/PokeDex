package com.pokedex.app.data

import com.google.common.truth.Truth.assertThat
import com.pokedex.app.data.remote.dto.MoveSlotDto
import com.pokedex.app.data.remote.dto.MoveVersionGroupDetailDto
import com.pokedex.app.data.remote.dto.NamedApiResourceDto
import com.pokedex.app.data.remote.dto.PokemonDto
import org.junit.Test

class MappersTest {

    private fun trainMove(name: String) = MoveSlotDto(
        move = NamedApiResourceDto(name),
        versionGroupDetails = listOf(MoveVersionGroupDetailDto(NamedApiResourceDto("train"))),
    )

    private fun nonTrainMove(name: String) = MoveSlotDto(
        move = NamedApiResourceDto(name),
        versionGroupDetails = listOf(MoveVersionGroupDetailDto(NamedApiResourceDto("level-up"))),
    )

    @Test
    fun `species with no train data falls back to the blended movePool, patched for known gaps`() {
        val dto = PokemonDto(
            id = 597,
            name = "golisopod",
            moves = listOf(nonTrainMove("first-impression")),
        )

        val movePool = dto.toDomain().movePool

        assertThat(movePool).containsAtLeast("first-impression", "u-turn", "aqua-jet")
    }

    @Test
    fun `move pool patch does not leak onto other species`() {
        val dto = PokemonDto(
            id = 25,
            name = "pikachu",
            moves = listOf(nonTrainMove("thunderbolt")),
        )

        val movePool = dto.toDomain().movePool

        assertThat(movePool).containsExactly("thunderbolt")
    }

    @Test
    fun `species with train data uses only train moves, excluding Champions-disabled ones`() {
        // Mirrors Tsareena: PokeAPI's blended history includes Magical Leaf from older
        // games, but PokeAPI's own "train" data (the real Champions v1.0 learnset)
        // deliberately excludes it because it's disabled in Champions.
        val dto = PokemonDto(
            id = 763,
            name = "tsareena",
            moves = listOf(trainMove("trop-kick"), trainMove("u-turn"), nonTrainMove("magical-leaf")),
        )

        val movePool = dto.toDomain().movePool

        assertThat(movePool).containsExactly("trop-kick", "u-turn")
    }

    @Test
    fun `patches never apply once a species has train data, even for a patched species`() {
        // If Golisopod ever gains train data, MOVE_POOL_PATCHES must not reintroduce
        // u-turn/aqua-jet on top of it — train is authoritative, and a patch sourced
        // from Golisopod's general-game learnset can't know whether those moves are
        // actually enabled in Champions.
        val dto = PokemonDto(
            id = 597,
            name = "golisopod",
            moves = listOf(trainMove("first-impression")),
        )

        val movePool = dto.toDomain().movePool

        assertThat(movePool).containsExactly("first-impression")
    }
}
