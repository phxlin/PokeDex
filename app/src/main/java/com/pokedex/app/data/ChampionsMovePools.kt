package com.pokedex.app.data

/**
 * Champions learnsets for species where the two gendered varieties learn different moves and
 * PokéAPI has no `train` (Champions) data yet, so [movePoolFor] would otherwise fall back to
 * "every move from any game" — which for Indeedee lets the female learn the male's Expanding Force
 * and Gravity, and the male learn the female's Follow Me. Keyed by PokéAPI variety slug.
 *
 * Source: Pokémon Showdown's Champions learnsets (`data/mods/champions/learnsets.ts`, `indeedee`
 * and `indeedeef`). Like `MOVE_POOL_PATCHES`, only used when PokéAPI has no `train` data: once it
 * does, that data is authoritative and this table should be dropped for that species.
 */
internal val CHAMPIONS_MOVE_POOLS: Map<String, List<String>> = mapOf(
    "indeedee-male" to listOf(
        "after-you", "body-slam", "calm-mind", "dazzling-gleam", "drain-punch", "draining-kiss", "encore",
        "endure", "energy-ball", "expanding-force", "extrasensory", "facade", "fake-out", "future-sight",
        "gravity", "healing-wish", "helping-hand", "hyper-voice", "imprison", "last-resort", "magic-room",
        "mystical-fire", "play-rough", "power-split", "power-swap", "protect", "psych-up", "psychic",
        "psychic-noise", "psychic-terrain", "psyshock", "rest", "round", "shadow-ball", "skill-swap",
        "sleep-talk", "snore", "stored-power", "substitute", "terrain-pulse", "tri-attack", "trick",
        "trick-room", "wish", "wonder-room", "zen-headbutt",
    ),
    "indeedee-female" to listOf(
        "alluring-voice", "baton-pass", "body-slam", "calm-mind", "charm", "dazzling-gleam", "drain-punch",
        "draining-kiss", "endure", "energy-ball", "facade", "fake-out", "follow-me", "future-sight",
        "guard-split", "guard-swap", "heal-pulse", "healing-wish", "helping-hand", "hyper-voice", "imprison",
        "light-screen", "mystical-fire", "play-rough", "protect", "psych-up", "psychic", "psychic-terrain",
        "psyshock", "reflect", "rest", "round", "safeguard", "shadow-ball", "sing", "skill-swap",
        "sleep-talk", "snore", "stored-power", "substitute", "terrain-pulse", "trick", "trick-room", "wish",
        "zen-headbutt",
    ),
)
