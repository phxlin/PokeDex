@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pokedex.app.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.request.ImageRequest
import com.pokedex.app.domain.model.PokemonSummary
import com.pokedex.app.ui.components.DexActionButton
import com.pokedex.app.ui.components.PokedexHeader
import com.pokedex.app.ui.components.ScreenMessage
import com.pokedex.app.ui.components.ScreenSurface
import com.pokedex.app.ui.components.SpriteImage
import com.pokedex.app.ui.theme.DexCard
import com.pokedex.app.ui.theme.DexCardBorder
import com.pokedex.app.ui.theme.DexCardInk
import com.pokedex.app.ui.theme.DexCardWell
import com.pokedex.app.ui.theme.DexRed
import com.pokedex.app.ui.theme.DexScreenBezel
import kotlinx.coroutines.launch

@Composable
fun PokemonListScreen(
    onPokemonClick: (idOrName: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PokemonListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PokedexHeader(
            title = "Pokédex",
            subtitle = headerSubtitle(state),
            actions = {
                DexActionButton(
                    onClick = { showFilters = true },
                    contentDescription = "Filter and sort",
                    badge = state.controls.hasActiveFilters,
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = DexRed)
                }
            },
        )

        SearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )

        if (state.controls.hasActiveFilters || state.filterError != null) {
            ActiveFilters(
                state = state,
                onRemoveType = viewModel::toggleType,
                onRemoveGeneration = viewModel::toggleGeneration,
                onClear = viewModel::clearFilters,
            )
        }

        ScreenSurface(Modifier.fillMaxSize()) {
            if (state.isFiltering) {
                LinearProgressIndicator(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                )
            }
            when {
                state.isLoading -> ScreenLoading()
                state.error != null -> ScreenError(state.error!!, viewModel::retry)
                state.isEmptyResult -> ScreenMessage("No Pokémon match your search and filters.")
                else -> PokemonGrid(items = state.visible, onPokemonClick = onPokemonClick)
            }
        }
    }

    if (showFilters) {
        FilterSortSheet(
            controls = state.controls,
            onDismiss = { showFilters = false },
            onSort = viewModel::setSort,
            onToggleType = viewModel::toggleType,
            onToggleGeneration = viewModel::toggleGeneration,
            onClear = viewModel::clearFilters,
        )
    }
}

private fun headerSubtitle(state: PokemonListUiState): String = when {
    state.isLoading -> "Booting…"
    state.all.isEmpty() -> "Offline"
    state.controls.query.isNotBlank() || state.controls.hasActiveFilters ->
        "${state.visible.size} of ${state.all.size}"
    else -> "${state.all.size} entries"
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = DexScreenBezel,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_field"),
            singleLine = true,
            placeholder = { Text("Search name or №", color = Color.White.copy(alpha = 0.5f)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = Color.White.copy(alpha = 0.7f))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = DexRed,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun ActiveFilters(
    state: PokemonListUiState,
    onRemoveType: (String) -> Unit,
    onRemoveGeneration: (Int) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
        FilterChipsRow(
            types = state.controls.types,
            generations = state.controls.generations,
            onRemoveType = onRemoveType,
            onRemoveGeneration = onRemoveGeneration,
            onClear = onClear,
        )
        state.filterError?.let {
            Text(
                it,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun PokemonGrid(items: List<PokemonSummary>, onPokemonClick: (String) -> Unit) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val showJump by remember { derivedStateOf { gridState.firstVisibleItemIndex > 8 } }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 158.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize().testTag("pokemon_grid"),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { pokemon ->
                PokemonCard(pokemon = pokemon, onClick = { onPokemonClick(pokemon.name) })
            }
        }

        AnimatedVisibility(
            visible = showJump,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .navigationBarsPadding(),
        ) {
            SmallFloatingActionButton(
                onClick = { scope.launch { gridState.animateScrollToItem(0) } },
                containerColor = DexRed,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
            }
        }
    }
}

@Composable
private fun PokemonCard(pokemon: PokemonSummary, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = DexCard,
        contentColor = DexCardInk,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, DexCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pokemon_card_${pokemon.name}"),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = DexRed,
                contentColor = Color.White,
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.Start),
            ) {
                Text(
                    pokemon.dexLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Box(
                Modifier
                    .padding(vertical = 6.dp)
                    .size(104.dp)
                    .background(DexCardWell, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                SpriteImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(pokemon.spriteUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = pokemon.displayName,
                    size = 88.dp,
                    spinnerColor = DexRed.copy(alpha = 0.7f),
                )
            }
            Text(
                pokemon.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ScreenLoading() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.size(12.dp))
            Text(
                "Loading the National Dex…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ScreenError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.size(12.dp))
            TextButton(onClick = onRetry) { Text("Retry", color = MaterialTheme.colorScheme.primary) }
        }
    }
}
