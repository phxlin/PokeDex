package com.pokedex.app.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokedex.app.data.repository.PokemonRepository
import com.pokedex.app.data.repository.TeamRepository
import com.pokedex.app.domain.team.Team
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TeamListViewModel @Inject constructor(
    private val repository: TeamRepository,
    private val pokeRepo: PokemonRepository,
) : ViewModel() {

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
}

fun formSpriteKey(slug: String, shiny: Boolean): String = "$slug|$shiny"
