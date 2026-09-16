package com.pokedex.app.data.remote

import com.pokedex.app.data.remote.dto.AbilityDto
import com.pokedex.app.data.remote.dto.EvolutionChainDto
import com.pokedex.app.data.remote.dto.ItemDto
import com.pokedex.app.data.remote.dto.PokemonDto
import com.pokedex.app.data.remote.dto.PokemonListResponseDto
import com.pokedex.app.data.remote.dto.MoveDto
import com.pokedex.app.data.remote.dto.SpeciesDto
import com.pokedex.app.data.remote.dto.TypeDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface PokeApiService {

    @GET("pokemon")
    suspend fun getPokemonList(
        @Query("limit") limit: Int = 20000,
        @Query("offset") offset: Int = 0,
    ): PokemonListResponseDto

    @GET("pokemon/{idOrName}")
    suspend fun getPokemon(@Path("idOrName") idOrName: String): PokemonDto

    @GET("pokemon-species/{idOrName}")
    suspend fun getSpecies(@Path("idOrName") idOrName: String): SpeciesDto

    @GET("evolution-chain/{id}")
    suspend fun getEvolutionChain(@Path("id") id: Int): EvolutionChainDto

    @GET("ability/{idOrName}")
    suspend fun getAbility(@Path("idOrName") idOrName: String): AbilityDto

    @GET("type/{name}")
    suspend fun getType(@Path("name") name: String): TypeDto

    @GET("move/{idOrName}")
    suspend fun getMove(@Path("idOrName") idOrName: String): MoveDto

    @GET("item/{idOrName}")
    suspend fun getItem(@Path("idOrName") idOrName: String): ItemDto
}
