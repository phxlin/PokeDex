package com.pokedex.app.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.pokedex.app.data.BackupException
import com.pokedex.app.data.local.PokeDexDatabase
import com.pokedex.app.domain.team.Gender
import com.pokedex.app.domain.team.Nature
import com.pokedex.app.domain.team.StatKey
import com.pokedex.app.domain.team.TeamMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Against real (in-memory) Room, because the parts worth trusting are exactly the ones a mock
 * can't check: gender / shiny / form travel through Room's packed `teraType` and `level` columns,
 * and replacing every team must cascade to `team_member` without leaving orphans.
 */
@RunWith(AndroidJUnit4::class)
class TeamBackupRoundTripTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var source: PokeDexDatabase
    private lateinit var target: PokeDexDatabase
    private lateinit var sourceRepo: TeamRepositoryImpl
    private lateinit var targetRepo: TeamRepositoryImpl

    @Before
    fun setUp() {
        source = Room.inMemoryDatabaseBuilder(context, PokeDexDatabase::class.java).build()
        target = Room.inMemoryDatabaseBuilder(context, PokeDexDatabase::class.java).build()
        sourceRepo = TeamRepositoryImpl(source.teamDao(), Dispatchers.IO)
        targetRepo = TeamRepositoryImpl(target.teamDao(), Dispatchers.IO)
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private fun ninetales() = TeamMember(
        slot = 3,
        speciesId = 38,
        speciesName = "ninetales",
        displayName = "Ninetales",
        ability = "snow-cloak",
        nature = Nature.TIMID,
        item = "focus-sash",
        shiny = true,
        gender = Gender.FEMALE,
        formSlug = "ninetales-alola",
        sp = StatKey.entries.associateWith { if (it == StatKey.SPE) 32 else 1 },
        moves = listOf("blizzard", "freeze-dry", null, "protect"),
    )

    private fun memberCount(db: PokeDexDatabase): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM team_member").use {
            it.moveToFirst()
            it.getInt(0)
        }

    @Test
    fun exportedTeamsRestoreIntoAFreshDatabaseIncludingPackedFields() = runTest {
        val idA = sourceRepo.createTeam("First")
        sourceRepo.saveMembers(idA, listOf(ninetales()))
        sourceRepo.createTeam("Second") // created later, so it leads the list

        targetRepo.importBackup(sourceRepo.exportBackup())

        val restored = targetRepo.observeTeams().first()
        assertThat(restored.map { it.name }).containsExactly("Second", "First").inOrder()
        val back = restored.single { it.name == "First" }.members.single()
        assertThat(back.slot).isEqualTo(3)
        assertThat(back.speciesId).isEqualTo(38)
        assertThat(back.ability).isEqualTo("snow-cloak")
        assertThat(back.nature).isEqualTo(Nature.TIMID)
        assertThat(back.item).isEqualTo("focus-sash")
        assertThat(back.shiny).isTrue()
        assertThat(back.gender).isEqualTo(Gender.FEMALE)
        assertThat(back.formSlug).isEqualTo("ninetales-alola")
        assertThat(back.sp).isEqualTo(ninetales().sp)
        assertThat(back.moves).containsExactly("blizzard", "freeze-dry", null, "protect").inOrder()
    }

    @Test
    fun importReplacesExistingTeamsAndTheirMembersWithoutOrphans() = runTest {
        val old = targetRepo.createTeam("Old")
        targetRepo.saveMembers(old, listOf(ninetales()))
        val incoming = sourceRepo.createTeam("Incoming")
        sourceRepo.saveMembers(incoming, listOf(ninetales().copy(slot = 0), ninetales().copy(slot = 1, speciesId = 25, speciesName = "pikachu")))

        targetRepo.importBackup(sourceRepo.exportBackup())

        assertThat(targetRepo.observeTeams().first().map { it.name }).containsExactly("Incoming")
        assertThat(memberCount(target)).isEqualTo(2) // the old team's member is gone, not orphaned
    }

    @Test
    fun invalidFileLeavesExistingTeamsUntouched() = runTest {
        val old = targetRepo.createTeam("Keep me")
        targetRepo.saveMembers(old, listOf(ninetales()))

        val failure = runCatching { targetRepo.importBackup("""{"app":"PokeDex","version":1,"teams":[{"name":"x","members":[{"slot":9}]}]}""") }
            .exceptionOrNull()

        assertThat(failure).isInstanceOf(BackupException::class.java)
        assertThat(targetRepo.observeTeams().first().map { it.name }).containsExactly("Keep me")
        assertThat(memberCount(target)).isEqualTo(1)
    }

    @Test
    fun deleteAllTeamsRemovesEveryTeamAndItsMembers() = runTest {
        targetRepo.saveMembers(targetRepo.createTeam("One"), listOf(ninetales()))
        targetRepo.saveMembers(targetRepo.createTeam("Two"), listOf(ninetales().copy(slot = 0)))

        targetRepo.deleteAllTeams()

        assertThat(targetRepo.observeTeams().first()).isEmpty()
        assertThat(memberCount(target)).isEqualTo(0)
    }

    @Test
    fun restoringAnEmptyBackupClearsEverything() = runTest {
        targetRepo.saveMembers(targetRepo.createTeam("Gone"), listOf(ninetales()))

        targetRepo.importBackup(sourceRepo.exportBackup()) // source has no teams

        assertThat(targetRepo.observeTeams().first()).isEmpty()
        assertThat(memberCount(target)).isEqualTo(0)
    }
}
