package com.pokedex.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PokemonNamesTest {

    @Test
    fun `plain names are lowercased`() {
        assertThat(PokemonNames.normalize("Pikachu")).isEqualTo("pikachu")
        assertThat(PokemonNames.normalize("  BULBASAUR ")).isEqualTo("bulbasaur")
    }

    @Test
    fun `periods and spaces become hyphens`() {
        assertThat(PokemonNames.normalize("Mr. Mime")).isEqualTo("mr-mime")
        assertThat(PokemonNames.normalize("Mime Jr.")).isEqualTo("mime-jr")
    }

    @Test
    fun `gender symbols map to suffixes`() {
        assertThat(PokemonNames.normalize("Nidoran♀")).isEqualTo("nidoran-f")
        assertThat(PokemonNames.normalize("Nidoran♂")).isEqualTo("nidoran-m")
    }

    @Test
    fun `apostrophes are dropped`() {
        assertThat(PokemonNames.normalize("Farfetch'd")).isEqualTo("farfetchd")
        assertThat(PokemonNames.normalize("Sirfetch’d")).isEqualTo("sirfetchd")
    }

    @Test
    fun `diacritics are stripped`() {
        assertThat(PokemonNames.normalize("Flabébé")).isEqualTo("flabebe")
    }

    @Test
    fun `colons and extra punctuation are handled`() {
        assertThat(PokemonNames.normalize("Type: Null")).isEqualTo("type-null")
        assertThat(PokemonNames.normalize("Ho-Oh")).isEqualTo("ho-oh")
        assertThat(PokemonNames.normalize("Porygon-Z")).isEqualTo("porygon-z")
        assertThat(PokemonNames.normalize("Porygon Z")).isEqualTo("porygon-z")
    }

    @Test
    fun `paradox forms keep their hyphenated slug`() {
        assertThat(PokemonNames.normalize("Great Tusk")).isEqualTo("great-tusk")
        assertThat(PokemonNames.normalize("Iron Valiant")).isEqualTo("iron-valiant")
    }

    @Test
    fun `displayName reverses a slug`() {
        assertThat(PokemonNames.displayName("mr-mime")).isEqualTo("Mr Mime")
        assertThat(PokemonNames.displayName("pikachu")).isEqualTo("Pikachu")
    }
}
