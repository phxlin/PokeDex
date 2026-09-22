package com.pokedex.app.ui.team

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.pokedex.app.data.remote.dto.NamedApiResourceDto
import com.pokedex.app.data.remote.dto.PokemonDto
import com.pokedex.app.data.repository.PokemonRepository
import com.pokedex.app.data.repository.TeamRepository
import com.pokedex.app.data.toDomain
import com.pokedex.app.domain.model.FormKind
import com.pokedex.app.domain.model.FormVariant
import com.pokedex.app.domain.model.FormsBundle
import com.pokedex.app.domain.model.PokemonDetail
import com.pokedex.app.domain.team.Gender
import com.pokedex.app.domain.team.Team
import com.pokedex.app.domain.team.TeamMember
import io.mockk.coEvery
import io.mockk.coVerify
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
 * Indeedee's genders are separate PokéAPI varieties (`indeedee-male` is the base form,
 * `indeedee-female` an alternate), each with its own Champions learnset — so the form decides the
 * gender, and switching form has to switch the move pool with it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GenderSplitFormsTest {

    private val indeedeeSpecies = NamedApiResourceDto("indeedee")
    private val male = PokemonDto(id = 876, name = "indeedee-male", species = indeedeeSpecies).toDomain()
    private val female = PokemonDto(id = 10186, name = "indeedee-female", species = indeedeeSpecies).toDomain()
    private val indeedeeForms = FormsBundle(male, listOf(FormVariant(FormKind.OTHER, "FEMALE", female)))

    private val charizard = PokemonDto(id = 6, name = "charizard").toDomain()
    private val charizardForms = FormsBundle(
        charizard,
        listOf(FormVariant(FormKind.MEGA_X, "MEGA X", PokemonDto(id = 10034, name = "charizard-mega-x").toDomain())),
    )

    private fun indeedee(gender: Gender = Gender.DEFAULT, formSlug: String? = null) = TeamMember(
        slot = 0,
        speciesId = 876,
        speciesName = "indeedee",
        displayName = "Indeedee",
        gender = gender,
        formSlug = formSlug,
    )

    private fun create(
        member: TeamMember,
        detail: PokemonDetail,
        forms: FormsBundle,
        teams: TeamRepository = mockk(relaxed = true),
    ): TeamEditorViewModel {
        val pokemon = mockk<PokemonRepository>()
        every { teams.observeTeam(1L) } returns flowOf(Team(1L, "Test", listOf(member)))
        every { teams.observeTeams() } returns flowOf(emptyList())
        coEvery { pokemon.getTypeChart() } returns Result.failure(IllegalStateException("offline"))
        coEvery { pokemon.getMoveInfo(any()) } returns Result.failure(IllegalStateException("offline"))
        coEvery { pokemon.getPokemon(any()) } returns Result.success(detail)
        coEvery { pokemon.getFormsBundle(any()) } returns Result.success(forms)
        return TeamEditorViewModel(teams, pokemon, SavedStateHandle(mapOf("teamId" to "1")))
    }

    private fun runIndeedee(
        member: TeamMember = indeedee(),
        block: suspend TestScope.(TeamEditorViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = create(member, male, indeedeeForms)
        try {
            runCurrent()
            block(vm)
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `correcting an equipped move on saved form reload persists the correction`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val teams = mockk<TeamRepository>(relaxed = true)
        val member = indeedee(Gender.FEMALE, "indeedee-female")
            .copy(moves = listOf("gravity", "psychic", null, null))
        val vm = create(member, male, indeedeeForms, teams)
        try {
            runCurrent()
            assertThat(vm.state.value.members.single().moves).containsExactly(null, "psychic", null, null).inOrder()
            coVerify { teams.saveMembers(1L, match { it.single().moves == listOf(null, "psychic", null, null) }) }
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `the base Indeedee is male once its forms have loaded`() = runIndeedee { vm ->
        assertThat(vm.state.value.members.single().gender).isEqualTo(Gender.MALE)
        assertThat(vm.state.value.lockedGender(0)).isEqualTo(Gender.MALE)
    }

    @Test
    fun `the form tabs read MALE and FEMALE, not Base`() = runIndeedee { vm ->
        val labels = vm.state.value.forms.getValue(0).map { it.label }
        assertThat(labels).containsExactly("MALE", "FEMALE").inOrder()
    }

    @Test
    fun `a species without gender-split forms still says Base`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = create(TeamMember(0, 6, "charizard", "Charizard"), charizard, charizardForms)
        try {
            runCurrent()
            assertThat(vm.state.value.forms.getValue(0).first().label).isEqualTo("Base")
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `switching to the female form makes it female and swaps to the female move pool`() = runIndeedee { vm ->
        vm.selectForm(0, 1)
        runCurrent()

        val member = vm.state.value.members.single()
        assertThat(member.gender).isEqualTo(Gender.FEMALE)
        assertThat(member.formSlug).isEqualTo("indeedee-female")
        assertThat(member.movePool).doesNotContain("expanding-force")
        assertThat(member.movePool).doesNotContain("gravity")
        assertThat(member.movePool).contains("follow-me")
    }

    @Test
    fun `switching to female removes equipped moves that only the male can learn`() =
        runIndeedee(indeedee().copy(moves = listOf("expanding-force", "gravity", "psychic", null))) { vm ->
            vm.selectForm(0, 1)
            runCurrent()

            val member = vm.state.value.members.single()
            assertThat(member.moves).doesNotContain("expanding-force")
            assertThat(member.moves).doesNotContain("gravity")
            assertThat(member.moves).contains("psychic")
        }

    @Test
    fun `switching back to the base form makes it male again with the male move pool`() = runIndeedee { vm ->
        vm.selectForm(0, 1)
        runCurrent()
        vm.selectForm(0, 0)
        runCurrent()

        val member = vm.state.value.members.single()
        assertThat(member.gender).isEqualTo(Gender.MALE)
        assertThat(member.formSlug).isNull()
        assertThat(member.movePool).containsAtLeast("expanding-force", "gravity")
        assertThat(member.movePool).doesNotContain("follow-me")
    }

    @Test
    fun `the gender can't be changed while the form fixes it`() = runIndeedee { vm ->
        vm.setGender(0, Gender.FEMALE)
        runCurrent()
        assertThat(vm.state.value.members.single().gender).isEqualTo(Gender.MALE)

        vm.selectForm(0, 1)
        runCurrent()
        vm.setGender(0, Gender.DEFAULT)
        runCurrent()
        assertThat(vm.state.value.members.single().gender).isEqualTo(Gender.FEMALE)
    }

    @Test
    fun `a saved female form with a stale gender is corrected to female when it loads`() =
        runIndeedee(indeedee(gender = Gender.DEFAULT, formSlug = "indeedee-female")) { vm ->
            assertThat(vm.state.value.members.single().gender).isEqualTo(Gender.FEMALE)
            assertThat(vm.state.value.members.single().movePool).doesNotContain("gravity")
        }

    @Test
    fun `species whose forms don't split by gender keep a free gender choice`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val member = TeamMember(0, 6, "charizard", "Charizard", gender = Gender.FEMALE)
        val vm = create(member, charizard, charizardForms)
        try {
            runCurrent()
            assertThat(vm.state.value.lockedGender(0)).isNull()
            assertThat(vm.state.value.members.single().gender).isEqualTo(Gender.FEMALE)

            vm.setGender(0, Gender.MALE)
            runCurrent()
            assertThat(vm.state.value.members.single().gender).isEqualTo(Gender.MALE)
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
