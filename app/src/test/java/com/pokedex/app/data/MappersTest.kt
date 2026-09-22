package com.pokedex.app.data

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
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

    @Test
    fun `Indeedee female gets its own Champions learnset, not the male's Expanding Force or Gravity`() {
        // PokeAPI has no train data for Indeedee, so the blended union would hand every move of
        // either gender to both.
        val dto = PokemonDto(
            id = 10186,
            name = "indeedee-female",
            moves = listOf(nonTrainMove("expanding-force"), nonTrainMove("gravity"), nonTrainMove("follow-me")),
        )

        val movePool = dto.toDomain().movePool

        assertThat(movePool).doesNotContain("expanding-force")
        assertThat(movePool).doesNotContain("gravity")
        assertThat(movePool).containsAtLeast("follow-me", "alluring-voice", "baton-pass", "guard-split")
    }

    @Test
    fun `Indeedee male keeps Expanding Force and Gravity but not the female's Follow Me`() {
        val dto = PokemonDto(
            id = 876,
            name = "indeedee-male",
            moves = listOf(nonTrainMove("follow-me")),
        )

        val movePool = dto.toDomain().movePool

        assertThat(movePool).containsAtLeast("expanding-force", "gravity", "psychic-terrain")
        assertThat(movePool).doesNotContain("follow-me")
    }

    @Test
    fun `Champions learnset table never overrides PokeAPI train data`() {
        val dto = PokemonDto(
            id = 10186,
            name = "indeedee-female",
            moves = listOf(trainMove("psychic"), trainMove("gravity")),
        )

        assertThat(dto.toDomain().movePool).containsExactly("gravity", "psychic")
    }

    @Test
    fun `every Champions learnset entry is a valid lowercase PokeAPI move slug, sorted without duplicates`() {
        CHAMPIONS_MOVE_POOLS.forEach { (species, moves) ->
            assertWithMessage(species).that(moves).containsNoDuplicates()
            assertWithMessage(species).that(moves).isInStrictOrder()
            moves.forEach { assertThat(it).matches("[a-z]+(-[a-z]+)*") }
        }
    }
}
