package com.pokedex.app.data.classifier

import com.pokedex.app.BuildConfig
import com.pokedex.app.core.ScaledImage
import com.pokedex.app.data.remote.anthropic.AnthropicMessage
import com.pokedex.app.data.remote.anthropic.AnthropicRequest
import com.pokedex.app.data.remote.anthropic.AnthropicService
import com.pokedex.app.data.remote.anthropic.ContentBlock
import com.pokedex.app.data.remote.anthropic.ImageSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

class AnthropicPokemonClassifier @Inject constructor(
    private val service: AnthropicService,
    private val io: CoroutineDispatcher,
) : PokemonClassifier {

    override suspend fun classify(image: ScaledImage): ClassificationResult = withContext(io) {
        if (BuildConfig.ANTHROPIC_API_KEY.isBlank()) {
            throw ClassifierException(
                "No Anthropic API key configured. Add ANTHROPIC_API_KEY to local.properties (see the README).",
            )
        }

        val request = AnthropicRequest(
            model = MODEL,
            maxTokens = 512,
            system = SYSTEM_PROMPT,
            messages = listOf(
                AnthropicMessage(
                    role = "user",
                    content = listOf(
                        ContentBlock.Image(
                            ImageSource(mediaType = image.mediaType, data = image.base64),
                        ),
                        ContentBlock.Text(USER_PROMPT),
                    ),
                ),
            ),
        )

        val text = try {
            service.createMessage(request).firstText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            throw ClassifierException(httpMessage(e), e)
        } catch (e: IOException) {
            throw ClassifierException("Network error reaching the classifier. Check your connection.", e)
        } catch (e: Exception) {
            throw ClassifierException(e.message ?: "The classifier request failed.", e)
        }

        if (text.isNullOrBlank()) {
            throw ClassifierException("The classifier returned an empty response.")
        }
        ClassificationParser.parse(text)
    }

    private fun httpMessage(e: HttpException): String = when (e.code()) {
        401 -> "The Anthropic API key was rejected (401). Check ANTHROPIC_API_KEY."
        429 -> "Rate limited by the Anthropic API (429). Wait a moment and try again."
        in 500..599 -> "The Anthropic API is unavailable right now (${e.code()})."
        else -> "The classifier request failed (${e.code()})."
    }

    companion object {
        const val MODEL = "claude-sonnet-4-6"

        const val SYSTEM_PROMPT =
            "You identify whether a photo contains a Pokémon (including toys, plush, trading cards, " +
                "drawings, screenshots, or costumes). Respond ONLY with JSON: " +
                "{\"is_pokemon\": bool, \"name\": string|null, \"confidence\": 0-1, \"reason\": short string}. " +
                "If it is not a Pokémon, set name to null and describe what it actually is in reason."

        const val USER_PROMPT =
            "Identify whether this image contains a Pokémon. Reply with only the JSON object."
    }
}
