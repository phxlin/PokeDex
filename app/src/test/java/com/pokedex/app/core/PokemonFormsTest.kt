package com.pokedex.app.core

import com.google.common.truth.Truth.assertThat
import com.pokedex.app.domain.model.FormKind
import org.junit.Test

class PokemonFormsTest {

    @Test
    fun `classifies mega, mega x-y, primal and gigantamax`() {
        assertThat(PokemonForms.classify("venusaur-mega")).isEqualTo(FormKind.MEGA)
        assertThat(PokemonForms.classify("charizard-mega-x")).isEqualTo(FormKind.MEGA_X)
        assertThat(PokemonForms.classify("charizard-mega-y")).isEqualTo(FormKind.MEGA_Y)
        assertThat(PokemonForms.classify("kyogre-primal")).isEqualTo(FormKind.PRIMAL)
        assertThat(PokemonForms.classify("charizard-gmax")).isEqualTo(FormKind.GIGANTAMAX)
    }

    @Test
    fun `classifies regional forms`() {
        assertThat(PokemonForms.classify("rattata-alola")).isEqualTo(FormKind.ALOLAN)
        assertThat(PokemonForms.classify("meowth-galar")).isEqualTo(FormKind.GALARIAN)
        assertThat(PokemonForms.classify("growlithe-hisui")).isEqualTo(FormKind.HISUIAN)
        assertThat(PokemonForms.classify("tauros-paldea-combat")).isEqualTo(FormKind.PALDEAN)
    }

    @Test
    fun `unknown suffixes are OTHER and not notable`() {
        assertThat(PokemonForms.classify("pikachu-world-cap")).isEqualTo(FormKind.OTHER)
        assertThat(PokemonForms.isNotableForm("pikachu-world-cap")).isFalse()
        assertThat(PokemonForms.isNotableForm("charizard-mega-x")).isTrue()
    }

    @Test
    fun `builds friendly display names`() {
        assertThat(PokemonForms.displayName("Charizard", "charizard-mega-x", FormKind.MEGA_X))
            .isEqualTo("Mega Charizard X")
        assertThat(PokemonForms.displayName("Rattata", "rattata-alola", FormKind.ALOLAN))
            .isEqualTo("Alolan Rattata")
        assertThat(PokemonForms.displayName("Kyogre", "kyogre-primal", FormKind.PRIMAL))
            .isEqualTo("Primal Kyogre")
    }

    @Test
    fun `gender-locked varieties keep a plain name`() {
        assertThat(PokemonForms.displayName("Indeedee", "indeedee-male", FormKind.OTHER))
            .isEqualTo("Indeedee")
        assertThat(PokemonForms.displayName("Indeedee", "indeedee-female", FormKind.OTHER))
            .isEqualTo("Indeedee")
        assertThat(PokemonForms.displayName("Meowstic", "meowstic-female", FormKind.OTHER))
            .isEqualTo("Meowstic")
        // A genuine cosmetic form still gets its parenthetical.
        assertThat(PokemonForms.displayName("Rotom", "rotom-heat", FormKind.OTHER))
            .isEqualTo("Rotom (Heat)")
    }

    @Test
    fun `builds badges`() {
        assertThat(PokemonForms.badge(FormKind.GIGANTAMAX, "charizard-gmax")).isEqualTo("G-MAX")
        assertThat(PokemonForms.badge(FormKind.MEGA_Y, "charizard-mega-y")).isEqualTo("MEGA Y")
    }
}
