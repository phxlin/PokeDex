package com.pokedex.app.ui.team

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.pokedex.app.data.repository.PokemonRepository
import com.pokedex.app.data.repository.TeamRepository
import com.pokedex.app.domain.team.Team
import com.pokedex.app.domain.team.TeamMember
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test

/**
 * Item Clause: no two Pokémon on the same team hold the same item. [ItemPickerSheet] enforces it
 * interactively (a held-elsewhere item shows unavailable, same as an illegal one — see
 * `Pickers.kt$ItemRow`, which has no dedicated test since the pre-existing Champions-legal gating
 * next to it never had one either); this file covers the one path that bypasses that picker
 * entirely: copying a Pokémon in from another team ([TeamEditorViewModel.addFromMember]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ItemClauseTest {

    private fun create(existing: List<TeamMember>): TeamEditorViewModel {
        val teams = mockk<TeamRepository>(relaxed = true)
        val pokemon = mockk<PokemonRepository>(relaxed = true)
        every { teams.observeTeam(1L) } returns flowOf(Team(1L, "Test", existing))
        every { teams.observeTeams() } returns flowOf(emptyList())
        coEvery { pokemon.getTypeChart() } returns Result.failure(IllegalStateException("offline"))
        // Item Clause is decided synchronously in addFromMember, before this fires — these tests
        // don't care about enrichment, only that it fails cleanly instead of on a relaxed-mock cast.
        coEvery { pokemon.getPokemon(any()) } returns Result.failure(IllegalStateException("offline"))
        coEvery { pokemon.getFormsBundle(any()) } returns Result.success(null)
        return TeamEditorViewModel(teams, pokemon, SavedStateHandle(mapOf("teamId" to "1")))
    }

    private fun runWithTeam(existing: List<TeamMember>, block: TestScope.(TeamEditorViewModel) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = create(existing)
        try {
            runCurrent()
            block(vm)
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `copying in a member whose item collides with this team's own drops the item`() =
        runWithTeam(listOf(TeamMember(0, 6, "charizard", "Charizard", item = "life-orb"))) { vm ->
            val source = TeamMember(0, 9, "blastoise", "Blastoise", item = "life-orb")

            vm.addFromMember(1, source)
            runCurrent()

            val added = vm.state.value.members.single { it.slot == 1 }
            assertThat(added.item).isNull()
        }

    @Test
    fun `copying in a member whose item is free on this team keeps it`() =
        runWithTeam(listOf(TeamMember(0, 6, "charizard", "Charizard", item = "life-orb"))) { vm ->
            val source = TeamMember(0, 9, "blastoise", "Blastoise", item = "leftovers")

            vm.addFromMember(1, source)
            runCurrent()

            val added = vm.state.value.members.single { it.slot == 1 }
            assertThat(added.item).isEqualTo("leftovers")
        }

    @Test
    fun `copying a member back into its own slot does not treat its own item as a collision`() =
        runWithTeam(listOf(TeamMember(0, 6, "charizard", "Charizard", item = "life-orb"))) { vm ->
            val source = TeamMember(0, 6, "charizard", "Charizard", item = "life-orb")

            vm.addFromMember(0, source)
            runCurrent()

            assertThat(vm.state.value.members.single().item).isEqualTo("life-orb")
        }

    @Test
    fun `no item to copy is not treated as a collision`() =
        runWithTeam(listOf(TeamMember(0, 6, "charizard", "Charizard", item = "life-orb"))) { vm ->
            val source = TeamMember(0, 9, "blastoise", "Blastoise", item = null)

            vm.addFromMember(1, source)
            runCurrent()

            assertThat(vm.state.value.members.single { it.slot == 1 }.item).isNull()
        }
}
