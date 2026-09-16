package com.pokedex.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpritesTest {

    @Test
    fun `pokemon sprite path`() {
        assertThat(Sprites.pokemon(445))
            .isEqualTo("${Sprites.ROOT}/pokemon/445.png")
        assertThat(Sprites.pokemon(445, shiny = true))
            .isEqualTo("${Sprites.ROOT}/pokemon/shiny/445.png")
    }

    @Test
    fun `artwork path`() {
        assertThat(Sprites.artwork(445))
            .isEqualTo("${Sprites.ROOT}/pokemon/other/official-artwork/445.png")
        assertThat(Sprites.artwork(445, shiny = true))
            .isEqualTo("${Sprites.ROOT}/pokemon/other/official-artwork/shiny/445.png")
    }

    @Test
    fun `item path`() {
        assertThat(Sprites.item("leftovers")).isEqualTo("${Sprites.ROOT}/items/leftovers.png")
    }
}
