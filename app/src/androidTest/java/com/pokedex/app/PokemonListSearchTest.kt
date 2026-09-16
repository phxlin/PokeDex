package com.pokedex.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.pokedex.app.domain.model.PokemonSummary
import com.pokedex.app.ui.list.PokemonListScreen
import com.pokedex.app.ui.list.PokemonListViewModel
import com.pokedex.app.ui.theme.PokeDexTheme
import org.junit.Rule
import org.junit.Test

class PokemonListSearchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val pokemon = listOf(
        PokemonSummary(1, "bulbasaur"),
        PokemonSummary(4, "charmander"),
        PokemonSummary(25, "pikachu"),
    )

    private fun setContent() {
        val viewModel = PokemonListViewModel(FakePokemonRepository(pokemon))
        composeRule.setContent {
            PokeDexTheme {
                PokemonListScreen(onPokemonClick = {}, viewModel = viewModel)
            }
        }
    }

    @Test
    fun typingNameFiltersTheGrid() {
        setContent()
        composeRule.onNodeWithText("Pikachu").assertIsDisplayed()

        composeRule.onNodeWithTag("search_field").performTextInput("char")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Charmander").assertIsDisplayed()
        composeRule.onNodeWithTag("pokemon_card_pikachu").assertDoesNotExist()
        composeRule.onNodeWithTag("pokemon_card_bulbasaur").assertDoesNotExist()
    }

    @Test
    fun typingDexNumberFiltersTheGrid() {
        setContent()
        composeRule.onNodeWithTag("search_field").performTextReplacement("25")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Pikachu").assertIsDisplayed()
        composeRule.onNodeWithTag("pokemon_card_charmander").assertDoesNotExist()
    }
}
