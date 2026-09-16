package com.pokedex.app.domain.team

/**
 * Full 18-type effectiveness chart. Built once from PokeAPI's `type/{name}`
 * `damage_relations` and cached. [multiplierAgainst] gives the damage multiplier
 * of an attacking type against a (mono- or dual-type) defender.
 */
class TypeChart(
    /** attackingType -> (defendingType -> multiplier) */
    private val relations: Map<String, Map<String, Double>>,
) {
    fun multiplier(attacking: String, defending: String): Double =
        relations[attacking]?.get(defending) ?: 1.0

    fun multiplierAgainst(attacking: String, defenderTypes: List<String>): Double =
        defenderTypes.fold(1.0) { acc, t -> acc * multiplier(attacking, t) }

    companion object {
        val TYPES = listOf(
            "normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison",
            "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy",
        )
    }
}

data class TypeMatchup(
    val type: String,
    val weak: Int,     // members taking >1x
    val resist: Int,   // members taking <1x but >0
    val immune: Int,   // members taking 0x
)
