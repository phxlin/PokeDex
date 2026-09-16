package com.pokedex.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokedex.app.ui.theme.DexLedGreen
import com.pokedex.app.ui.theme.DexLedRed
import com.pokedex.app.ui.theme.DexLedYellow
import com.pokedex.app.ui.theme.DexLensCyan
import com.pokedex.app.ui.theme.DexLensDeep
import com.pokedex.app.ui.theme.DexLensGlass
import com.pokedex.app.ui.theme.DexLensRim
import com.pokedex.app.ui.theme.DexRed
import com.pokedex.app.ui.theme.DexRedDark
import com.pokedex.app.ui.theme.DexRedShadow
import com.pokedex.app.ui.theme.DexScreenBezel
import com.pokedex.app.ui.theme.DexScreenBottom
import com.pokedex.app.ui.theme.DexScreenTop

/**
 * The red "device" header from the anime Pokédex: a big blinking blue lens,
 * a row of indicator LEDs, and a chunky title. Sits behind the status bar.
 */
@Composable
fun PokedexHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(DexRed, DexRedDark)))
            .statusBarsPadding()
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PokedexLens(
                modifier = Modifier
                    .size(46.dp)
                    .then(
                        if (onHome != null) {
                            Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onHome, role = Role.Button)
                                .semantics { contentDescription = "Home" }
                        } else {
                            Modifier
                        },
                    ),
            )
            Spacer(Modifier.width(12.dp))
            LedCluster()
            Spacer(Modifier.weight(1f))
            actions()
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Column {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = Color.White,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
fun PokedexLens(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "lens")
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "glow",
    )
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = center
        drawCircle(DexRedShadow, radius = r, center = c.copy(y = c.y + r * 0.08f))
        drawCircle(DexLensRim, radius = r)
        drawCircle(DexLensCyan, radius = r * 0.84f)
        drawCircle(DexLensGlass, radius = r * 0.6f)
        drawCircle(DexLensDeep.copy(alpha = glow), radius = r * 0.6f)
        drawCircle(
            Color.White.copy(alpha = 0.85f),
            radius = r * 0.16f,
            center = Offset(c.x - r * 0.28f, c.y - r * 0.28f),
        )
    }
}

@Composable
fun LedCluster(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Led(DexLedRed, delayMillis = 0)
        Led(DexLedYellow, delayMillis = 250)
        Led(DexLedGreen, delayMillis = 500)
    }
}

@Composable
private fun Led(color: Color, delayMillis: Int) {
    val transition = rememberInfiniteTransition(label = "led")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 700, delayMillis = delayMillis),
            RepeatMode.Reverse,
        ),
        label = "ledAlpha",
    )
    Box(
        Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

/** A round, glossy "device button" for header actions. */
@Composable
fun DexActionButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    badge: Boolean = false,
    icon: @Composable () -> Unit,
) {
    Box(modifier.size(44.dp), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE2E2E2))))
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(DexLedGreen),
            )
        }
    }
}

/**
 * The recessed "screen" the entries are displayed on: a pale-blue LCD framed by
 * a dark bezel, the way the anime Pokédex shows a Pokémon.
 */
@Composable
fun ScreenSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(DexScreenBezel)
            .padding(5.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Brush.verticalGradient(listOf(DexScreenTop, DexScreenBottom))),
        content = content,
    )
}

@Composable
fun ScreenMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
