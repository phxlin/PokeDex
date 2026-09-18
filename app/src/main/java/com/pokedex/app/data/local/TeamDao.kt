package com.pokedex.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamDao {

    @Transaction
    @Query("SELECT * FROM team ORDER BY sortOrder ASC, updatedAt DESC")
    fun observeTeams(): Flow<List<TeamWithMembers>>

    @Transaction
    @Query("SELECT * FROM team WHERE id = :id LIMIT 1")
    fun observeTeam(id: Long): Flow<TeamWithMembers?>

    @Insert
    suspend fun insertTeam(team: TeamEntity): Long

    @Query("DELETE FROM team WHERE id = :id")
    suspend fun deleteTeam(id: Long)

    @Query("UPDATE team SET name = :name, updatedAt = :ts WHERE id = :id")
    suspend fun renameTeam(id: Long, name: String, ts: Long)

    @Query("UPDATE team SET updatedAt = :ts WHERE id = :id")
    suspend fun touchTeam(id: Long, ts: Long)

    @Query("SELECT sortOrder FROM team WHERE id = :id")
    suspend fun sortOrderOf(id: Long): Long?

    @Query("UPDATE team SET sortOrder = :order WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Long)

    /** Swaps two teams' list positions — the same swap-in-place reordering used for team
     * slots and moves elsewhere in the editor, rather than shifting everything in between.
     * Deliberately a read-then-write, not a single `CASE`-based UPDATE: SQLite doesn't
     * guarantee row-processing order within one multi-row UPDATE, so a self-referencing
     * subquery risks reading the *other* row's already-updated value instead of its
     * original one, collapsing both rows onto the same sortOrder instead of swapping. */
    @Transaction
    suspend fun swapSortOrder(id1: Long, id2: Long) {
        val order1 = sortOrderOf(id1) ?: return
        val order2 = sortOrderOf(id2) ?: return
        setSortOrder(id1, order2)
        setSortOrder(id2, order1)
    }

    @Query("DELETE FROM team_member WHERE teamId = :teamId")
    suspend fun clearMembers(teamId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<TeamMemberEntity>)

    @Transaction
    suspend fun replaceMembers(teamId: Long, members: List<TeamMemberEntity>) {
        clearMembers(teamId)
        if (members.isNotEmpty()) insertMembers(members)
    }
}
