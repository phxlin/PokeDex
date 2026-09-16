package com.pokedex.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PokemonIndexEntity::class,
        RawCacheEntity::class,
        MetaEntity::class,
        TeamEntity::class,
        TeamMemberEntity::class,
    ],
    version = 2, // keep in sync with LATEST_VERSION
    exportSchema = true,
)
abstract class PokeDexDatabase : RoomDatabase() {
    abstract fun pokemonIndexDao(): PokemonIndexDao
    abstract fun rawCacheDao(): RawCacheDao
    abstract fun metaDao(): MetaDao
    abstract fun teamDao(): TeamDao

    companion object {
        const val NAME = "pokedex.db"

        /** Current schema version — bump alongside `@Database(version = …)` and add a migration. */
        const val LATEST_VERSION = 2
    }
}
