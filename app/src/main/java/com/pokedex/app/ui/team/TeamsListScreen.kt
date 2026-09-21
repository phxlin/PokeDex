@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pokedex.app.ui.team

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pokedex.app.domain.team.Team
import com.pokedex.app.domain.team.formBadge
import com.pokedex.app.domain.team.heldItemDisplayName
import com.pokedex.app.domain.team.heldItemFallbackGlyph
import com.pokedex.app.domain.team.heldItemSpriteUrl
import com.pokedex.app.domain.team.megaPokemonSlugFor
import com.pokedex.app.domain.team.megaSpriteFor
import com.pokedex.app.ui.components.DexActionButton
import com.pokedex.app.ui.components.PokedexHeader
import com.pokedex.app.ui.components.SpriteImage
import com.pokedex.app.ui.theme.DexAvatarWell
import com.pokedex.app.ui.theme.DexCardInk
import com.pokedex.app.ui.theme.DexRed
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun TeamsListScreen(
    onOpenTeam: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onHome: (() -> Unit)? = null,
    viewModel: TeamListViewModel = hiltViewModel(),
) {
    val teams by viewModel.teams.collectAsStateWithLifecycle()
    val formSprites by viewModel.formSprites.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var teamPendingDelete by remember { mutableStateOf<Team?>(null) }
    // rememberSaveable so the confirmation survives rotation while the file picker is open.
    var pendingImport by rememberSaveable { mutableStateOf<Uri?>(null) }
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME)) { uri ->
        if (uri != null) viewModel.exportBackup(context.contentResolver, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImport = uri
    }

    ShowMessage(message, snackbarHost, onShown = viewModel::messageShown)

    Scaffold(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.createTeam(onOpenTeam) },
                containerColor = Color.White,
                contentColor = DexRed,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New team", fontWeight = FontWeight.Bold) },
            )
        },
    ) { padding ->
        // Only the bottom padding from the Scaffold: PokedexHeader already insets for the status
        // bar and paints behind it, like the other tabs' headers, so applying the top padding too
        // left a blank band above it.
        Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            PokedexHeader(
                title = "Teams",
                subtitle = "${teams.size} saved · Champions rules",
                onHome = onHome,
                actions = {
                    BackupMenu(
                        onExport = { exportLauncher.launch(backupFileName()) },
                        onImport = { importLauncher.launch(arrayOf(BACKUP_MIME, "text/plain", "application/octet-stream")) },
                        canDeleteAll = teams.isNotEmpty(),
                        onDeleteAll = { confirmDeleteAll = true },
                    )
                },
            )

            if (teams.isEmpty()) {
                NoTeamsYet()
            } else {
                TeamsList(
                    teams = teams,
                    formSprites = formSprites,
                    onOpenTeam = onOpenTeam,
                    onDelete = { teamPendingDelete = it },
                    onSwap = viewModel::swapTeams,
                )
            }
        }
    }

    teamPendingDelete?.let { team ->
        DeleteTeamDialog(
            team = team,
            onConfirm = {
                viewModel.deleteTeam(team.id)
                teamPendingDelete = null
            },
            onDismiss = { teamPendingDelete = null },
        )
    }
    pendingImport?.let { uri ->
        ReplaceTeamsDialog(
            teamCount = teams.size,
            onConfirm = {
                pendingImport = null
                viewModel.importBackup(context.contentResolver, uri)
            },
            onDismiss = { pendingImport = null },
        )
    }
    if (confirmDeleteAll) {
        DeleteAllDialog(
            teamCount = teams.size,
            onConfirm = {
                confirmDeleteAll = false
                viewModel.deleteAllTeams()
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }
}

private const val BACKUP_MIME = "application/json"

@Composable
private fun NoTeamsYet() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "No teams yet.\nTap “New team” to build one.",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

private fun savedTeams(count: Int) = if (count == 1) "1 saved team" else "$count saved teams"

@Composable
private fun DeleteTeamDialog(team: Team, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = "Delete “${team.name}”?",
        text = "This removes all ${team.members.size} Pokémon in it. This can't be undone.",
        confirmLabel = "Delete",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
private fun ReplaceTeamsDialog(teamCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = "Replace your teams?",
        text = "Importing replaces your ${savedTeams(teamCount)} on this device with the contents of the backup file.",
        confirmLabel = "Replace",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Deleting everything can't be undone, so the user has to type [DELETE_CONFIRM_PHRASE] first. */
@Composable
private fun DeleteAllDialog(teamCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = "Delete all data?",
        text = "Your ${savedTeams(teamCount)} on this device will be permanently removed. " +
            "Export a backup first if you might want ${if (teamCount == 1) "it" else "them"} back.",
        confirmLabel = "Delete everything",
        confirmPhrase = DELETE_CONFIRM_PHRASE,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** Shows [message] once in the snackbar, then reports it as consumed. */
@Composable
private fun ShowMessage(message: String?, host: SnackbarHostState, onShown: () -> Unit) {
    LaunchedEffect(message) {
        if (message != null) {
            host.showSnackbar(message)
            onShown()
        }
    }
}

private fun backupFileName() = "pokedex-backup-${LocalDate.now()}.json"

/**
 * Explicit colors: the default M3 dialog surface/text tokens are overridden app-wide for the
 * Pokédex "screen" look (dark ink on a pale screen), which leaves the dialog's un-overridden
 * elevated container dark — pairing that with our dark ink text reads as low-contrast. Match
 * the white-panel style every sheet/picker in this app already uses instead (see
 * SheetBg/SheetInk in Pickers.kt).
 *
 * With a [confirmPhrase] the confirm button stays disabled until that word has been typed, for
 * actions that can't be undone.
 */
@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmPhrase: String? = null,
) {
    // Local to the dialog, so it starts empty every time the dialog is opened.
    var typed by rememberSaveable { mutableStateOf("") }
    val confirmed = confirmPhrase == null || matchesConfirmPhrase(typed, confirmPhrase)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        titleContentColor = DexCardInk,
        textContentColor = DexCardInk,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text)
                if (confirmPhrase != null) PhraseField(typed, { typed = it }, confirmPhrase)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmed,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = DexRed,
                    disabledContentColor = DexCardInk.copy(alpha = 0.38f),
                ),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DexCardInk) } },
    )
}

@Composable
private fun PhraseField(value: String, onValueChange: (String) -> Unit, phrase: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Type $phrase to confirm") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = DexCardInk,
            unfocusedTextColor = DexCardInk,
            cursorColor = DexRed,
            focusedBorderColor = DexRed,
            unfocusedBorderColor = DexCardInk.copy(alpha = 0.5f),
            focusedLabelColor = DexRed,
            unfocusedLabelColor = DexCardInk.copy(alpha = 0.7f),
        ),
    )
}

/** Header overflow menu: save the teams to a file, restore them from one, or delete them all. */
@Composable
private fun BackupMenu(onExport: () -> Unit, onImport: () -> Unit, canDeleteAll: Boolean, onDeleteAll: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        DexActionButton(onClick = { expanded = true }, contentDescription = "Backup, restore and delete") {
            Icon(Icons.Default.MoreVert, contentDescription = null, tint = DexRed)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color.White) {
            DropdownMenuItem(
                text = { Text("Export backup", color = DexCardInk) },
                onClick = {
                    expanded = false
                    onExport()
                },
            )
            DropdownMenuItem(
                text = { Text("Import backup", color = DexCardInk) },
                onClick = {
                    expanded = false
                    onImport()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete all data", color = if (canDeleteAll) DexRed else DexCardInk.copy(alpha = 0.38f)) },
                enabled = canDeleteAll,
                onClick = {
                    expanded = false
                    onDeleteAll()
                },
            )
        }
    }
}

/** Accessible equivalents of the drag gesture, for the "Move …" custom actions. */
private fun teamReorderActions(
    index: Int,
    teams: List<Team>,
    team: Team,
    onSwap: (Long, Long) -> Unit,
): List<CustomAccessibilityAction> = buildList {
    if (index > 0) add(CustomAccessibilityAction("Move up") { onSwap(team.id, teams[index - 1].id); true })
    if (index < teams.lastIndex) add(CustomAccessibilityAction("Move down") { onSwap(team.id, teams[index + 1].id); true })
}

/** The scrollable team list, with long-press-drag reordering (see [tapOrDragToReorder]). */
@Composable
private fun TeamsList(
    teams: List<Team>,
    formSprites: Map<String, String>,
    onOpenTeam: (Long) -> Unit,
    onDelete: (Team) -> Unit,
    onSwap: (Long, Long) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // Disabled only once a row is actually being dragged (see the tapOrDragToReorder
    // call below for why this list can't disable scroll on every touch-down the way
    // TeamGrid/the move list do).
    var listTouchActive by remember { mutableStateOf(false) }
    var rowHeightPx by remember { mutableStateOf(0f) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var dropIndex by remember { mutableStateOf<Int?>(null) }

    if (teams.size > 1) {
        Text(
            "Press and hold a team to drag it to a new position.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = !listTouchActive,
        modifier = Modifier.testTag("teams_list"),
    ) {
        itemsIndexed(teams, key = { _, team -> team.id }) { index, team ->
            val isDragging = draggingId == team.id
            val isDropTarget = dropIndex == index && draggingId != null && !isDragging
            // tapOrDragToReorder is keyed on team.id (stable across a swap, so a
            // second drag doesn't get interrupted mid-gesture) — but that also means
            // its pointerInput coroutine is NOT relaunched when this row's index
            // shifts after a swap, so a directly-captured `index`/`teams` would stay
            // pinned to this row's position from the drag *before* last. Reading
            // through rememberUpdatedState instead makes the drag callbacks below
            // always see the current position, no matter how stale the coroutine is.
            val currentIndex by rememberUpdatedState(index)
            val currentTeams by rememberUpdatedState(teams)
            TeamRow(
                team,
                formSprites = formSprites,
                highlighted = isDropTarget,
                onDelete = { onDelete(team) },
                modifier = Modifier
                    .testTag("team_row_${team.id}")
                    .onSizeChanged { if (it.height > 0) rowHeightPx = it.height.toFloat() }
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = dragOffsetY
                            scaleX = 1.02f
                            scaleY = 1.02f
                            shadowElevation = 10f
                        }
                    }
                    .semantics {
                        role = Role.Button
                        semanticsOnClick(label = "Open ${team.name}") { onOpenTeam(team.id); true }
                        customActions = teamReorderActions(index, teams, team, onSwap)
                    }
                    .tapOrDragToReorder(
                        // Restarting on identity changes isn't a concern here: unlike
                        // TeamGrid's slots, a row's onClick/onSwap never change shape
                        // across recompositions, so a stable per-team key is enough —
                        // but it also means this coroutine outlives any single swap,
                        // so onDragStart/onDrag/onDragEnd read currentIndex/currentTeams
                        // (rememberUpdatedState) rather than the index/teams captured
                        // when the coroutine was first launched.
                        key = team.id,
                        // Unlike TeamGrid/the move list, this list IS the entire screen — almost
                        // every scroll the user makes starts with a finger on a row, so disabling
                        // scroll at raw touch-down (before we even know this is a drag) would make
                        // ordinary scrolling unreliable here specifically. Gate it on onDragStart
                        // instead: a stationary long-press never triggers the ancestor's own
                        // touch-slop scroll-claim in the first place, so this loses nothing for
                        // genuine holds while leaving ordinary swipes alone.
                        onActiveChange = { if (!it) listTouchActive = false },
                        onClick = { onOpenTeam(team.id) },
                        onDragStart = {
                            listTouchActive = true
                            draggingId = team.id
                            dragOffsetY = 0f
                            dropIndex = currentIndex
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { delta ->
                            dragOffsetY += delta.y
                            if (rowHeightPx > 0f) {
                                val moved = (dragOffsetY / rowHeightPx).roundToInt()
                                dropIndex = (currentIndex + moved).coerceIn(0, currentTeams.lastIndex)
                            }
                        },
                        onDragEnd = {
                            listTouchActive = false
                            val target = dropIndex
                            if (target != null && target != currentIndex) {
                                onSwap(team.id, currentTeams[target].id)
                            }
                            draggingId = null
                            dropIndex = null
                            dragOffsetY = 0f
                        },
                    ),
            )
        }
        item { Spacer(Modifier.size(72.dp)) }
    }
}

@Composable
private fun TeamRow(
    team: Team,
    formSprites: Map<String, String>,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Surface(
        color = Color.White,
        contentColor = DexCardInk,
        shape = RoundedCornerShape(16.dp),
        border = if (highlighted) BorderStroke(2.dp, DexRed) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(team.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${team.members.size}/6", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete team", tint = DexRed)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                repeat(6) { i ->
                    val m = team.members.firstOrNull { it.slot == i }
                    Box(
                        Modifier.size(44.dp).background(DexAvatarWell, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (m != null) {
                            val megaFormUrl = megaPokemonSlugFor(m.item)
                                ?.let { formSprites[formSpriteKey(it, m.shiny)] }?.takeIf { it.isNotEmpty() }
                            val model = megaFormUrl
                                ?: megaSpriteFor(m.item, m.shiny)
                                ?: m.formSlug?.let { formSprites[formSpriteKey(it, m.shiny)] }?.takeIf { it.isNotEmpty() }
                                ?: m.spriteUrl
                            SpriteImage(
                                model = model,
                                contentDescription = m.displayName,
                                size = 40.dp,
                                spinnerColor = DexRed.copy(alpha = 0.6f),
                            )
                            val badge = formBadge(m.formSlug, m.item)
                            if (badge != null) {
                                Text(
                                    badge.first().toString(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(if (badge.startsWith("MEGA")) DexRed else Color(0xFF00897B), CircleShape)
                                        .padding(horizontal = 4.dp),
                                )
                            }
                            if (m.item != null) {
                                val itemSpriteUrl = heldItemSpriteUrl(m.item)
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(18.dp)
                                        .background(Color.White, CircleShape)
                                        .border(1.dp, DexAvatarWell, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (itemSpriteUrl != null) {
                                        AsyncImage(
                                            model = itemSpriteUrl,
                                            contentDescription = heldItemDisplayName(m.item),
                                            modifier = Modifier.size(14.dp),
                                        )
                                    } else {
                                        Text(heldItemFallbackGlyph(m.item), fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
