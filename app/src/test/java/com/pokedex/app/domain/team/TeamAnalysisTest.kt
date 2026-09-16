package com.pokedex.app.domain.team

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TeamAnalysisTest {

    /** A hand-built slice of the type chart, enough for the cases below. */
    private val chart = TypeChart(
        mapOf(
            "fire" to mapOf(
                "grass" to 2.0, "bug" to 2.0, "steel" to 2.0, "ice" to 2.0,
                "water" to 0.5, "fire" to 0.5, "rock" to 0.5, "dragon" to 0.5,
            ),
            "water" to mapOf(
                "fire" to 2.0, "ground" to 2.0, "rock" to 2.0,
                "water" to 0.5, "grass" to 0.5, "dragon" to 0.5,
            ),
            "grass" to mapOf(
                "water" to 2.0, "ground" to 2.0, "rock" to 2.0,
                "fire" to 0.5, "grass" to 0.5, "flying" to 0.5,
            ),
            "electric" to mapOf(
                "water" to 2.0, "flying" to 2.0,
                "electric" to 0.5, "grass" to 0.5, "ground" to 0.0,
            ),
            "ground" to mapOf(
                "fire" to 2.0, "electric" to 2.0, "poison" to 2.0, "rock" to 2.0, "steel" to 2.0,
                "grass" to 0.5, "flying" to 0.0,
            ),
        ),
    )

    private fun profile(vararg typeSets: List<String>, immune: Set<String> = emptySet()) =
        DefProfile(typeSets.toList(), immune)

    private fun matchup(list: List<TypeMatchup>, type: String) = list.first { it.type == type }

    // ---- defensiveMatchups -----------------------------------------------------

    @Test
    fun `no members means every matchup is zero`() {
        TeamAnalysis.defensiveMatchups(emptyList(), chart).forEach {
            assertThat(it.weak).isEqualTo(0)
            assertThat(it.resist).isEqualTo(0)
            assertThat(it.immune).isEqualTo(0)
        }
    }

    @Test
    fun `a mono-Water member resists Fire and is weak to Grass`() {
        val result = TeamAnalysis.defensiveMatchups(listOf(profile(listOf("water"))), chart)

        assertThat(matchup(result, "fire")).isEqualTo(TypeMatchup("fire", weak = 0, resist = 1, immune = 0))
        assertThat(matchup(result, "grass")).isEqualTo(TypeMatchup("grass", weak = 1, resist = 0, immune = 0))
        assertThat(matchup(result, "electric")).isEqualTo(TypeMatchup("electric", weak = 1, resist = 0, immune = 0))
    }

    @Test
    fun `an ability immunity counts as immune, not weak`() {
        // Grass type (normally weak to Fire) with a Fire-negating ability.
        val result = TeamAnalysis.defensiveMatchups(
            listOf(profile(listOf("grass"), immune = setOf("fire"))),
            chart,
        )
        assertThat(matchup(result, "fire")).isEqualTo(TypeMatchup("fire", weak = 0, resist = 0, immune = 1))
    }

    @Test
    fun `a zero multiplier is an immunity`() {
        // Ground does 0x to a Flying type.
        val result = TeamAnalysis.defensiveMatchups(listOf(profile(listOf("flying"))), chart)
        assertThat(matchup(result, "ground").immune).isEqualTo(1)
    }

    @Test
    fun `a members Mega form resistance is credited alongside its base form`() {
        // Base Grass is weak to Fire (2x); Mega Fire resists it (0.5x). Net: resist.
        val result = TeamAnalysis.defensiveMatchups(
            listOf(profile(listOf("grass"), listOf("fire"))),
            chart,
        )
        assertThat(matchup(result, "fire")).isEqualTo(TypeMatchup("fire", weak = 0, resist = 1, immune = 0))
    }

    @Test
    fun `a member is only weak when every form is weak`() {
        // Grass form weak to Fire; Fire form resists Fire -> not counted weak.
        val result = TeamAnalysis.defensiveMatchups(
            listOf(profile(listOf("grass"), listOf("fire"))),
            chart,
        )
        assertThat(matchup(result, "fire").weak).isEqualTo(0)
    }

    // ---- attackingTypesFor -----------------------------------------------------

    @Test
    fun `attackingTypesFor drops status moves and moves missing from moveInfo`() {
        val moveInfo = mapOf(
            "will-o-wisp" to move("will-o-wisp", type = "fire", damageClass = "status", power = null),
        )
        val types = TeamAnalysis.attackingTypesFor(listOf("will-o-wisp", "unregistered-move"), moveInfo, ability = null)
        assertThat(types).isEmpty()
    }

    @Test
    fun `attackingTypesFor still counts a variable-power damaging move`() {
        // Grass Knot has no fixed PokéAPI `power` (it depends on the target's weight),
        // but it damages the target just as much as a fixed-power move for coverage.
        val moveInfo = mapOf(
            "grass-knot" to move("grass-knot", type = "grass", power = null),
        )
        val types = TeamAnalysis.attackingTypesFor(listOf("grass-knot"), moveInfo, ability = null)
        assertThat(types).containsExactly("grass")
    }

    @Test
    fun `an -ate ability substitutes its type for Normal-type moves only`() {
        // Mega Salamence's Aerilate: its Normal-type moves are judged as Flying, not Normal.
        val moveInfo = mapOf(
            "tackle" to move("tackle", type = "normal"),
            "flamethrower" to move("flamethrower", type = "fire"),
        )
        assertThat(TeamAnalysis.attackingTypesFor(listOf("tackle"), moveInfo, "aerilate")).containsExactly("flying")
        assertThat(TeamAnalysis.attackingTypesFor(listOf("tackle"), moveInfo, null)).containsExactly("normal")
        assertThat(TeamAnalysis.attackingTypesFor(listOf("flamethrower"), moveInfo, "aerilate")).containsExactly("fire")
    }

    // ---- offensiveCoverage ---------------------------------------------------

    @Test
    fun `coverage is the union of types the given attacking types hit super-effectively`() {
        val covered = TeamAnalysis.offensiveCoverage(setOf("fire", "water"), chart)

        assertThat(covered).containsAtLeast("grass", "bug", "steel", "ice") // from Fire
        assertThat(covered).containsAtLeast("fire", "ground", "rock")       // from Water
        assertThat(covered).doesNotContain("water")   // Fire is 0.5x, Water is 0.5x
        assertThat(covered).doesNotContain("normal")  // nothing hits it > 1x
    }

    @Test
    fun `no attacking types means no coverage`() {
        assertThat(TeamAnalysis.offensiveCoverage(emptySet(), chart)).isEmpty()
    }

    // ---- speedOrder ---------------------------------------------------------

    @Test
    fun `speed order sorts by final Speed descending and drops un-enriched members`() {
        val fast = speedster(slot = 0, name = "jolteon", baseSpe = 130)
        val mid = speedster(slot = 1, name = "garchomp", baseSpe = 102)
        val notLoaded = TeamMember(slot = 2, speciesId = 3, speciesName = "ditto", displayName = "Ditto")

        val order = TeamAnalysis.speedOrder(listOf(mid, notLoaded, fast))

        assertThat(order.map { it.first.speciesName }).containsExactly("jolteon", "garchomp").inOrder()
    }

    @Test
    fun `a Jolly booster outspeeds a faster neutral base`() {
        val jollyBooster = speedster(slot = 0, name = "booster", baseSpe = 90, sp = 32, nature = Nature.JOLLY)
        val neutralFast = speedster(slot = 1, name = "natural", baseSpe = 100)

        val order = TeamAnalysis.speedOrder(listOf(neutralFast, jollyBooster))

        assertThat(order.first().first.speciesName).isEqualTo("booster")
    }

    // ---- sharedWeaknesses -------------------------------------------------------

    @Test
    fun `a shared weakness needs 3+ weak members outnumbering resists and immunities`() {
        val matchups = listOf(
            TypeMatchup("fire", weak = 4, resist = 1, immune = 0),  // flagged
            TypeMatchup("water", weak = 3, resist = 2, immune = 2), // not: 3 !> 4
            TypeMatchup("grass", weak = 2, resist = 0, immune = 0), // not: < 3
            TypeMatchup("ice", weak = 5, resist = 0, immune = 0),   // flagged
        )

        val shared = TeamAnalysis.sharedWeaknesses(matchups)

        assertThat(shared.map { it.type }).containsExactly("ice", "fire").inOrder()
    }

    // ---- abilityImmuneType -------------------------------------------------------

    @Test
    fun `known ability-immunity abilities map to their negated type`() {
        assertThat(abilityImmuneType("flash-fire")).isEqualTo("fire")
        assertThat(abilityImmuneType("lightning-rod")).isEqualTo("electric")
        assertThat(abilityImmuneType("levitate")).isEqualTo("ground")
        assertThat(abilityImmuneType("sap-sipper")).isEqualTo("grass")
    }

    @Test
    fun `abilityImmuneType is case-insensitive and null-safe`() {
        assertThat(abilityImmuneType("Flash-Fire")).isEqualTo("fire")
        assertThat(abilityImmuneType(null)).isNull()
        assertThat(abilityImmuneType("intimidate")).isNull()
    }

    // ---- abilityNormalMoveType -----------------------------------------------------

    @Test
    fun `known -ate abilities map to the type they turn Normal moves into`() {
        assertThat(abilityNormalMoveType("aerilate")).isEqualTo("flying")
        assertThat(abilityNormalMoveType("pixilate")).isEqualTo("fairy")
        assertThat(abilityNormalMoveType("refrigerate")).isEqualTo("ice")
        assertThat(abilityNormalMoveType("galvanize")).isEqualTo("electric")
    }

    @Test
    fun `abilityNormalMoveType is case-insensitive and null-safe`() {
        assertThat(abilityNormalMoveType("Aerilate")).isEqualTo("flying")
        assertThat(abilityNormalMoveType(null)).isNull()
        assertThat(abilityNormalMoveType("intimidate")).isNull()
    }

    // ---- helpers -------------------------------------------------------------

    private fun move(
        name: String,
        type: String,
        damageClass: String = "special",
        power: Int? = 90,
    ) = MoveInfo(
        name = name,
        displayName = name,
        type = type,
        damageClass = damageClass,
        power = power,
        accuracy = 100,
        pp = 15,
    )

    private fun speedster(
        slot: Int,
        name: String,
        baseSpe: Int,
        sp: Int = 0,
        nature: Nature = Nature.SERIOUS,
    ) = TeamMember(
        slot = slot,
        speciesId = slot,
        speciesName = name,
        displayName = name,
        nature = nature,
        baseStats = mapOf(StatKey.SPE to baseSpe),
        sp = StatKey.entries.associateWith { if (it == StatKey.SPE) sp else 0 },
    )
}
