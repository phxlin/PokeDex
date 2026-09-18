package com.pokedex.app.ui.team

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.pokedex.app.data.repository.PokemonRepository
import com.pokedex.app.data.repository.TeamRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeamListViewModelTest {

    private fun create(teams: TeamRepository = mockk(relaxed = true)): TeamListViewModel {
        every { teams.observeTeams() } returns flowOf(emptyList())
        val pokemon = mockk<PokemonRepository>(relaxed = true)
        return TeamListViewModel(teams, pokemon)
    }

    @Test
    fun `swapTeams asks the repository to swap the two ids`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val teams = mockk<TeamRepository>(relaxed = true)
        coEvery { teams.swapTeams(any(), any()) } returns Unit
        val vm = create(teams)
        try {
            vm.swapTeams(1L, 2L)
            runCurrent()

            coVerify { teams.swapTeams(1L, 2L) }
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `swapTeams with the same id twice is a no-op`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val teams = mockk<TeamRepository>(relaxed = true)
        val vm = create(teams)
        try {
            vm.swapTeams(5L, 5L)
            runCurrent()

            coVerify(exactly = 0) { teams.swapTeams(any(), any()) }
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
