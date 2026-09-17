package com.pokedex.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.pokedex.app.data.local.MetaDao
import com.pokedex.app.data.local.PokemonIndexDao
import com.pokedex.app.data.local.PokemonIndexEntity
import com.pokedex.app.data.local.RawCacheDao
import com.pokedex.app.data.remote.PokeApiService
import com.pokedex.app.data.remote.dto.NamedApiResourceDto
import com.pokedex.app.data.remote.dto.PokemonDto
import com.pokedex.app.data.remote.dto.PokemonListResponseDto
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PokemonRepositoryImplTest {

    private val service: PokeApiService = mockk()
    private val indexDao: PokemonIndexDao = mockk(relaxed = true)
    private val cacheDao: RawCacheDao = mockk(relaxed = true)
    private val metaDao: MetaDao = mockk(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private fun repo() = PokemonRepositoryImpl(
        service = service,
        indexDao = indexDao,
        cacheDao = cacheDao,
        metaDao = metaDao,
        json = json,
        io = UnconfinedTestDispatcher(),
    )

    @Test
    fun `legacy cache without learn methods is refreshed before choosing Champions moves`() = runTest {
        val legacy = """{"id":763,"name":"tsareena","moves":[{"move":{"name":"trop-kick"}},{"move":{"name":"magical-leaf"}}]}"""
        coEvery { cacheDao.get("pokemon/tsareena") } returns com.pokedex.app.data.local.RawCacheEntity(
            "pokemon/tsareena", legacy, System.currentTimeMillis(),
        )
        val freshBody = """
            {"id":763,"name":"tsareena","moves":[
                {"move":{"name":"trop-kick"},"version_group_details":[{"move_learn_method":{"name":"train"}}]},
                {"move":{"name":"magical-leaf"},"version_group_details":[{"move_learn_method":{"name":"level-up"}}]}
            ]}
        """.trimIndent()
        val fresh = json.decodeFromString(PokemonDto.serializer(), freshBody)
        coEvery { service.getPokemon("tsareena") } returns fresh

        val detail = repo().getPokemon("tsareena").getOrThrow()

        assertThat(detail.movePool).containsExactly("trop-kick")
    }

    @Test
    fun `refreshIndex filters alternate forms, sorts, and records sync time`() = runTest {
        coEvery { service.getPokemonList(any(), any()) } returns PokemonListResponseDto(
            count = 3,
            results = listOf(
                NamedApiResourceDto("pikachu", "https://pokeapi.co/api/v2/pokemon/25/"),
                NamedApiResourceDto("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/"),
                NamedApiResourceDto("pikachu-rock-star", "https://pokeapi.co/api/v2/pokemon/10080/"),
            ),
        )
        coEvery { indexDao.upsertAll(any()) } just Runs
        coEvery { metaDao.put(any()) } just Runs

        val result = repo().refreshIndex()

        assertThat(result.isSuccess).isTrue()
        val captured = slot<List<PokemonIndexEntity>>()
        coVerify { indexDao.upsertAll(capture(captured)) }
        assertThat(captured.captured.map { it.id }).containsExactly(1, 25).inOrder()
        coVerify { metaDao.put(any()) }
    }

    @Test
    fun `resolvePokemonId prefers the local index`() = runTest {
        coEvery { indexDao.snapshot() } returns listOf(
            PokemonIndexEntity(25, "pikachu"),
            PokemonIndexEntity(26, "raichu"),
        )

        val id = repo().resolvePokemonId("Pikachu").getOrNull()

        assertThat(id).isEqualTo(25)
        coVerify(exactly = 0) { service.getPokemon(any()) }
    }

    @Test
    fun `resolvePokemonId falls back to the network for names not in the index`() = runTest {
        coEvery { indexDao.snapshot() } returns emptyList()
        coEvery { service.getPokemon("mr-mime") } returns PokemonDto(id = 122, name = "mr-mime")

        val id = repo().resolvePokemonId("Mr. Mime").getOrNull()

        assertThat(id).isEqualTo(122)
    }
}
