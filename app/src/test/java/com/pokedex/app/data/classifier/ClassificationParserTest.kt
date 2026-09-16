package com.pokedex.app.data.classifier

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ClassificationParserTest {

    @Test
    fun `parses a bare json object`() {
        val result = ClassificationParser.parse(
            """{"is_pokemon": true, "name": "Pikachu", "confidence": 0.92, "reason": "Yellow mouse"}""",
        )
        assertThat(result.isPokemon).isTrue()
        assertThat(result.name).isEqualTo("Pikachu")
        assertThat(result.confidence).isEqualTo(0.92)
        assertThat(result.confidencePercent).isEqualTo(92)
    }

    @Test
    fun `strips markdown code fences`() {
        val raw = """
            Here is the result:
            ```json
            {"is_pokemon": true, "name": "Bulbasaur", "confidence": 0.8, "reason": "Seed on back"}
            ```
        """.trimIndent()
        val result = ClassificationParser.parse(raw)
        assertThat(result.name).isEqualTo("Bulbasaur")
    }

    @Test
    fun `handles prose before and after the object`() {
        val raw = "I think this is: {\"is_pokemon\": false, \"name\": null, \"confidence\": 0.3, " +
            "\"reason\": \"a domestic cat\"} — hope that helps!"
        val result = ClassificationParser.parse(raw)
        assertThat(result.isPokemon).isFalse()
        assertThat(result.name).isNull()
        assertThat(result.reason).isEqualTo("a domestic cat")
    }

    @Test
    fun `normalises confidence given as a percentage`() {
        val result = ClassificationParser.parse(
            """{"is_pokemon": true, "name": "Eevee", "confidence": 75, "reason": "fox-like"}""",
        )
        assertThat(result.confidence).isEqualTo(0.75)
    }

    @Test
    fun `treats string null name as no name`() {
        val result = ClassificationParser.parse(
            """{"is_pokemon": false, "name": "null", "confidence": 0.1, "reason": "a mug"}""",
        )
        assertThat(result.name).isNull()
    }

    @Test
    fun `forces name to null when not a pokemon even if model supplied one`() {
        val result = ClassificationParser.parse(
            """{"is_pokemon": false, "name": "Meowth", "confidence": 0.2, "reason": "a real cat"}""",
        )
        assertThat(result.name).isNull()
    }

    @Test
    fun `throws a ClassifierException on unparseable input`() {
        try {
            ClassificationParser.parse("no json here at all")
            assert(false) { "expected ClassifierException" }
        } catch (e: ClassifierException) {
            // expected
        }
    }

    @Test
    fun `tolerates trailing commas and single quotes via lenient parsing`() {
        val result = ClassificationParser.parse(
            """{"is_pokemon": true, "name": "Snorlax", "confidence": 0.6, "reason": "sleeping blob",}""",
        )
        assertThat(result.name).isEqualTo("Snorlax")
    }
}
