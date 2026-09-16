package com.pokedex.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamDao {

    @Transaction
    @Query("SELECT * FROM team ORDER BY updatedAt DESC")
    fun observeTeams(): Flow<List<TeamWithMembers>>

    @Transaction
    @Query("SELECT * FROM team WHERE id = :id LIMIT 1")
    fun observeTeam(id: Long): Flow<TeamWithMembers?>

    @Insert
    suspend fun insertTeam(team: TeamEntity): Long

    @Update
    suspend fun updateTeam(team: TeamEntity)

    @Query("DELETE FROM team WHERE id = :id")
    suspend fun deleteTeam(id: Long)

    @Query("UPDATE team SET name = :name, updatedAt = :ts WHERE id = :id")
    suspend fun renameTeam(id: Long, name: String, ts: Long)

    @Query("UPDATE team SET updatedAt = :ts WHERE id = :id")
    suspend fun touchTeam(id: Long, ts: Long)

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
