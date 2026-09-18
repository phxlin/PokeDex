package com.pokedex.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "team")
data class TeamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val updatedAt: Long,
    /** User-controlled position in the Teams list (ascending), set by long-press drag reordering. */
    val sortOrder: Long = 0,
)

@Entity(
    tableName = "team_member",
    foreignKeys = [
        ForeignKey(
            entity = TeamEntity::class,
            parentColumns = ["id"],
            childColumns = ["teamId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("teamId")],
)
data class TeamMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val teamId: Long,
    val slot: Int,
    val speciesId: Int,
    val speciesName: String,
    val ability: String?,
    val nature: String,
    val teraType: String,
    val item: String?,
    val level: Int,
    val evHp: Int,
    val evAtk: Int,
    val evDef: Int,
    val evSpa: Int,
    val evSpd: Int,
    val evSpe: Int,
    val move1: String?,
    val move2: String?,
    val move3: String?,
    val move4: String?,
)

data class TeamWithMembers(
    @Embedded val team: TeamEntity,
    @Relation(parentColumn = "id", entityColumn = "teamId")
    val members: List<TeamMemberEntity>,
)
