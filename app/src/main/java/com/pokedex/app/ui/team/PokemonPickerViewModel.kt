package com.pokedex.app.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokedex.app.data.repository.PokemonRepository
import com.pokedex.app.domain.model.PokemonSummary
import com.pokedex.app.ui.list.Generation
import com.pokedex.app.ui.list.SortOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PickerControls(
    val query: String = "",
    val sort: SortOption = SortOption.DEX_ASC,
    val types: Set<String> = emptySet(),
    val generations: Set<Int> = emptySet(),
) {
    val activeFilterCount: Int get() = types.size + generations.size
}

@HiltViewModel
class PokemonPickerViewModel @Inject constructor(
    private val repository: PokemonRepository,
) : ViewModel() {

    private val controls = MutableStateFlow(PickerControls())
    val currentControls: StateFlow<PickerControls> = controls.asStateFlow()

    /** Resolved id-sets per type, filled lazily as types are selected. */
    private val typeIds = MutableStateFlow<Map<String, Set<Int>>>(emptyMap())
    private val typeLoading = MutableStateFlow(false)
    val isFiltering: StateFlow<Boolean> = typeLoading.asStateFlow()

    init {
        viewModelScope.launch {
            controls.map { it.types }.distinctUntilChanged().collect { selected ->
                val missing = selected - typeIds.value.keys
                if (missing.isEmpty()) return@collect
                typeLoading.value = true
                missing.forEach { type ->
                    repository.pokemonIdsOfType(type).onSuccess { ids ->
                        typeIds.update { it + (type to ids) }
                    }
                }
                typeLoading.value = false
            }
        }
    }

    val results: StateFlow<List<PokemonSummary>> =
        combine(repository.observePokemonIndex(), controls, typeIds) { resource, ctrl, typeMap ->
            val all = resource.data ?: emptyList()
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
            val t = ctrl.query.trim().lowercase()
            if (t.isNotEmpty()) {
                val digits = t.trimStart('#', '0')
                val asNumber = digits.toIntOrNull()
                seq = seq.filter { p ->
                    p.name.contains(t) ||
                        p.displayName.lowercase().contains(t) ||
                        p.id.toString() == digits ||
                        (asNumber != null && p.id == asNumber)
                }
            }
            when (ctrl.sort) {
                SortOption.DEX_ASC -> seq.sortedBy { it.id }
                SortOption.DEX_DESC -> seq.sortedByDescending { it.id }
                SortOption.NAME_ASC -> seq.sortedBy { it.name }
                SortOption.NAME_DESC -> seq.sortedByDescending { it.name }
            }.toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(v: String) = controls.update { it.copy(query = v) }
    fun setSort(sort: SortOption) = controls.update { it.copy(sort = sort) }
    fun toggleType(type: String) = controls.update {
        it.copy(types = if (type in it.types) it.types - type else it.types + type)
    }
    fun toggleGeneration(n: Int) = controls.update {
        it.copy(generations = if (n in it.generations) it.generations - n else it.generations + n)
    }
    fun clearFilters() = controls.update { it.copy(types = emptySet(), generations = emptySet()) }
}
