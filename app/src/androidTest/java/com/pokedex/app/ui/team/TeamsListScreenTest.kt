package com.pokedex.app.ui.team

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.google.common.truth.Truth.assertThat
import com.pokedex.app.FakePokemonRepository
import com.pokedex.app.FakeTeamRepository
import com.pokedex.app.domain.team.Team
import com.pokedex.app.ui.theme.PokeDexTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test

/**
 * Drives the real [TeamsListScreen] gesture (not a mock of it), because both regressions this
 * guards were only visible through actual Compose pointer-input timing: a `CASE`-free swap query
 * is a data-layer concern, but "does a second drag on a row use its current position" and "does
 * an ordinary swipe still scroll the list" only show up once real touch events are dispatched.
 */
class TeamsListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun team(id: Long, name: String) = Team(id = id, name = name)

    private fun setContent(teams: List<Team>): FakeTeamRepository {
        val repo = FakeTeamRepository(teams)
        val viewModel = TeamListViewModel(repo, FakePokemonRepository(emptyList()), Dispatchers.IO)
        composeRule.setContent {
            PokeDexTheme {
                TeamsListScreen(onOpenTeam = {}, viewModel = viewModel)
            }
        }
        return repo
    }

    @Test
    fun secondDragOnAMovedRowUsesItsCurrentPositionNotItsOriginalOne() {
        val repo = setContent((1L..3L).map { team(it, "Team $it") })
        composeRule.waitForIdle()

        // Drag team 1 (index 0) far enough down to clamp to the last slot, swapping it with team 3.
        composeRule.onNodeWithTag("team_row_1").performTouchInput {
            down(center)
            advanceEventTime(600)
            moveTo(Offset(center.x, center.y + 3000f))
            up()
        }
        composeRule.waitForIdle()
        assertThat(repo.current.map { it.id }).containsExactly(3L, 2L, 1L).inOrder()

        // Team 1's row is now at index 2 (bottom), not the index 0 its pointerInput coroutine
        // was originally launched with (the key is team.id, stable across the swap, so that
        // coroutine never restarted). Dragging it back up should move it using its CURRENT
        // position — to index 0 — not the stale one from before the first drag.
        composeRule.onNodeWithTag("team_row_1").performTouchInput {
            down(center)
            advanceEventTime(600)
            moveTo(Offset(center.x, center.y - 3000f))
            up()
        }
        composeRule.waitForIdle()
        assertThat(repo.current.map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
    }

    @Test
    fun ordinarySwipeStartingOnARowScrollsInsteadOfBeingSwallowed() {
        val repo = setContent((1L..15L).map { team(it, "Team $it") })
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("team_row_15").assertDoesNotExist()

        // Swipe on the list container (not a specific row's tag) since the row that's under
        // the finger keeps changing as the list actually scrolls — a real touch lands wherever
        // it happens to be on screen, which is exactly what's under test here. Several swipes:
        // one real gesture only covers about a screen's worth of rows, and this must still land
        // on a row each time to prove the *row-level* gesture handling isn't swallowing it.
        repeat(5) {
            composeRule.onNodeWithTag("teams_list").performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("team_row_15").assertIsDisplayed()
        assertThat(repo.swapCount).isEqualTo(0)
    }

    private fun openDeleteAllDialog() {
        composeRule.onNodeWithContentDescription("Backup, restore and delete").performClick()
        composeRule.onNodeWithText("Delete all data").performClick()
        composeRule.onNodeWithText("Delete all data?").assertIsDisplayed()
    }

    @Test
    fun deleteAllDataStaysDisabledUntilDELETEIsTypedThenRemovesEveryTeam() {
        val repo = setContent(listOf(team(1, "Rain"), team(2, "Sun")))
        openDeleteAllDialog()

        composeRule.onNodeWithText("Delete everything").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput("DELET")
        composeRule.onNodeWithText("Delete everything").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput("E")
        composeRule.onNodeWithText("Delete everything").assertIsEnabled()
        assertThat(repo.current).hasSize(2)

        composeRule.onNodeWithText("Delete everything").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { repo.current.isEmpty() }

        composeRule.onNodeWithText("Delete all data?").assertDoesNotExist()
    }

    @Test
    fun cancellingTheDeleteAllDialogKeepsTheTeams() {
        val repo = setContent(listOf(team(1, "Rain")))
        openDeleteAllDialog()

        composeRule.onNode(hasSetTextAction()).performTextInput("DELETE")
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Delete all data?").assertDoesNotExist()
        assertThat(repo.current).hasSize(1)
    }

    @Test
    fun deleteAllDataIsDisabledWhenThereAreNoTeams() {
        setContent(emptyList())

        composeRule.onNodeWithContentDescription("Backup, restore and delete").performClick()

        composeRule.onNodeWithText("Delete all data").assertIsNotEnabled()
    }
}
