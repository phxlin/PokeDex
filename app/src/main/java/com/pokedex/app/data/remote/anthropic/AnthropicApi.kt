package com.pokedex.app.data.remote.anthropic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface AnthropicService {
    @POST("messages")
    suspend fun createMessage(@Body request: AnthropicRequest): AnthropicResponse
}

@Serializable
data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<AnthropicMessage>,
)

@Serializable
data class AnthropicMessage(
    val role: String,
    val content: List<ContentBlock>,
)

@Serializable
sealed interface ContentBlock {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ContentBlock

    @Serializable
    @SerialName("image")
    data class Image(val source: ImageSource) : ContentBlock
}

@Serializable
data class ImageSource(
    val type: String = "base64",
    @SerialName("media_type") val mediaType: String,
    val data: String,
)

@Serializable
data class AnthropicResponse(
    val content: List<ResponseContent> = emptyList(),
    @SerialName("stop_reason") val stopReason: String? = null,
) {
    fun firstText(): String? = content.firstOrNull { it.type == "text" }?.text
}

@Serializable
data class ResponseContent(
    val type: String = "",
    val text: String? = null,
)

@Serializable
data class AnthropicErrorEnvelope(
    val error: AnthropicError? = null,
)

@Serializable
data class AnthropicError(
    val type: String = "",
    val message: String = "",
)
