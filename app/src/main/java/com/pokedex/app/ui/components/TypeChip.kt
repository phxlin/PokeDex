package com.pokedex.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pokedex.app.R
import java.util.Locale

/** The 18 filterable Pokémon types, in the conventional Pokédex order. */
val POKEMON_TYPES: List<String> = listOf(
    "normal", "fire", "water", "electric", "grass", "ice",
    "fighting", "poison", "ground", "flying", "psychic", "bug",
    "rock", "ghost", "dragon", "dark", "steel", "fairy",
)

private val TYPE_COLORS: Map<String, Color> = mapOf(
    "normal" to Color(0xFFA8A77A),
    "fire" to Color(0xFFEE8130),
    "water" to Color(0xFF6390F0),
    "electric" to Color(0xFFF7D02C),
    "grass" to Color(0xFF7AC74C),
    "ice" to Color(0xFF96D9D6),
    "fighting" to Color(0xFFC22E28),
    "poison" to Color(0xFFA33EA1),
    "ground" to Color(0xFFE2BF65),
    "flying" to Color(0xFFA98FF3),
    "psychic" to Color(0xFFF95587),
    "bug" to Color(0xFFA6B91A),
    "rock" to Color(0xFFB6A136),
    "ghost" to Color(0xFF735797),
    "dragon" to Color(0xFF6F35FC),
    "dark" to Color(0xFF705746),
    "steel" to Color(0xFFB7B7CE),
    "fairy" to Color(0xFFD685AD),
    "stellar" to Color(0xFF3FA9AB),
)

fun typeColor(type: String): Color = TYPE_COLORS[type.lowercase()] ?: Color(0xFF777777)

/** Bundled Scarlet/Violet type-symbol drawable for [type], or null (e.g. "stellar"). */
@DrawableRes
private fun typeSymbolRes(type: String): Int? = when (type.lowercase()) {
    "normal" -> R.drawable.type_normal
    "fire" -> R.drawable.type_fire
    "water" -> R.drawable.type_water
    "electric" -> R.drawable.type_electric
    "grass" -> R.drawable.type_grass
    "ice" -> R.drawable.type_ice
    "fighting" -> R.drawable.type_fighting
    "poison" -> R.drawable.type_poison
    "ground" -> R.drawable.type_ground
    "flying" -> R.drawable.type_flying
    "psychic" -> R.drawable.type_psychic
    "bug" -> R.drawable.type_bug
    "rock" -> R.drawable.type_rock
    "ghost" -> R.drawable.type_ghost
    "dragon" -> R.drawable.type_dragon
    "dark" -> R.drawable.type_dark
    "steel" -> R.drawable.type_steel
    "fairy" -> R.drawable.type_fairy
    else -> null
}

/**
 * The round Scarlet/Violet type badge. The 18 icons are bundled as drawables and
 * drawn synchronously — the team-analysis screen renders a few hundred of these,
 * so an async image loader per instance was a real source of jank. Unknown types
 * (e.g. "stellar") fall back to a coloured circle with the type's initial.
 */
@Composable
fun TypeSymbol(type: String, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    val res = typeSymbolRes(type)
    if (res != null) {
        Image(
            painter = painterResource(res),
            contentDescription = type,
            modifier = modifier.size(size),
        )
    } else {
        val bg = typeColor(type)
        Box(
            modifier.size(size).background(bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                type.take(1).uppercase(Locale.ROOT),
                color = if (bg.luminance() > 0.55f) Color(0xFF1A1A1A) else Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
fun TypeChip(type: String, modifier: Modifier = Modifier) {
    val bg = typeColor(type)
    val fg = if (bg.luminance() > 0.55f) Color(0xFF1A1A1A) else Color.White
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(
            text = type.replaceFirstChar { it.titlecase(Locale.ROOT) },
            color = fg,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(PaddingValues(horizontal = 12.dp, vertical = 4.dp)),
        )
    }
}

/** A toggleable type chip for the filter sheet. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SelectableTypeChip(
    type: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val base = typeColor(type)
    val container = if (selected) base else Color.Transparent
    val fg = when {
        selected && base.luminance() > 0.55f -> Color(0xFF1A1A1A)
        selected -> Color.White
        else -> Color(0xFF12363F)
    }
    Surface(
        onClick = onClick,
        color = container,
        shape = RoundedCornerShape(50),
        border = if (selected) null else BorderStroke(1.5.dp, base),
        modifier = modifier,
    ) {
        Text(
            text = type.replaceFirstChar { it.titlecase(Locale.ROOT) },
            color = fg,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
