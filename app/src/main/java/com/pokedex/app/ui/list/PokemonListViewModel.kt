package com.pokedex.app.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokedex.app.core.Resource
import com.pokedex.app.data.repository.PokemonRepository
import com.pokedex.app.domain.model.PokemonSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PokemonListUiState(
    val controls: ListControls = ListControls(),
    val all: List<PokemonSummary> = emptyList(),
    val visible: List<PokemonSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isFiltering: Boolean = false,
    val error: String? = null,
    val filterError: String? = null,
) {
    val query: String get() = controls.query
    val isEmptyResult: Boolean
        get() = !isLoading && error == null && visible.isEmpty() && all.isNotEmpty()
}

@HiltViewModel
class PokemonListViewModel @Inject constructor(
    private val repository: PokemonRepository,
) : ViewModel() {

    private val controls = MutableStateFlow(ListControls())
    private val refreshing = MutableStateFlow(false)

    /** Resolved id-sets per type, filled lazily as types are selected. */
    private val typeIds = MutableStateFlow<Map<String, Set<Int>>>(emptyMap())
    private val typeLoading = MutableStateFlow(false)
    private val typeError = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            controls.map { it.types }.distinctUntilChanged().collect { selected ->
                val missing = selected - typeIds.value.keys
                if (missing.isEmpty()) {
                    typeError.value = null
                    return@collect
                }
                typeLoading.value = true
                typeError.value = null
                var anyFailed = false
                missing.forEach { type ->
                    repository.pokemonIdsOfType(type)
                        .onSuccess { ids -> typeIds.update { it + (type to ids) } }
                        .onFailure { anyFailed = true }
                }
                typeLoading.value = false
                if (anyFailed) typeError.value = "Couldn't load the type filter — check your connection."
            }
        }
    }

    val uiState: StateFlow<PokemonListUiState> =
        combine(
            repository.observePokemonIndex(),
            controls,
            typeIds,
            refreshing,
            combine(typeLoading, typeError) { loading, error -> loading to error },
        ) { resource, ctrl, typeMap, isRefreshing, (typeLoad, typeErr) ->
            val all = resource.data ?: emptyList()
            PokemonListUiState(
                controls = ctrl,
                all = all,
                visible = applyFilters(all, ctrl, typeMap),
                isLoading = resource is Resource.Loading && all.isEmpty(),
                isRefreshing = isRefreshing,
                isFiltering = typeLoad,
                error = (resource as? Resource.Error)?.message?.takeIf { all.isEmpty() },
                filterError = typeErr,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PokemonListUiState(),
        )

    fun onQueryChange(value: String) = controls.update { it.copy(query = value) }

    fun setSort(sort: SortOption) = controls.update { it.copy(sort = sort) }

    fun toggleType(type: String) = controls.update {
        it.copy(types = if (type in it.types) it.types - type else it.types + type)
    }

    fun toggleGeneration(number: Int) = controls.update {
        it.copy(generations = if (number in it.generations) it.generations - number else it.generations + number)
    }

    fun clearFilters() = controls.update { it.copy(types = emptySet(), generations = emptySet()) }

    fun retry() {
        viewModelScope.launch {
            refreshing.value = true
            repository.refreshIndex()
            // Retry any type filters that failed.
            val pending = controls.value.types - typeIds.value.keys
            if (pending.isNotEmpty()) {
                typeLoading.value = true
                typeError.value = null
                var anyFailed = false
                pending.forEach { type ->
                    repository.pokemonIdsOfType(type)
                        .onSuccess { ids -> typeIds.update { it + (type to ids) } }
                        .onFailure { anyFailed = true }
                }
                typeLoading.value = false
                if (anyFailed) typeError.value = "Couldn't load the type filter — check your connection."
            }
            refreshing.value = false
        }
    }

    private fun applyFilters(
        all: List<PokemonSummary>,
        ctrl: ListControls,
        typeMap: Map<String, Set<Int>>,
    ): List<PokemonSummary> {
        var seq = all.asSequence()

        if (ctrl.generations.isNotEmpty()) {
            seq = seq.filter { p ->
                ctrl.generations.any { g -> Generation.ofNumber(g)?.range?.contains(p.id) == true }
            }
        }

        if (ctrl.types.isNotEmpty()) {
            val resolved = ctrl.types.mapNotNull { typeMap[it] }
            if (resolved.isNotEmpty()) {
                val union = HashSet<Int>().apply { resolved.forEach { addAll(it) } }
                seq = seq.filter { it.id in union }
            }
        }

        val q = ctrl.query.trim().lowercase()
        if (q.isNotEmpty()) {
            val digits = q.trimStart('#', '0')
            val asNumber = digits.toIntOrNull()
            seq = seq.filter { p ->
                p.name.contains(q) ||
                    p.displayName.lowercase().contains(q) ||
                    p.id.toString() == digits ||
                    (asNumber != null && p.id == asNumber) ||
                    p.id.toString().padStart(4, '0').contains(q.trimStart('#'))
            }
        }

        return when (ctrl.sort) {
            SortOption.DEX_ASC -> seq.sortedBy { it.id }
            SortOption.DEX_DESC -> seq.sortedByDescending { it.id }
            SortOption.NAME_ASC -> seq.sortedBy { it.name }
            SortOption.NAME_DESC -> seq.sortedByDescending { it.name }
        }.toList()
    }
}
