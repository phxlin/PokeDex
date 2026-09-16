package com.pokedex.app

import com.pokedex.app.core.Resource
import com.pokedex.app.data.repository.PokemonDetailBundle
import com.pokedex.app.data.repository.PokemonRepository
import com.pokedex.app.domain.model.AbilityDetail
import com.pokedex.app.domain.model.EvolutionChain
import com.pokedex.app.domain.model.PokemonSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** In-memory [PokemonRepository] for UI tests — no network, no Room. */
class FakePokemonRepository(
    private val index: List<PokemonSummary>,
) : PokemonRepository {

    override fun observePokemonIndex(): Flow<Resource<List<PokemonSummary>>> =
        flowOf(Resource.Success(index))

    override suspend fun refreshIndex(): Result<Unit> = Result.success(Unit)

    override fun observePokemonDetail(idOrName: String): Flow<Resource<PokemonDetailBundle>> =
        flowOf(Resource.Loading())

    override suspend fun getEvolutionChain(chainId: Int): Result<EvolutionChain> =
        Result.success(EvolutionChain(null))

    override suspend fun getFormsBundle(speciesId: Int): Result<com.pokedex.app.domain.model.FormsBundle?> =
        Result.success(null)

    override suspend fun getPokemon(idOrName: String): Result<com.pokedex.app.domain.model.PokemonDetail> =
        Result.failure(UnsupportedOperationException())

    override suspend fun getMoveInfo(name: String): Result<com.pokedex.app.domain.team.MoveInfo> =
        Result.failure(UnsupportedOperationException())

    override suspend fun getItemInfo(name: String): Result<com.pokedex.app.domain.team.ItemInfo> =
        Result.failure(UnsupportedOperationException())

    override suspend fun getTypeChart(): Result<com.pokedex.app.domain.team.TypeChart> =
        Result.failure(UnsupportedOperationException())

    override suspend fun getAbility(name: String): Result<AbilityDetail> =
        Result.success(AbilityDetail(name, name, "", ""))

    override suspend fun pokemonIdsOfType(type: String): Result<Set<Int>> =
        Result.success(index.map { it.id }.toSet())

    override suspend fun resolvePokemonId(rawName: String): Result<Int> =
        index.firstOrNull { it.name == rawName }?.id?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("not found"))
}
