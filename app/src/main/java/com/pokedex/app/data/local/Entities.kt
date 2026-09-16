package com.pokedex.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The full National Dex index (id + slug), so search is a local query. */
@Entity(tableName = "pokemon_index")
data class PokemonIndexEntity(
    @PrimaryKey val id: Int,
    val name: String,
)

/**
 * Raw JSON body for a single PokeAPI endpoint, keyed by a stable cache key such
 * as `"pokemon/25"` or `"species/25"`. Storing the response verbatim keeps the
 * cache schema tiny and means new fields are picked up without a migration.
 */
@Entity(tableName = "raw_cache")
data class RawCacheEntity(
    @PrimaryKey val cacheKey: String,
    val body: String,
    val fetchedAt: Long,
)

/** Single-row-per-key store for small bits of metadata (e.g. last index sync). */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
