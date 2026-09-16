package com.pokedex.app.ui.detail

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pokedex.app.domain.model.EvolutionNode
import com.pokedex.app.domain.model.PokemonDetail
import com.pokedex.app.domain.model.PokemonSpecies
import com.pokedex.app.domain.model.PokemonSummary
import kotlin.math.abs
import com.pokedex.app.ui.components.ErrorState
import com.pokedex.app.ui.components.LoadingState
import com.pokedex.app.ui.components.PokedexHeader
import com.pokedex.app.ui.components.ScreenSurface
import com.pokedex.app.ui.components.SpriteImage
import com.pokedex.app.ui.components.TypeChip
import com.pokedex.app.ui.components.typeColor
import com.pokedex.app.ui.theme.DexPanelBorder
import com.pokedex.app.ui.theme.DexStatDown
import com.pokedex.app.ui.theme.DexStatUp
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    idOrName: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onEvolutionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail = state.displayedDetail
    val headerDetail = state.headerDetail
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { state.variantTabs.size.coerceAtLeast(1) })
    SyncFormTabPager(pagerState, state.variantTabs.size, state.selectedTab, viewModel::selectTab)

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PokedexHeader(
            title = detail?.displayName ?: idOrName.replaceFirstChar { it.uppercase() },
            subtitle = headerDetail?.let { d ->
                buildString {
                    append(d.dexLabel)
                    state.species?.genus?.takeIf { it.isNotBlank() }?.let { append("  ·  $it") }
                }
            },
            onBack = onBack,
            onHome = onHome,
        )

        if (state.variantTabs.size > 1) {
            FormTabRow(
                tabs = state.variantTabs.map { it.label },
                selected = state.selectedTab,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )
        }

        state.banner?.let { banner -> DismissibleBanner(banner, onDismiss = viewModel::dismissBanner) }

        ScreenSurface(Modifier.fillMaxSize()) {
            when {
                state.isLoading && detail == null -> LoadingState()
                state.error != null && detail == null ->
                    ErrorState(state.error!!, onRetry = viewModel::load)
                detail != null && state.variantTabs.size > 1 -> HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val tab = state.variantTabs[page]
                    DetailContent(
                        page = DetailPage(tab.detail, if (page > 0) state.variantTabs[0].detail else null, tab),
                        species = state.species,
                        state = state,
                        onToggleAbility = viewModel::toggleAbility,
                        onPlayCry = viewModel::playCry,
                        onEvolutionClick = onEvolutionClick,
                    )
                }
                detail != null -> DetailContent(
                    page = DetailPage(detail, state.comparisonBase, state.variantTabs.getOrNull(state.selectedTab)),
                    species = state.species,
                    state = state,
                    onToggleAbility = viewModel::toggleAbility,
                    onPlayCry = viewModel::playCry,
                    onEvolutionClick = onEvolutionClick,
                )
            }
        }
    }
}

/**
 * Keeps a form pager and the ViewModel's committed tab selection in sync both ways: a
 * FormTabRow tap animates the pager, and a swipe reports the settled page back via
 * [onSettle] so cry playback / ability loading act on whichever form is actually on screen.
 */
@Composable
private fun SyncFormTabPager(
    pagerState: androidx.compose.foundation.pager.PagerState,
    tabCount: Int,
    selectedTab: Int,
    onSettle: (Int) -> Unit,
) {
    LaunchedEffect(tabCount, selectedTab) {
        if (tabCount > 1 && pagerState.currentPage != selectedTab) pagerState.scrollToPage(selectedTab)
    }
    LaunchedEffect(pagerState.currentPage) {
        if (tabCount > 1) onSettle(pagerState.currentPage)
    }
}

@Composable
private fun DismissibleBanner(text: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White)
            }
        }
    }
}

@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = com.pokedex.app.ui.theme.DexPanel,
            contentColor = com.pokedex.app.ui.theme.DexPanelInk,
        ),
        border = BorderStroke(1.dp, DexPanelBorder),
        content = content,
    )
}

/** One page's worth of content: the form on screen, what to diff its stats against, and its tab info. */
private data class DetailPage(
    val detail: PokemonDetail,
    val comparisonBase: PokemonDetail?,
    val activeTab: VariantTab?,
)

@Composable
private fun DetailContent(
    page: DetailPage,
    species: PokemonSpecies?,
    state: DetailUiState,
    onToggleAbility: (String) -> Unit,
    onPlayCry: () -> Unit,
    onEvolutionClick: (String) -> Unit,
) {
    val detail = page.detail
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = 28.dp,
            start = 16.dp,
            end = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "artwork") { ArtworkHeader(detail) }
        item(key = "sprites") { SpriteRow(detail.sprites.spriteRow()) }
        item(key = "cry") { CryButton(state.cry, onPlayCry) }
        species?.let { sp ->
            if (!sp.genus.isNullOrBlank() || !sp.flavorText.isNullOrBlank()) {
                item(key = "lore") { LoreCard(sp) }
            }
        }
        item(key = "metrics") { MetricsRow(detail) }
        item(key = "stats") { StatsCard(detail, page.comparisonBase) }
        item(key = "abilities") {
            AbilitiesCard(detail = detail, state = state, onToggle = onToggleAbility)
        }
        item(key = "evolution") {
            // A species with region-suffixed varieties of its own (Raichu / Raichu
            // -Alola) is driven by whichever form tab is active. A "convergent"
            // Hisuian evolution (Sneasler, Kleavor, ...) has no varieties of its
            // own to tab between, so fall back to whatever region its place in the
            // chain requires — otherwise its evolution card would show the plain
            // line's parent instead of the regional one that actually leads to it.
            val chainRegionSuffix = remember(state.evolution, detail.speciesId) {
                (state.evolution as? SectionState.Success)?.value?.regionSuffixFor(detail.speciesId)
            }
            val regionSuffix = page.activeTab?.kind?.regionSuffix() ?: chainRegionSuffix
            val regionLabel = page.activeTab?.kind?.regionLabel() ?: chainRegionSuffix?.let(::regionLabelForSuffix)
            EvolutionCard(
                state = state.evolution,
                currentId = detail.speciesId,
                activeRegionSuffix = regionSuffix,
                activeRegionLabel = regionLabel,
                onClick = onEvolutionClick,
            )
        }
    }
}

private fun com.pokedex.app.domain.model.FormKind?.regionLabel(): String? = when (this) {
    com.pokedex.app.domain.model.FormKind.ALOLAN -> "Alolan"
    com.pokedex.app.domain.model.FormKind.GALARIAN -> "Galarian"
    com.pokedex.app.domain.model.FormKind.HISUIAN -> "Hisuian"
    com.pokedex.app.domain.model.FormKind.PALDEAN -> "Paldean"
    else -> null
}

/** The PokéAPI slug suffix for a regional form kind (`ninetales-alola` → `alola`). */
private fun com.pokedex.app.domain.model.FormKind?.regionSuffix(): String? = when (this) {
    com.pokedex.app.domain.model.FormKind.ALOLAN -> "alola"
    com.pokedex.app.domain.model.FormKind.GALARIAN -> "galar"
    com.pokedex.app.domain.model.FormKind.HISUIAN -> "hisui"
    com.pokedex.app.domain.model.FormKind.PALDEAN -> "paldea"
    else -> null
}

/** The reverse of [regionSuffix], for a suffix inferred from the chain rather than a form tab. */
private fun regionLabelForSuffix(suffix: String): String = when (suffix) {
    "alola" -> "Alolan"
    "galar" -> "Galarian"
    "hisui" -> "Hisuian"
    "paldea" -> "Paldean"
    else -> suffix.replaceFirstChar { it.uppercase() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun ArtworkHeader(detail: PokemonDetail) {
    val bg = detail.types.firstOrNull()?.name?.let { typeColor(it).copy(alpha = 0.18f) }
        ?: MaterialTheme.colorScheme.surfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            SpriteImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(detail.sprites.officialArtwork ?: detail.sprites.frontDefault)
                    .crossfade(true)
                    .build(),
                contentDescription = "${detail.displayName} official artwork",
                size = 230.dp,
                spinnerColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            detail.types.forEach { TypeChip(it.name) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpriteRow(sprites: List<com.pokedex.app.domain.model.PokemonSprites.Labeled>) {
    if (sprites.isEmpty()) return
    Column {
        SectionTitle("Sprites")
        val pagerState = rememberPagerState(pageCount = { sprites.size })
        InfoCard {
            Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalPager(state = pagerState) { page ->
                    val sprite = sprites[page]
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AsyncImage(
                            model = sprite.url,
                            contentDescription = sprite.label,
                            modifier = Modifier.size(150.dp),
                        )
                        Text(sprite.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(
                    Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(sprites.size) { i ->
                        val selected = pagerState.currentPage == i
                        Box(
                            Modifier
                                .size(if (selected) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CryButton(status: CryStatus, onPlay: () -> Unit) {
    FilledTonalButton(
        onClick = onPlay,
        enabled = status != CryStatus.Loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        when (status) {
            CryStatus.Loading -> {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Loading cry…")
            }
            CryStatus.Playing -> {
                Icon(Icons.Default.VolumeUp, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Playing…")
            }
            CryStatus.Error -> {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Cry unavailable — tap to retry")
            }
            CryStatus.Idle -> {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Play cry")
            }
        }
    }
}

@Composable
private fun LoreCard(species: PokemonSpecies) {
    InfoCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            species.genus?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
            }
            species.flavorText?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MetricsRow(detail: PokemonDetail) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("Height", "%.1f m".format(Locale.US, detail.heightMeters), Modifier.weight(1f))
        MetricCard("Weight", "%.1f kg".format(Locale.US, detail.weightKg), Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    InfoCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatsCard(detail: PokemonDetail, comparisonBase: PokemonDetail?) {
    val baseByName = comparisonBase?.stats?.associate { it.name to it.base }
    val anyChange = baseByName != null &&
        detail.stats.any { (baseByName[it.name] ?: it.base) != it.base }
    InfoCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Base stats")
                if (anyChange) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "vs base",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            detail.stats.forEach { stat ->
                val baseValue = baseByName?.get(stat.name)
                val delta = if (baseValue != null) stat.base - baseValue else 0
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stat.label, Modifier.width(40.dp), style = MaterialTheme.typography.labelMedium)
                    Text(
                        stat.base.toString(),
                        Modifier.width(34.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    StatBar(
                        value = stat.base,
                        baseValue = baseValue.takeIf { delta != 0 },
                        modifier = Modifier.weight(1f),
                    )
                    if (delta != 0) {
                        Spacer(Modifier.width(6.dp))
                        DeltaChip(delta)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Total", Modifier.width(74.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    detail.statTotal.toString(),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
                comparisonBase?.let {
                    val td = detail.statTotal - it.statTotal
                    if (td != 0) {
                        Spacer(Modifier.width(8.dp))
                        DeltaChip(td)
                    }
                }
            }
        }
    }
}

private val StatLossTrail = Color(0x33EE5B52)  // faint trail showing where a dropped stat used to reach

@Composable
private fun StatBar(value: Int, baseValue: Int?, modifier: Modifier = Modifier) {
    fun frac(v: Int) = (v / 200f).coerceIn(0.02f, 1f)
    val increased = baseValue != null && value > baseValue
    val decreased = baseValue != null && value < baseValue
    // The bar's colour always reflects the CURRENT value's tier.
    val quality = statColor(value)

    Box(
        modifier
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xFFDCE7EC)),
    ) {
        when {
            increased -> {
                // Whole current bar in a bright tint of the current tier, then repaint the
                // part that was already there in the solid tier colour. The lighter tip = the gain.
                Box(
                    Modifier.fillMaxWidth(frac(value)).fillMaxHeight()
                        .background(androidx.compose.ui.graphics.lerp(quality, Color.White, 0.42f)),
                )
                Box(Modifier.fillMaxWidth(frac(baseValue!!)).fillMaxHeight().background(quality))
                SplitLine(frac(baseValue))
            }
            decreased -> {
                // Faint red trail out to the old value, then the current bar in its (lower) tier colour.
                Box(Modifier.fillMaxWidth(frac(baseValue!!)).fillMaxHeight().background(StatLossTrail))
                Box(Modifier.fillMaxWidth(frac(value)).fillMaxHeight().background(quality))
                SplitLine(frac(value))
            }
            else -> {
                Box(Modifier.fillMaxWidth(frac(value)).fillMaxHeight().background(quality))
            }
        }
    }
}

/** A crisp white seam where "already there" meets the gained / lost segment. */
@Composable
private fun SplitLine(atFraction: Float) {
    Box(Modifier.fillMaxWidth(atFraction).fillMaxHeight()) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White),
        )
    }
}

@Composable
private fun DeltaChip(delta: Int) {
    val positive = delta > 0
    val fg = if (positive) DexStatUp else DexStatDown
    val bg = if (positive) Color(0xFFD7F2E0) else Color(0xFFFBDEDC)
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            (if (positive) "▲ " else "▼ ") + abs(delta),
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun statColor(base: Int): Color = when {
    base >= 120 -> DexStatUp
    base >= 90 -> Color(0xFF7CB342)
    base >= 60 -> Color(0xFFF9A825)
    base >= 35 -> Color(0xFFF57F17)
    else -> DexStatDown
}

@Composable
private fun AbilitiesCard(
    detail: PokemonDetail,
    state: DetailUiState,
    onToggle: (String) -> Unit,
) {
    InfoCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            SectionTitle("Abilities")
            detail.abilities.forEach { ability ->
                val expanded = ability.name in state.expandedAbilities
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(ability.name) }
                        .padding(vertical = 8.dp)
                        .animateContentSize(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            ability.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (ability.isHidden) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondary,
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    "Hidden",
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                        )
                    }
                    if (expanded) {
                        Spacer(Modifier.height(4.dp))
                        when (val s = state.abilities[ability.name]) {
                            is SectionState.Success -> Text(
                                s.value.shortEffect.ifBlank { s.value.effect },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            is SectionState.Error -> Text(
                                s.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Loading…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EvolutionCard(
    state: SectionState<com.pokedex.app.domain.model.EvolutionChain>?,
    currentId: Int,
    activeRegionSuffix: String?,
    activeRegionLabel: String?,
    onClick: (String) -> Unit,
) {
    InfoCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            SectionTitle("Evolution")
            when (state) {
                null, SectionState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Loading…", style = MaterialTheme.typography.bodySmall)
                }
                is SectionState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                is SectionState.Success -> {
                    val chain = state.value
                    if (!chain.hasEvolutions) {
                        Text("This Pokémon does not evolve.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        // Every species in the chain that has a variety for the region we're
                        // viewing (Alolan Vulpix → Alolan Ninetales via Ice Stone, …).
                        val regionForms = activeRegionSuffix
                            ?.let { chain.regionForms(it) }
                            .orEmpty()
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            EvoTree(
                                node = chain.root!!,
                                regionForms = regionForms,
                                regionLabel = activeRegionLabel,
                                regionSuffix = activeRegionSuffix,
                                highlightId = currentId,
                                onClick = onClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Renders an evolution (sub)tree: linear lines stay in a row, branch points fan into a column. */
@Composable
private fun EvoTree(
    node: EvolutionNode,
    regionForms: Map<Int, com.pokedex.app.domain.model.RegionForm>,
    regionLabel: String?,
    regionSuffix: String?,
    highlightId: Int,
    onClick: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val here = regionForms[node.speciesId]
        EvoNodeCard(
            node = node,
            regionForm = here,
            regionLabel = regionLabel,
            highlighted = node.speciesId == highlightId,
            onClick = { onClick(here?.slug ?: node.name) },
        )
        // Only show branches that apply to the variety on screen: on a regional
        // line, the children on the path to a regional variety; otherwise the
        // plain (non-regional) line.
        val children = node.visibleChildren(regionForms, regionSuffix)
        when (children.size) {
            0 -> Unit
            1 -> EvoBranch(children.first(), regionForms, regionLabel, regionSuffix, highlightId, onClick)
            else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                children.forEach { child ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EvoBranch(child, regionForms, regionLabel, regionSuffix, highlightId, onClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun EvoBranch(
    child: EvolutionNode,
    regionForms: Map<Int, com.pokedex.app.domain.model.RegionForm>,
    regionLabel: String?,
    regionSuffix: String?,
    highlightId: Int,
    onClick: (String) -> Unit,
) {
    EvoConnector(child.methodFor(regionForms))
    EvoTree(
        node = child,
        regionForms = regionForms,
        regionLabel = regionLabel,
        regionSuffix = regionSuffix,
        highlightId = highlightId,
        onClick = onClick,
    )
}

@Composable
private fun EvoConnector(method: com.pokedex.app.domain.model.EvoMethod?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        method?.itemSpriteUrl?.let { EvoItemIcon(it) }
        Text("→", style = MaterialTheme.typography.titleLarge)
        method?.text?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(84.dp),
            )
        }
    }
}

@Composable
private fun EvoItemIcon(url: String) {
    var failed by androidx.compose.runtime.remember(url) {
        androidx.compose.runtime.mutableStateOf(false)
    }
    if (failed) {
        Icon(
            Icons.Filled.Diamond,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
            contentDescription = null,
            modifier = Modifier.size(30.dp),
            onError = { failed = true },
        )
    }
}

@Composable
private fun EvoNodeCard(
    node: EvolutionNode,
    regionForm: com.pokedex.app.domain.model.RegionForm?,
    regionLabel: String?,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val spriteId = regionForm?.pokemonId ?: node.speciesId
    val label = if (regionForm != null && regionLabel != null) {
        "$regionLabel ${node.displayName}"
    } else {
        node.displayName
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(
                if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
            )
            .padding(8.dp)
            .width(92.dp),
    ) {
        AsyncImage(
            model = PokemonSummary(spriteId, node.name).spriteUrl,
            contentDescription = label,
            modifier = Modifier.size(68.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun FormTabRow(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val isSelected = index == selected
            Surface(
                onClick = { onSelect(index) },
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    if (index == 0) label else label.uppercase(),
                    color = if (isSelected) com.pokedex.app.ui.theme.DexRed else Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}
