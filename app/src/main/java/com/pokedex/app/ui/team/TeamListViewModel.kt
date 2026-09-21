package com.pokedex.app.ui.team

import android.content.ContentResolver
import android.database.SQLException
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokedex.app.data.BackupException
import com.pokedex.app.data.TeamBackup
import com.pokedex.app.data.repository.PokemonRepository
import com.pokedex.app.data.repository.TeamRepository
import com.pokedex.app.domain.team.Team
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class TeamListViewModel @Inject constructor(
    private val repository: TeamRepository,
    private val pokeRepo: PokemonRepository,
    private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)

    /** One-shot result of the last backup export/import, for a snackbar. Cleared by [messageShown]. */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun messageShown() {
        _message.value = null
    }

    /** "<form-slug>|<shiny>" -> that form's front sprite URL, resolved lazily for forms shown on the list. */
    private val _formSprites = MutableStateFlow<Map<String, String>>(emptyMap())
    val formSprites: StateFlow<Map<String, String>> = _formSprites.asStateFlow()

    val teams: StateFlow<List<Team>> = repository.observeTeams()
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            teams.collect { list ->
                // (form slug, shiny) pairs we need a sprite for.
                val wanted = buildSet {
                    list.flatMap { it.members }.forEach { m ->
                        m.formSlug?.let { add(it to m.shiny) }
                        com.pokedex.app.domain.team.megaPokemonSlugFor(m.item)?.let { add(it to m.shiny) }
                    }
                }
                wanted.map { (slug, shiny) -> formSpriteKey(slug, shiny) to (slug to shiny) }
                    .filterNot { _formSprites.value.containsKey(it.first) }
                    .forEach { (key, pair) ->
                        val (slug, shiny) = pair
                        launch {
                            // Empty string marks "tried"; a real URL overwrites it if the fetch succeeds.
                            _formSprites.update { if (it.containsKey(key)) it else it + (key to "") }
                            pokeRepo.getPokemon(slug).getOrNull()?.let { d ->
                                val url = if (shiny) d.sprites.frontShiny ?: d.sprites.frontDefault
                                else d.sprites.frontDefault
                                url?.let { u -> _formSprites.update { m -> m + (key to u) } }
                            }
                        }
                    }
            }
        }
    }

    fun createTeam(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.createTeam("Team ${teams.value.size + 1}")
            onCreated(id)
        }
    }

    fun deleteTeam(id: Long) {
        viewModelScope.launch { repository.deleteTeam(id) }
    }

    /** Deletes every saved team. The screen asks the user to type DELETE before calling this. */
    fun deleteAllTeams() {
        viewModelScope.launch {
            _message.value = try {
                repository.deleteAllTeams()
                "All teams deleted."
            } catch (e: SQLException) {
                "Couldn't delete your teams."
            }
        }
    }

    /** Swaps two teams' positions in the list — called as a drag lands on a new slot. */
    fun swapTeams(id1: Long, id2: Long) {
        if (id1 == id2) return
        viewModelScope.launch { repository.swapTeams(id1, id2) }
    }

    /** Writes every saved team as a backup file to [uri] (from the system "create document" picker). */
    fun exportBackup(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _message.value = try {
                val json = repository.exportBackup()
                withContext(io) {
                    resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: throw IOException("Couldn't open the file")
                }
                "Backup saved."
            } catch (e: IOException) {
                "Couldn't save the backup."
            }
        }
    }

    /** Replaces every saved team with the backup file at [uri]. An invalid file changes nothing. */
    fun importBackup(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _message.value = try {
                val json = withContext(io) {
                    resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use(TeamBackup::readBounded)
                        ?: throw IOException("Couldn't open the file")
                }
                repository.importBackup(json)
                "Backup restored."
            } catch (e: BackupException) {
                "Not restored: ${e.message}"
            } catch (e: IOException) {
                "Couldn't read that file."
            } catch (e: SQLException) {
                // The whole replace runs in one transaction, so the old teams are still there.
                "Couldn't restore the backup."
            }
        }
    }
}

fun formSpriteKey(slug: String, shiny: Boolean): String = "$slug|$shiny"
