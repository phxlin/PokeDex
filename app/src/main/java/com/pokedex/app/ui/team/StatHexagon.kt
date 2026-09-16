package com.pokedex.app.ui.team

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.pokedex.app.domain.team.StatKey
import com.pokedex.app.ui.theme.DexStatDown
import com.pokedex.app.ui.theme.DexStatUp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The classic six-point stat hexagon: HP at the top, then Atk / Def / Spe / SpD / SpA
 * clockwise. Stats are scaled against [maxStat].
 */
@Composable
fun StatHexagon(
    stats: Map<StatKey, Int>,
    modifier: Modifier = Modifier,
    base: Map<StatKey, Int>? = null,
    raisedStat: StatKey? = null,
    loweredStat: StatKey? = null,
    fill: Color = Color(0xFFE3350D),
    baseFill: Color = Color(0xFF5C7A87),
    axis: Color = Color(0xFFB9CBD3),
    label: Color = Color(0xFF3A5560),
    maxStat: Int = 220,
) {
    val raisedColor = DexStatUp.toArgb()
    val loweredColor = DexStatDown.toArgb()
    val order = listOf(StatKey.HP, StatKey.ATK, StatKey.DEF, StatKey.SPE, StatKey.SPD, StatKey.SPA)
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = minOf(cx, cy) * 0.62f

            fun point(i: Int, r: Float): Offset {
                val angle = -PI / 2 + i * (2 * PI / 6)
                return Offset(cx + (r * cos(angle)).toFloat(), cy + (r * sin(angle)).toFloat())
            }

            for (ring in 1..3) {
                val rr = radius * ring / 3f
                val p = Path()
                (0..5).forEach { i ->
                    val pt = point(i, rr)
                    if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
                }
                p.close()
                drawPath(p, axis.copy(alpha = 0.35f), style = Stroke(width = 1.5f))
            }
            (0..5).forEach { i ->
                drawLine(axis.copy(alpha = 0.4f), point(i, 0f), point(i, radius), strokeWidth = 1.5f)
            }

            fun polygon(values: Map<StatKey, Int>): Path {
                val path = Path()
                order.forEachIndexed { i, key ->
                    val v = (values[key] ?: 0).coerceIn(0, maxStat).toFloat() / maxStat
                    val pt = point(i, radius * (0.06f + 0.94f * v))
                    if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }
                path.close()
                return path
            }

            // Ghost polygon: the Pokémon's raw base stats at Lv. 50, for reference.
            if (base != null) {
                val basePath = polygon(base)
                drawPath(basePath, baseFill.copy(alpha = 0.12f))
                drawPath(
                    basePath,
                    baseFill.copy(alpha = 0.55f),
                    style = Stroke(
                        width = 2f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(6f, 6f),
                        ),
                    ),
                )
            }

            val dataPath = polygon(stats)
            drawPath(dataPath, fill.copy(alpha = 0.26f))
            drawPath(dataPath, fill, style = Stroke(width = 3f))

            val paint = android.graphics.Paint().apply {
                color = label.toArgb()
                textSize = 10.5.dp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
            }
            order.forEachIndexed { i, key ->
                val pt = point(i, radius + 16.dp.toPx())
                paint.color = when (key) {
                    raisedStat -> raisedColor
                    loweredStat -> loweredColor
                    else -> label.toArgb()
                }
                val mark = when (key) {
                    raisedStat -> "▲ "
                    loweredStat -> "▼ "
                    else -> ""
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "$mark${key.short} ${stats[key] ?: 0}",
                    pt.x,
                    pt.y + paint.textSize / 3,
                    paint,
                )
            }
        }
    }
}
