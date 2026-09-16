@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pokedex.app.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokedex.app.domain.team.TeamMember
import com.pokedex.app.domain.team.TypeChart
import com.pokedex.app.domain.team.TypeMatchup
import com.pokedex.app.ui.components.SpriteImage
import com.pokedex.app.ui.components.TypeSymbol
import com.pokedex.app.ui.components.typeColor
import com.pokedex.app.ui.theme.DexAvatarWell

/** Memoised slice of the team analysis — recomputed only when its real inputs change. */
private class AnalysisSnapshot(
    val sharedWeaknesses: List<TypeMatchup>,
    val defensiveByMon: List<MonDefense>,
    val speedOrder: List<Pair<TeamMember, Int>>,
    val offensiveCoverage: Set<String>,
    val offensiveByMon: List<MonOffense>,
)

@Composable
internal fun AnalysisCard(state: TeamEditorUiState) {
    // The coverage getters on TeamEditorUiState are recomputed on every read, and the
    // whole card recomposes on every ViewModel emission (forms, per-move / per-item /
    // per-ability info streaming in). Memoise on the inputs the analysis actually uses
    // so item/ability effect loading no longer re-runs the type maths.
    val analysis = remember(state.members, state.forms, state.chart, state.moveInfo) {
        AnalysisSnapshot(
            sharedWeaknesses = state.sharedWeaknesses,
            defensiveByMon = state.defensiveByMon,
            speedOrder = state.speedOrder,
            offensiveCoverage = state.offensiveCoverage,
            offensiveByMon = state.offensiveByMon,
        )
    }
    Surface(color = Color.White, contentColor = CardInk, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Team analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (state.chart == null) {
                Spacer(Modifier.height(6.dp))
                Text("Loading type chart…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }

            Spacer(Modifier.height(12.dp))
            SectionLabel("Shared weaknesses")
            val shared = analysis.sharedWeaknesses
            if (shared.isEmpty()) {
                Text("None — no type hits 3+ of your Pokémon.", style = MaterialTheme.typography.bodySmall)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shared.forEach { m ->
                        Surface(color = typeColor(m.type), shape = RoundedCornerShape(50)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            ) {
                                TypeSymbol(m.type, size = 18.dp)
                                Text(
                                    "×${m.weak}",
                                    color = Color.White, fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Defensive coverage")
            SubLabel("How each Pokémon takes hits. A Pokémon that can Mega gets its own line for each form.")
            Spacer(Modifier.height(6.dp))
            CoverageLegend(
                "weak" to Color(0xFFE57373),
                "4×" to Color(0xFFB71C1C),
                "resist" to Color(0xFF81C784),
                "immune" to Color(0xFF5C6BC0),
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                analysis.defensiveByMon.forEach { mon -> MonDefenseRow(mon) }
            }

            Spacer(Modifier.height(16.dp))
            SectionLabel("Offensive coverage")
            val covered = analysis.offensiveCoverage
            SubLabel("What each Pokémon's damaging moves hit.")
            Spacer(Modifier.height(6.dp))
            CoverageLegend(
                "super-effective" to Color(0xFF81C784),
                "resisted / immune" to Color(0xFFE57373),
            )
            val uncovered = TypeChart.TYPES.filter { it !in covered }
            if (covered.isNotEmpty() && uncovered.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("No super-effective move vs ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    uncovered.forEach {
                        TypeSymbol(it, size = 15.dp)
                        Spacer(Modifier.width(2.dp))
                    }
                }
            } else if (covered.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Every type is hit super-effectively by something.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2E7D32),
                )
            }
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                analysis.offensiveByMon.forEach { mon -> MonOffenseRow(mon) }
            }

            if (analysis.speedOrder.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Speed order")
                analysis.speedOrder.forEach { (m, spe) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Text(m.displayName, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text("$spe", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SubLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** A compact colour key: swatch + label pairs. */
@Composable
private fun CoverageLegend(vararg entries: Pair<String, Color>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        entries.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(11.dp).background(color, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = CardInk)
            }
        }
    }
}

@Composable
private fun MonDefenseRow(mon: MonDefense) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(DexAvatarWell), contentAlignment = Alignment.Center) {
            SpriteImage(model = mon.spriteUrl, contentDescription = null, size = 34.dp)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(mon.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            val weak = mon.weak4 + mon.weak2
            if (weak.isNotEmpty()) {
                DefTypeRow("⚔", weak) { t ->
                    val quad = t in mon.weak4
                    DefChip(t, if (quad) Color(0xFFB71C1C) else Color(0xFFE57373), tag = if (quad) "4×" else null)
                }
            }
            val safe = mon.resist + mon.immune
            if (safe.isNotEmpty()) {
                DefTypeRow("🛡", safe) { t ->
                    val imm = t in mon.immune
                    DefChip(t, if (imm) Color(0xFF5C6BC0) else Color(0xFF81C784), tag = if (imm) "⊘" else null)
                }
            }
            if (weak.isEmpty() && safe.isEmpty()) {
                Text("Neutral to everything.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DefTypeRow(prefix: String, types: List<String>, chip: @Composable (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(prefix, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp))
        types.forEach { chip(it) }
    }
}

@Composable
private fun DefChip(type: String, bg: Color, tag: String?) {
    Box(
        Modifier.background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypeSymbol(type, size = 15.dp)
            if (tag != null) {
                Spacer(Modifier.width(1.dp))
                Text(tag, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun MonOffenseRow(mon: MonOffense) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(DexAvatarWell), contentAlignment = Alignment.Center) {
            SpriteImage(model = mon.spriteUrl, contentDescription = null, size = 34.dp)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(mon.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                mon.moveTypes.forEach {
                    TypeSymbol(it, size = 14.dp)
                    Spacer(Modifier.width(2.dp))
                }
            }
            when {
                mon.noMoves -> Text(
                    "No damaging moves set.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    if (mon.superEffective.isNotEmpty()) {
                        DefTypeRow("💥", mon.superEffective) { DefChip(it, Color(0xFF81C784), tag = null) }
                    }
                    if (mon.resisted.isNotEmpty()) {
                        DefTypeRow("🛡", mon.resisted) { DefChip(it, Color(0xFFE57373), tag = null) }
                    }
                    if (mon.superEffective.isEmpty() && mon.resisted.isEmpty()) {
                        Text(
                            "Hits everything for neutral damage.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
