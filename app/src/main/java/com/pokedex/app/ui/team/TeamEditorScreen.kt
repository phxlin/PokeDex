package com.pokedex.app.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.material3.MaterialTheme
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Shared across the team-editor screens (this file, TeamGrid.kt, MemberEditor.kt, AnalysisCard.kt).
internal val CardInk = Color(0xFF15323C)
internal val CardWell = Color(0xFFEAF2F5)

/**
 * A press-and-hold-to-drag reorder handler for one item, shared by the team grid and the
 * move list: release before the system long-press timeout and it's a tap ([onClick]);
 * hold past it and the item is picked up instead, tracking drag deltas via [onDrag] until
 * release ([onDragEnd]). Both are handled by this single gesture — rather than a separate
 * `Surface(onClick = …)` alongside a drag detector — because two independent detectors on
 * a parent/child pair race the same touch stream and the child's plain click reliably wins
 * on release regardless of how the long-press-then-drag resolved.
 *
 * A quick swipe that starts on the item (scrolling the page past it) also fails the long
 * press, but must *not* fire [onClick]: the ancestor scrollable claims that gesture by
 * consuming its position changes, and [awaitLongPressOrCancellation] returns `null` for
 * that exactly the same way it does for a genuine tap-up. The two are told apart by
 * checking whether the pointer was actually released, unconsumed, right after — mirroring
 * how Compose's own `detectTapGestures` only fires `onTap` on a real, unconsumed up.
 *
 * [onActiveChange] fires `true` the instant a finger touches down and `false` once the
 * gesture (tap or drag) fully resolves. Callers whose reorder axis matches a scrollable
 * ancestor (the move list drags vertically inside a vertically-scrolling screen; the grid
 * can too, for a same-column swap) should disable that ancestor's scrolling while active —
 * otherwise the scrollable's own touch-slop detection claims the gesture as a page scroll
 * before the long-press timeout ever completes, and every hold-and-drag just reads as a tap.
 */
internal fun Modifier.tapOrDragToReorder(
    key: Any?,
    onActiveChange: (Boolean) -> Unit = {},
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown()
        onActiveChange(true)
        try {
            val longPress = awaitLongPressOrCancellation(down.id)
            if (longPress == null) {
                // Null means either "released before the timeout" (a tap) or "something
                // else — an ancestor scroll — claimed the gesture" (not a tap). Only the
                // former leaves the pointer up and unconsumed.
                val change = currentEvent.changes.firstOrNull { it.id == down.id }
                if (change != null && !change.isConsumed && !change.pressed) {
                    onClick()
                }
            } else {
                onDragStart()
                drag(down.id) { change ->
                    // Read the delta before consuming — positionChange() returns Offset.Zero
                    // for an already-consumed change, unlike positionChangeIgnoreConsumed().
                    onDrag(change.positionChange())
                    change.consume()
                }
                onDragEnd()
            }
        } finally {
            onActiveChange(false)
        }
    }
}

@Composable
fun TeamEditorScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TeamEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingSlot by remember { mutableStateOf<Int?>(null) }
    var reusingSlot by remember { mutableStateOf<Int?>(null) }

    val open = state.openSlot
    val openMember = open?.let { s -> state.members.firstOrNull { it.slot == s } }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (open != null && openMember != null) {
            val forms = state.forms[open].orEmpty()
            MemberEditor(
                member = openMember,
                moveInfo = state.moveInfo,
                itemInfo = state.itemInfo,
                abilityInfo = state.abilityInfo,
                forms = forms,
                selectedForm = state.selectedFormIndex(open),
                onSelectForm = { viewModel.selectForm(open, it) },
                onBack = viewModel::closeSlot,
                onHome = onHome,
                onRemove = { viewModel.removeMember(open) },
                viewModel = viewModel,
            )
        } else {
            TeamOverview(
                state = state,
                onBack = onBack,
                onHome = onHome,
                onRename = viewModel::renameTeam,
                onAddSlot = { pickingSlot = it },
                onOpenSlot = viewModel::openSlot,
                onSwapSlots = viewModel::swapSlots,
            )
        }
    }

    pickingSlot?.let { slot ->
        PokemonPickerSheet(
            onDismiss = { pickingSlot = null },
            onPick = { id, name -> viewModel.addPokemon(slot, id, name) },
            onBrowseExisting = { pickingSlot = null; reusingSlot = slot },
        )
    }

    reusingSlot?.let { slot ->
        ReuseMemberPickerSheet(
            teams = state.otherTeams,
            excludeSpeciesIds = state.members.filter { it.slot != slot }.map { it.speciesId }.toSet(),
            onDismiss = { reusingSlot = null },
            onPick = { member -> viewModel.addFromMember(slot, member) },
        )
    }
}
