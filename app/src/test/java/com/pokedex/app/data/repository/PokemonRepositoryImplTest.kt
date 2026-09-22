package com.pokedex.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.pokedex.app.data.local.MetaDao
import com.pokedex.app.data.local.PokemonIndexDao
import com.pokedex.app.data.local.PokemonIndexEntity
import com.pokedex.app.data.local.RawCacheDao
import com.pokedex.app.data.local.RawCacheEntity
import com.pokedex.app.data.remote.PokeApiService
import com.pokedex.app.data.remote.dto.AbilityDto
import com.pokedex.app.data.remote.dto.AbilityPokemonDto
import com.pokedex.app.data.remote.dto.MoveDto
import com.pokedex.app.data.remote.dto.MoveSlotDto
import com.pokedex.app.data.remote.dto.MoveVersionGroupDetailDto
import com.pokedex.app.data.remote.dto.NamedApiResourceDto
import com.pokedex.app.data.remote.dto.PokemonDto
import com.pokedex.app.data.remote.dto.PokemonListResponseDto
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
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
    fun `move search includes patched learners missing from the reverse index`() = runTest {
        coEvery { service.getMove("aqua-jet") } returns MoveDto(
            name = "aqua-jet", learnedByPokemon = emptyList(),
        )
        coEvery { service.getPokemon(any()) } returns PokemonDto(id = 768, name = "golisopod")

        val ids = repo().pokemonIdsOfMove("aqua-jet").getOrThrow()

        assertThat(ids).contains(768)
    }

    @Test
    fun `failed candidate verification must not become a successful empty result`() = runTest {
        coEvery { service.getMove("magical-leaf") } returns MoveDto(
            name = "magical-leaf",
            learnedByPokemon = listOf(NamedApiResourceDto("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/")),
        )
        coEvery { service.getPokemon("1") } throws java.io.IOException("offline")

        val result = repo().pokemonIdsOfMove("magical-leaf")

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `legacy cache without learn methods is refreshed before choosing Champions moves`() = runTest {
        val legacy = """{"id":763,"name":"tsareena","moves":[{"move":{"name":"trop-kick"}},{"move":{"name":"magical-leaf"}}]}"""
        coEvery { cacheDao.get("pokemon/tsareena") } returns RawCacheEntity(
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
    fun `pokemonIdsOfMove only returns species where the move is Champions-legal`() = runTest {
        // PokeAPI's move endpoint says both bulbasaur and tsareena can learn magical-leaf,
        // but that reverse index has no per-species learn-method breakdown. Bulbasaur has
        // no train data (falls back to the blended list, which does include the move), while
        // tsareena's train data — the same Tsareena/Magical Leaf case from earlier — omits it
        // because it's disabled in Champions. Only bulbasaur should come back.
        coEvery { service.getMove("magical-leaf") } returns MoveDto(
            name = "magical-leaf",
            learnedByPokemon = listOf(
                NamedApiResourceDto("bulbasaur", "https://pokeapi.co/api/v2/pokemon/1/"),
                NamedApiResourceDto("tsareena", "https://pokeapi.co/api/v2/pokemon/763/"),
            ),
        )
        coEvery { service.getPokemon("1") } returns PokemonDto(
            id = 1,
            name = "bulbasaur",
            moves = listOf(MoveSlotDto(NamedApiResourceDto("magical-leaf"))),
        )
        coEvery { service.getPokemon("763") } returns PokemonDto(
            id = 763,
            name = "tsareena",
            moves = listOf(
                MoveSlotDto(
                    NamedApiResourceDto("trop-kick"),
                    listOf(MoveVersionGroupDetailDto(NamedApiResourceDto("train"))),
                ),
                MoveSlotDto(
                    NamedApiResourceDto("magical-leaf"),
                    listOf(MoveVersionGroupDetailDto(NamedApiResourceDto("level-up"))),
                ),
            ),
        )

        val ids = repo().pokemonIdsOfMove("magical-leaf").getOrThrow()

        assertThat(ids).containsExactly(1)
    }

    private val indeedeeSpecies = NamedApiResourceDto("indeedee", "https://pokeapi.co/api/v2/pokemon-species/876/")
    private val meowsticSpecies = NamedApiResourceDto("meowstic", "https://pokeapi.co/api/v2/pokemon-species/678/")

    @Test
    fun `move search finds Indeedee for a female-only move PokeAPI lists only under the female variety`() = runTest {
        // PokeAPI's own reverse index lists only indeedee-female (id 10186, not the male's 876) as
        // a Follow Me learner. 10186 is outside 1..10000 — the range every other alternate-variety
        // id is excluded from to avoid duplicate Mega/regional results — so reaching it at all needs
        // GENDER_SPLIT_VARIETY_IDS; once fetched, its own movePool falls back to the curated table.
        coEvery { service.getMove("follow-me") } returns MoveDto(
            name = "follow-me",
            learnedByPokemon = listOf(NamedApiResourceDto("indeedee-female", "https://pokeapi.co/api/v2/pokemon/10186/")),
        )
        coEvery { service.getPokemon("10186") } returns PokemonDto(id = 10186, name = "indeedee-female", species = indeedeeSpecies)

        val ids = repo().pokemonIdsOfMove("follow-me").getOrThrow()

        // 876, Indeedee's species id — not 10186, the female variety's own id.
        assertThat(ids).containsExactly(876)
    }

    @Test
    fun `move search finds Indeedee for a male-only move even when PokeAPI lists both genders`() = runTest {
        coEvery { service.getMove("gravity") } returns MoveDto(
            name = "gravity",
            learnedByPokemon = listOf(
                NamedApiResourceDto("indeedee-male", "https://pokeapi.co/api/v2/pokemon/876/"),
                NamedApiResourceDto("indeedee-female", "https://pokeapi.co/api/v2/pokemon/10186/"),
            ),
        )
        // Both are reached: 876 is in the ordinary 1..10000 range, 10186 via GENDER_SPLIT_VARIETY_IDS.
        // Neither has train data, so both fall back to the curated table — only the male's includes
        // gravity — and both resolve to the same species id, so the result isn't doubled up either.
        coEvery { service.getPokemon("876") } returns PokemonDto(id = 876, name = "indeedee-male", species = indeedeeSpecies)
        coEvery { service.getPokemon("10186") } returns PokemonDto(id = 10186, name = "indeedee-female", species = indeedeeSpecies)

        val ids = repo().pokemonIdsOfMove("gravity").getOrThrow()

        assertThat(ids).containsExactly(876)
    }

    @Test
    fun `move search finds Meowstic for a female-only move, from PokeApi's own data with no curated table`() = runTest {
        // Unlike Indeedee, PokéAPI's per-variety move lists for Meowstic are already correctly
        // split by gender — extrasensory only shows up under meowstic-female — so once
        // GENDER_SPLIT_VARIETY_IDS lets 10025 through, the ordinary blended-fallback movePool
        // (no CHAMPIONS_MOVE_POOLS entry for Meowstic) already gets this right.
        coEvery { service.getMove("extrasensory") } returns MoveDto(
            name = "extrasensory",
            learnedByPokemon = listOf(NamedApiResourceDto("meowstic-female", "https://pokeapi.co/api/v2/pokemon/10025/")),
        )
        coEvery { service.getPokemon("10025") } returns PokemonDto(
            id = 10025,
            name = "meowstic-female",
            species = meowsticSpecies,
            moves = listOf(MoveSlotDto(NamedApiResourceDto("extrasensory"))),
        )

        val ids = repo().pokemonIdsOfMove("extrasensory").getOrThrow()

        assertThat(ids).containsExactly(678)
    }

    @Test
    fun `move search does not add a species absent from Champions move pools`() = runTest {
        coEvery { service.getMove("tackle") } returns MoveDto(name = "tackle", learnedByPokemon = emptyList())

        val ids = repo().pokemonIdsOfMove("tackle").getOrThrow()

        assertThat(ids).isEmpty()
    }

    @Test
    fun `pokemonIdsOfAbility returns every species PokeApi lists directly, no fetch needed`() = runTest {
        coEvery { service.getAbility("intimidate") } returns AbilityDto(
            name = "intimidate",
            pokemon = listOf(
                AbilityPokemonDto(NamedApiResourceDto("gyarados", "https://pokeapi.co/api/v2/pokemon/130/")),
                AbilityPokemonDto(NamedApiResourceDto("arcanine", "https://pokeapi.co/api/v2/pokemon/59/")),
            ),
        )

        val ids = repo().pokemonIdsOfAbility("intimidate").getOrThrow()

        assertThat(ids).containsExactly(130, 59)
        coVerify(exactly = 0) { service.getPokemon(any()) } // no alternate varieties here, so no fetch needed
    }

    @Test
    fun `ability search resolves an alternate variety to its species id, not drops it`() = runTest {
        // Alolan Ninetales has Snow Warning but Kantonian Ninetales doesn't — PokeApi's ability
        // index lists only the id>10000 alternate-variety id, never the base species id, so
        // dropping ids outside 1..10000 (as pokemonIdsOfType does) would silently lose this result.
        coEvery { service.getAbility("snow-warning") } returns AbilityDto(
            name = "snow-warning",
            pokemon = listOf(
                AbilityPokemonDto(NamedApiResourceDto("ninetales-alola", "https://pokeapi.co/api/v2/pokemon/10104/")),
            ),
        )
        coEvery { service.getPokemon("10104") } returns PokemonDto(
            id = 10104,
            name = "ninetales-alola",
            species = NamedApiResourceDto("ninetales", "https://pokeapi.co/api/v2/pokemon-species/38/"),
        )

        assertThat(repo().pokemonIdsOfAbility("snow-warning").getOrThrow()).containsExactly(38)
    }

    @Test
    fun `a cached ability body from before search existed is refetched, not read as zero matches`() = runTest {
        // A body cached under the old (unversioned) key predates the `pokemon` field entirely —
        // decoding it would silently default `pokemon` to empty and report no matches forever.
        coEvery { cacheDao.get("ability/intimidate") } returns RawCacheEntity(
            "ability/intimidate", """{"name":"intimidate"}""", System.currentTimeMillis(),
        )
        coEvery { service.getAbility("intimidate") } returns AbilityDto(
            name = "intimidate",
            pokemon = listOf(AbilityPokemonDto(NamedApiResourceDto("gyarados", "https://pokeapi.co/api/v2/pokemon/130/"))),
        )

        val ids = repo().pokemonIdsOfAbility("intimidate").getOrThrow()

        assertThat(ids).containsExactly(130)
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
