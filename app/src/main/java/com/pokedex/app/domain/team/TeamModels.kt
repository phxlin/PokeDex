package com.pokedex.app.domain.team

import com.pokedex.app.core.Sprites

/** The six battle stats, in canonical order. */
enum class StatKey(val short: String, val apiName: String) {
    HP("HP", "hp"),
    ATK("Atk", "attack"),
    DEF("Def", "defense"),
    SPA("SpA", "special-attack"),
    SPD("SpD", "special-defense"),
    SPE("Spe", "speed"),
}

/**
 * Stat Alignments (Pokémon Champions' renamed Natures). Champions collapses the
 * five neutral natures into one ("Serious"), leaving 21 choices.
 */
enum class Nature(val raises: StatKey?, val lowers: StatKey?, val emoji: String) {
    SERIOUS(null, null, "😐"),
    LONELY(StatKey.ATK, StatKey.DEF, "😔"),
    BRAVE(StatKey.ATK, StatKey.SPE, "💪"),
    ADAMANT(StatKey.ATK, StatKey.SPA, "😤"),
    NAUGHTY(StatKey.ATK, StatKey.SPD, "😈"),
    BOLD(StatKey.DEF, StatKey.ATK, "🛡️"),
    RELAXED(StatKey.DEF, StatKey.SPE, "😌"),
    IMPISH(StatKey.DEF, StatKey.SPA, "😏"),
    LAX(StatKey.DEF, StatKey.SPD, "🥱"),
    TIMID(StatKey.SPE, StatKey.ATK, "😰"),
    HASTY(StatKey.SPE, StatKey.DEF, "💨"),
    JOLLY(StatKey.SPE, StatKey.SPA, "😄"),
    NAIVE(StatKey.SPE, StatKey.SPD, "😇"),
    MODEST(StatKey.SPA, StatKey.ATK, "🙂"),
    MILD(StatKey.SPA, StatKey.DEF, "🌤️"),
    QUIET(StatKey.SPA, StatKey.SPE, "🤫"),
    RASH(StatKey.SPA, StatKey.SPD, "😠"),
    CALM(StatKey.SPD, StatKey.ATK, "🧘"),
    GENTLE(StatKey.SPD, StatKey.DEF, "🍃"),
    SASSY(StatKey.SPD, StatKey.SPE, "💅"),
    CAREFUL(StatKey.SPD, StatKey.SPA, "🧐"),
    ;

    val display: String get() = name.lowercase().replaceFirstChar { it.uppercase() }

    fun multiplier(stat: StatKey): Double = when (stat) {
        raises -> 1.1
        lowers -> 0.9
        else -> 1.0
    }

    fun summary(): String = when {
        raises != null && lowers != null -> "+${raises.short} −${lowers.short}"
        else -> "neutral"
    }

    companion object {
        /** Legacy neutral natures fold onto Serious. */
        fun fromStored(name: String): Nature = runCatching { valueOf(name) }.getOrElse {
            when (name) {
                "HARDY", "DOCILE", "BASHFUL", "QUIRKY" -> SERIOUS
                else -> SERIOUS
            }
        }
    }
}

/**
 * Pokémon Champions stat model: every Pokémon is Level 50 with 31 IVs, and you
 * spend Stat Points (SP) instead of training EVs — 66 total, max 32 per stat,
 * each point worth +1 before the alignment modifier.
 */
object StatCalc {
    const val LEVEL = 50
    const val MAX_SP_TOTAL = 66
    const val MAX_SP_PER_STAT = 32
    private const val IV = 31

    fun value(stat: StatKey, base: Int, sp: Int, nature: Nature): Int {
        val inner = (2 * base + IV) * LEVEL / 100
        return if (stat == StatKey.HP) {
            if (base == 1) 1 else inner + LEVEL + 10 + sp // Shedinja
        } else {
            ((inner + 5 + sp) * nature.multiplier(stat)).toInt()
        }
    }
}

data class AbilityChoice(val name: String, val display: String, val isHidden: Boolean)

/** A team member's gender — cosmetic in this builder (no legality checks). */
enum class Gender(val symbol: String, val label: String) {
    DEFAULT("", "Any"),
    MALE("♂", "Male"),
    FEMALE("♀", "Female"),
    ;

    companion object {
        fun fromStored(s: String?): Gender = entries.firstOrNull { it.name == s } ?: DEFAULT
    }
}

/** One configured team member. [types] and [baseStats] are filled from the Pokédex cache. */
data class TeamMember(
    val slot: Int,
    val speciesId: Int,
    val speciesName: String,
    val displayName: String,
    val ability: String? = null,
    val nature: Nature = Nature.SERIOUS,
    val item: String? = null,
    val shiny: Boolean = false,
    val gender: Gender = Gender.DEFAULT,
    /** The chosen alternate form's pokemon slug (e.g. "ninetales-alola"); null = base form. */
    val formSlug: String? = null,
    val sp: Map<StatKey, Int> = StatKey.entries.associateWith { 0 },
    val moves: List<String?> = listOf(null, null, null, null),
    // Filled from the Pokédex cache once the species detail loads (not persisted).
    val types: List<String> = emptyList(),
    val baseStats: Map<StatKey, Int> = emptyMap(),
    val movePool: List<String> = emptyList(),
    val abilityChoices: List<AbilityChoice> = emptyList(),
) {
    val spTotal: Int get() = sp.values.sum()
    val spRemaining: Int get() = StatCalc.MAX_SP_TOTAL - spTotal
    val spentMoves: List<String> get() = moves.filterNotNull().filter { it.isNotBlank() }

    val spriteUrl: String get() = Sprites.pokemon(speciesId, shiny)

    val artworkUrl: String get() = Sprites.artwork(speciesId, shiny)

    fun finalStat(stat: StatKey): Int? {
        val base = baseStats[stat] ?: return null
        return StatCalc.value(stat, base, sp[stat] ?: 0, nature)
    }

    fun finalStats(): Map<StatKey, Int> =
        StatKey.entries.associateWith { finalStat(it) ?: 0 }
}

data class Team(
    val id: Long = 0,
    val name: String,
    val members: List<TeamMember> = emptyList(),
) {
    val isFull: Boolean get() = members.size >= 6
    fun memberAt(slot: Int): TeamMember? = members.firstOrNull { it.slot == slot }
    fun hasSpecies(speciesId: Int): Boolean = members.any { it.speciesId == speciesId }
}

/** Info about a single move, used for coverage analysis and the move picker. */
data class MoveInfo(
    val name: String,
    val displayName: String,
    val type: String,
    val damageClass: String, // physical | special | status
    val power: Int?,
    val accuracy: Int?,
    val pp: Int?,
    /** One-line PokéAPI effect summary, e.g. "Inflicts regular damage with no additional effect." */
    val shortEffect: String? = null,
) {
    // Every physical/special move deals damage by definition — even ones PokéAPI has no
    // single fixed `power` for (Grass Knot, Low Kick, Seismic Toss, …: their real damage
    // depends on the target's weight/level/HP, not a flat number). Requiring power > 0
    // here used to silently drop those from coverage analysis as if they were status moves.
    val isAttacking: Boolean get() = damageClass != "status"
}

/** One-line effect text for a held item, shown in the item picker. */
data class ItemInfo(
    val name: String,
    val displayName: String,
    val shortEffect: String?,
)
