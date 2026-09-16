package com.pokedex.app.di

import com.pokedex.app.data.classifier.AnthropicPokemonClassifier
import com.pokedex.app.data.classifier.PokemonClassifier
import com.pokedex.app.data.repository.PokemonRepository
import com.pokedex.app.data.repository.PokemonRepositoryImpl
import com.pokedex.app.data.repository.TeamRepository
import com.pokedex.app.data.repository.TeamRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindPokemonRepository(impl: PokemonRepositoryImpl): PokemonRepository

    @Binds
    @Singleton
    abstract fun bindPokemonClassifier(impl: AnthropicPokemonClassifier): PokemonClassifier

    @Binds
    @Singleton
    abstract fun bindTeamRepository(impl: TeamRepositoryImpl): TeamRepository
}
