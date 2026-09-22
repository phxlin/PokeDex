package com.pokedex.app.data.repository

import com.pokedex.app.core.Resource
import com.pokedex.app.domain.model.AbilityDetail
import com.pokedex.app.domain.model.EvolutionChain
import com.pokedex.app.domain.model.FormsBundle
import com.pokedex.app.domain.model.PokemonDetail
import com.pokedex.app.domain.model.PokemonSpecies
import com.pokedex.app.domain.model.PokemonSummary
import com.pokedex.app.domain.team.ItemInfo
import com.pokedex.app.domain.team.MoveInfo
import com.pokedex.app.domain.team.TypeChart
import kotlinx.coroutines.flow.Flow

/** Bundle the detail screen needs up front. */
data class PokemonDetailBundle(
    val detail: PokemonDetail,
    val species: PokemonSpecies,
)

interface PokemonRepository {

    /** National Dex index for the home grid. Cache-first, refreshes if stale. */
    fun observePokemonIndex(): Flow<Resource<List<PokemonSummary>>>

    /** Force a network refresh of the index (retry button). */
    suspend fun refreshIndex(): Result<Unit>

    /** Detail + species for one Pokémon. Emits cached data instantly, then refreshes. */
    fun observePokemonDetail(idOrName: String): Flow<Resource<PokemonDetailBundle>>

    suspend fun getEvolutionChain(chainId: Int): Result<EvolutionChain>

    /**
     * Base form + every notable alternate variety of a species, each with full detail
     * so the detail screen can show one tab per form. `null` when the species has none.
     */
    suspend fun getFormsBundle(speciesId: Int): Result<FormsBundle?>

    suspend fun getAbility(name: String): Result<AbilityDetail>

    /** National Dex ids of every Pokémon that has [type]. Cached; used by the type filter. */
    suspend fun pokemonIdsOfType(type: String): Result<Set<Int>>

    /**
     * National Dex ids of every Pokémon that can actually learn [move] under Champions —
     * i.e. it shows up in that Pokémon's own Champions-accurate movePool (see
     * [PokemonDetail.movePool] / `Mappers.movePoolFor`), not just PokéAPI's raw "any game,
     * any method" move→Pokémon index. Cached; used by the team builder's move filter.
     */
    suspend fun pokemonIdsOfMove(move: String): Result<Set<Int>>

    /**
     * National Dex ids of every Pokémon that can have [ability] — PokéAPI's own ability→Pokémon
     * index, unlike [pokemonIdsOfMove]'s move index, already names exactly the species (and
     * variety) that can actually have it, hidden or not, with no further per-species *legality*
     * check needed: Champions doesn't disable abilities the way it disables some moves. An
     * alternate-variety entry (e.g. Alolan Ninetales for Snow Warning) is still resolved back to
     * its species id, since that variety can be the only holder of the ability. Cached; used by
     * the team builder's ability filter.
     */
    suspend fun pokemonIdsOfAbility(ability: String): Result<Set<Int>>

    /** Resolve a (possibly messy) name to a National Dex id — used by camera identify. */
    suspend fun resolvePokemonId(rawName: String): Result<Int>

    /** Single-shot cache-first fetch of one Pokémon's full detail (team builder). */
    suspend fun getPokemon(idOrName: String): Result<PokemonDetail>

    /** Move type / category / power, cached. */
    suspend fun getMoveInfo(name: String): Result<MoveInfo>

    /** Held-item effect text, cached. */
    suspend fun getItemInfo(name: String): Result<ItemInfo>

    /** The full 18-type effectiveness chart, cached. */
    suspend fun getTypeChart(): Result<TypeChart>
}
