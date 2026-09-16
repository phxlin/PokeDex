package com.pokedex.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PokemonIndexDao {
    @Query("SELECT * FROM pokemon_index ORDER BY id")
    fun observeAll(): Flow<List<PokemonIndexEntity>>

    @Query("SELECT * FROM pokemon_index ORDER BY id")
    suspend fun snapshot(): List<PokemonIndexEntity>

    @Query("SELECT COUNT(*) FROM pokemon_index")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<PokemonIndexEntity>)

    @Query("DELETE FROM pokemon_index")
    suspend fun clear()
}

@Dao
interface RawCacheDao {
    @Query("SELECT * FROM raw_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun get(key: String): RawCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: RawCacheEntity)
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM meta WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: MetaEntity)
}
