package com.pokedex.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

/**
 * A Pokémon sprite/artwork [AsyncImage] that shows a small spinner in place of the
 * usual blank gap while Coil is still fetching it over the network.
 */
@Composable
fun SpriteImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    spinnerColor: Color = Color.Gray.copy(alpha = 0.6f),
) {
    var loadState by remember(model) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    Box(
        modifier = if (size != null) modifier.size(size) else modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (loadState is AsyncImagePainter.State.Loading) {
            CircularProgressIndicator(
                modifier = Modifier.size((size ?: 40.dp) * 0.4f),
                strokeWidth = 2.dp,
                color = spinnerColor,
            )
        }
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            onState = { loadState = it },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
