package com.pokedex.app.data.classifier

import com.pokedex.app.core.ScaledImage

/**
 * Identifies whether an image contains a Pokémon. The Anthropic-backed
 * implementation lives in [AnthropicPokemonClassifier]; this interface exists so
 * it can later be swapped for an on-device TFLite model with no UI changes.
 */
interface PokemonClassifier {
    suspend fun classify(image: ScaledImage): ClassificationResult
}

@kotlinx.serialization.Serializable
data class ClassificationResult(
    val isPokemon: Boolean,
    val name: String?,
    val confidence: Double,
    val reason: String,
) {
    val confidencePercent: Int get() = (confidence.coerceIn(0.0, 1.0) * 100).toInt()
}

/** Thrown for transport/auth problems so the UI can show a clear message + retry. */
class ClassifierException(message: String, cause: Throwable? = null) : Exception(message, cause)
