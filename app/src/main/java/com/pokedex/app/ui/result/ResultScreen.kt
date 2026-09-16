package com.pokedex.app.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pokedex.app.core.PokemonNames
import com.pokedex.app.data.classifier.ClassificationResult
import kotlinx.serialization.json.Json

@Composable
fun ResultScreen(
    payload: String,
    onOpenPokemon: (String) -> Unit,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val result = remember(payload) {
        runCatching { Json.decodeFromString(ClassificationResult.serializer(), payload) }.getOrNull()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (result == null) {
                Text("Couldn't read the result.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(20.dp))
                Button(onClick = onTryAgain) { Text("Try again") }
                return@Column
            }

            val maybeName = result.name
            val icon = if (maybeName != null) Icons.Default.CatchingPokemon else Icons.Default.HelpOutline
            Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))

            if (maybeName != null) {
                Text(
                    "Might be ${PokemonNames.displayName(PokemonNames.normalize(maybeName))}",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Only ${result.confidencePercent}% sure — ${result.reason}",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { onOpenPokemon(PokemonNames.normalize(maybeName)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open it anyway") }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onTryAgain, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
            } else {
                Text(
                    "That's not a Pokémon",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    result.reason,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onTryAgain, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
            }
        }
    }
}
