package com.pokedex.app

import com.pokedex.app.data.TeamBackup
import com.pokedex.app.data.repository.TeamRepository
import com.pokedex.app.domain.team.Team
import com.pokedex.app.domain.team.TeamMember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [TeamRepository] for UI tests — no Room. [swapTeams] mirrors the real DAO's
 * swap-in-place semantics (see `TeamDao.swapSortOrder`'s doc comment for why it matters
 * that this is a genuine exchange, not a shift). */
class FakeTeamRepository(initial: List<Team>) : TeamRepository {
    private val state = MutableStateFlow(initial)
    val current: List<Team> get() = state.value
    var swapCount = 0
        private set

    override fun observeTeams(): Flow<List<Team>> = state

    override fun observeTeam(id: Long): Flow<Team?> = state.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun createTeam(name: String): Long {
        val id = (state.value.maxOfOrNull { it.id } ?: 0) + 1
        state.update { it + Team(id = id, name = name) }
        return id
    }

    override suspend fun renameTeam(id: Long, name: String) {
        state.update { list -> list.map { if (it.id == id) it.copy(name = name) else it } }
    }

    override suspend fun deleteTeam(id: Long) {
        state.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun saveMembers(teamId: Long, members: List<TeamMember>) {
        state.update { list -> list.map { if (it.id == teamId) it.copy(members = members) else it } }
    }

    override suspend fun exportBackup(): String = TeamBackup.toJson(state.value)

    override suspend fun importBackup(json: String) {
        val teams = TeamBackup.parse(json)
        state.value = teams.mapIndexed { index, team -> team.copy(id = index + 1L) }
    }

    override suspend fun deleteAllTeams() {
        state.value = emptyList()
    }

    override suspend fun swapTeams(id1: Long, id2: Long) {
        swapCount++
        state.update { list ->
            val i1 = list.indexOfFirst { it.id == id1 }
            val i2 = list.indexOfFirst { it.id == id2 }
            list.toMutableList().also { it[i1] = list[i2]; it[i2] = list[i1] }
        }
    }
}
