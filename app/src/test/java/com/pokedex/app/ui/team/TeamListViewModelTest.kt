package com.pokedex.app.ui.team

import android.content.ContentResolver
import android.database.SQLException
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.pokedex.app.data.BackupException
import com.pokedex.app.data.TeamBackup
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class TeamListViewModelTest {

    private fun create(teams: TeamRepository = mockk(relaxed = true)): TeamListViewModel {
        every { teams.observeTeams() } returns flowOf(emptyList())
        val pokemon = mockk<PokemonRepository>(relaxed = true)
        return TeamListViewModel(teams, pokemon, UnconfinedTestDispatcher())
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

    @Test
    fun `deleteAllTeams asks the repository to delete everything and says so`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val teams = mockk<TeamRepository>(relaxed = true)
        val vm = create(teams)
        try {
            vm.deleteAllTeams()
            runCurrent()

            coVerify(exactly = 1) { teams.deleteAllTeams() }
            assertThat(vm.message.value).isEqualTo("All teams deleted.")
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `deleteAllTeams reports a database failure instead of crashing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val teams = mockk<TeamRepository>(relaxed = true)
        coEvery { teams.deleteAllTeams() } throws SQLException("disk full")
        val vm = create(teams)
        try {
            vm.deleteAllTeams()
            runCurrent()

            assertThat(vm.message.value).isEqualTo("Couldn't delete your teams.")
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `exportBackup writes the repository's JSON to the chosen file`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val teams = mockk<TeamRepository>(relaxed = true)
        coEvery { teams.exportBackup() } returns """{"app":"PokeDex"}"""
        val out = ByteArrayOutputStream()
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver> { every { openOutputStream(uri, "wt") } returns out }
        val vm = create(teams)
        try {
            vm.exportBackup(resolver, uri)
            runCurrent()

            assertThat(out.toString(Charsets.UTF_8)).isEqualTo("""{"app":"PokeDex"}""")
            assertThat(vm.message.value).isEqualTo("Backup saved.")
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `exportBackup reports a failure when the file can't be opened`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val teams = mockk<TeamRepository>(relaxed = true)
        coEvery { teams.exportBackup() } returns "{}"
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver> { every { openOutputStream(uri, "wt") } returns null }
        val vm = create(teams)
        try {
            vm.exportBackup(resolver, uri)
            runCurrent()

            assertThat(vm.message.value).isEqualTo("Couldn't save the backup.")
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `importBackup hands the file's contents to the repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val teams = mockk<TeamRepository>(relaxed = true)
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver> {
            every { openInputStream(uri) } returns ByteArrayInputStream("""{"app":"PokeDex"}""".toByteArray())
        }
        val vm = create(teams)
        try {
            vm.importBackup(resolver, uri)
            runCurrent()

            coVerify { teams.importBackup("""{"app":"PokeDex"}""") }
            assertThat(vm.message.value).isEqualTo("Backup restored.")
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `importBackup surfaces why an invalid file was rejected`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val teams = mockk<TeamRepository>(relaxed = true)
        coEvery { teams.importBackup(any()) } throws BackupException("This file isn't a valid backup.")
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver> { every { openInputStream(uri) } returns ByteArrayInputStream("nope".toByteArray()) }
        val vm = create(teams)
        try {
            vm.importBackup(resolver, uri)
            runCurrent()

            assertThat(vm.message.value).isEqualTo("Not restored: This file isn't a valid backup.")
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `importBackup refuses an oversized file without touching the repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val teams = mockk<TeamRepository>(relaxed = true)
        val uri = mockk<Uri>()
        val huge = ByteArray(TeamBackup.MAX_CHARS + 1) { 'x'.code.toByte() }
        val resolver = mockk<ContentResolver> { every { openInputStream(uri) } returns ByteArrayInputStream(huge) }
        val vm = create(teams)
        try {
            vm.importBackup(resolver, uri)
            runCurrent()

            coVerify(exactly = 0) { teams.importBackup(any()) }
            assertThat(vm.message.value).isEqualTo("Not restored: This file is too large to be a PokéDex backup.")
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `importBackup reports an unreadable file without touching the repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val teams = mockk<TeamRepository>(relaxed = true)
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver> { every { openInputStream(uri) } returns null }
        val vm = create(teams)
        try {
            vm.importBackup(resolver, uri)
            runCurrent()

            coVerify(exactly = 0) { teams.importBackup(any()) }
            assertThat(vm.message.value).isEqualTo("Couldn't read that file.")
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `messageShown clears the message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val teams = mockk<TeamRepository>(relaxed = true)
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver> { every { openInputStream(uri) } returns null }
        val vm = create(teams)
        try {
            vm.importBackup(resolver, uri)
            runCurrent()
            assertThat(vm.message.value).isNotNull()

            vm.messageShown()

            assertThat(vm.message.value).isNull()
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
