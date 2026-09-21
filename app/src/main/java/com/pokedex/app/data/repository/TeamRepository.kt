package com.pokedex.app.data.repository

import com.pokedex.app.core.PokemonNames
import com.pokedex.app.data.BackupException
import com.pokedex.app.data.TeamBackup
import com.pokedex.app.data.local.TeamDao
import com.pokedex.app.data.local.TeamEntity
import com.pokedex.app.data.local.TeamMemberEntity
import com.pokedex.app.data.local.TeamWithMembers
import com.pokedex.app.domain.team.Gender
import com.pokedex.app.domain.team.Nature
import com.pokedex.app.domain.team.StatKey
import com.pokedex.app.domain.team.Team
import com.pokedex.app.domain.team.TeamMember
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface TeamRepository {
    fun observeTeams(): Flow<List<Team>>
    fun observeTeam(id: Long): Flow<Team?>
    suspend fun createTeam(name: String): Long
    suspend fun renameTeam(id: Long, name: String)
    suspend fun deleteTeam(id: Long)
    suspend fun saveMembers(teamId: Long, members: List<TeamMember>)
    suspend fun swapTeams(id1: Long, id2: Long)

    /** All saved teams as a backup file's JSON, in their on-screen order. */
    suspend fun exportBackup(): String

    /**
     * Replaces every saved team with the backup's contents. Throws [BackupException] if the file is
     * invalid, in which case nothing changes.
     */
    suspend fun importBackup(json: String)
}

@Singleton
class TeamRepositoryImpl @Inject constructor(
    private val dao: TeamDao,
    private val io: CoroutineDispatcher,
) : TeamRepository {

    override fun observeTeams(): Flow<List<Team>> =
        dao.observeTeams().map { list -> list.map { it.toDomain() } }

    override fun observeTeam(id: Long): Flow<Team?> =
        dao.observeTeam(id).map { it?.toDomain() }

    override suspend fun createTeam(name: String): Long = withContext(io) {
        val now = System.currentTimeMillis()
        // Negative-of-now always sorts before any prior team's sortOrder (0, or a small
        // swapped value from manual reordering), and before any later-created team's own
        // (more negative) value — so a new team leads the list, same as the old
        // updatedAt-DESC default did, without a read-then-write race against other inserts.
        dao.insertTeam(TeamEntity(name = name.ifBlank { "New Team" }, updatedAt = now, sortOrder = -now))
    }

    override suspend fun renameTeam(id: Long, name: String) = withContext(io) {
        dao.renameTeam(id, name.ifBlank { "Team" }, System.currentTimeMillis())
    }

    override suspend fun deleteTeam(id: Long) = withContext(io) { dao.deleteTeam(id) }

    override suspend fun saveMembers(teamId: Long, members: List<TeamMember>) = withContext(io) {
        dao.replaceMembers(teamId, members.map { it.toEntity(teamId) })
        dao.touchTeam(teamId, System.currentTimeMillis())
    }

    override suspend fun swapTeams(id1: Long, id2: Long) = withContext(io) {
        dao.swapSortOrder(id1, id2)
    }

    override suspend fun exportBackup(): String = withContext(io) {
        TeamBackup.toJson(dao.observeTeams().first().map { it.toDomain() })
    }

    override suspend fun importBackup(json: String) = withContext(io) {
        val teams = TeamBackup.parse(json)
        val now = System.currentTimeMillis()
        dao.replaceAllTeams(
            teams.mapIndexed { position, team ->
                TeamEntity(name = team.name, updatedAt = now, sortOrder = position.toLong()) to
                    team.members.map { it.toEntity(teamId = 0) }
            },
        )
    }
}

private fun TeamWithMembers.toDomain() = Team(
    id = team.id,
    name = team.name,
    members = members.sortedBy { it.slot }.map { it.toDomain() },
)

// The entity's ev* columns now hold Stat Points. Champions fixes level at 50 and
// has no Tera, so those two columns are repurposed: `level` carries the shiny flag
// (1 = shiny) and `teraType` carries the gender enum name.
private fun TeamMemberEntity.toDomain(): TeamMember {
    val raw = mapOf(
        StatKey.HP to evHp, StatKey.ATK to evAtk, StatKey.DEF to evDef,
        StatKey.SPA to evSpa, StatKey.SPD to evSpd, StatKey.SPE to evSpe,
    )
    // Rows saved under the old EV system (up to 252 / 508) aren't valid SP — reset.
    val looksLikeEvs = raw.values.any { it > 32 } || raw.values.sum() > 66
    val sp = if (looksLikeEvs) StatKey.entries.associateWith { 0 } else raw

    return TeamMember(
        slot = slot,
        speciesId = speciesId,
        speciesName = speciesName,
        displayName = PokemonNames.displayName(speciesName),
        ability = ability,
        nature = Nature.fromStored(nature),
        item = item,
        shiny = level == 1,
        gender = Gender.fromStored(teraType.substringBefore('|')),
        formSlug = teraType.substringAfter('|', "").ifBlank { null },
        sp = sp,
        moves = listOf(move1, move2, move3, move4),
    )
}

private fun TeamMember.toEntity(teamId: Long) = TeamMemberEntity(
    teamId = teamId,
    slot = slot,
    speciesId = speciesId,
    speciesName = speciesName,
    ability = ability,
    nature = nature.name,
    // `teraType` column is repurposed to pack "<gender>|<formSlug>".
    teraType = gender.name + (formSlug?.let { "|$it" } ?: ""),
    item = item,
    level = if (shiny) 1 else 0,
    evHp = sp[StatKey.HP] ?: 0,
    evAtk = sp[StatKey.ATK] ?: 0,
    evDef = sp[StatKey.DEF] ?: 0,
    evSpa = sp[StatKey.SPA] ?: 0,
    evSpd = sp[StatKey.SPD] ?: 0,
    evSpe = sp[StatKey.SPE] ?: 0,
    move1 = moves.getOrNull(0),
    move2 = moves.getOrNull(1),
    move3 = moves.getOrNull(2),
    move4 = moves.getOrNull(3),
)
