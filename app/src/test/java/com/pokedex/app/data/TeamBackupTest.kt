package com.pokedex.app.data

import com.google.common.truth.Truth.assertThat
import com.pokedex.app.domain.team.Gender
import com.pokedex.app.domain.team.Nature
import com.pokedex.app.domain.team.StatKey
import com.pokedex.app.domain.team.Team
import com.pokedex.app.domain.team.TeamMember
import org.junit.Assert.fail
import org.junit.Test
import java.io.StringReader
import java.time.Instant

class TeamBackupTest {

    private fun member(slot: Int, speciesId: Int, name: String, block: (TeamMember) -> TeamMember = { it }) =
        block(TeamMember(slot = slot, speciesId = speciesId, speciesName = name, displayName = name))

    private fun assertRejected(json: String, messagePart: String) =
        assertRejected(messagePart) { TeamBackup.parse(json) }

    private fun assertRejected(messagePart: String, action: () -> Unit) {
        try {
            action()
            fail("expected BackupException")
        } catch (e: BackupException) {
            assertThat(e.message).contains(messagePart)
        }
    }

    private fun backup(teams: String, app: String = "PokeDex", version: String = "1") =
        """{"app":"$app","version":$version,"teams":$teams}"""

    @Test
    fun `round trip keeps every persisted field and the team order`() {
        val ninetales = member(2, 38, "ninetales") {
            it.copy(
                ability = "drought",
                nature = Nature.TIMID,
                item = "focus-sash",
                shiny = true,
                gender = Gender.FEMALE,
                formSlug = "ninetales-alola",
                sp = StatKey.entries.associateWith { key ->
                    when (key) {
                        StatKey.SPE -> 32
                        StatKey.SPA -> 30
                        else -> 1
                    }
                },
                moves = listOf("flamethrower", null, "protect", "fire-blast"),
            )
        }
        val teams = listOf(
            Team(name = "Rain", members = listOf(member(0, 6, "charizard"), ninetales)),
            Team(name = "Empty"),
        )

        val parsed = TeamBackup.parse(TeamBackup.toJson(teams, Instant.parse("2026-09-19T00:00:00Z")))

        assertThat(parsed.map { it.name }).containsExactly("Rain", "Empty").inOrder()
        assertThat(parsed[1].members).isEmpty()
        val back = parsed[0].members.single { it.slot == 2 }
        assertThat(back.speciesId).isEqualTo(38)
        assertThat(back.ability).isEqualTo("drought")
        assertThat(back.nature).isEqualTo(Nature.TIMID)
        assertThat(back.item).isEqualTo("focus-sash")
        assertThat(back.shiny).isTrue()
        assertThat(back.gender).isEqualTo(Gender.FEMALE)
        assertThat(back.formSlug).isEqualTo("ninetales-alola")
        assertThat(back.sp).isEqualTo(ninetales.sp)
        assertThat(back.moves).containsExactly("flamethrower", null, "protect", "fire-blast").inOrder()
    }

    @Test
    fun `optional fields left out of a hand-edited file fall back to defaults`() {
        val teams = TeamBackup.parse(backup("""[{"name":"Bare","members":[{"slot":0,"speciesId":25,"speciesName":"pikachu"}]}]"""))

        val pikachu = teams.single().members.single()
        assertThat(pikachu.ability).isNull()
        assertThat(pikachu.nature).isEqualTo(Nature.SERIOUS)
        assertThat(pikachu.shiny).isFalse()
        assertThat(pikachu.gender).isEqualTo(Gender.DEFAULT)
        assertThat(pikachu.formSlug).isNull()
        assertThat(pikachu.sp.values.sum()).isEqualTo(0)
        assertThat(pikachu.moves).containsExactly(null, null, null, null)
    }

    @Test
    fun `unknown keys are ignored so a newer file with extra fields still imports`() {
        val teams = TeamBackup.parse(backup("""[{"name":"A","colour":"red","members":[]}]"""))

        assertThat(teams.single().name).isEqualTo("A")
    }

    @Test
    fun `a blank team name becomes Team`() {
        assertThat(TeamBackup.parse(backup("""[{"name":"  ","members":[]}]""")).single().name).isEqualTo("Team")
    }

    @Test
    fun `an empty backup is valid and restores to no teams`() {
        assertThat(TeamBackup.parse(backup("[]"))).isEmpty()
    }

    @Test
    fun `rejects text that isn't a backup`() {
        assertRejected("not json at all", "valid backup")
        assertRejected("", "valid backup")
        assertRejected("""{"app":"PokeDex","version":"one","teams":[]}""", "valid backup")
    }

    @Test
    fun `rejects another app's file and a newer format version`() {
        assertRejected(backup("[]", app = "Avalanche"), "PokéDex backup")
        assertRejected("""{"version":1,"teams":[]}""", "PokéDex backup")
        assertRejected(backup("[]", version = "2"), "newer version")
        assertRejected("""{"app":"PokeDex","teams":[]}""", "version")
        assertRejected("""{"app":"PokeDex","version":1}""", "team list")
    }

    @Test
    fun `rejects members that would corrupt a team`() {
        fun team(vararg members: String) = backup("""[{"name":"T","members":[${members.joinToString(",")}]}]""")
        val ok = """{"slot":0,"speciesId":1,"speciesName":"bulbasaur"}"""

        assertRejected(team("""{"slot":6,"speciesId":1,"speciesName":"bulbasaur"}"""), "invalid slot")
        assertRejected(team("""{"slot":-1,"speciesId":1,"speciesName":"bulbasaur"}"""), "invalid slot")
        assertRejected(team("""{"speciesId":1,"speciesName":"bulbasaur"}"""), "invalid slot")
        assertRejected(team("""{"slot":0,"speciesName":"bulbasaur"}"""), "species id")
        assertRejected(team("""{"slot":0,"speciesId":0,"speciesName":"bulbasaur"}"""), "species id")
        assertRejected(team("""{"slot":0,"speciesId":1}"""), "name")
        assertRejected(team(ok, ok), "same slot")
        assertRejected(team(*Array(7) { """{"slot":${it % 6},"speciesId":1,"speciesName":"x"}""" }), "more than 6")
    }

    @Test
    fun `rejects duplicate species in different slots`() {
        val json = backup(
            """[{"name":"Duplicates","members":[
                {"slot":0,"speciesId":25,"speciesName":"pikachu"},
                {"slot":1,"speciesId":25,"speciesName":"pikachu"}
            ]}]""",
        )
        assertRejected(json, "species")
    }

    @Test
    fun `a file over the size limit is rejected without being read to the end`() {
        assertRejected("too large") { TeamBackup.readBounded(StringReader("x".repeat(TeamBackup.MAX_CHARS + 1))) }
    }

    @Test
    fun `a file exactly at the size limit is read in full`() {
        val text = "x".repeat(TeamBackup.MAX_CHARS)

        assertThat(TeamBackup.readBounded(StringReader(text))).hasLength(TeamBackup.MAX_CHARS)
    }

    @Test
    fun `rejects Stat Points and moves outside the game's limits`() {
        fun one(extra: String) =
            backup("""[{"name":"T","members":[{"slot":0,"speciesId":1,"speciesName":"bulbasaur",$extra}]}]""")

        assertRejected(one(""""sp":{"ATK":33}"""), "Stat Points")
        assertRejected(one(""""sp":{"ATK":-1}"""), "Stat Points")
        assertRejected(one(""""sp":{"HP":32,"ATK":32,"DEF":32}"""), "Stat Points")
        assertRejected(one(""""sp":{"LUCK":1}"""), "LUCK")
        assertRejected(one(""""moves":["a","b","c","d","e"]"""), "more than 4 moves")
    }
}
