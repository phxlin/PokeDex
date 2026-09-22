package com.pokedex.app.ui.team

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.pokedex.app.data.repository.PokemonRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class PokemonPickerVerificationTest {
    @Test
    fun `switching to name mode clears loading after cancelling a move search`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<PokemonRepository>()
        every { repository.observePokemonIndex() } returns emptyFlow()
        coEvery { repository.pokemonIdsOfMove(any()) } coAnswers { awaitCancellation() }
        val vm = PokemonPickerViewModel(repository)
        try {
            vm.setSearchMode(PickerSearchMode.MOVE)
            vm.onSearchTextChange("surf")
            runCurrent()
            advanceTimeBy(401.milliseconds)
            runCurrent()
            assertThat(vm.isFilteringByMove.value).isTrue()

            vm.setSearchMode(PickerSearchMode.NAME)
            runCurrent()
            advanceTimeBy(401.milliseconds)
            runCurrent()

            assertThat(vm.isFilteringByMove.value).isFalse()
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `switching to name mode clears a failed move search error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<PokemonRepository>()
        every { repository.observePokemonIndex() } returns emptyFlow()
        coEvery { repository.pokemonIdsOfMove(any()) } returns Result.failure(java.io.IOException("offline"))
        val vm = PokemonPickerViewModel(repository)
        try {
            vm.setSearchMode(PickerSearchMode.MOVE)
            vm.onSearchTextChange("surf")
            runCurrent()
            advanceTimeBy(401.milliseconds)
            runCurrent()
            assertThat(vm.hasMoveError.value).isTrue()

            vm.setSearchMode(PickerSearchMode.NAME)
            runCurrent()
            advanceTimeBy(401.milliseconds)
            runCurrent()

            assertThat(vm.hasMoveError.value).isFalse()
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `switching to name mode clears loading after cancelling an ability search`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<PokemonRepository>()
        every { repository.observePokemonIndex() } returns emptyFlow()
        coEvery { repository.pokemonIdsOfAbility(any()) } coAnswers { awaitCancellation() }
        val vm = PokemonPickerViewModel(repository)
        try {
            vm.setSearchMode(PickerSearchMode.ABILITY)
            vm.onSearchTextChange("intimidate")
            runCurrent()
            advanceTimeBy(401.milliseconds)
            runCurrent()
            assertThat(vm.isFilteringByAbility.value).isTrue()

            vm.setSearchMode(PickerSearchMode.NAME)
            runCurrent()
            advanceTimeBy(401.milliseconds)
            runCurrent()

            assertThat(vm.isFilteringByAbility.value).isFalse()
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `switching to name mode clears a failed ability search error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<PokemonRepository>()
        every { repository.observePokemonIndex() } returns emptyFlow()
        coEvery { repository.pokemonIdsOfAbility(any()) } returns Result.failure(java.io.IOException("offline"))
        val vm = PokemonPickerViewModel(repository)
        try {
            vm.setSearchMode(PickerSearchMode.ABILITY)
            vm.onSearchTextChange("intimidate")
            runCurrent()
            advanceTimeBy(401.milliseconds)
            runCurrent()
            assertThat(vm.hasAbilityError.value).isTrue()

            vm.setSearchMode(PickerSearchMode.NAME)
            runCurrent()
            advanceTimeBy(401.milliseconds)
            runCurrent()

            assertThat(vm.hasAbilityError.value).isFalse()
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `move and ability searches carry independent loading state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<PokemonRepository>()
        every { repository.observePokemonIndex() } returns emptyFlow()
        coEvery { repository.pokemonIdsOfMove("surf") } returns Result.success(setOf(1))
        coEvery { repository.pokemonIdsOfAbility("intimidate") } returns Result.success(setOf(130))
        val vm = PokemonPickerViewModel(repository)
        try {
            vm.setSearchMode(PickerSearchMode.MOVE)
            vm.onSearchTextChange("surf")
            runCurrent()
            advanceTimeBy(401.milliseconds)
            runCurrent()
            assertThat(vm.isFilteringByMove.value).isFalse()
            assertThat(vm.isFilteringByAbility.value).isFalse()

            vm.setSearchMode(PickerSearchMode.ABILITY)
            vm.onSearchTextChange("intimidate")
            runCurrent()
            advanceTimeBy(401.milliseconds)
            runCurrent()

            // Resolving the ability search must not touch the (already-resolved) move flag.
            assertThat(vm.isFilteringByAbility.value).isFalse()
            assertThat(vm.isFilteringByMove.value).isFalse()
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
