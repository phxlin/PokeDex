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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/** Normalizes free-text move/ability search input to a PokéAPI slug, e.g. "U-Turn " -> "u-turn". */
private fun apiSlug(raw: String) = raw.trim().lowercase().replace(' ', '-')

/** What the single search field in [PokemonPickerSheet] matches against. */
enum class PickerSearchMode(val label: String, val placeholder: String) {
    NAME("Name", "Search name or №"),
    MOVE("Move", "e.g. Flamethrower"),
    ABILITY("Ability", "e.g. Intimidate"),
}

data class PickerControls(
    val searchMode: PickerSearchMode = PickerSearchMode.NAME,
    val searchText: String = "",
    val sort: SortOption = SortOption.DEX_ASC,
    val types: Set<String> = emptySet(),
    val generations: Set<Int> = emptySet(),
) {
    val activeFilterCount: Int get() = types.size + generations.size
}

/** Shape shared by [PokemonPickerViewModel]'s per-mode id caches: slugs already looked up, each mapped to its matching ids. */
private typealias SlugIndex = Map<String, Set<Int>>

@OptIn(kotlinx.coroutines.FlowPreview::class)
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

    /** Resolved id-sets per searched move slug, filled lazily (and debounced) while in [PickerSearchMode.MOVE]. */
    private val moveIds = MutableStateFlow<SlugIndex>(emptyMap())
    private val moveLoading = MutableStateFlow(false)
    val isFilteringByMove: StateFlow<Boolean> = moveLoading.asStateFlow()

    /** Set when the last move search failed (e.g. offline) rather than genuinely finding
     * nothing — a failed lookup is never cached in [moveIds], so it stays distinguishable
     * from a real zero-result search. Cleared as soon as a new search starts. */
    private val moveError = MutableStateFlow(false)
    val hasMoveError: StateFlow<Boolean> = moveError.asStateFlow()

    /** Same as [moveIds]/[moveLoading]/[moveError], for [PickerSearchMode.ABILITY]. */
    private val abilityIds = MutableStateFlow<SlugIndex>(emptyMap())
    private val abilityLoading = MutableStateFlow(false)
    val isFilteringByAbility: StateFlow<Boolean> = abilityLoading.asStateFlow()
    private val abilityError = MutableStateFlow(false)
    val hasAbilityError: StateFlow<Boolean> = abilityError.asStateFlow()

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
        launchSlugSearch(
            mode = PickerSearchMode.MOVE,
            ids = moveIds,
            loading = moveLoading,
            error = moveError,
            fetch = repository::pokemonIdsOfMove,
        )
        launchSlugSearch(
            mode = PickerSearchMode.ABILITY,
            ids = abilityIds,
            loading = abilityLoading,
            error = abilityError,
            fetch = repository::pokemonIdsOfAbility,
        )
    }

    /**
     * Debounced id lookup shared by [PickerSearchMode.MOVE] and [PickerSearchMode.ABILITY]: while
     * [mode] is active, the typed text (as a PokéAPI slug) is resolved to a set of ids via [fetch],
     * cached in [ids] by slug so re-typing an earlier query doesn't re-fetch it, with [loading] and
     * [error] tracking the in-flight lookup the same way for both modes.
     */
    private fun launchSlugSearch(
        mode: PickerSearchMode,
        ids: MutableStateFlow<SlugIndex>,
        loading: MutableStateFlow<Boolean>,
        error: MutableStateFlow<Boolean>,
        fetch: suspend (String) -> Result<Set<Int>>,
    ) {
        viewModelScope.launch {
            controls
                .map { if (it.searchMode == mode) apiSlug(it.searchText) else "" }
                .distinctUntilChanged()
                .debounce(400.milliseconds)
                // collectLatest, not collect: verifying a popular move/ability can mean 200+
                // candidate fetches, and switching to a different query (or search mode) mid-search
                // should cancel that work rather than block behind it.
                .collectLatest { slug ->
                    // Both flags belong to whichever search is currently running — cancelling
                    // one (a new slug arriving, or leaving this mode) must not leave them stuck
                    // from the search that got cancelled, so this early return resets them same
                    // as a completed search would.
                    if (slug.isBlank() || slug in ids.value) {
                        loading.value = false
                        error.value = false
                        return@collectLatest
                    }
                    error.value = false
                    try {
                        loading.value = true
                        fetch(slug)
                            .onSuccess { found -> ids.update { it + (slug to found) } }
                            .onFailure { error.value = true }
                    } finally {
                        // Runs on cancellation too (e.g. collectLatest cancelling this block
                        // for a newer query), so "Checking…" can't get stuck showing forever.
                        loading.value = false
                    }
                }
        }
    }

    val results: StateFlow<List<PokemonSummary>> =
        combine(repository.observePokemonIndex(), controls, typeIds, moveIds, abilityIds) { resource, ctrl, typeMap, moveMap, abilityMap ->
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
            when (ctrl.searchMode) {
                PickerSearchMode.NAME -> {
                    val t = ctrl.searchText.trim().lowercase()
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
                }
                PickerSearchMode.MOVE -> {
                    val slug = apiSlug(ctrl.searchText)
                    if (slug.isNotEmpty()) {
                        // Not yet resolved (still debouncing/loading) -> show nothing rather
                        // than a stale unfiltered list that would suddenly narrow once it lands.
                        seq = seq.filter { it.id in (moveMap[slug] ?: emptySet()) }
                    }
                }
                PickerSearchMode.ABILITY -> {
                    val slug = apiSlug(ctrl.searchText)
                    if (slug.isNotEmpty()) {
                        seq = seq.filter { it.id in (abilityMap[slug] ?: emptySet()) }
                    }
                }
            }
            when (ctrl.sort) {
                SortOption.DEX_ASC -> seq.sortedBy { it.id }
                SortOption.DEX_DESC -> seq.sortedByDescending { it.id }
                SortOption.NAME_ASC -> seq.sortedBy { it.name }
                SortOption.NAME_DESC -> seq.sortedByDescending { it.name }
            }.toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onSearchTextChange(v: String) = controls.update { it.copy(searchText = v) }

    /** Switching modes clears the text — a half-typed name and a move/ability slug aren't interchangeable. */
    fun setSearchMode(mode: PickerSearchMode) = controls.update {
        if (it.searchMode == mode) it else it.copy(searchMode = mode, searchText = "")
    }
    fun setSort(sort: SortOption) = controls.update { it.copy(sort = sort) }
    fun toggleType(type: String) = controls.update {
        it.copy(types = if (type in it.types) it.types - type else it.types + type)
    }
    fun toggleGeneration(n: Int) = controls.update {
        it.copy(generations = if (n in it.generations) it.generations - n else it.generations + n)
    }
    fun clearFilters() = controls.update { it.copy(types = emptySet(), generations = emptySet()) }
}
