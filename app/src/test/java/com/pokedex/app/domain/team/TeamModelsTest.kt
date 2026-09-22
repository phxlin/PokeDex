package com.pokedex.app.domain.team

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** [StatCalc], [Nature], [Gender] and the Stat-Point helpers on [TeamMember]. */
class TeamModelsTest {

    @Test
    fun `a gender-split form slug fixes the gender and other forms leave it free`() {
        assertThat(Gender.lockedByForm("indeedee-female")).isEqualTo(Gender.FEMALE)
        assertThat(Gender.lockedByForm("indeedee-male")).isEqualTo(Gender.MALE)
        assertThat(Gender.lockedByForm("charizard")).isNull()
        assertThat(Gender.lockedByForm("charizard-mega-x")).isNull()
        assertThat(Gender.lockedByForm("ninetales-alola")).isNull()
    }

    // ---- StatCalc -------------------------------------------------------------

    @Test
    fun `hp uses the champions level-50 formula`() {
        // Garchomp base HP 108, no Stat Points: (2*108 + 31) * 50 / 100 + 50 + 10.
        assertThat(StatCalc.value(StatKey.HP, base = 108, sp = 0, nature = Nature.SERIOUS))
            .isEqualTo(183)
    }

    @Test
    fun `stat points are added to hp one for one`() {
        val zero = StatCalc.value(StatKey.HP, base = 108, sp = 0, nature = Nature.SERIOUS)
        val twenty = StatCalc.value(StatKey.HP, base = 108, sp = 20, nature = Nature.SERIOUS)
        assertThat(twenty - zero).isEqualTo(20)
    }

    @Test
    fun `shedinja always has one hp`() {
        assertThat(StatCalc.value(StatKey.HP, base = 1, sp = 0, nature = Nature.SERIOUS)).isEqualTo(1)
        assertThat(StatCalc.value(StatKey.HP, base = 1, sp = 32, nature = Nature.BRAVE)).isEqualTo(1)
    }

    @Test
    fun `a neutral alignment does not scale a non-hp stat`() {
        // Garchomp base Speed 102.
        assertThat(StatCalc.value(StatKey.SPE, base = 102, sp = 0, nature = Nature.SERIOUS))
            .isEqualTo(122)
    }

    @Test
    fun `a raising alignment multiplies the stat by 1_1`() {
        val neutral = StatCalc.value(StatKey.SPE, base = 102, sp = 32, nature = Nature.SERIOUS)
        val jolly = StatCalc.value(StatKey.SPE, base = 102, sp = 32, nature = Nature.JOLLY)
        assertThat(jolly).isEqualTo((neutral * 1.1).toInt())
        assertThat(jolly).isGreaterThan(neutral)
    }

    @Test
    fun `a lowering alignment multiplies the stat by 0_9`() {
        val neutral = StatCalc.value(StatKey.ATK, base = 130, sp = 0, nature = Nature.SERIOUS)
        val modest = StatCalc.value(StatKey.ATK, base = 130, sp = 0, nature = Nature.MODEST) // -Atk
        assertThat(modest).isEqualTo((neutral * 0.9).toInt())
        assertThat(modest).isLessThan(neutral)
    }

    @Test
    fun `the champions caps are 66 total and 32 per stat at level 50`() {
        assertThat(StatCalc.LEVEL).isEqualTo(50)
        assertThat(StatCalc.MAX_SP_TOTAL).isEqualTo(66)
        assertThat(StatCalc.MAX_SP_PER_STAT).isEqualTo(32)
    }

    // ---- Nature (Stat Alignments) ------------------------------------------------

    @Test
    fun `serious is the default first alignment and is neutral`() {
        assertThat(Nature.entries.first()).isEqualTo(Nature.SERIOUS)
        assertThat(Nature.SERIOUS.raises).isNull()
        assertThat(Nature.SERIOUS.lowers).isNull()
        assertThat(Nature.SERIOUS.summary()).isEqualTo("neutral")
    }

    @Test
    fun `multiplier reflects the raised and lowered stats`() {
        assertThat(Nature.JOLLY.multiplier(StatKey.SPE)).isEqualTo(1.1) // +Spe
        assertThat(Nature.JOLLY.multiplier(StatKey.SPA)).isEqualTo(0.9) // -SpA
        assertThat(Nature.JOLLY.multiplier(StatKey.ATK)).isEqualTo(1.0)
    }

    @Test
    fun `fromStored folds legacy neutral natures onto serious`() {
        listOf("HARDY", "DOCILE", "BASHFUL", "QUIRKY").forEach {
            assertThat(Nature.fromStored(it)).isEqualTo(Nature.SERIOUS)
        }
    }

    @Test
    fun `fromStored falls back to serious for unknown input`() {
        assertThat(Nature.fromStored("not-a-nature")).isEqualTo(Nature.SERIOUS)
        assertThat(Nature.fromStored("jolly")).isEqualTo(Nature.SERIOUS) // case-sensitive
    }

    @Test
    fun `fromStored round-trips a real alignment`() {
        assertThat(Nature.fromStored(Nature.ADAMANT.name)).isEqualTo(Nature.ADAMANT)
    }

    // ---- Gender -------------------------------------------------------------------

    @Test
    fun `gender fromStored matches an enum name or defaults`() {
        assertThat(Gender.fromStored("MALE")).isEqualTo(Gender.MALE)
        assertThat(Gender.fromStored("FEMALE")).isEqualTo(Gender.FEMALE)
        assertThat(Gender.fromStored(null)).isEqualTo(Gender.DEFAULT)
        assertThat(Gender.fromStored("male")).isEqualTo(Gender.DEFAULT)
        assertThat(Gender.fromStored("garbage")).isEqualTo(Gender.DEFAULT)
    }

    // ---- TeamMember Stat-Point helpers ------------------------------------------

    @Test
    fun `spRemaining is 66 minus what is spent`() {
        val member = sampleMember().copy(sp = mapOf(StatKey.ATK to 32, StatKey.SPE to 20))
        assertThat(member.spTotal).isEqualTo(52)
        assertThat(member.spRemaining).isEqualTo(14)
    }

    @Test
    fun `finalStat returns null until base stats are loaded`() {
        assertThat(sampleMember().finalStat(StatKey.SPE)).isNull()
        val loaded = sampleMember().copy(baseStats = mapOf(StatKey.SPE to 100))
        assertThat(loaded.finalStat(StatKey.SPE)).isEqualTo(120)
    }

    private fun sampleMember() = TeamMember(
        slot = 0,
        speciesId = 445,
        speciesName = "garchomp",
        displayName = "Garchomp",
    )

    // ---- MoveInfo.isAttacking ---------------------------------------------------

    @Test
    fun `a variable-power damaging move is still attacking`() {
        // Grass Knot, Low Kick, Seismic Toss, … deal real damage but PokéAPI has no
        // single fixed base power for them, so `power` comes back null.
        val grassKnot = sampleMove(type = "grass", damageClass = "special", power = null)
        assertThat(grassKnot.isAttacking).isTrue()
    }

    @Test
    fun `a fixed-power damaging move is attacking`() {
        assertThat(sampleMove(type = "fire", damageClass = "special", power = 90).isAttacking).isTrue()
    }

    @Test
    fun `a status move is never attacking, power or not`() {
        assertThat(sampleMove(type = "psychic", damageClass = "status", power = null).isAttacking).isFalse()
        assertThat(sampleMove(type = "psychic", damageClass = "status", power = 0).isAttacking).isFalse()
    }

    private fun sampleMove(type: String, damageClass: String, power: Int?) = MoveInfo(
        name = "move",
        displayName = "Move",
        type = type,
        damageClass = damageClass,
        power = power,
        accuracy = 100,
        pp = 10,
    )
}
