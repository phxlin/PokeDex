package com.pokedex.app.ui.team

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.pokedex.app.data.repository.PokemonRepository
import com.pokedex.app.data.repository.TeamRepository
import com.pokedex.app.data.remote.dto.PokemonDto
import com.pokedex.app.data.toDomain
import com.pokedex.app.domain.model.PokemonDetail
import com.pokedex.app.domain.model.FormsBundle
import com.pokedex.app.domain.team.Team
import com.pokedex.app.domain.team.TeamMember
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeamEditorVerificationTest {
    private val member = TeamMember(0, 6, "charizard", "Charizard", ability = "blaze")

    private fun create(
        pending: CompletableDeferred<Result<PokemonDetail>>,
        forms: CompletableDeferred<Result<FormsBundle?>> = CompletableDeferred(Result.success(null)),
    ): TeamEditorViewModel {
        val teams = mockk<TeamRepository>(relaxed = true)
        val pokemon = mockk<PokemonRepository>()
        every { teams.observeTeam(1L) } returns flowOf(Team(1L, "Test", listOf(member)))
        every { teams.observeTeams() } returns flowOf(emptyList())
        coEvery { pokemon.getTypeChart() } returns Result.failure(IllegalStateException("offline"))
        coEvery { pokemon.getPokemon(any()) } coAnswers { pending.await() }
        coEvery { pokemon.getFormsBundle(any()) } coAnswers { forms.await() }
        return TeamEditorViewModel(teams, pokemon, SavedStateHandle(mapOf("teamId" to "1")))
    }

    @Test
    fun `initial enrichment preserves an edit made during loading`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = CompletableDeferred<Result<PokemonDetail>>()
        val vm = create(pending)
        try {
            runCurrent()
            vm.setShiny(0, true)
            pending.complete(Result.success(PokemonDto(id = 6, name = "charizard").toDomain()))
            runCurrent()
            assertThat(vm.state.value.members.single().shiny).isTrue()
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `initial enrichment follows a member moved while loading`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = CompletableDeferred<Result<PokemonDetail>>()
        val vm = create(pending)
        try {
            runCurrent()
            vm.swapSlots(0, 1)
            val detail = PokemonDto(id = 6, name = "charizard").toDomain().copy(movePool = listOf("flamethrower"))
            pending.complete(Result.success(detail))
            runCurrent()
            assertThat(vm.state.value.members.single().slot).isEqualTo(1)
            assertThat(vm.state.value.members.single().movePool).contains("flamethrower")
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `initial forms load starts at the moved members current slot`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = CompletableDeferred<Result<PokemonDetail>>()
        val detail = PokemonDto(id = 6, name = "charizard").toDomain()
        val forms = CompletableDeferred<Result<FormsBundle?>>(Result.success(FormsBundle(detail, emptyList())))
        val vm = create(pending, forms)
        try {
            runCurrent()
            vm.swapSlots(0, 1)
            pending.complete(Result.success(detail))
            runCurrent()
            assertThat(vm.state.value.forms.containsKey(1)).isTrue()
            assertThat(vm.state.value.forms[1]).isNotEmpty()
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `pending forms follow a member moved after the forms request starts`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val detail = PokemonDto(id = 6, name = "charizard").toDomain()
        val pending = CompletableDeferred<Result<PokemonDetail>>(Result.success(detail))
        val forms = CompletableDeferred<Result<FormsBundle?>>()
        val vm = create(pending, forms)
        try {
            runCurrent()
            vm.swapSlots(0, 1)
            forms.complete(Result.success(FormsBundle(detail, emptyList())))
            runCurrent()
            assertThat(vm.state.value.forms[1]).isNotEmpty()
            assertThat(vm.state.value.forms.containsKey(0)).isFalse()
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed initial enrichment preserves a saved ability`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val pending = CompletableDeferred<Result<PokemonDetail>>()
        val vm = create(pending)
        try {
            runCurrent()
            pending.complete(Result.failure(IllegalStateException("offline")))
            runCurrent()
            assertThat(vm.state.value.members.single().ability).isEqualTo("blaze")
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
