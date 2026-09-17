package com.pokedex.app.data

import com.pokedex.app.core.PokemonForms
import com.pokedex.app.core.PokemonNames
import com.pokedex.app.core.Sprites
import com.pokedex.app.core.cleanFlavorText
import com.pokedex.app.data.remote.dto.AbilityDto
import com.pokedex.app.data.remote.dto.ChainLinkDto
import com.pokedex.app.data.remote.dto.EvolutionChainDto
import com.pokedex.app.data.remote.dto.EvolutionDetailDto
import com.pokedex.app.data.remote.dto.MoveSlotDto
import com.pokedex.app.data.remote.dto.PokemonDto
import com.pokedex.app.data.remote.dto.SpeciesDto
import com.pokedex.app.domain.model.AbilityDetail
import com.pokedex.app.domain.model.EvoMethod
import com.pokedex.app.domain.model.EvolutionChain
import com.pokedex.app.domain.model.EvolutionNode
import com.pokedex.app.domain.model.PokemonAbilitySummary
import com.pokedex.app.domain.model.PokemonCry
import com.pokedex.app.domain.model.PokemonDetail
import com.pokedex.app.domain.model.PokemonSpecies
import com.pokedex.app.domain.model.PokemonSprites
import com.pokedex.app.domain.model.PokemonStat
import com.pokedex.app.domain.model.PokemonType

/**
 * Patches for species where the "every move from any game" fallback (see
 * [movePoolFor]) is missing an otherwise well-documented move — Golisopod's U-turn
 * and Aqua Jet are both listed on Bulbapedia but missing from its PokéAPI entry.
 * Only applied in that fallback: once a species has PokéAPI's `train` data, that
 * data is authoritative and a patch must not reintroduce a move `train` omits
 * because it's actually disabled in Champions.
 */
private val MOVE_POOL_PATCHES: Map<String, List<String>> = mapOf(
    "golisopod" to listOf("u-turn", "aqua-jet"),
)

/**
 * Species slugs whose [MOVE_POOL_PATCHES] entry includes [moveSlug] — e.g. Golisopod for
 * "u-turn". PokéAPI's own move→Pokémon reverse index (`learned_by_pokemon`, used to seed
 * move-search candidates) doesn't know about these patches, since Golisopod is missing from
 * that index for these exact moves — the same gap the patch exists to work around. Search
 * needs this to find Golisopod the same way the species' own move picker already does.
 */
fun speciesPatchedFor(moveSlug: String): Set<String> =
    MOVE_POOL_PATCHES.filterValues { moveSlug in it }.keys

private val STAT_LABELS = mapOf(
    "hp" to "HP",
    "attack" to "Atk",
    "defense" to "Def",
    "special-attack" to "SpA",
    "special-defense" to "SpD",
    "speed" to "Spe",
)
private val STAT_ORDER = STAT_LABELS.keys.toList()

fun PokemonDto.toDomain(): PokemonDetail {
    val orderedStats = stats
        .map { PokemonStat(it.stat.name, STAT_LABELS[it.stat.name] ?: it.stat.name, it.baseStat) }
        .sortedBy { s -> STAT_ORDER.indexOf(s.name).let { if (it == -1) Int.MAX_VALUE else it } }

    val baseName = species.name.ifBlank { name }
    val display = if (name != baseName) {
        PokemonForms.displayName(
            PokemonNames.displayName(baseName),
            name,
            PokemonForms.classify(name),
        )
    } else {
        PokemonNames.displayName(name)
    }

    return PokemonDetail(
        id = id,
        name = name,
        displayName = display,
        types = types.sortedBy { it.slot }.map { PokemonType(it.type.name, it.slot) },
        heightMeters = height / 10.0,
        weightKg = weight / 10.0,
        stats = orderedStats,
        abilities = abilities.sortedBy { it.slot }.map {
            PokemonAbilitySummary(
                name = it.ability.name,
                displayName = PokemonNames.displayName(it.ability.name),
                isHidden = it.isHidden,
            )
        },
        sprites = PokemonSprites(
            officialArtwork = sprites.other?.officialArtwork?.frontDefault,
            officialArtworkShiny = sprites.other?.officialArtwork?.frontShiny,
            frontDefault = sprites.frontDefault,
            backDefault = sprites.backDefault,
            frontShiny = sprites.frontShiny,
            backShiny = sprites.backShiny,
        ),
        cry = PokemonCry(latest = cries?.latest, legacy = cries?.legacy),
        speciesId = species.idFromUrl() ?: id,
        movePool = movePoolFor(moves, name),
    )
}

/**
 * PokéAPI's `train` move-learn-method (added for Pokémon Champions v1.0, sourced from
 * mined Champions game files) is the authoritative per-species Champions moveset — it
 * already excludes moves a species knows in older games but that are disabled in
 * Champions (Tsareena knows Magical Leaf, but it's unusable there). Prefer it when
 * PokéAPI has populated it for this species; a species it hasn't reached yet (Golisopod,
 * as of this writing) falls back to the older "every move from any game" union, patched
 * for known gaps via [MOVE_POOL_PATCHES].
 */
private fun movePoolFor(moves: List<MoveSlotDto>, slug: String): List<String> {
    val trainMoves = moves
        .filter { slot -> slot.versionGroupDetails.any { it.moveLearnMethod.name == "train" } }
        .mapNotNull { it.move.name.takeIf { n -> n.isNotBlank() } }
    if (trainMoves.isNotEmpty()) return trainMoves.distinct().sorted()

    // No train data for this species yet — MOVE_POOL_PATCHES only applies here, never
    // on top of train data: a patch is sourced from a species' general-game learnset,
    // which says nothing about whether that move is actually enabled in Champions.
    val fallback = moves.mapNotNull { it.move.name.takeIf { n -> n.isNotBlank() } }
    return (fallback + MOVE_POOL_PATCHES[slug].orEmpty()).distinct().sorted()
}

fun SpeciesDto.toDomain(): PokemonSpecies {
    val english = flavorTextEntries.filter { it.language.name == "en" }
    val flavor = english.firstOrNull()?.flavorText?.let(::cleanFlavorText)
    val genus = genera.firstOrNull { it.language.name == "en" }?.genus
    return PokemonSpecies(
        id = id,
        name = name,
        genus = genus,
        flavorText = flavor,
        evolutionChainId = evolutionChain?.idFromUrl(),
    )
}

fun EvolutionChainDto.toDomain(): EvolutionChain {
    fun build(link: ChainLinkDto, methods: List<EvoMethod>): EvolutionNode? {
        val speciesId = link.species.idFromUrl() ?: return null
        return EvolutionNode(
            speciesId = speciesId,
            name = link.species.name,
            displayName = PokemonNames.displayName(link.species.name),
            methods = methods,
            children = link.evolvesTo.mapNotNull { child ->
                val childMethods = child.evolutionDetails.map { d ->
                    val c = d.condition()
                    // A day/night requirement (Sneasel -> Weavile at night vs. Hisuian
                    // Sneasel -> Sneasler by day, Eevee's Espeon/Umbreon, ...) can
                    // accompany any trigger PokéAPI reports, so it's appended here,
                    // once, to whatever text `condition()` produced.
                    val time = d.timeOfDay?.takeIf { it.isNotBlank() }
                    val text = if (time != null) c.text?.let { "$it ($time)" } else c.text
                    EvoMethod(
                        text = text,
                        itemSpriteUrl = c.itemSlug?.let(Sprites::item),
                        priority = c.priority,
                        baseFormId = d.baseForm?.idFromUrl(),
                        baseFormName = d.baseForm?.name?.takeIf { it.isNotBlank() },
                        evolvedFormId = d.evolvedForm?.idFromUrl(),
                        evolvedFormName = d.evolvedForm?.name?.takeIf { it.isNotBlank() },
                    )
                }.ifEmpty { listOf(EvoMethod(null, null, 0)) }
                build(child, childMethods)
            },
        )
    }
    return EvolutionChain(build(chain, emptyList()))
}

private data class EvoCondition(
    val text: String? = null,
    val itemSlug: String? = null,
    val priority: Int = 0,
)

private fun EvolutionDetailDto.condition(): EvoCondition {
    // Plain "level up" / "friendship" / "trade" are the mainline methods — rank them
    // above item methods, which are more often a regional-variety special case.
    minLevel?.let { return EvoCondition("Lv. $it", priority = 6) }
    minHappiness?.let { return EvoCondition("High friendship", priority = 6) }
    if (trigger?.name == "trade" && item == null && heldItem == null && tradeSpecies == null) {
        return EvoCondition("Trade", priority = 6)
    }
    item?.name?.takeIf { it.isNotBlank() }?.let {
        val verb = if (trigger?.name == "trade") "Trade holding" else "Use"
        return EvoCondition("$verb ${PokemonNames.displayName(it)}", it, priority = 5)
    }
    heldItem?.name?.takeIf { it.isNotBlank() }?.let {
        return EvoCondition("Hold ${PokemonNames.displayName(it)}, level up", it, priority = 5)
    }
    knownMoveType?.name?.let { return EvoCondition("Knows a $it move", priority = 4) }
    location?.name?.takeIf { it.isNotBlank() }?.let {
        val text = when {
            "moss" in it -> "Near a Mossy Rock"
            "ice" in it || "icy" in it || "snow" in it -> "Near an Icy Rock"
            "magnet" in it || "electric" in it -> "In a special magnetic field"
            else -> "Level up at ${PokemonNames.displayName(it)}"
        }
        return EvoCondition(text, priority = 3)
    }
    if (needsOverworldRain) return EvoCondition("Level up in rain", priority = 2)
    tradeSpecies?.name?.let { return EvoCondition("Trade for ${PokemonNames.displayName(it)}", priority = 5) }
    trigger?.name?.let {
        return EvoCondition(
            when (it) {
                "trade" -> "Trade"
                "shed" -> "Level up (empty slot + Poké Ball)"
                "three-critical-hits" -> "Land 3 critical hits in one battle"
                "tower-of-darkness" -> "Train in the Tower of Darkness"
                "tower-of-waters" -> "Train in the Tower of Waters"
                else -> PokemonNames.displayName(it)
            },
            priority = 1,
        )
    }
    return EvoCondition()
}

fun AbilityDto.toDomain(): AbilityDetail {
    val en = effectEntries.firstOrNull { it.language.name == "en" }
    val flavor = flavorTextEntries.firstOrNull { it.language.name == "en" }?.flavorText?.let(::cleanFlavorText)
    return AbilityDetail(
        name = name,
        displayName = PokemonNames.displayName(name),
        effect = en?.effect?.let(::cleanFlavorText) ?: flavor ?: "No description available.",
        shortEffect = en?.shortEffect?.let(::cleanFlavorText) ?: flavor ?: "",
    )
}
