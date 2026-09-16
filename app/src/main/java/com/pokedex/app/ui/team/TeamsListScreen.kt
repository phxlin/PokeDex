@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pokedex.app.ui.team

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import com.pokedex.app.ui.components.PokedexHeader
import com.pokedex.app.ui.components.SpriteImage
import com.pokedex.app.ui.theme.DexAvatarWell
import com.pokedex.app.ui.theme.DexCardInk
import com.pokedex.app.ui.theme.DexRed

@Composable
fun TeamsListScreen(
    onOpenTeam: (Long) -> Unit,
    onHome: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: TeamListViewModel = hiltViewModel(),
) {
    val teams by viewModel.teams.collectAsStateWithLifecycle()
    val formSprites by viewModel.formSprites.collectAsStateWithLifecycle()
    var teamPendingDelete by remember { mutableStateOf<Team?>(null) }

    Scaffold(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        containerColor = MaterialTheme.colorScheme.background,
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
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            PokedexHeader(
                title = "Teams",
                subtitle = "${teams.size} saved · Champions rules",
                onHome = onHome,
            )

            if (teams.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No teams yet.\nTap “New team” to build one.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(teams, key = { it.id }) { team ->
                        TeamRow(
                            team,
                            formSprites = formSprites,
                            onClick = { onOpenTeam(team.id) },
                            onDelete = { teamPendingDelete = team },
                        )
                    }
                    item { Spacer(Modifier.size(72.dp)) }
                }
            }
        }
    }

    teamPendingDelete?.let { team ->
        // Explicit colors: the default M3 dialog surface/text tokens are overridden
        // app-wide for the Pokédex "screen" look (dark ink on a pale screen), which
        // leaves the dialog's un-overridden elevated container dark — pairing that
        // with our dark ink text reads as low-contrast. Match the white-panel style
        // every sheet/picker in this app already uses instead (see SheetBg/SheetInk
        // in Pickers.kt).
        AlertDialog(
            onDismissRequest = { teamPendingDelete = null },
            containerColor = Color.White,
            titleContentColor = DexCardInk,
            textContentColor = DexCardInk,
            title = { Text("Delete “${team.name}”?") },
            text = { Text("This removes all ${team.members.size} Pokémon in it. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTeam(team.id)
                    teamPendingDelete = null
                }) {
                    Text("Delete", color = DexRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { teamPendingDelete = null }) { Text("Cancel", color = DexCardInk) }
            },
        )
    }
}

@Composable
private fun TeamRow(
    team: Team,
    formSprites: Map<String, String>,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = Color.White,
        contentColor = DexCardInk,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
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
