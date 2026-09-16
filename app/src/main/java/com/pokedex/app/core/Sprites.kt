package com.pokedex.app.core

/**
 * Every PokéAPI sprite URL the app builds by hand goes through here, so the base
 * URL and the path shapes live in exactly one place. Responses fetched from
 * PokeAPI already carry their own sprite URLs — those are used as-is; this is only
 * for the cases where the app constructs a URL from an id or slug to avoid a fetch.
 */
object Sprites {

    const val ROOT = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites"

    private fun shinySegment(shiny: Boolean) = if (shiny) "shiny/" else ""

    /** Gen-5-style front sprite for a National Dex id. */
    fun pokemon(id: Int, shiny: Boolean = false): String =
        "$ROOT/pokemon/${shinySegment(shiny)}$id.png"

    /** Official artwork for a National Dex id. */
    fun artwork(id: Int, shiny: Boolean = false): String =
        "$ROOT/pokemon/other/official-artwork/${shinySegment(shiny)}$id.png"

    /** Item sprite for a PokéAPI item slug. */
    fun item(slug: String): String = "$ROOT/items/$slug.png"
}
