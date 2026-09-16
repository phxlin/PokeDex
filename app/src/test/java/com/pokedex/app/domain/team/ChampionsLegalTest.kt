package com.pokedex.app.domain.team

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChampionsLegalTest {

    // ---- Species ---------------------------------------------------------------

    @Test
    fun `a rostered species is legal`() {
        assertThat(ChampionsLegal.isLegalSpecies("garchomp")).isTrue()
        assertThat(ChampionsLegal.isLegalSpecies("whimsicott")).isTrue()
        assertThat(ChampionsLegal.isLegalSpecies("mr-mime")).isTrue()
    }

    @Test
    fun `species matching is case-insensitive`() {
        assertThat(ChampionsLegal.isLegalSpecies("GARCHOMP")).isTrue()
        assertThat(ChampionsLegal.isLegalSpecies("Garchomp")).isTrue()
    }

    @Test
    fun `a form slug of a legal species is legal`() {
        assertThat(ChampionsLegal.isLegalSpecies("garchomp-mega")).isTrue()
        assertThat(ChampionsLegal.isLegalSpecies("ninetales-alola")).isTrue()
        assertThat(ChampionsLegal.isLegalSpecies("raichu-alola")).isTrue()
    }

    @Test
    fun `an off-roster species is not legal`() {
        assertThat(ChampionsLegal.isLegalSpecies("mewtwo")).isFalse()
        assertThat(ChampionsLegal.isLegalSpecies("magikarp")).isFalse()
        assertThat(ChampionsLegal.isLegalSpecies("koraidon")).isFalse()
    }

    @Test
    fun `a bare prefix is not mistaken for a form slug`() {
        // "ar" is a prefix of "arbok" / "ariados" but not "<species>-…".
        assertThat(ChampionsLegal.isLegalSpecies("ar")).isFalse()
    }

    // ---- Items ---------------------------------------------------------------

    @Test
    fun `the legal-item set includes staples and mega stones`() {
        listOf(
            "leftovers", "focus-sash", "choice-scarf", "life-orb", "rocky-helmet",
            "leek", "sitrus-berry", "gyaradosite", "charizardite-x",
        ).forEach {
            assertThat(ChampionsLegal.ITEMS).contains(it)
        }
    }

    @Test
    fun `the legal-item set excludes items banned in Reg M-C`() {
        listOf(
            "choice-band", "choice-specs", "assault-vest", "eviolite", "clear-amulet",
            "covert-cloak", "booster-energy", "heavy-duty-boots", "loaded-dice",
        ).forEach {
            assertThat(ChampionsLegal.ITEMS).doesNotContain(it)
        }
    }

    @Test
    fun `isLegalItem allows an empty slot and a legal item, rejects the rest`() {
        assertThat(ChampionsLegal.isLegalItem(null)).isTrue()
        assertThat(ChampionsLegal.isLegalItem("leftovers")).isTrue()
        assertThat(ChampionsLegal.isLegalItem("choice-band")).isFalse()
        assertThat(ChampionsLegal.isLegalItem("not-a-real-item")).isFalse()
    }

    @Test
    fun `the legal-item list is roughly the documented size`() {
        // Reg M-C lists 166 items; keep a loose bound so a single add/remove
        // doesn't break the build, but a wholesale regression does.
        assertThat(ChampionsLegal.ITEMS.size).isGreaterThan(139)
        assertThat(ChampionsLegal.ITEMS.size).isLessThan(201)
    }
}
