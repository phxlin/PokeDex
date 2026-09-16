@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.pokedex.app.ui.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokedex.app.ui.components.SelectableTypeChip
import com.pokedex.app.ui.theme.DexReadout
import com.pokedex.app.ui.theme.DexReadoutDim
import com.pokedex.app.ui.theme.DexRed
import com.pokedex.app.ui.theme.DexScreenTop

@Composable
fun FilterSortSheet(
    controls: ListControls,
    onDismiss: () -> Unit,
    onSort: (SortOption) -> Unit,
    onToggleType: (String) -> Unit,
    onToggleGeneration: (Int) -> Unit,
    onClear: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DexScreenTop,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Section("Sort by") {
                SortOption.entries.forEach { option ->
                    PillToggle(
                        label = option.label,
                        selected = controls.sort == option,
                        onClick = { onSort(option) },
                    )
                }
            }

            Section("Generation") {
                Generation.entries.forEach { gen ->
                    PillToggle(
                        label = "${gen.label} · ${gen.region}",
                        selected = gen.number in controls.generations,
                        onClick = { onToggleGeneration(gen.number) },
                    )
                }
            }

            Section("Type") {
                com.pokedex.app.ui.components.POKEMON_TYPES.forEach { type ->
                    SelectableTypeChip(
                        type = type,
                        selected = type in controls.types,
                        onClick = { onToggleType(type) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onClear,
                    enabled = controls.hasActiveFilters,
                ) {
                    Text(
                        "Clear filters",
                        color = if (controls.hasActiveFilters) DexReadout else DexReadoutDim,
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable FlowRowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = DexReadoutDim,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun PillToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (selected) DexRed else Color.Transparent,
        shape = RoundedCornerShape(50),
        border = if (selected) null else BorderStroke(1.dp, DexReadout.copy(alpha = 0.3f)),
    ) {
        Text(
            label,
            color = if (selected) Color.White else DexReadout,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

/** Removable chips shown under the search bar for the currently-applied filters. */
@Composable
fun FilterChipsRow(
    types: Set<String>,
    generations: Set<Int>,
    onRemoveType: (String) -> Unit,
    onRemoveGeneration: (Int) -> Unit,
    onClear: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        generations.sorted().forEach { n ->
            RemovableChip(Generation.ofNumber(n)?.let { "Gen ${it.label}" } ?: "Gen $n") { onRemoveGeneration(n) }
        }
        types.forEach { t ->
            RemovableChip(t.replaceFirstChar { it.uppercase() }) { onRemoveType(t) }
        }
        if (types.size + generations.size > 1) {
            Surface(
                onClick = onClear,
                color = Color.Transparent,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
            ) {
                Text(
                    "Clear all",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    Surface(
        onClick = onRemove,
        color = Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        ) {
            Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(2.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove $label",
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
