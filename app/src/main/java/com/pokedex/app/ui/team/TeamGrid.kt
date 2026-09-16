package com.pokedex.app.ui.team

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.pokedex.app.core.PokemonNames
import com.pokedex.app.domain.team.Gender
import com.pokedex.app.domain.team.TeamMember
import com.pokedex.app.domain.team.formBadge
import com.pokedex.app.domain.team.heldItemDisplayName
import com.pokedex.app.domain.team.heldItemFallbackGlyph
import com.pokedex.app.domain.team.heldItemSpriteUrl
import com.pokedex.app.domain.team.megaSpriteFor
import com.pokedex.app.ui.components.PokedexHeader
import com.pokedex.app.ui.components.SpriteImage
import com.pokedex.app.ui.components.TypeSymbol
import com.pokedex.app.ui.components.typeColor

// ---- Team overview -------------------------------------------------------------

@Composable
internal fun TeamOverview(
    state: TeamEditorUiState,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRename: (String) -> Unit,
    onAddSlot: (Int) -> Unit,
    onOpenSlot: (Int) -> Unit,
    onSwapSlots: (Int, Int) -> Unit,
) {
    // Disabled while a slot is pressed so a vertical same-column drag isn't stolen by this
    // list's own scroll gesture before the long press completes (see tapOrDragToReorder).
    var gridTouchActive by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        userScrollEnabled = !gridTouchActive,
    ) {
        item {
            PokedexHeader(
                title = "Team",
                subtitle = "${state.members.size}/6 Pokémon",
                onBack = onBack,
                onHome = onHome,
            )
        }
        item { TeamNameField(state.name, onRename, Modifier.padding(horizontal = 16.dp)) }
        if (state.members.size > 1) {
            item {
                Text(
                    "Press and hold a Pokémon to drag it to a new slot.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item {
            TeamGrid(state, onAddSlot, onOpenSlot, onSwapSlots) { gridTouchActive = it }
        }

        if (state.members.isNotEmpty()) {
            item { Box(Modifier.padding(horizontal = 16.dp)) { AnalysisCard(state) } }
        }
    }
}

/** A slot's accessible neighbours in the 2×3 grid, for the "Move …" custom actions. */
private fun neighborsOf(slot: Int): List<Pair<String, Int>> {
    val col = slot % 2
    val row = slot / 2
    return buildList {
        if (col == 1) add("Move left" to slot - 1)
        if (col == 0) add("Move right" to slot + 1)
        if (row > 0) add("Move up" to slot - 2)
        if (row < 2) add("Move down" to slot + 2)
    }
}

/** The 2×3 slot grid, with long-press-drag reordering (see [tapOrDragToReorder]). */
@Composable
private fun TeamGrid(
    state: TeamEditorUiState,
    onAddSlot: (Int) -> Unit,
    onOpenSlot: (Int) -> Unit,
    onSwapSlots: (Int, Int) -> Unit,
    onTouchActiveChange: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var containerWidthPx by remember { mutableStateOf(0f) }
    var draggingSlot by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dropTarget by remember { mutableStateOf<Int?>(null) }
    val hGap = 10.dp
    val rowPitchPx = with(density) { 160.dp.toPx() }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .onSizeChanged { containerWidthPx = it.width.toFloat() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in 0..2) {
                Row(horizontalArrangement = Arrangement.spacedBy(hGap)) {
                    for (col in 0..1) {
                        val slot = row * 2 + col
                        val member = state.members.firstOrNull { it.slot == slot }
                        val isDragging = draggingSlot == slot
                        val isDropTarget = dropTarget == slot && draggingSlot != null && !isDragging
                        Box(
                            Modifier
                                .weight(1f)
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                        scaleX = 1.05f
                                        scaleY = 1.05f
                                        shadowElevation = 18f
                                    }
                                }
                                .semantics {
                                    role = Role.Button
                                    semanticsOnClick(label = if (member != null) "Open ${member.displayName}" else "Add Pokémon") {
                                        if (member != null) onOpenSlot(slot) else onAddSlot(slot)
                                        true
                                    }
                                    if (member != null) {
                                        customActions = neighborsOf(slot).map { (label, target) ->
                                            CustomAccessibilityAction(label) { onSwapSlots(slot, target); true }
                                        }
                                    }
                                }
                                .tapOrDragToReorder(
                                    // Restart the gesture handler when this slot's filled/empty
                                    // state changes, not just on `slot` alone: pointerInput(key)
                                    // keeps its already-running coroutine — and whatever onClick
                                    // it captured at launch — for as long as key is unchanged, so
                                    // a slot that started empty (onClick -> onAddSlot) would keep
                                    // reopening the picker forever even after a Pokémon filled it.
                                    key = slot to (member != null),
                                    onActiveChange = onTouchActiveChange,
                                    onClick = { if (member != null) onOpenSlot(slot) else onAddSlot(slot) },
                                    onDragStart = {
                                        draggingSlot = slot
                                        dragOffset = Offset.Zero
                                        dropTarget = slot
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { delta ->
                                        dragOffset += delta
                                        // Read containerWidthPx (state) fresh here rather than a
                                        // precomputed val: this callback runs on the pointerInput
                                        // coroutine launched at this cell's first composition, and
                                        // a plain val captured then would freeze at whatever the
                                        // container's width was at that moment (often still 0,
                                        // before the first onSizeChanged) for the rest of the drag.
                                        val cw = containerWidthPx
                                        if (cw > 0f) {
                                            val colPitchPx = (cw + with(density) { hGap.toPx() }) / 2f
                                            val cx = col * colPitchPx + colPitchPx / 2 + dragOffset.x
                                            val cy = row * rowPitchPx + rowPitchPx / 2 + dragOffset.y
                                            val tCol = (cx / colPitchPx).toInt().coerceIn(0, 1)
                                            val tRow = (cy / rowPitchPx).toInt().coerceIn(0, 2)
                                            dropTarget = (tRow * 2 + tCol).coerceIn(0, 5)
                                        }
                                    },
                                    onDragEnd = {
                                        val target = dropTarget
                                        if (target != null && target != slot) onSwapSlots(slot, target)
                                        draggingSlot = null
                                        dropTarget = null
                                        dragOffset = Offset.Zero
                                    },
                                ),
                        ) {
                            if (member == null) {
                                EmptySlot(highlighted = isDropTarget)
                            } else {
                                val forms = state.forms[slot].orEmpty()
                                val fi = state.selectedFormIndex(slot)
                                SlotCard(
                                    member = member,
                                    form = forms.getOrNull(fi)?.takeIf { fi > 0 },
                                    highlighted = isDropTarget,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamNameField(name: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = name,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = CardInk),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Composable
private fun EmptySlot(highlighted: Boolean = false) {
    Surface(
        color = if (highlighted) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            if (highlighted) 2.dp else 1.dp,
            if (highlighted) Color.White else Color.White.copy(alpha = 0.35f),
        ),
        modifier = Modifier.fillMaxWidth().height(150.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            Text("Add", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** A compact one-line summary of where a member's Stat Points went. */
private fun spSpread(member: TeamMember): String {
    val spent = member.sp.filterValues { it > 0 }.entries.sortedByDescending { it.value }
    val nature = if (member.nature.raises != null) "${member.nature.display} · " else ""
    if (spent.isEmpty()) return "${nature}no Stat Points spent"
    return nature + spent.joinToString(" ") { "${it.value}${it.key.short}" }
}

@Composable
private fun SlotCard(member: TeamMember, form: MemberForm?, highlighted: Boolean = false) {
    val types = form?.types ?: member.types
    // Prefer the pre-mapped Mega sprite so an equipped stone shows the Mega without waiting on form data.
    val sprite = megaSpriteFor(member.item, member.shiny)
        ?: form?.spriteUrl(member.shiny)
        ?: member.spriteUrl
    val c1 = types.getOrNull(0)?.let { typeColor(it) } ?: MaterialTheme.colorScheme.primary
    val c2 = types.getOrNull(1)?.let { typeColor(it) } ?: c1
    // Badge for the special form the member is fielding (Mega / Alolan / …).
    val badge = formBadge(member.formSlug, member.item)
    // A Mega form has a fixed Ability; otherwise show the member's chosen one.
    val abilityLabel = if (form?.isMega == true) {
        form.abilities.firstOrNull()?.display
    } else {
        val choices = form?.abilities ?: member.abilityChoices
        member.ability?.let { slug -> choices.firstOrNull { it.name == slug }?.display ?: PokemonNames.displayName(slug) }
    }
    val name = member.displayName
    // Types / stats / abilities are filled in from the Pokédex cache after the card first
    // appears — show a spinner until then so an empty card doesn't read as frozen.
    val loading = member.types.isEmpty()
    Surface(
        color = Color.White,
        contentColor = CardInk,
        shape = RoundedCornerShape(18.dp),
        border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().height(150.dp),
    ) {
        Column(
            Modifier
                .background(Brush.linearGradient(listOf(c1.copy(alpha = 0.14f), c2.copy(alpha = 0.20f))))
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SlotSprite(sprite, badge, c1, c2, loading)
                Spacer(Modifier.width(8.dp))
                SlotHeaderText(member, name, abilityLabel, loading)
            }
            Spacer(Modifier.height(6.dp))
            if (loading) {
                Spacer(Modifier.weight(1f))
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    types.forEach { TypeSymbol(it, size = 20.dp) }
                }
                Spacer(Modifier.weight(1f))
                SlotItemRow(member)
                Spacer(Modifier.height(2.dp))
                Text(
                    spSpread(member),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The sprite circle, with a loading spinner or the special-form badge overlaid. */
@Composable
private fun SlotSprite(sprite: String, badge: String?, c1: Color, c2: Color, loading: Boolean) {
    Box(
        Modifier.size(44.dp).background(
            Brush.linearGradient(listOf(c1.copy(alpha = 0.28f), c2.copy(alpha = 0.28f))),
            CircleShape,
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = CardInk.copy(alpha = 0.55f),
            )
        } else {
            SpriteImage(
                model = sprite,
                contentDescription = null,
                size = 38.dp,
                spinnerColor = CardInk.copy(alpha = 0.55f),
            )
        }
        if (badge != null && !loading) {
            Surface(
                color = c1,
                shape = RoundedCornerShape(50),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Text(
                    badge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** Name (+ shiny sparkle, gender symbol) and the ability line under it. */
@Composable
private fun SlotHeaderText(member: TeamMember, name: String, abilityLabel: String?, loading: Boolean) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (member.shiny) {
                Text("✨", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(2.dp))
            }
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (member.gender != Gender.DEFAULT) {
                Spacer(Modifier.width(3.dp))
                Text(
                    member.gender.symbol,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (member.gender == Gender.MALE) Color(0xFF3B7DD8) else Color(0xFFD8407D),
                )
            }
        }
        Text(
            if (loading) "Loading…" else abilityLabel ?: "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The held-item icon + name row at the bottom of a filled slot card. */
@Composable
private fun SlotItemRow(member: TeamMember) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (member.item != null) {
            val itemSprite = heldItemSpriteUrl(member.item)
            if (itemSprite != null) {
                AsyncImage(
                    model = itemSprite,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(heldItemFallbackGlyph(member.item), style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(4.dp))
        }
        Text(
            member.item?.let { heldItemDisplayName(it) } ?: "No item",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
