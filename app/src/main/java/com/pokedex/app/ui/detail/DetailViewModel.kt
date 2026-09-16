package com.pokedex.app.ui.detail

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokedex.app.core.Resource
import com.pokedex.app.data.repository.PokemonRepository
import com.pokedex.app.domain.model.AbilityDetail
import com.pokedex.app.domain.model.EvolutionChain
import com.pokedex.app.domain.model.FormKind
import com.pokedex.app.domain.model.PokemonDetail
import com.pokedex.app.domain.model.PokemonSpecies
import com.pokedex.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CryStatus { Idle, Loading, Playing, Error }

sealed interface SectionState<out T> {
    data object Loading : SectionState<Nothing>
    data class Success<T>(val value: T) : SectionState<T>
    data class Error(val message: String) : SectionState<Nothing>
}

/** A form tab. Index 0 is always the base form ([kind] == null). */
data class VariantTab(
    val label: String,
    val detail: PokemonDetail,
    val kind: FormKind?,
)

data class DetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: PokemonDetail? = null,
    val species: PokemonSpecies? = null,
    val evolution: SectionState<EvolutionChain>? = null,
    val abilities: Map<String, SectionState<AbilityDetail>> = emptyMap(),
    val expandedAbilities: Set<String> = emptySet(),
    val cry: CryStatus = CryStatus.Idle,
    val banner: String? = null,
    /** Empty or size 1 -> no form tabs shown. */
    val variantTabs: List<VariantTab> = emptyList(),
    val selectedTab: Int = 0,
) {
    /** The detail currently on screen: the selected form, or the plain detail. */
    val displayedDetail: PokemonDetail? get() = variantTabs.getOrNull(selectedTab)?.detail ?: detail

    /** Base-form detail for stat comparison; null when the base tab is selected. */
    val comparisonBase: PokemonDetail? get() =
        if (selectedTab > 0) variantTabs.firstOrNull()?.detail else null

    /** Base form for the header (dex number / genus stay species-level). */
    val headerDetail: PokemonDetail? get() = variantTabs.firstOrNull()?.detail ?: detail
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: PokemonRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val idOrName: String = savedStateHandle.get<String>(Routes.ARG_ID_OR_NAME).orEmpty()

    private val _state = MutableStateFlow(
        DetailUiState(banner = savedStateHandle.get<String>(Routes.ARG_BANNER)),
    )
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    fun dismissBanner() {
        _state.update { it.copy(banner = null) }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var evolutionLoadedForChain: Int? = null
    private var variantsLoadedForSpecies: Int? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            repository.observePokemonDetail(idOrName).collect { resource ->
                _state.update { current ->
                    current.copy(
                        isLoading = resource is Resource.Loading && resource.data == null,
                        error = (resource as? Resource.Error)?.message?.takeIf { resource.data == null },
                        detail = resource.data?.detail ?: current.detail,
                        species = resource.data?.species ?: current.species,
                    )
                }
                val chainId = resource.data?.species?.evolutionChainId
                if (chainId != null && chainId != evolutionLoadedForChain) {
                    evolutionLoadedForChain = chainId
                    loadEvolution(chainId)
                }
                val speciesId = resource.data?.species?.id
                if (speciesId != null && speciesId != variantsLoadedForSpecies) {
                    variantsLoadedForSpecies = speciesId
                    loadVariants(speciesId)
                }
            }
        }
    }

    private fun loadVariants(speciesId: Int) {
        viewModelScope.launch {
            repository.getFormsBundle(speciesId).onSuccess { bundle ->
                if (bundle == null) return@onSuccess
                val tabs = buildList {
                    add(VariantTab("Base", bundle.base, null))
                    bundle.alternates.forEach { add(VariantTab(it.tabLabel, it.detail, it.kind)) }
                }
                val openedName = _state.value.detail?.name ?: idOrName.lowercase()
                val preselect = tabs.indexOfFirst { it.detail.name == openedName }.coerceAtLeast(0)
                _state.update { it.copy(variantTabs = tabs, selectedTab = preselect) }
            }
        }
    }

    fun selectTab(index: Int) {
        if (index == _state.value.selectedTab) return
        releasePlayer()
        _state.update { it.copy(selectedTab = index, cry = CryStatus.Idle) }
    }

    private fun loadEvolution(chainId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(evolution = SectionState.Loading) }
            repository.getEvolutionChain(chainId)
                .onSuccess { chain -> _state.update { it.copy(evolution = SectionState.Success(chain)) } }
                .onFailure { e -> _state.update { it.copy(evolution = SectionState.Error(e.message ?: "Couldn't load evolutions")) } }
        }
    }

    fun toggleAbility(name: String) {
        val expanded = _state.value.expandedAbilities
        val nowExpanded = if (name in expanded) expanded - name else expanded + name
        _state.update { it.copy(expandedAbilities = nowExpanded) }

        if (name !in expanded && _state.value.abilities[name] !is SectionState.Success) {
            viewModelScope.launch {
                _state.update { it.copy(abilities = it.abilities + (name to SectionState.Loading)) }
                repository.getAbility(name)
                    .onSuccess { a -> _state.update { it.copy(abilities = it.abilities + (name to SectionState.Success(a))) } }
                    .onFailure { e ->
                        _state.update {
                            it.copy(abilities = it.abilities + (name to SectionState.Error(e.message ?: "Couldn't load ability")))
                        }
                    }
            }
        }
    }

    fun playCry() {
        val url = _state.value.displayedDetail?.cry?.bestUrl ?: run {
            _state.update { it.copy(cry = CryStatus.Error) }
            return
        }
        releasePlayer()
        _state.update { it.copy(cry = CryStatus.Loading) }
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            setOnPreparedListener {
                _state.update { s -> s.copy(cry = CryStatus.Playing) }
                it.start()
            }
            setOnCompletionListener {
                _state.update { s -> s.copy(cry = CryStatus.Idle) }
                releasePlayer()
            }
            setOnErrorListener { _, _, _ ->
                _state.update { s -> s.copy(cry = CryStatus.Error) }
                releasePlayer()
                true
            }
            runCatching {
                setDataSource(url)
                prepareAsync()
            }.onFailure {
                _state.update { s -> s.copy(cry = CryStatus.Error) }
                releasePlayer()
            }
        }
    }

    private fun releasePlayer() {
        mediaPlayer?.runCatching { reset(); release() }
        mediaPlayer = null
    }

    override fun onCleared() {
        releasePlayer()
    }
}
