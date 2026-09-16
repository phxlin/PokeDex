package com.pokedex.app.di

import android.content.Context
import androidx.room.Room
import com.pokedex.app.data.local.MetaDao
import com.pokedex.app.data.local.PokeDexDatabase
import com.pokedex.app.data.local.PokeDexMigrations
import com.pokedex.app.data.local.PokemonIndexDao
import com.pokedex.app.data.local.RawCacheDao
import com.pokedex.app.data.local.TeamDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PokeDexDatabase =
        Room.databaseBuilder(context, PokeDexDatabase::class.java, PokeDexDatabase.NAME)
            .addMigrations(*PokeDexMigrations.ALL)
            // Version 1 predates the team tables, so a destructive rebuild there
            // only drops the disposable Pokédex cache. From version 2 on, a schema
            // bump without a matching migration in PokeDexMigrations fails loudly
            // rather than silently deleting the user's saved teams.
            .fallbackToDestructiveMigrationFrom(1)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun providePokemonIndexDao(db: PokeDexDatabase): PokemonIndexDao = db.pokemonIndexDao()
    @Provides fun provideRawCacheDao(db: PokeDexDatabase): RawCacheDao = db.rawCacheDao()
    @Provides fun provideMetaDao(db: PokeDexDatabase): MetaDao = db.metaDao()
    @Provides fun provideTeamDao(db: PokeDexDatabase): TeamDao = db.teamDao()
}
