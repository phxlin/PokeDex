package com.pokedex.app.data.classifier

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses the model's reply into a [ClassificationResult]. The model is asked for
 * bare JSON, but this tolerates ```code fences```, leading/trailing prose, and
 * missing or wrongly-typed fields.
 */
object ClassificationParser {

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
    }

    fun parse(raw: String): ClassificationResult {
        val jsonText = extractJsonObject(raw)
            ?: throw ClassifierException("The classifier returned an unreadable response.")

        val obj = runCatching { json.parseToJsonElement(jsonText).jsonObject }
            .getOrElse { throw ClassifierException("The classifier returned malformed JSON.") }

        fun str(key: String): String? = obj[key]?.let {
            runCatching { it.jsonPrimitive.content }.getOrNull()
        }

        val isPokemon = when (str("is_pokemon")?.lowercase()) {
            "true", "yes", "1" -> true
            else -> false
        }
        val confidence = str("confidence")?.toDoubleOrNull()?.let {
            if (it > 1.0) it / 100.0 else it
        } ?: 0.0
        val name = str("name")?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        val reason = str("reason")?.takeIf { it.isNotBlank() } ?: "No explanation provided."

        return ClassificationResult(
            isPokemon = isPokemon,
            name = if (isPokemon) name else null,
            confidence = confidence.coerceIn(0.0, 1.0),
            reason = reason,
        )
    }

    /** Pull the first balanced `{ ... }` block out of an arbitrary string. */
    internal fun extractJsonObject(raw: String): String? {
        val text = raw
            .replace(Regex("```(?:json)?", RegexOption.IGNORE_CASE), "")
            .replace("```", "")
            .trim()
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
