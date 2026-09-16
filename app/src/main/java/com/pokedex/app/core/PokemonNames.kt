package com.pokedex.app.core

import java.util.Locale

/**
 * Converts a human-facing Pokémon name (from a user, or from the vision model)
 * into the slug PokeAPI expects, e.g. `"Mr. Mime"` -> `"mr-mime"`,
 * `"Nidoran♀"` -> `"nidoran-f"`, `"Farfetch'd"` -> `"farfetchd"`,
 * `"Flabébé"` -> `"flabebe"`, `"Type: Null"` -> `"type-null"`.
 *
 * Kept pure and dependency-free so it is trivial to unit-test.
 */
object PokemonNames {

    /** Explicit overrides where a mechanical transform would produce the wrong slug. */
    private val SPECIAL_CASES: Map<String, String> = mapOf(
        "nidoran male" to "nidoran-m",
        "nidoran female" to "nidoran-f",
        "nidoran m" to "nidoran-m",
        "nidoran f" to "nidoran-f",
        "farfetchd" to "farfetchd",
        "sirfetchd" to "sirfetchd",
        "mr mime" to "mr-mime",
        "mr mime galar" to "mr-mime-galar",
        "mime jr" to "mime-jr",
        "mr rime" to "mr-rime",
        "type null" to "type-null",
        "ho oh" to "ho-oh",
        "porygon z" to "porygon-z",
        "jangmo o" to "jangmo-o",
        "hakamo o" to "hakamo-o",
        "kommo o" to "kommo-o",
        "great tusk" to "great-tusk",
        "scream tail" to "scream-tail",
        "brute bonnet" to "brute-bonnet",
        "flutter mane" to "flutter-mane",
        "slither wing" to "slither-wing",
        "sandy shocks" to "sandy-shocks",
        "iron treads" to "iron-treads",
        "iron bundle" to "iron-bundle",
        "iron hands" to "iron-hands",
        "iron jugulis" to "iron-jugulis",
        "iron moth" to "iron-moth",
        "iron thorns" to "iron-thorns",
        "roaring moon" to "roaring-moon",
        "iron valiant" to "iron-valiant",
        "walking wake" to "walking-wake",
        "iron leaves" to "iron-leaves",
        "gouging fire" to "gouging-fire",
        "raging bolt" to "raging-bolt",
        "iron boulder" to "iron-boulder",
        "iron crown" to "iron-crown",
    )

    fun normalize(raw: String): String {
        var s = raw.trim().lowercase(Locale.ROOT)

        // Gendered symbols.
        s = s.replace("♀", " female").replace("♂", " male")

        // Strip diacritics (é -> e, etc.).
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

        // Drop apostrophes entirely (Farfetch'd -> farfetchd).
        s = s.replace("'", "").replace("’", "").replace("`", "")

        // Everything else that is not a letter or digit becomes a space.
        s = s.replace(Regex("[^a-z0-9]+"), " ").trim()

        SPECIAL_CASES[s]?.let { return it }

        return s.replace(Regex("\\s+"), "-")
    }

    /** Turns a slug back into a display name: `"mr-mime"` -> `"Mr. Mime"` (best effort). */
    fun displayName(slug: String): String =
        slug.split("-").joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
}
