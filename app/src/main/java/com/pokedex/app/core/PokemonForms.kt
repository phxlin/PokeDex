package com.pokedex.app.core

import com.pokedex.app.domain.model.FormKind

/**
 * Classifies a PokeAPI variety slug (e.g. `"charizard-mega-x"`, `"rattata-alola"`,
 * `"kyogre-primal"`) into a [FormKind] and produces its display name / badge.
 * Pure and dependency-free for easy testing.
 */
object PokemonForms {

    fun classify(slug: String): FormKind {
        val s = slug.lowercase()
        return when {
            s.endsWith("-mega-x") -> FormKind.MEGA_X
            s.endsWith("-mega-y") -> FormKind.MEGA_Y
            s.endsWith("-mega") -> FormKind.MEGA
            s.endsWith("-primal") -> FormKind.PRIMAL
            s.endsWith("-gmax") -> FormKind.GIGANTAMAX
            s.contains("-alola") -> FormKind.ALOLAN
            s.contains("-galar") -> FormKind.GALARIAN
            s.contains("-hisui") -> FormKind.HISUIAN
            s.contains("-paldea") -> FormKind.PALDEAN
            else -> FormKind.OTHER
        }
    }

    /** True when the slug is one of the form categories worth always surfacing. */
    fun isNotableForm(slug: String): Boolean = classify(slug) != FormKind.OTHER

    fun badge(kind: FormKind, slug: String): String = when (kind) {
        FormKind.MEGA -> "MEGA"
        FormKind.MEGA_X -> "MEGA X"
        FormKind.MEGA_Y -> "MEGA Y"
        FormKind.PRIMAL -> "PRIMAL"
        FormKind.GIGANTAMAX -> "G-MAX"
        FormKind.ALOLAN -> "ALOLAN"
        FormKind.GALARIAN -> "GALARIAN"
        FormKind.HISUIAN -> "HISUIAN"
        FormKind.PALDEAN -> "PALDEAN"
        FormKind.OTHER -> slug.substringAfter('-', "").replace('-', ' ').trim().uppercase()
            .ifBlank { "FORM" }
    }

    /** e.g. base "Charizard" + "charizard-mega-x" -> "Mega Charizard X". */
    fun displayName(baseDisplayName: String, slug: String, kind: FormKind): String = when (kind) {
        FormKind.MEGA -> "Mega $baseDisplayName"
        FormKind.MEGA_X -> "Mega $baseDisplayName X"
        FormKind.MEGA_Y -> "Mega $baseDisplayName Y"
        FormKind.PRIMAL -> "Primal $baseDisplayName"
        FormKind.GIGANTAMAX -> "Gigantamax $baseDisplayName"
        FormKind.ALOLAN -> "Alolan $baseDisplayName"
        FormKind.GALARIAN -> "Galarian $baseDisplayName"
        FormKind.HISUIAN -> "Hisuian $baseDisplayName"
        FormKind.PALDEAN -> "Paldean $baseDisplayName"
        FormKind.OTHER -> {
            val suffix = slug.substringAfter('-', "").replace('-', ' ').trim()
            when {
                suffix.isBlank() -> baseDisplayName
                // Gender-locked varieties (Indeedee, Meowstic, Basculegion, Oinkologne):
                // the ♂/♀ is surfaced as a symbol elsewhere, so keep the name plain.
                suffix.equals("male", ignoreCase = true) ||
                    suffix.equals("female", ignoreCase = true) -> baseDisplayName
                else -> "$baseDisplayName (${suffix.replaceFirstChar { it.uppercase() }})"
            }
        }
    }
}
