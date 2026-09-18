@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.pokedex.app.ui.team

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pokedex.app.core.PokemonNames
import com.pokedex.app.domain.model.PokemonSummary
import com.pokedex.app.domain.team.ChampionsLegal
import com.pokedex.app.domain.team.HELD_ITEMS
import com.pokedex.app.domain.team.HeldItem
import com.pokedex.app.domain.team.ItemCategory
import com.pokedex.app.domain.team.MoveInfo
import com.pokedex.app.domain.team.Nature
import com.pokedex.app.domain.team.Team
import com.pokedex.app.domain.team.TeamMember
import com.pokedex.app.domain.team.heldItemDisplayName
import com.pokedex.app.domain.team.heldItemFallbackGlyph
import com.pokedex.app.ui.components.POKEMON_TYPES
import com.pokedex.app.ui.components.SelectableTypeChip
import com.pokedex.app.ui.components.TypeChip
import com.pokedex.app.ui.list.Generation
import com.pokedex.app.ui.list.SortOption
import com.pokedex.app.ui.theme.DexStatDown
import com.pokedex.app.ui.theme.DexStatUp

private val SheetBg = Color.White
private val SheetInk = Color(0xFF15323C)
private val SheetDim = Color(0xFF5C7A87)
private val SheetField = Color(0xFFEFF3F5)

@Composable
private fun PickerSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetBg,
        contentColor = SheetInk,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SheetInk,
                modifier = Modifier.padding(16.dp),
            )
            content()
        }
    }
}

@Composable
private fun SheetSearchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder, color = SheetDim) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = SheetDim) },
        singleLine = true,
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SheetField,
            unfocusedContainerColor = SheetField,
            focusedTextColor = SheetInk,
            unfocusedTextColor = SheetInk,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun ClearRow(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Text(
            text,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

// ---- shared filter / sort controls -----------------------------------------

@Composable
private fun SheetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else SheetField,
        contentColor = if (selected) Color.White else SheetInk,
        shape = RoundedCornerShape(50),
        border = if (selected) null else BorderStroke(1.dp, SheetDim.copy(alpha = 0.35f)),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/** A horizontally-scrolling row of mutually-exclusive sort options. */
@Composable
private fun <T> SheetSortRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("SORT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SheetDim)
        options.forEach { (label, value) ->
            SheetChip(label, selected == value) { onSelect(value) }
        }
    }
}

/** A collapsible "Filter" section. [activeCount] shows a badge; [content] holds the chips. */
@Composable
private fun SheetFilters(
    activeCount: Int,
    onClear: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(onClick = { expanded = !expanded }, color = Color.Transparent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = SheetInk,
                    )
                    Text(
                        if (activeCount > 0) "Filter · $activeCount" else "Filter",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = SheetInk,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (activeCount > 0) {
                Surface(onClick = onClear, color = Color.Transparent) {
                    Text(
                        "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
        }
        if (expanded) {
            // Capped + independently scrollable: without this, a filter section with many
            // groups (Generation + Type + …) can grow tall enough to push the results list
            // — and even the count text right below this — off the bottom of the sheet with
            // no way to reach them, since the sheet's outer content isn't itself scrollable.
            Surface(color = SheetField, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun FilterGroup(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SheetDim)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

// ---- Pokémon --------------------------------------------------------------

private val POKEMON_SORTS = listOf(
    "Lowest №" to SortOption.DEX_ASC,
    "Highest №" to SortOption.DEX_DESC,
    "A → Z" to SortOption.NAME_ASC,
    "Z → A" to SortOption.NAME_DESC,
)

/** The "copy from another team" entry row shown atop [PokemonPickerSheet]. */
@Composable
private fun BrowseExistingRow(onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔁", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(10.dp))
            Text(
                "Copy an already-built Pokémon from another team",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = SheetInk,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = SheetDim)
        }
    }
    HorizontalDivider()
}

/** Mutually-exclusive "Name" / "Move" toggle for [PokemonPickerSheet]'s single search field. */
@Composable
private fun SearchModeToggle(mode: PickerSearchMode, onSelect: (PickerSearchMode) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PickerSearchMode.entries.forEach { m ->
            SheetChip(m.label, m == mode) { onSelect(m) }
        }
    }
}

@Composable
fun PokemonPickerSheet(
    onDismiss: () -> Unit,
    onPick: (id: Int, name: String) -> Unit,
    onBrowseExisting: (() -> Unit)? = null,
    viewModel: PokemonPickerViewModel = hiltViewModel(),
) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    val controls by viewModel.currentControls.collectAsStateWithLifecycle()
    val filtering by viewModel.isFiltering.collectAsStateWithLifecycle()
    val filteringByMove by viewModel.isFilteringByMove.collectAsStateWithLifecycle()
    val hasMoveError by viewModel.hasMoveError.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(controls.sort, controls.types, controls.generations, controls.searchMode, controls.searchText) {
        listState.scrollToItem(0)
    }

    PickerSheet("Choose a Pokémon", onDismiss) {
        onBrowseExisting?.let { BrowseExistingRow(it) }
        SearchModeToggle(controls.searchMode, viewModel::setSearchMode)
        Spacer(Modifier.size(8.dp))
        SheetSearchField(controls.searchText, viewModel::onSearchTextChange, controls.searchMode.placeholder)
        if (controls.searchMode == PickerSearchMode.MOVE) {
            Text(
                "Only Pokémon that can actually learn it under ${ChampionsLegal.REGULATION}.",
                style = MaterialTheme.typography.labelSmall,
                color = SheetDim,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        SheetSortRow(POKEMON_SORTS, controls.sort, viewModel::setSort)
        Spacer(Modifier.size(6.dp))
        SheetFilters(controls.activeFilterCount, viewModel::clearFilters) {
            FilterGroup("Generation") {
                Generation.entries.forEach { gen ->
                    SheetChip("${gen.label} · ${gen.region}", gen.number in controls.generations) {
                        viewModel.toggleGeneration(gen.number)
                    }
                }
            }
            FilterGroup("Type") {
                POKEMON_TYPES.forEach { type ->
                    SelectableTypeChip(type, type in controls.types, onClick = { viewModel.toggleType(type) })
                }
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(
            when {
                filtering -> "Loading type filter…"
                filteringByMove -> "Checking which Pokémon can learn that move…"
                hasMoveError -> "Couldn't check that move — check your connection and try again."
                else -> "${results.size} Pokémon"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (hasMoveError && !filteringByMove) MaterialTheme.colorScheme.error else SheetDim,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Text(
            "Greyed-out Pokémon aren't legal in ${ChampionsLegal.REGULATION}.",
            style = MaterialTheme.typography.labelSmall,
            color = SheetDim,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.size(4.dp))
        HorizontalDivider()
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = 420.dp)) {
            items(results, key = { it.id }) { p ->
                PokemonResultRow(p, onClick = { onPick(p.id, p.name); onDismiss() })
            }
        }
    }
}

@Composable
private fun PokemonResultRow(p: PokemonSummary, onClick: () -> Unit) {
    val legal = ChampionsLegal.isLegalSpecies(p.name)
    Surface(onClick = { if (legal) onClick() }, color = Color.Transparent) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = p.spriteUrl,
                contentDescription = null,
                alpha = if (legal) 1f else 0.4f,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.size(12.dp))
            Text(p.displayName, style = MaterialTheme.typography.bodyLarge, color = if (legal) SheetInk else SheetDim)
            if (!legal) {
                Spacer(Modifier.size(6.dp))
                Text("🚫", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.weight(1f))
            Text(p.dexLabel, style = MaterialTheme.typography.labelMedium, color = SheetDim)
        }
    }
}

// ---- Reuse a build from another team ---------------------------------------

/**
 * Lets the user copy an already-configured Pokémon from one of their other saved
 * teams into this one, as an independent starting point (see [TeamEditorViewModel.addFromMember]).
 */
@Composable
fun ReuseMemberPickerSheet(
    teams: List<Team>,
    excludeSpeciesIds: Set<Int>,
    onDismiss: () -> Unit,
    onPick: (TeamMember) -> Unit,
) {
    val entries = remember(teams) { teams.flatMap { t -> t.members.map { m -> t.name to m } } }

    PickerSheet("Copy from another team", onDismiss) {
        Text(
            "Copies its item, moves, ability and Stat Points as a starting point — " +
                "editing it here won't change the team it came from.",
            style = MaterialTheme.typography.labelSmall,
            color = SheetDim,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.size(8.dp))
        HorizontalDivider()
        if (entries.isEmpty()) {
            Text(
                "None of your other teams have a Pokémon built yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = SheetDim,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(entries, key = { (teamName, m) -> "$teamName/${m.slot}/${m.speciesId}" }) { (teamName, m) ->
                    val dupe = m.speciesId in excludeSpeciesIds
                    Surface(
                        onClick = { if (!dupe) { onPick(m); onDismiss() } },
                        color = Color.Transparent,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = m.spriteUrl,
                                contentDescription = null,
                                alpha = if (dupe) 0.4f else 1f,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    PokemonSummary(m.speciesId, m.speciesName).displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (dupe) SheetDim else SheetInk,
                                )
                                val itemLabel = heldItemDisplayName(m.item)
                                val moveCount = m.moves.count { !it.isNullOrBlank() }
                                Text(
                                    buildString {
                                        append("From \"$teamName\"")
                                        itemLabel?.let { append(" · $it") }
                                        if (moveCount > 0) append(" · $moveCount move${if (moveCount == 1) "" else "s"}")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SheetDim,
                                )
                            }
                            if (dupe) {
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    "Already on this team",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SheetDim,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Moves ---------------------------------------------------------------

private enum class MoveSort(val label: String) { NAME("A → Z"), POWER("Power"), ACCURACY("Accuracy") }

@Composable
fun MovePickerSheet(
    pool: List<String>,
    moveInfo: Map<String, MoveInfo>,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(MoveSort.NAME) }
    var types by remember { mutableStateOf(emptySet<String>()) }
    var classes by remember { mutableStateOf(emptySet<String>()) }
    val listState = rememberLazyListState()
    LaunchedEffect(sort, types, classes, query) { listState.scrollToItem(0) }

    val infoReady = pool.count { moveInfo.containsKey(it) }
    val filtered = remember(query, pool, moveInfo, sort, types, classes) {
        val t = query.trim().lowercase().replace(' ', '-')
        pool.asSequence()
            .filter { t.isEmpty() || it.contains(t) }
            .filter { m ->
                val info = moveInfo[m]
                (types.isEmpty() || info?.type in types) &&
                    (classes.isEmpty() || info?.damageClass in classes)
            }
            .sortedWith(
                when (sort) {
                    MoveSort.NAME -> compareBy { it }
                    MoveSort.POWER -> compareByDescending { moveInfo[it]?.power ?: -1 }
                    MoveSort.ACCURACY -> compareByDescending { moveInfo[it]?.accuracy ?: -1 }
                },
            )
            .toList()
    }

    PickerSheet("Choose a move", onDismiss) {
        SheetSearchField(query, { query = it }, "Search moves")
        Spacer(Modifier.size(10.dp))
        SheetSortRow(MoveSort.entries.map { it.label to it }, sort, { sort = it })
        Spacer(Modifier.size(6.dp))
        SheetFilters(types.size + classes.size, { types = emptySet(); classes = emptySet() }) {
            FilterGroup("Category") {
                listOf("physical" to "Physical", "special" to "Special", "status" to "Status").forEach { (slug, label) ->
                    SheetChip(label, slug in classes) {
                        classes = if (slug in classes) classes - slug else classes + slug
                    }
                }
            }
            FilterGroup("Type") {
                POKEMON_TYPES.forEach { type ->
                    SelectableTypeChip(
                        type,
                        type in types,
                        onClick = { types = if (type in types) types - type else types + type },
                    )
                }
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(
            if (infoReady < pool.size) "Loading move details… ($infoReady/${pool.size})"
            else "${filtered.size} moves — Champions restores every move a Pokémon can learn across games",
            style = MaterialTheme.typography.labelSmall,
            color = SheetDim,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        ClearRow("— Clear this slot —", onClick = { onPick(null); onDismiss() })
        HorizontalDivider()
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = 400.dp)) {
            items(filtered, key = { it }) { move ->
                val info = moveInfo[move]
                Surface(
                    onClick = { onPick(move); onDismiss() },
                    color = Color.Transparent,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                PokemonNames.displayName(move),
                                style = MaterialTheme.typography.bodyLarge,
                                color = SheetInk,
                            )
                            if (info != null) {
                                Text(
                                    buildString {
                                        append(info.damageClass.replaceFirstChar { it.uppercase() })
                                        info.power?.let { append("  ·  $it BP") }
                                        info.accuracy?.let { append("  ·  $it%") }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SheetDim,
                                )
                                info.shortEffect?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = SheetDim, maxLines = 2)
                                }
                            }
                        }
                        if (info != null) TypeChip(info.type)
                    }
                }
            }
        }
    }
}

// ---- Items --------------------------------------------------------------

private enum class ItemSort(val label: String) { CURATED("Suggested"), NAME_ASC("A → Z"), NAME_DESC("Z → A") }

@Composable
fun ItemPickerSheet(
    species: String?,
    itemInfo: Map<String, com.pokedex.app.domain.team.ItemInfo>,
    onNeedInfo: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ItemSort.CURATED) }
    var cats by remember { mutableStateOf(emptySet<ItemCategory>()) }
    val listState = rememberLazyListState()
    LaunchedEffect(sort, cats, query) { listState.scrollToItem(0) }

    val filtered = remember(query, sort, cats, species) {
        val t = query.trim().lowercase()
        HELD_ITEMS.asSequence()
            // A Mega Stone only shows for the species it belongs to.
            .filter { it.category != ItemCategory.MEGA_STONE || it.megaSpecies == species }
            .filter { t.isEmpty() || it.display.lowercase().contains(t) }
            .filter { cats.isEmpty() || it.category in cats }
            .let { seq ->
                when (sort) {
                    ItemSort.CURATED -> seq
                    ItemSort.NAME_ASC -> seq.sortedBy { it.display }
                    ItemSort.NAME_DESC -> seq.sortedByDescending { it.display }
                }
            }
            .toList()
    }

    PickerSheet("Held item", onDismiss) {
        SheetSearchField(query, { query = it }, "Search items")
        Spacer(Modifier.size(10.dp))
        SheetSortRow(ItemSort.entries.map { it.label to it }, sort, { sort = it })
        Spacer(Modifier.size(6.dp))
        SheetFilters(cats.size, { cats = emptySet() }) {
            FilterGroup("Category") {
                ItemCategory.entries.forEach { c ->
                    SheetChip(c.label, c in cats) {
                        cats = if (c in cats) cats - c else cats + c
                    }
                }
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(
            "Greyed-out items aren't legal in ${ChampionsLegal.REGULATION}.",
            style = MaterialTheme.typography.labelSmall,
            color = SheetDim,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        ClearRow("— No item —", onClick = { onPick(null); onDismiss() })
        HorizontalDivider()
        // Fetch effect text only for rows actually on screen (plus what's just scrolled past),
        // not all ~115 items at once.
        LaunchedEffect(listState, filtered) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
                .collect { visible -> if (visible.isNotEmpty()) onNeedInfo(visible) }
        }
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = 400.dp)) {
            items(filtered, key = { it.slug }) { item: HeldItem ->
                val legal = item.championsLegal
                val spriteUrl = item.spriteUrl
                val fallbackGlyph = heldItemFallbackGlyph(item.slug)
                val effect = itemInfo[item.slug]?.shortEffect?.takeIf { it.isNotBlank() } ?: item.blurb
                Surface(
                    onClick = { if (legal) { onPick(item.slug); onDismiss() } },
                    color = Color.Transparent,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (spriteUrl == null) {
                            Text(
                                fallbackGlyph,
                                modifier = Modifier.size(26.dp).alpha(if (legal) 1f else 0.4f),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        } else {
                            AsyncImage(
                                model = spriteUrl,
                                contentDescription = null,
                                alpha = if (legal) 1f else 0.4f,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    item.display,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (legal) SheetInk else SheetDim,
                                )
                                if (!legal) {
                                    Spacer(Modifier.size(6.dp))
                                    Text("🚫", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            effect?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SheetDim,
                                    maxLines = 2,
                                    modifier = Modifier.alpha(if (legal) 1f else 0.6f),
                                )
                            }
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(item.category.label, style = MaterialTheme.typography.labelSmall, color = SheetDim)
                    }
                }
            }
        }
    }
}

@Composable
fun NaturePickerSheet(onDismiss: () -> Unit, onPick: (Nature) -> Unit) {
    PickerSheet("Stat Alignment", onDismiss) {
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            items(Nature.entries.toList(), key = { it.name }) { n ->
                Surface(onClick = { onPick(n); onDismiss() }, color = Color.Transparent) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(n.emoji, style = MaterialTheme.typography.titleMedium)
                        Text(n.display, style = MaterialTheme.typography.bodyLarge, color = SheetInk, modifier = Modifier.weight(1f))
                        if (n.raises != null) {
                            Text(
                                "+${n.raises.short}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = DexStatUp,
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                "−${n.lowers!!.short}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = DexStatDown,
                            )
                        } else {
                            Text(
                                "neutral",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = SheetDim,
                            )
                        }
                    }
                }
            }
        }
    }
}
