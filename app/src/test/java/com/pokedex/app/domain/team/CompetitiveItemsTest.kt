package com.pokedex.app.domain.team

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CompetitiveItemsTest {

    // ---- catalogue invariants -------------------------------------------------

    @Test
    fun `held items have no duplicate slugs`() {
        val slugs = HELD_ITEMS.map { it.slug }
        assertThat(slugs).containsNoDuplicates()
        assertThat(slugs).isNotEmpty()
    }

    @Test
    fun `legal items are listed before illegal ones`() {
        val flags = HELD_ITEMS.map { it.championsLegal }
        assertThat(flags).isEqualTo(flags.sortedByDescending { it })
        assertThat(HELD_ITEMS.first().championsLegal).isTrue()
        assertThat(HELD_ITEMS.last().championsLegal).isFalse()
    }

    // ---- PokéAPI slug / sprite / blurb ------------------------------------------

    @Test
    fun `leek resolves to PokeAPI's legacy stick slug`() {
        assertThat(heldItemApiSlug("leek")).isEqualTo("stick")
        assertThat(heldItemSpriteUrl("leek")).endsWith("/items/stick.png")
    }

    @Test
    fun `api slug is the identity for normal items and unknown slugs`() {
        assertThat(heldItemApiSlug("leftovers")).isEqualTo("leftovers")
        assertThat(heldItemApiSlug("made-up")).isEqualTo("made-up")
        assertThat(heldItemSpriteUrl("leftovers")).endsWith("/items/leftovers.png")
    }

    @Test
    fun `spriteless items report no sprite url`() {
        // Gen VIII/IX items PokéAPI's sprite repo doesn't carry.
        assertThat(heldItemSpriteUrl("clear-amulet")).isNull()
        assertThat(heldItemSpriteUrl("heavy-duty-boots")).isNull()
        assertThat(heldItemSpriteUrl(null)).isNull()
    }

    @Test
    fun `bundled blurbs fill in where PokeAPI has no effect text`() {
        assertThat(heldItemBlurb("clear-amulet")).contains("stat")
        assertThat(heldItemBlurb("loaded-dice")).isNotEmpty()
        // Items PokéAPI already documents carry no bundled blurb.
        assertThat(heldItemBlurb("leftovers")).isNull()
        assertThat(heldItemBlurb(null)).isNull()
    }

    @Test
    fun `every mega stone has a blurb`() {
        HELD_ITEMS.filter { it.category == ItemCategory.MEGA_STONE }.forEach {
            assertThat(it.blurb).isNotNull()
        }
    }

    @Test
    fun `display name comes from the catalogue and title-cases unknowns`() {
        assertThat(heldItemDisplayName("leek")).isEqualTo("Leek")
        assertThat(heldItemDisplayName("choice-band")).isEqualTo("Choice Band")
        assertThat(heldItemDisplayName("some-mystery-item")).isEqualTo("Some Mystery Item")
        assertThat(heldItemDisplayName(null)).isNull()
    }

    // ---- Mega Stone helpers ------------------------------------------------------

    @Test
    fun `formBadge reads the mega suffix from the equipped stone`() {
        assertThat(formBadge(formSlug = null, itemSlug = "gyaradosite")).isEqualTo("MEGA")
        assertThat(formBadge(formSlug = null, itemSlug = "charizardite-x")).isEqualTo("MEGA X")
        assertThat(formBadge(formSlug = null, itemSlug = "charizardite-y")).isEqualTo("MEGA Y")
    }

    @Test
    fun `formBadge reads a regional form slug`() {
        assertThat(formBadge(formSlug = "raichu-alola", itemSlug = null)).isEqualTo("ALOLA")
        assertThat(formBadge(formSlug = "farfetchd-galar", itemSlug = null)).isEqualTo("GALAR")
        assertThat(formBadge(formSlug = null, itemSlug = null)).isNull()
    }

    @Test
    fun `an equipped stone wins over a stale form slug`() {
        assertThat(formBadge(formSlug = "raichu-alola", itemSlug = "raichunite-y")).isEqualTo("MEGA Y")
    }

    @Test
    fun `megaStoneMatches pairs a stone with its species only`() {
        assertThat(megaStoneMatches("gyaradosite", "gyarados")).isTrue()
        assertThat(megaStoneMatches("gyaradosite", "gengar")).isFalse()
        assertThat(megaStoneMatches("leftovers", "gyarados")).isFalse()
        assertThat(megaStoneMatches(null, "gyarados")).isFalse()
    }

    @Test
    fun `megaPokemonSlugFor builds the form slug`() {
        assertThat(megaPokemonSlugFor("gyaradosite")).isEqualTo("gyarados-mega")
        assertThat(megaPokemonSlugFor("charizardite-x")).isEqualTo("charizard-mega-x")
        assertThat(megaPokemonSlugFor("leftovers")).isNull()
        assertThat(megaPokemonSlugFor(null)).isNull()
    }

    @Test
    fun `megaSpriteFor uses the hard-coded id and honours shiny`() {
        assertThat(megaSpriteFor("venusaurite", shiny = false)).endsWith("/pokemon/10033.png")
        assertThat(megaSpriteFor("venusaurite", shiny = true)).contains("/shiny/")
        // Champions-only stones have no mainline id -> no fast-path sprite.
        assertThat(megaSpriteFor("meganiumite", shiny = false)).isNull()
        assertThat(megaSpriteFor(null, shiny = false)).isNull()
    }
}
