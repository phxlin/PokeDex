package com.pokedex.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PokemonSummaryTest {

    @Test
    fun `plain species name is title-cased`() {
        assertThat(PokemonSummary(1, "bulbasaur").displayName).isEqualTo("Bulbasaur")
        assertThat(PokemonSummary(866, "mr-mime").displayName).isEqualTo("Mr Mime")
    }

    @Test
    fun `trailing gender segment is dropped`() {
        assertThat(PokemonSummary(876, "indeedee-male").displayName).isEqualTo("Indeedee")
        assertThat(PokemonSummary(10025, "meowstic-female").displayName).isEqualTo("Meowstic")
        assertThat(PokemonSummary(902, "basculegion-male").displayName).isEqualTo("Basculegion")
    }

    @Test
    fun `non-gender hyphenated forms are left intact`() {
        assertThat(PokemonSummary(10034, "charizard-mega-x").displayName).isEqualTo("Charizard Mega X")
        assertThat(PokemonSummary(10250, "tauros-paldea-combat").displayName).isEqualTo("Tauros Paldea Combat")
    }
}
