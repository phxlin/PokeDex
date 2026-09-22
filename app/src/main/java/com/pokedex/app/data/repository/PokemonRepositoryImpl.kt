package com.pokedex.app.data.repository

import com.pokedex.app.core.PokemonNames
import com.pokedex.app.core.Resource
import com.pokedex.app.core.cleanFlavorText
import com.pokedex.app.data.local.MetaDao
import com.pokedex.app.data.local.MetaEntity
import com.pokedex.app.data.local.PokemonIndexDao
import com.pokedex.app.data.local.PokemonIndexEntity
import com.pokedex.app.data.local.RawCacheDao
import com.pokedex.app.data.local.RawCacheEntity
import com.pokedex.app.data.remote.PokeApiService
import com.pokedex.app.data.remote.dto.AbilityDto
import com.pokedex.app.data.remote.dto.EvolutionChainDto
import com.pokedex.app.data.remote.dto.ItemDto
import com.pokedex.app.data.remote.dto.MoveDto
import com.pokedex.app.data.remote.dto.PokemonDto
import com.pokedex.app.data.remote.dto.SpeciesDto
import com.pokedex.app.data.remote.dto.TypeDto
import com.pokedex.app.core.PokemonForms
import com.pokedex.app.data.GENDER_SPLIT_VARIETY_IDS
import com.pokedex.app.data.speciesPatchedFor
import com.pokedex.app.data.toDomain
import com.pokedex.app.domain.model.AbilityDetail
import com.pokedex.app.domain.model.EvolutionChain
import com.pokedex.app.domain.model.FormVariant
import com.pokedex.app.domain.model.FormsBundle
import com.pokedex.app.domain.model.PokemonDetail
import com.pokedex.app.domain.model.PokemonSummary
import com.pokedex.app.domain.team.ItemInfo
import com.pokedex.app.domain.team.MoveInfo
import com.pokedex.app.domain.team.TypeChart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class PokemonRepositoryImpl @Inject constructor(
    private val service: PokeApiService,
    private val indexDao: PokemonIndexDao,
    private val cacheDao: RawCacheDao,
    private val metaDao: MetaDao,
    private val json: Json,
    private val io: CoroutineDispatcher,
) : PokemonRepository {

    override fun observePokemonIndex(): Flow<Resource<List<PokemonSummary>>> = channelFlow {
        launch {
            indexDao.observeAll().collect { rows ->
                if (rows.isNotEmpty()) {
                    send(Resource.Success(rows.map { PokemonSummary(it.id, it.name) }))
                }
            }
        }
        val haveCache = indexDao.count() > 0
        if (!haveCache) send(Resource.Loading())
        if (!haveCache || isStale(META_INDEX_SYNC, INDEX_TTL_MS)) {
            runCatching { refreshIndexInternal() }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    if (indexDao.count() == 0) send(Resource.Error(e.toUserMessage(), e))
                }
        }
    }

    override suspend fun refreshIndex(): Result<Unit> = withContext(io) {
        runCatching { refreshIndexInternal() }.mapCatchingCancellation()
    }

    private suspend fun refreshIndexInternal() {
        val response = service.getPokemonList()
        val entries = response.results
            .mapNotNull { r -> r.idFromUrl()?.let { id -> id to r.name } }
            .filter { it.first in 1..10000 }
            .sortedBy { it.first }
            .map { PokemonIndexEntity(it.first, it.second) }
        if (entries.isNotEmpty()) {
            indexDao.upsertAll(entries)
            metaDao.put(MetaEntity(META_INDEX_SYNC, System.currentTimeMillis().toString()))
        }
    }

    override fun observePokemonDetail(idOrName: String): Flow<Resource<PokemonDetailBundle>> = channelFlow {
        val key = idOrName.lowercase().trim()
        val cached = loadCachedBundle(key)
        if (cached != null) send(Resource.Loading(cached)) else send(Resource.Loading())

        val fresh = runCatching {
            withContext(io) {
                val pokemon = service.getPokemon(key)
                val speciesRef = pokemon.species.name.ifBlank { pokemon.name }
                val species = service.getSpecies(speciesRef)
                cacheDao.put(RawCacheEntity(pokemonKey(pokemon.id), json.encodeToString(PokemonDto.serializer(), pokemon), now()))
                cacheDao.put(RawCacheEntity(pokemonKey(pokemon.name), json.encodeToString(PokemonDto.serializer(), pokemon), now()))
                cacheDao.put(RawCacheEntity(speciesKey(species.id), json.encodeToString(SpeciesDto.serializer(), species), now()))
                PokemonDetailBundle(pokemon.toDomain(), species.toDomain())
            }
        }
        fresh.onSuccess { send(Resource.Success(it)) }
            .onFailure { e ->
                if (e is CancellationException) throw e
                send(Resource.Error(e.toUserMessage(), e, cached))
            }
    }

    private suspend fun loadCachedBundle(key: String): PokemonDetailBundle? = withContext(io) {
        val pokemonRaw = cacheDao.get(pokemonKey(key)) ?: return@withContext null
        val pokemon = runCatching { json.decodeFromString(PokemonDto.serializer(), pokemonRaw.body) }.getOrNull()
            ?: return@withContext null
        val speciesId = pokemon.species.idFromUrl() ?: pokemon.id
        val speciesRaw = cacheDao.get(speciesKey(speciesId)) ?: return@withContext null
        val species = runCatching { json.decodeFromString(SpeciesDto.serializer(), speciesRaw.body) }.getOrNull()
            ?: return@withContext null
        PokemonDetailBundle(pokemon.toDomain(), species.toDomain())
    }

    override suspend fun getEvolutionChain(chainId: Int): Result<EvolutionChain> = withContext(io) {
        runCatching { fetchEvolutionChainCached(chainId).toDomain() }.mapCatchingCancellation()
    }

    /** Same cache-then-revalidate-then-fallback shape as fetchPokemonCached/fetchSpeciesCached. */
    private suspend fun fetchEvolutionChainCached(chainId: Int): EvolutionChainDto {
        val key = "evolution/$chainId"
        val entry = cacheDao.get(key)
        if (entry != null && now() - entry.fetchedAt <= DETAIL_TTL_MS) {
            runCatching { json.decodeFromString(EvolutionChainDto.serializer(), entry.body) }.getOrNull()?.let { return it }
        }
        val fetched = runCatching { service.getEvolutionChain(chainId) }
        fetched.getOrNull()?.let { fresh ->
            cacheDao.put(RawCacheEntity(key, json.encodeToString(EvolutionChainDto.serializer(), fresh), now()))
            return fresh
        }
        return entry?.body?.let { body ->
            runCatching { json.decodeFromString(EvolutionChainDto.serializer(), body) }.getOrNull()
        } ?: throw fetched.exceptionOrNull()!!
    }

    override suspend fun getFormsBundle(speciesId: Int): Result<FormsBundle?> = withContext(io) {
        runCatching {
            val species = fetchSpeciesCached(speciesId.toString())
            val varieties = species.varieties
            if (varieties.size <= 1) return@runCatching null

            val defaultName = varieties.firstOrNull { it.isDefault }?.pokemon?.name ?: species.name
            val baseDto = fetchPokemonCached(defaultName)
            val baseStats = baseDto.stats.associate { it.stat.name to it.baseStat }
            val baseTypes = baseDto.types.map { it.type.name }.toSet()

            val alternates = mutableListOf<FormVariant>()
            for (variety in varieties.filterNot { it.isDefault }) {
                val name = variety.pokemon.name
                if (name.isBlank()) continue
                val dto = runCatching { fetchPokemonCached(name) }.getOrNull() ?: continue
                val statDiff = dto.stats.any { s -> (baseStats[s.stat.name] ?: s.baseStat) != s.baseStat }
                val typeDiff = dto.types.map { it.type.name }.toSet() != baseTypes
                if (!(PokemonForms.isNotableForm(dto.name) || statDiff || typeDiff)) continue
                val kind = PokemonForms.classify(dto.name)
                alternates += FormVariant(
                    kind = kind,
                    tabLabel = PokemonForms.badge(kind, dto.name),
                    detail = dto.toDomain(),
                )
            }

            if (alternates.isEmpty()) null
            else FormsBundle(base = baseDto.toDomain(), alternates = alternates.sortedBy { it.kind.ordinal })
        }.mapCatchingCancellation()
    }

    override suspend fun getPokemon(idOrName: String): Result<PokemonDetail> = withContext(io) {
        runCatching { fetchPokemonCached(idOrName).toDomain() }.mapCatchingCancellation()
    }

    override suspend fun getMoveInfo(name: String): Result<MoveInfo> = withContext(io) {
        runCatching {
            val slug = name.lowercase().trim()
            val dto = fetchMoveCached(slug)
            val short = dto.effectEntries.firstOrNull { it.language.name == "en" }
                ?.shortEffect
                ?.replace("\$effect_chance%", dto.effectChance?.let { "$it%" } ?: "a chance")
                ?.let(::cleanFlavorText)
                ?.takeIf { it.isNotBlank() }
            val flavor = dto.flavorTextEntries.firstOrNull { it.language.name == "en" }?.flavorText
                ?.let(::cleanFlavorText)
                ?.takeIf { it.isNotBlank() }
            MoveInfo(
                name = slug,
                displayName = PokemonNames.displayName(slug),
                type = dto.type.name.ifBlank { "normal" },
                damageClass = dto.damageClass.name.ifBlank { "status" },
                power = dto.power,
                accuracy = dto.accuracy,
                pp = dto.pp,
                shortEffect = short ?: flavor,
            )
        }.mapCatchingCancellation()
    }

    override suspend fun getItemInfo(name: String): Result<ItemInfo> = withContext(io) {
        runCatching {
            val slug = name.lowercase().trim()
            val key = "item/$slug"
            val dto = cacheDao.get(key)?.body
                ?.let { runCatching { json.decodeFromString(ItemDto.serializer(), it) }.getOrNull() }
                ?: service.getItem(slug).also {
                    cacheDao.put(RawCacheEntity(key, json.encodeToString(ItemDto.serializer(), it), now()))
                }
            val short = dto.effectEntries.firstOrNull { it.language.name == "en" }?.shortEffect
                ?.let(::cleanFlavorText)
                ?.takeIf { it.isNotBlank() }
            val flavor = dto.flavorTextEntries.firstOrNull { it.language.name == "en" }?.text
                ?.let(::cleanFlavorText)
                ?.takeIf { it.isNotBlank() }
            ItemInfo(
                name = slug,
                displayName = PokemonNames.displayName(slug),
                shortEffect = short ?: flavor,
            )
        }.mapCatchingCancellation()
    }

    override suspend fun getTypeChart(): Result<TypeChart> = withContext(io) {
        runCatching {
            val relations = HashMap<String, MutableMap<String, Double>>()
            for (type in TypeChart.TYPES) {
                val key = "typechart/$type"
                val dto = cacheDao.get(key)?.body
                    ?.let { runCatching { json.decodeFromString(TypeDto.serializer(), it) }.getOrNull() }
                    ?: service.getType(type).also {
                        cacheDao.put(RawCacheEntity(key, json.encodeToString(TypeDto.serializer(), it), now()))
                    }
                // Read from the DEFENDER's perspective isn't provided directly; PokeAPI gives
                // this attacking type's relations, so index as relations[attacking][defending].
                val dr = dto.damageRelations
                for (t in TypeChart.TYPES) relations.getOrPut(type) { HashMap() }[t] = 1.0
                dr.doubleDamageTo.forEach { relations.getOrPut(type) { HashMap() }[it.name] = 2.0 }
                dr.halfDamageTo.forEach { relations.getOrPut(type) { HashMap() }[it.name] = 0.5 }
                dr.noDamageTo.forEach { relations.getOrPut(type) { HashMap() }[it.name] = 0.0 }
            }
            TypeChart(relations)
        }.mapCatchingCancellation()
    }

    override suspend fun getAbility(name: String): Result<AbilityDetail> = withContext(io) {
        runCatching { fetchAbilityCached(name.lowercase().trim()).toDomain() }.mapCatchingCancellation()
    }

    override suspend fun pokemonIdsOfType(type: String): Result<Set<Int>> = withContext(io) {
        runCatching {
            val slug = type.lowercase().trim()
            val key = "type/$slug"
            val dto = cacheDao.get(key)?.body
                ?.let { runCatching { json.decodeFromString(TypeDto.serializer(), it) }.getOrNull() }
                ?: service.getType(slug).also {
                    cacheDao.put(RawCacheEntity(key, json.encodeToString(TypeDto.serializer(), it), now()))
                }
            dto.pokemon
                .mapNotNull { it.pokemon.idFromUrl() }
                .filter { it in 1..10000 }
                .toSet()
        }.mapCatchingCancellation()
    }

    override suspend fun pokemonIdsOfMove(move: String): Result<Set<Int>> = withContext(io) {
        runCatching {
            val slug = move.lowercase().trim()
            val candidateIds = fetchMoveCached(slug).learnedByPokemon
                .mapNotNull { it.idFromUrl() }
                // A National Dex id (1..10000) is a species' default variety; anything above that
                // is an alternate form — Mega, regional, Gigantamax — and would otherwise duplicate
                // its own base species' result here. GENDER_SPLIT_VARIETY_IDS is let through as a
                // deliberate, narrow exception: a gender-split species' non-default (female) variety
                // is not a redundant alternate form the way a Mega is, it's the only place some of
                // its moves show up at all (Follow Me lists only `indeedee-female`, never the male).
                .filter { it in 1..10000 || it in GENDER_SPLIT_VARIETY_IDS }
                .map { it.toString() }
            // A species can be missing from PokéAPI's reverse index for exactly the move a
            // MOVE_POOL_PATCHES entry exists to cover (Golisopod for u-turn/aqua-jet) — the
            // patch wouldn't otherwise help here since this candidate list is what search
            // checks in the first place. fetchPokemonCached takes a name just as well as an id.
            val candidateKeys = (candidateIds + speciesPatchedFor(slug)).distinct()

            // learned_by_pokemon has no per-entry learn-method breakdown, so it's only a
            // candidate list (any game, any method) — confirm each one Champions-legally
            // via its own movePool (movePoolFor's train-preferred logic, which for a
            // GENDER_SPLIT_VARIETY_IDS candidate also covers the curated fallback in
            // CHAMPIONS_MOVE_POOLS where PokéAPI's own per-variety data can't be trusted), the
            // same check each Pokémon's own move picker already enforces. Bounded-concurrency
            // fan-out: a popular move can have 200+ candidates and this is a one-shot search, not
            // something to run fully sequentially. A candidate fetch that fails (not just
            // resolves to "no match") is left to propagate rather than silently dropped —
            // otherwise a network hiccup would report success with an incomplete/empty set,
            // and the caller would cache that as if it were the real answer.
            val gate = Semaphore(MOVE_SEARCH_CONCURRENCY)
            coroutineScope {
                candidateKeys.map { key ->
                    async { gate.withPermit { fetchPokemonCached(key) } }
                }.awaitAll()
            }.filter { slug in it.toDomain().movePool }
                // speciesId, not id: a GENDER_SPLIT_VARIETY_IDS candidate's own id is the >10000
                // variety id, but its species field (shared with the default variety) resolves to
                // the one Pokédex entry search results are actually about.
                .map { it.toDomain().speciesId }
                .toSet()
        }.mapCatchingCancellation()
    }

    override suspend fun pokemonIdsOfAbility(ability: String): Result<Set<Int>> = withContext(io) {
        runCatching {
            val ids = fetchAbilityCached(ability.lowercase().trim()).pokemon.mapNotNull { it.pokemon.idFromUrl() }
            val (direct, alternate) = ids.partition { it in 1..10000 }
            // A >10000 id here is a regional/Mega/Gigantamax variety — e.g. Alolan Ninetales has
            // Snow Warning but Kantonian Ninetales doesn't, so unlike pokemonIdsOfType this can't
            // just drop them: that variety may be the *only* holder of this ability. Resolve each
            // one to its shared species id (the id the picker's index actually lists) instead, the
            // same way pokemonIdsOfMove resolves a GENDER_SPLIT_VARIETY_IDS candidate — no legality
            // check needed here, just the id mapping, so no Semaphore: these lists run a handful of
            // alternate varieties per ability, nothing like a move's 200+ candidates.
            val resolved = coroutineScope {
                alternate.map { id -> async { fetchPokemonCached(id.toString()).toDomain().speciesId } }.awaitAll()
            }
            (direct + resolved).toSet()
        }.mapCatchingCancellation()
    }

    private suspend fun fetchAbilityCached(slug: String): AbilityDto {
        val key = abilityKey(slug)
        return cacheDao.get(key)?.body
            ?.let { runCatching { json.decodeFromString(AbilityDto.serializer(), it) }.getOrNull() }
            ?: service.getAbility(slug).also {
                cacheDao.put(RawCacheEntity(key, json.encodeToString(AbilityDto.serializer(), it), now()))
            }
    }

    private suspend fun fetchMoveCached(slug: String): MoveDto {
        val key = moveKey(slug)
        return cacheDao.get(key)?.body
            ?.let { runCatching { json.decodeFromString(MoveDto.serializer(), it) }.getOrNull() }
            ?: service.getMove(slug).also {
                cacheDao.put(RawCacheEntity(key, json.encodeToString(MoveDto.serializer(), it), now()))
            }
    }

    // v4: cache bumped when learned_by_pokemon was added to MoveDto (v3 was bumped for
    // flavor_text_entries — PokéAPI leaves effect_entries empty for many of the newest
    // moves, e.g. Upper Hand, Dire Claw, even though it has a perfectly good flavor-text
    // description, so that's the fallback getMoveInfo uses).
    private fun moveKey(slug: String) = "move/v4/$slug"

    // v2: cache bumped when the pokemon reverse-index was added to AbilityDto for ability search —
    // a v1 body would otherwise deserialize with an empty list and search would find nothing.
    private fun abilityKey(slug: String) = "ability/v2/$slug"

    override suspend fun resolvePokemonId(rawName: String): Result<Int> = withContext(io) {
        runCatching {
            val slug = PokemonNames.normalize(rawName)
            val index = indexDao.snapshot()
            val fromIndex = index.firstOrNull { it.name == slug }
                ?: index.firstOrNull { it.name.startsWith("$slug-") }
                ?: index.firstOrNull { slug.startsWith("${it.name}-") }
            fromIndex?.id ?: service.getPokemon(slug).id
        }.mapCatchingCancellation()
    }

    // --- helpers -------------------------------------------------------------

    /**
     * Champions is a live, still-evolving game (new Mega Evolutions, regulation
     * changes, …), so a species/pokemon response fetched once and cached forever
     * can go permanently stale — e.g. a Pokémon whose Mega form PokeAPI hadn't
     * finished filling in yet at fetch time would show an empty ability list
     * forever after, with no way for a later, complete response to overwrite it.
     * Revalidate on the same cadence as [getEvolutionChain] once [DETAIL_TTL_MS]
     * has passed, falling back to the (possibly stale) cached copy only if the
     * network fetch itself fails.
     */
    private suspend fun fetchPokemonCached(idOrName: String): PokemonDto {
        val key = pokemonKey(idOrName)
        val entry = cacheDao.get(key)
        if (entry != null && now() - entry.fetchedAt <= DETAIL_TTL_MS) {
            runCatching { json.decodeFromString(PokemonDto.serializer(), entry.body) }.getOrNull()?.let { return it }
        }
        val fetched = runCatching { service.getPokemon(idOrName.lowercase()) }
        fetched.getOrNull()?.let { fresh ->
            cacheDao.put(RawCacheEntity(pokemonKey(fresh.id), json.encodeToString(PokemonDto.serializer(), fresh), now()))
            cacheDao.put(RawCacheEntity(pokemonKey(fresh.name), json.encodeToString(PokemonDto.serializer(), fresh), now()))
            return fresh
        }
        // The network fetch failed — fall back to the stale cached copy WITHOUT
        // touching its fetchedAt, so the next read still sees it as stale and
        // retries the network instead of waiting out a fresh 24h window for data
        // that was never actually revalidated.
        return entry?.body?.let { body ->
            runCatching { json.decodeFromString(PokemonDto.serializer(), body) }.getOrNull()
        } ?: throw fetched.exceptionOrNull()!!
    }

    private suspend fun fetchSpeciesCached(idOrName: String): SpeciesDto {
        val key = idOrName.toIntOrNull()?.let { speciesKey(it) }
        val entry = key?.let { cacheDao.get(it) }
        if (entry != null && now() - entry.fetchedAt <= DETAIL_TTL_MS) {
            runCatching { json.decodeFromString(SpeciesDto.serializer(), entry.body) }.getOrNull()?.let { return it }
        }
        val fetched = runCatching { service.getSpecies(idOrName.lowercase()) }
        fetched.getOrNull()?.let { fresh ->
            cacheDao.put(RawCacheEntity(speciesKey(fresh.id), json.encodeToString(SpeciesDto.serializer(), fresh), now()))
            return fresh
        }
        // Same reasoning as fetchPokemonCached: don't refresh the timestamp on a
        // fallback so the entry keeps retrying once the network is back.
        return entry?.body?.let { body ->
            runCatching { json.decodeFromString(SpeciesDto.serializer(), body) }.getOrNull()
        } ?: throw fetched.exceptionOrNull()!!
    }

    private fun now() = System.currentTimeMillis()

    private suspend fun isStale(metaKey: String, ttlMs: Long): Boolean {
        val last = metaDao.get(metaKey)?.toLongOrNull() ?: return true
        return now() - last > ttlMs
    }

    // v2: cache bumped when version_group_details/move_learn_method were added to
    // MoveSlotDto — a v1 entry decodes to an empty list for that field (it was never
    // serialized in the first place), which would silently fall back to the pre-Champions
    // blended movePool instead of refreshing to get the real train-method data.
    private fun pokemonKey(id: Int) = "pokemon/v2/$id"
    private fun pokemonKey(name: String) = "pokemon/v2/${name.lowercase()}"
    private fun speciesKey(id: Int) = "species/$id"

    companion object {
        private const val META_INDEX_SYNC = "index_synced_at"
        private const val INDEX_TTL_MS = 7L * 24 * 60 * 60 * 1000
        private const val DETAIL_TTL_MS = 24L * 60 * 60 * 1000
        private const val MOVE_SEARCH_CONCURRENCY = 16
    }
}

private fun <T> Result<T>.mapCatchingCancellation(): Result<T> =
    onFailure { if (it is CancellationException) throw it }
