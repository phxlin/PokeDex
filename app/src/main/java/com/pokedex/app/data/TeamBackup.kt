package com.pokedex.app.data

import com.pokedex.app.core.PokemonNames
import com.pokedex.app.domain.team.Gender
import com.pokedex.app.domain.team.Nature
import com.pokedex.app.domain.team.StatCalc
import com.pokedex.app.domain.team.StatKey
import com.pokedex.app.domain.team.Team
import com.pokedex.app.domain.team.TeamMember
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * JSON backup format for saved teams. Every field is nullable so a malformed or hand-edited file is
 * rejected with a clear message instead of producing half-initialised objects. Gender, shiny and form
 * are stored as their own fields, not the packed `teraType` / `level` columns Room uses, so the file
 * format doesn't depend on that storage shortcut.
 */
@Serializable
data class BackupFile(
    val app: String? = null,
    val version: Int? = null,
    val exportedAt: String? = null,
    val teams: List<BackupTeam>? = null,
)

@Serializable
data class BackupTeam(
    val name: String? = null,
    val members: List<BackupMember>? = null,
)

@Serializable
data class BackupMember(
    val slot: Int? = null,
    val speciesId: Int? = null,
    val speciesName: String? = null,
    val ability: String? = null,
    val nature: String? = null,
    val item: String? = null,
    val shiny: Boolean? = null,
    val gender: String? = null,
    val formSlug: String? = null,
    /** Stat Points by [StatKey] name, e.g. `"ATK": 32`. */
    val sp: Map<String, Int>? = null,
    val moves: List<String?>? = null,
)

class BackupException(message: String) : Exception(message)

object TeamBackup {
    const val APP_ID = "PokeDex"
    const val VERSION = 1

    private const val MAX_MEMBERS = 6
    private const val MAX_MOVES = 4

    private val writer = Json { prettyPrint = true }
    private val reader = Json { ignoreUnknownKeys = true }

    /** Teams are written in the order given, which is their on-screen order. */
    fun toJson(teams: List<Team>, now: Instant = Instant.now()): String =
        writer.encodeToString(
            BackupFile.serializer(),
            BackupFile(
                app = APP_ID,
                version = VERSION,
                exportedAt = now.toString(),
                teams = teams.map { team ->
                    BackupTeam(
                        name = team.name,
                        members = team.members.sortedBy { it.slot }.map { it.toBackup() },
                    )
                },
            ),
        )

    /** Returns the teams in file order, or throws [BackupException] without side effects. */
    fun parse(json: String): List<Team> {
        val file = try {
            reader.decodeFromString(BackupFile.serializer(), json)
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException, so this covers bad syntax,
            // wrong field types and an empty file alike.
            invalid("This file isn't a valid backup.")
        }

        if (file.app != APP_ID) invalid("This doesn't look like a PokéDex backup.")
        val version = file.version ?: invalid("The backup has no version number.")
        if (version > VERSION) invalid("This backup was made by a newer version of the app.")

        val teams = file.teams ?: invalid("The backup contains no team list.")
        return teams.map { team ->
            Team(
                name = team.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Team",
                members = parseMembers(team.members.orEmpty()),
            )
        }
    }

    private fun parseMembers(members: List<BackupMember>): List<TeamMember> {
        if (members.size > MAX_MEMBERS) invalid("A team in the backup has more than $MAX_MEMBERS Pokémon.")
        val parsed = members.map { it.toDomain() }
        if (parsed.map { it.slot }.toSet().size != parsed.size) {
            invalid("A team in the backup has two Pokémon in the same slot.")
        }
        // The team editor treats a species as unique within a team and rewrites every matching member
        // when it loads move pools, so a duplicate would collapse into one build on the next save.
        if (parsed.map { it.speciesId }.toSet().size != parsed.size) {
            invalid("A team in the backup has the same species more than once.")
        }
        return parsed
    }

    private fun TeamMember.toBackup() = BackupMember(
        slot = slot,
        speciesId = speciesId,
        speciesName = speciesName,
        ability = ability,
        nature = nature.name,
        item = item,
        shiny = shiny,
        gender = gender.name,
        formSlug = formSlug,
        sp = StatKey.entries.associate { it.name to (sp[it] ?: 0) },
        moves = moves,
    )

    private fun BackupMember.toDomain(): TeamMember {
        val slot = slot?.takeIf { it in 0 until MAX_MEMBERS } ?: invalid("A Pokémon has an invalid slot.")
        val speciesId = speciesId?.takeIf { it > 0 } ?: invalid("A Pokémon is missing its species id.")
        val speciesName = speciesName?.trim()?.takeIf { it.isNotEmpty() }
            ?: invalid("A Pokémon is missing its name.")
        val moveList = moves.orEmpty()
        if (moveList.size > MAX_MOVES) invalid("A Pokémon in the backup has more than $MAX_MOVES moves.")

        return TeamMember(
            slot = slot,
            speciesId = speciesId,
            speciesName = speciesName,
            displayName = PokemonNames.displayName(speciesName),
            ability = ability.cleaned(),
            nature = Nature.fromStored(nature.orEmpty()),
            item = item.cleaned(),
            shiny = shiny == true,
            gender = Gender.fromStored(gender),
            formSlug = formSlug.cleaned(),
            sp = parseStatPoints(sp.orEmpty()),
            moves = List(MAX_MOVES) { moveList.getOrNull(it).cleaned() },
        )
    }

    private fun parseStatPoints(raw: Map<String, Int>): Map<StatKey, Int> {
        val byName = StatKey.entries.associateBy { it.name }
        raw.keys.firstOrNull { it !in byName }?.let { invalid("Unknown stat \"$it\" in the backup.") }
        val points = StatKey.entries.associateWith { raw[it.name] ?: 0 }
        if (points.values.any { it !in 0..StatCalc.MAX_SP_PER_STAT } || points.values.sum() > StatCalc.MAX_SP_TOTAL) {
            invalid("A Pokémon in the backup has invalid Stat Points.")
        }
        return points
    }

    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun invalid(message: String): Nothing = throw BackupException(message)
}
