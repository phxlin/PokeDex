package com.pokedex.app.ui.team

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DeleteConfirmationTest {

    @Test
    fun `the phrase matches regardless of case and surrounding whitespace`() {
        assertThat(matchesConfirmPhrase("DELETE")).isTrue()
        assertThat(matchesConfirmPhrase("delete")).isTrue()
        assertThat(matchesConfirmPhrase("Delete ")).isTrue()
        assertThat(matchesConfirmPhrase("  dElEtE  ")).isTrue()
    }

    @Test
    fun `anything else does not match`() {
        assertThat(matchesConfirmPhrase("")).isFalse()
        assertThat(matchesConfirmPhrase("   ")).isFalse()
        assertThat(matchesConfirmPhrase("DELET")).isFalse()
        assertThat(matchesConfirmPhrase("DELETE ALL")).isFalse()
        assertThat(matchesConfirmPhrase("de lete")).isFalse()
    }

    @Test
    fun `a different phrase can be required`() {
        assertThat(matchesConfirmPhrase("erase", phrase = "ERASE")).isTrue()
        assertThat(matchesConfirmPhrase("DELETE", phrase = "ERASE")).isFalse()
    }
}
