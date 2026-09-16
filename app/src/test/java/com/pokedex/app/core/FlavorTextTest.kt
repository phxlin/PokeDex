package com.pokedex.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FlavorTextTest {

    @Test
    fun `collapses newlines and form feeds into single spaces`() {
        val raw = "It was created by\na scientist afteryears of research."
        assertThat(cleanFlavorText(raw))
            .isEqualTo("It was created by a scientist after years of research.")
    }

    @Test
    fun `trims and squeezes repeated whitespace`() {
        assertThat(cleanFlavorText("  GENGAR\n\n  hides   in shadows.  "))
            .isEqualTo("GENGAR hides in shadows.")
    }

    @Test
    fun `handles non-breaking spaces`() {
        assertThat(cleanFlavorText("A quick fox")).isEqualTo("A quick fox")
    }
}
