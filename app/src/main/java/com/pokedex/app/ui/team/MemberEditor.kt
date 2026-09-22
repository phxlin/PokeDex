@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pokedex.app.ui.team

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pokedex.app.core.PokemonNames
import com.pokedex.app.domain.team.Gender
import com.pokedex.app.domain.team.HELD_ITEMS
import com.pokedex.app.domain.team.ItemCategory
import com.pokedex.app.domain.team.ItemInfo
import com.pokedex.app.domain.team.MoveInfo
import com.pokedex.app.domain.team.Nature
import com.pokedex.app.domain.team.StatCalc
import com.pokedex.app.domain.team.StatKey
import com.pokedex.app.domain.team.TeamMember
import com.pokedex.app.domain.team.heldItemDisplayName
import com.pokedex.app.domain.team.heldItemFallbackGlyph
import com.pokedex.app.domain.team.heldItemSpriteUrl
import com.pokedex.app.ui.components.PokedexHeader
import com.pokedex.app.ui.components.SpriteImage
import com.pokedex.app.ui.components.TypeSymbol
import com.pokedex.app.ui.components.typeColor
import com.pokedex.app.ui.theme.DexStatDown
import com.pokedex.app.ui.theme.DexStatUp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// ---- Member editor -----------------------------------------------------------

@Composable
internal fun MemberEditor(
    member: TeamMember,
    moveInfo: Map<String, MoveInfo>,
    itemInfo: Map<String, ItemInfo>,
    abilityInfo: Map<String, String>,
    forms: List<MemberForm>,
    selectedForm: Int,
    onSelectForm: (Int) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRemove: () -> Unit,
    viewModel: TeamEditorViewModel,
) {
    var sheet by remember { mutableStateOf<String?>(null) }
    val slot = member.slot

    // What the rest of the screen (Ability / Stat Points sections) shows: the picked
    // form's data, falling back to the base member. The profile card computes its own
    // per-form data itself, since it's swipeable and needs it per page.
    val activeForm = forms.getOrNull(selectedForm)?.takeIf { forms.size > 1 }
    val onForm = activeForm != null && !activeForm.isBase
    val showBaseStats = activeForm?.baseStats ?: member.baseStats
    val showFinal = StatKey.entries.associateWith { stat ->
        val b = showBaseStats[stat] ?: return@associateWith 0
        StatCalc.value(stat, b, member.sp[stat] ?: 0, member.nature)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PokedexHeader(
            title = member.displayName,
            subtitle = "Slot ${slot + 1}",
            onBack = onBack,
            onHome = onHome,
            actions = {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove", tint = Color.White)
                }
            },
        )
        // Disabled while a move row is pressed so its vertical drag isn't stolen by this
        // screen's own scroll gesture before the long press completes (see tapOrDragToReorder).
        var movesTouchActive by remember { mutableStateOf(false) }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = !movesTouchActive).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MemberProfileCard(member, forms, selectedForm, onSelectForm)
            AppearanceSection(member, slot, activeForm?.let { Gender.lockedByForm(it.slug) }, viewModel)
            AbilitySection(member, slot, onForm, activeForm, abilityInfo, viewModel)
            StatAlignmentSection(member, forms, onOpenSheet = { sheet = it })
            StatPointsSection(member, slot, showBaseStats, showFinal, viewModel)
            MovesSection(
                member = member,
                slot = slot,
                moveInfo = moveInfo,
                onTouchActiveChange = { movesTouchActive = it },
                onOpenSheet = { sheet = it },
                viewModel = viewModel,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    val movePickerOpen = sheet?.startsWith("move") == true
    LaunchedEffect(movePickerOpen, member.movePool) {
        if (movePickerOpen && member.movePool.isNotEmpty()) viewModel.loadMovePool(member.movePool)
    }

    when (val s = sheet) {
        "nature" -> NaturePickerSheet(onDismiss = { sheet = null }, onPick = { viewModel.setNature(slot, it) })
        "item" -> ItemPickerSheet(
            species = member.speciesName,
            itemInfo = itemInfo,
            onNeedInfo = { viewModel.loadItemInfo(it) },
            onDismiss = { sheet = null },
            onPick = { viewModel.setItem(slot, it) },
        )
        else -> if (s != null && s.startsWith("move")) {
            val idx = s.removePrefix("move").toInt()
            MovePickerSheet(
                pool = member.movePool,
                moveInfo = moveInfo,
                onDismiss = { sheet = null },
                onPick = { viewModel.setMove(slot, idx, it) },
            )
        }
    }
}

/**
 * The artwork + type row + stat hexagon card at the top of the member editor —
 * swipeable between the Pokémon's forms (Base / Mega / regional…) exactly like tapping
 * a pill: settling on a new page commits it for real (equipping/removing a Mega Stone,
 * swapping the movepool, …), so this isn't a free preview, just an alternate gesture
 * for the same action the pills already perform.
 */
@Composable
private fun MemberProfileCard(
    member: TeamMember,
    forms: List<MemberForm>,
    selectedForm: Int,
    onSelectForm: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { forms.size.coerceAtLeast(1) })

    LaunchedEffect(forms, selectedForm) {
        if (forms.size > 1) {
            val target = selectedForm.coerceIn(0, forms.lastIndex)
            if (pagerState.currentPage != target) pagerState.scrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (forms.size > 1 && pagerState.currentPage != selectedForm) onSelectForm(pagerState.currentPage)
    }

    // Explicit color: Surface with no `color` falls back to the theme's `surface` token,
    // which this app repurposes as the pale-blue Pokédex "screen" tint (see Theme.kt) —
    // a mismatched tint here. Transparent lets each page's own gradient (below) show
    // through the whole card, the same way the Pokédex detail screen's form tabs sit
    // directly on its red header rather than in their own bar.
    Surface(shape = RoundedCornerShape(18.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
            MemberProfileFormPage(
                member = member,
                activeForm = forms.getOrNull(page)?.takeIf { forms.size > 1 },
                tabs = forms.map { it.label }.takeIf { forms.size > 1 },
                selectedTab = pagerState.currentPage,
                onSelectTab = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )
        }
    }
}

/** One form's tab row + artwork + type row + stat hexagon, tinted by that form's own type(s). */
@Composable
private fun MemberProfileFormPage(
    member: TeamMember,
    activeForm: MemberForm?,
    tabs: List<String>?,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
) {
    val showTypes = activeForm?.types ?: member.types
    val showBaseStats = activeForm?.baseStats ?: member.baseStats
    val showArtwork = activeForm?.artworkUrl(member.shiny) ?: member.artworkUrl

    fun finalFor(stat: StatKey): Int {
        val b = showBaseStats[stat] ?: return 0
        return StatCalc.value(stat, b, member.sp[stat] ?: 0, member.nature)
    }
    val showFinal = StatKey.entries.associateWith { finalFor(it) }
    val showBaseline = StatKey.entries.associateWith { stat ->
        showBaseStats[stat]?.let { StatCalc.value(stat, it, 0, Nature.SERIOUS) } ?: 0
    }

    val c1 = showTypes.getOrNull(0)?.let { typeColor(it) } ?: MaterialTheme.colorScheme.primary
    val c2 = showTypes.getOrNull(1)?.let { typeColor(it) } ?: c1
    Column(
        Modifier
            .background(Brush.linearGradient(listOf(c1, c2)))
            .padding(14.dp),
    ) {
        if (tabs != null) {
            FormTabRow(tabs = tabs, selected = selectedTab, onSelect = onSelectTab)
            Spacer(Modifier.height(10.dp))
        }
        Row(
            Modifier.align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            showTypes.forEach { TypeSymbol(it, size = 30.dp) }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().size(150.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(150.dp).background(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)),
                    CircleShape,
                ),
            )
            SpriteImage(
                model = ImageRequest.Builder(LocalContext.current).data(showArtwork).crossfade(true).build(),
                contentDescription = activeForm?.label?.let { "$it ${member.displayName}" } ?: member.displayName,
                size = 150.dp,
                spinnerColor = Color.White.copy(alpha = 0.85f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Surface(color = Color.White, contentColor = CardInk, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                StatHexagon(
                    stats = showFinal,
                    base = showBaseline.takeIf { it.values.any { v -> v > 0 } },
                    raisedStat = member.nature.raises,
                    loweredStat = member.nature.lowers,
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    fill = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Base stat total ${showBaseStats.values.sum()}" +
                        if (member.spTotal > 0) "  ·  your build ${showFinal.values.sum()}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun AppearanceSection(member: TeamMember, slot: Int, lockedGender: Gender?, viewModel: TeamEditorViewModel) {
    SectionCard("Appearance") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (member.shiny) "✨ Shiny" else "Shiny", selected = member.shiny) {
                viewModel.setShiny(slot, !member.shiny)
            }
            // A form that fixes the gender (Indeedee-F, or the male base form) offers only that one.
            (if (lockedGender != null) listOf(lockedGender) else Gender.entries).forEach { g ->
                Pill(
                    if (g == Gender.DEFAULT) "Any gender" else "${g.symbol} ${g.label}",
                    selected = member.gender == g || lockedGender == g,
                ) { viewModel.setGender(slot, g) }
            }
        }
    }
}

@Composable
private fun AbilitySection(
    member: TeamMember,
    slot: Int,
    onForm: Boolean,
    activeForm: MemberForm?,
    abilityInfo: Map<String, String>,
    viewModel: TeamEditorViewModel,
) {
    // Mega forms show their fixed Ability (info only); base and regional forms let you choose.
    val megaLocked = onForm && activeForm!!.isMega
    SectionCard(if (onForm) "Ability (${activeForm!!.label})" else "Ability") {
        val choices = if (onForm) activeForm!!.abilities else member.abilityChoices
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { a ->
                // On a Mega tab the Ability is fixed, so highlight it regardless of the base choice.
                Pill(
                    a.display + if (a.isHidden) "  (H)" else "",
                    selected = if (megaLocked) true else member.ability == a.name,
                ) { if (!megaLocked) viewModel.setAbility(slot, a.name) }
            }
        }
        val shownAbility = if (megaLocked) choices.firstOrNull()?.name else member.ability
        abilityInfo[shownAbility]?.takeIf { it.isNotBlank() }?.let { desc ->
            Spacer(Modifier.height(6.dp))
            Text(
                desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (megaLocked) {
            Spacer(Modifier.height(6.dp))
            Text(
                "${member.displayName} keeps its own Ability until it Mega Evolves.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatAlignmentSection(
    member: TeamMember,
    forms: List<MemberForm>,
    onOpenSheet: (String) -> Unit,
) {
    SectionCard("Stat Alignment & item") {
        Row(
            Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChooserButton(
                text = "${member.nature.emoji}  ${member.nature.display}",
                onClick = { onOpenSheet("nature") },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            ChooserButton(
                text = member.item?.let { heldItemDisplayName(it) } ?: "No item",
                onClick = { onOpenSheet("item") },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                iconUrl = heldItemSpriteUrl(member.item),
                fallbackGlyph = member.item?.let(::heldItemFallbackGlyph),
            )
        }
        if (member.nature.raises != null) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "+${member.nature.raises.short}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = DexStatUp,
                )
                Text(
                    "−${member.nature.lowers!!.short}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = DexStatDown,
                )
            }
        }
        val megaWithoutForm = member.item?.let { s ->
            val stone = HELD_ITEMS.firstOrNull { i -> i.slug == s }
            stone?.category == ItemCategory.MEGA_STONE &&
                stone.megaSpecies == member.speciesName &&
                forms.none { f -> f.isMega }
        } == true
        if (megaWithoutForm) {
            Spacer(Modifier.height(6.dp))
            Text(
                "PokéAPI has no data for this Mega yet, so its form can't be previewed.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatPointsSection(
    member: TeamMember,
    slot: Int,
    showBaseStats: Map<StatKey, Int>,
    showFinal: Map<StatKey, Int>,
    viewModel: TeamEditorViewModel,
) {
    SectionCard("Stat Points  ·  ${member.spTotal}/${StatCalc.MAX_SP_TOTAL}") {
        StatKey.entries.forEach { stat ->
            val v = member.sp[stat] ?: 0
            val barColor = when (member.nature.multiplier(stat)) {
                1.1 -> DexStatUp
                0.9 -> DexStatDown
                else -> MaterialTheme.colorScheme.primary
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Text(stat.short, Modifier.width(34.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (showBaseStats.isEmpty()) "—" else "${showFinal[stat] ?: 0}",
                    Modifier.width(40.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                StatSlider(
                    value = v,
                    max = StatCalc.MAX_SP_PER_STAT,
                    trackColor = barColor,
                    onValueChange = { viewModel.setSp(slot, stat, it) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text("$v", Modifier.width(26.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = { viewModel.clearSp(slot) },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
        ) { Text("Clear") }
    }
}

@Composable
private fun MovesSection(
    member: TeamMember,
    slot: Int,
    moveInfo: Map<String, MoveInfo>,
    onTouchActiveChange: (Boolean) -> Unit,
    onOpenSheet: (String) -> Unit,
    viewModel: TeamEditorViewModel,
) {
    SectionCard("Moves") {
        Text(
            "Press and hold a move to drag it to a new slot.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        val haptics = LocalHapticFeedback.current
        var rowHeightPx by remember { mutableStateOf(0f) }
        var draggingMove by remember { mutableStateOf<Int?>(null) }
        var moveDragOffsetY by remember { mutableStateOf(0f) }
        var moveDropTarget by remember { mutableStateOf<Int?>(null) }

        for (i in 0..3) {
            val mv = member.moves.getOrNull(i)
            val isDragging = draggingMove == i
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { if (it.height > 0) rowHeightPx = it.height.toFloat() }
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = moveDragOffsetY
                            scaleX = 1.02f
                            scaleY = 1.02f
                            shadowElevation = 10f
                        }
                    }
                    .semantics {
                        role = Role.Button
                        semanticsOnClick(label = mv?.let { "Change ${PokemonNames.displayName(it)}" } ?: "Set move ${i + 1}") {
                            onOpenSheet("move$i")
                            true
                        }
                        if (mv != null) {
                            customActions = buildList {
                                if (i > 0) add(CustomAccessibilityAction("Move up") { viewModel.swapMoves(slot, i, i - 1); true })
                                if (i < 3) add(CustomAccessibilityAction("Move down") { viewModel.swapMoves(slot, i, i + 1); true })
                            }
                        }
                    }
                    .tapOrDragToReorder(
                        key = i,
                        onActiveChange = onTouchActiveChange,
                        onClick = { onOpenSheet("move$i") },
                        onDragStart = {
                            draggingMove = i
                            moveDragOffsetY = 0f
                            moveDropTarget = i
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { delta ->
                            moveDragOffsetY += delta.y
                            if (rowHeightPx > 0f) {
                                val moved = (moveDragOffsetY / rowHeightPx).roundToInt()
                                moveDropTarget = (i + moved).coerceIn(0, 3)
                            }
                        },
                        onDragEnd = {
                            val target = moveDropTarget
                            if (target != null && target != i) viewModel.swapMoves(slot, i, target)
                            draggingMove = null
                            moveDropTarget = null
                            moveDragOffsetY = 0f
                        },
                    ),
            ) {
                MoveSlotRow(
                    index = i,
                    move = mv,
                    info = mv?.let { moveInfo[it] },
                    highlighted = moveDropTarget == i && draggingMove != null && !isDragging,
                )
            }
        }
    }
}

@Composable
private fun EditorCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, contentColor = CardInk, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    EditorCard {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

/** One move slot: type-coloured when filled, showing type · power · PP · accuracy. */
@Composable
private fun MoveSlotRow(index: Int, move: String?, info: MoveInfo?, highlighted: Boolean = false) {
    val filled = info != null
    Surface(
        color = info?.type?.let { typeColor(it) } ?: CardWell,
        contentColor = if (filled) Color.White else CardInk,
        shape = RoundedCornerShape(12.dp),
        border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    move?.let { PokemonNames.displayName(it) } ?: "Move ${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (info != null) {
                    Text(
                        buildString {
                            append(info.type.replaceFirstChar { it.uppercase() })
                            append("  ·  ")
                            append(info.damageClass.replaceFirstChar { it.uppercase() })
                            append("  ·  ")
                            append(info.power?.let { "$it BP" } ?: "— BP")
                            append("  ·  ")
                            append(info.pp?.let { "$it PP" } ?: "— PP")
                            info.accuracy?.let { append("  ·  $it%") }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
            if (info != null) {
                TypeSymbol(info.type, size = 22.dp)
            }
        }
    }
}

/**
 * Horizontal, scrollable pill tabs for a member's forms (Base / Mega / regional…), styled for the
 * profile card's colored header — its only caller.
 */
@Composable
private fun FormTabRow(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val isSelected = index == selected
            val bg = if (isSelected) Color.White else Color.White.copy(alpha = 0.22f)
            val fg = if (isSelected) CardInk else Color.White
            Surface(
                onClick = { onSelect(index) },
                color = bg,
                contentColor = fg,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** A draggable slider for allocating Stat Points, scaled 0..[max]. */
@Composable
private fun StatSlider(
    value: Int,
    max: Int,
    trackColor: Color,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = max.coerceAtLeast(1)
    BoxWithConstraints(
        modifier.height(28.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val thumb = 22.dp
        val fraction = (value.toFloat() / range).coerceIn(0f, 1f)

        fun report(x: Float) =
            onValueChange((x / widthPx * range).roundToInt().coerceIn(0, range))

        val gestures = Modifier
            .pointerInput(range, widthPx) {
                detectTapGestures { report(it.x) }
            }
            .pointerInput(range, widthPx) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    report(change.position.x)
                }
            }

        Box(
            Modifier.fillMaxWidth().height(8.dp)
                .background(Color(0xFFE1EBEE), RoundedCornerShape(4.dp)),
        )
        Box(
            Modifier.fillMaxWidth(fraction).height(8.dp)
                .background(trackColor, RoundedCornerShape(4.dp)),
        )
        Box(Modifier.matchParentSize().then(gestures))
        Box(
            Modifier
                .offset { IntOffset(((widthPx - thumb.toPx()) * fraction).roundToInt(), 0) }
                .size(thumb)
                .background(Color.White, CircleShape)
                .border(2.dp, trackColor, CircleShape),
        )
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text,
            color = if (selected) Color.White else CardInk,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun ChooserButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconUrl: String? = null,
    fallbackGlyph: String? = null,
) {
    Surface(
        onClick = onClick,
        color = CardWell,
        contentColor = CardInk,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconUrl != null) {
                AsyncImage(model = iconUrl, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            } else if (fallbackGlyph != null) {
                Text(fallbackGlyph, modifier = Modifier.width(20.dp), textAlign = TextAlign.Center)
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        }
    }
}
