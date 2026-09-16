package com.pokedex.app.domain.team

/** One team member's defensive picture: the type-combos it can field, plus types its Ability negates. */
data class DefProfile(val typeSets: List<List<String>>, val abilityImmunities: Set<String>)

/** Abilities that grant a full immunity to an attacking type. */
private val ABILITY_TYPE_IMMUNITY: Map<String, String> = mapOf(
    "flash-fire" to "fire",
    "well-baked-body" to "fire",
    "water-absorb" to "water",
    "storm-drain" to "water",
    "dry-skin" to "water",
    "volt-absorb" to "electric",
    "lightning-rod" to "electric",
    "motor-drive" to "electric",
    "levitate" to "ground",
    "earth-eater" to "ground",
    "sap-sipper" to "grass",
)

fun abilityImmuneType(ability: String?): String? = ability?.lowercase()?.let { ABILITY_TYPE_IMMUNITY[it] }

/**
 * The "-ate" abilities: every Normal-type move the Pokémon uses becomes this type
 * instead (Aerilate → Flying, etc.), so it also inherits that type's matchups —
 * e.g. Mega Salamence's Aerilate turns its Normal-type moves into Flying-type ones.
 */
private val ABILITY_NORMAL_MOVE_TYPE: Map<String, String> = mapOf(
    "aerilate" to "flying",
    "pixilate" to "fairy",
    "refrigerate" to "ice",
    "galvanize" to "electric",
)

fun abilityNormalMoveType(ability: String?): String? = ability?.lowercase()?.let { ABILITY_NORMAL_MOVE_TYPE[it] }

object TeamAnalysis {

    /**
     * For every attacking type, how the team handles it defensively.
     *
     * Each entry in [profiles] is one team member's set of possible type-combos
     * (e.g. base form + Mega form). A member counts as resisting/immune to a type
     * when *any* of its forms does, and as weak only when *every* form is weak —
     * so a Pokémon's Mega resistances are credited alongside its base ones.
     */
    fun defensiveMatchups(profiles: List<DefProfile>, chart: TypeChart): List<TypeMatchup> {
        val valid = profiles
            .map { it.copy(typeSets = it.typeSets.filter { s -> s.isNotEmpty() }) }
            .filter { it.typeSets.isNotEmpty() }
        return TypeChart.TYPES.map { atk ->
            var weak = 0
            var resist = 0
            var immune = 0
            valid.forEach { p ->
                if (atk in p.abilityImmunities) {
                    immune++
                    return@forEach
                }
                val mults = p.typeSets.map { chart.multiplierAgainst(atk, it) }
                val best = mults.min()
                when {
                    best == 0.0 -> immune++
                    best < 1.0 -> resist++
                    mults.all { it > 1.0 } -> weak++
                }
            }
            TypeMatchup(atk, weak, resist, immune)
        }
    }

    /**
     * A moveset's attacking types, ready for [offensiveCoverage] — Normal-type moves
     * come out as [abilityNormalMoveType]'s type when [ability] converts them (Mega
     * Salamence's Aerilate, …), so its offense is judged as Flying, not Normal.
     */
    fun attackingTypesFor(moves: List<String>, moveInfo: Map<String, MoveInfo>, ability: String?): Set<String> {
        val substitute = abilityNormalMoveType(ability)
        return moves.mapNotNull { moveInfo[it] }
            .filter { it.isAttacking }
            .map { if (substitute != null && it.type == "normal") substitute else it.type }
            .toSet()
    }

    /** Types the team can hit super-effectively with at least one damaging move. */
    fun offensiveCoverage(attackingTypes: Set<String>, chart: TypeChart): Set<String> =
        TypeChart.TYPES.filter { def ->
            attackingTypes.any { atk -> chart.multiplier(atk, def) > 1.0 }
        }.toSet()

    fun speedOrder(members: List<TeamMember>): List<Pair<TeamMember, Int>> =
        members.mapNotNull { m -> m.finalStat(StatKey.SPE)?.let { m to it } }
            .sortedByDescending { it.second }

    /** Shared weaknesses worth worrying about (3+ members weak, and outweighing resists). */
    fun sharedWeaknesses(matchups: List<TypeMatchup>): List<TypeMatchup> =
        matchups.filter { it.weak >= 3 && it.weak > it.resist + it.immune }
            .sortedByDescending { it.weak }
}
