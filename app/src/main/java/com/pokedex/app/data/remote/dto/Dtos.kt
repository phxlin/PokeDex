package com.pokedex.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NamedApiResourceDto(
    val name: String = "",
    val url: String = "",
) {
    /** Trailing path segment of the URL, e.g. ".../pokemon-species/25/" -> 25. */
    fun idFromUrl(): Int? = url.trimEnd('/').substringAfterLast('/').toIntOrNull()
}

@Serializable
data class PokemonListResponseDto(
    val count: Int = 0,
    val next: String? = null,
    val results: List<NamedApiResourceDto> = emptyList(),
)

// ---- pokemon/{id} ------------------------------------------------------------

@Serializable
data class PokemonDto(
    val id: Int,
    val name: String,
    val height: Int = 0,
    val weight: Int = 0,
    val types: List<TypeSlotDto> = emptyList(),
    val stats: List<StatSlotDto> = emptyList(),
    val abilities: List<AbilitySlotDto> = emptyList(),
    val sprites: SpritesDto = SpritesDto(),
    val cries: CriesDto? = null,
    val species: NamedApiResourceDto = NamedApiResourceDto(),
    val moves: List<MoveSlotDto> = emptyList(),
)

@Serializable
data class MoveSlotDto(
    val move: NamedApiResourceDto = NamedApiResourceDto(),
    @SerialName("version_group_details") val versionGroupDetails: List<MoveVersionGroupDetailDto> = emptyList(),
)

@Serializable
data class MoveVersionGroupDetailDto(
    @SerialName("move_learn_method") val moveLearnMethod: NamedApiResourceDto = NamedApiResourceDto(),
)

@Serializable
data class TypeSlotDto(val slot: Int = 0, val type: NamedApiResourceDto = NamedApiResourceDto())

@Serializable
data class StatSlotDto(
    @SerialName("base_stat") val baseStat: Int = 0,
    val stat: NamedApiResourceDto = NamedApiResourceDto(),
)

@Serializable
data class AbilitySlotDto(
    @SerialName("is_hidden") val isHidden: Boolean = false,
    val slot: Int = 0,
    val ability: NamedApiResourceDto = NamedApiResourceDto(),
)

@Serializable
data class SpritesDto(
    @SerialName("front_default") val frontDefault: String? = null,
    @SerialName("back_default") val backDefault: String? = null,
    @SerialName("front_shiny") val frontShiny: String? = null,
    @SerialName("back_shiny") val backShiny: String? = null,
    val other: OtherSpritesDto? = null,
)

@Serializable
data class OtherSpritesDto(
    @SerialName("official-artwork") val officialArtwork: OfficialArtworkDto? = null,
)

@Serializable
data class OfficialArtworkDto(
    @SerialName("front_default") val frontDefault: String? = null,
    @SerialName("front_shiny") val frontShiny: String? = null,
)

@Serializable
data class CriesDto(
    val latest: String? = null,
    val legacy: String? = null,
)

// ---- pokemon-species/{id} --------------------------------------------------

@Serializable
data class SpeciesDto(
    val id: Int,
    val name: String,
    @SerialName("flavor_text_entries") val flavorTextEntries: List<FlavorTextEntryDto> = emptyList(),
    val genera: List<GenusDto> = emptyList(),
    @SerialName("evolution_chain") val evolutionChain: ApiResourceDto? = null,
    val varieties: List<VarietyDto> = emptyList(),
)

@Serializable
data class VarietyDto(
    @SerialName("is_default") val isDefault: Boolean = false,
    val pokemon: NamedApiResourceDto = NamedApiResourceDto(),
)

@Serializable
data class ApiResourceDto(val url: String = "") {
    fun idFromUrl(): Int? = url.trimEnd('/').substringAfterLast('/').toIntOrNull()
}

@Serializable
data class FlavorTextEntryDto(
    @SerialName("flavor_text") val flavorText: String = "",
    val language: NamedApiResourceDto = NamedApiResourceDto(),
    val version: NamedApiResourceDto? = null,
)

@Serializable
data class GenusDto(
    val genus: String = "",
    val language: NamedApiResourceDto = NamedApiResourceDto(),
)

// ---- evolution-chain/{id} ------------------------------------------------------

@Serializable
data class EvolutionChainDto(
    val id: Int = 0,
    val chain: ChainLinkDto = ChainLinkDto(),
)

@Serializable
data class ChainLinkDto(
    val species: NamedApiResourceDto = NamedApiResourceDto(),
    @SerialName("evolves_to") val evolvesTo: List<ChainLinkDto> = emptyList(),
    @SerialName("evolution_details") val evolutionDetails: List<EvolutionDetailDto> = emptyList(),
)

@Serializable
data class EvolutionDetailDto(
    @SerialName("min_level") val minLevel: Int? = null,
    val trigger: NamedApiResourceDto? = null,
    val item: NamedApiResourceDto? = null,
    @SerialName("held_item") val heldItem: NamedApiResourceDto? = null,
    @SerialName("known_move_type") val knownMoveType: NamedApiResourceDto? = null,
    @SerialName("min_happiness") val minHappiness: Int? = null,
    @SerialName("time_of_day") val timeOfDay: String? = null,
    @SerialName("needs_overworld_rain") val needsOverworldRain: Boolean = false,
    val location: NamedApiResourceDto? = null,
    @SerialName("trade_species") val tradeSpecies: NamedApiResourceDto? = null,
    /** Set by PokeAPI when this method belongs to a regional variety (e.g. `sandshrew-alola`). */
    @SerialName("base_form") val baseForm: NamedApiResourceDto? = null,
    @SerialName("evolved_form") val evolvedForm: NamedApiResourceDto? = null,
)

// ---- ability/{id} ------------------------------------------------------------

@Serializable
data class AbilityDto(
    val id: Int = 0,
    val name: String = "",
    @SerialName("effect_entries") val effectEntries: List<VerboseEffectDto> = emptyList(),
    @SerialName("flavor_text_entries") val flavorTextEntries: List<AbilityFlavorTextDto> = emptyList(),
)

@Serializable
data class VerboseEffectDto(
    val effect: String = "",
    @SerialName("short_effect") val shortEffect: String = "",
    val language: NamedApiResourceDto = NamedApiResourceDto(),
)

@Serializable
data class AbilityFlavorTextDto(
    @SerialName("flavor_text") val flavorText: String = "",
    val language: NamedApiResourceDto = NamedApiResourceDto(),
)

// ---- type/{name} -----------------------------------------------------------

@Serializable
data class TypeDto(
    val name: String = "",
    val pokemon: List<TypePokemonEntryDto> = emptyList(),
    @SerialName("damage_relations") val damageRelations: DamageRelationsDto = DamageRelationsDto(),
)

@Serializable
data class TypePokemonEntryDto(
    val pokemon: NamedApiResourceDto = NamedApiResourceDto(),
)

@Serializable
data class DamageRelationsDto(
    @SerialName("double_damage_from") val doubleDamageFrom: List<NamedApiResourceDto> = emptyList(),
    @SerialName("half_damage_from") val halfDamageFrom: List<NamedApiResourceDto> = emptyList(),
    @SerialName("no_damage_from") val noDamageFrom: List<NamedApiResourceDto> = emptyList(),
    @SerialName("double_damage_to") val doubleDamageTo: List<NamedApiResourceDto> = emptyList(),
    @SerialName("half_damage_to") val halfDamageTo: List<NamedApiResourceDto> = emptyList(),
    @SerialName("no_damage_to") val noDamageTo: List<NamedApiResourceDto> = emptyList(),
)

// ---- move/{name} -----------------------------------------------------------

@Serializable
data class MoveDto(
    val name: String = "",
    val power: Int? = null,
    val accuracy: Int? = null,
    val pp: Int? = null,
    val type: NamedApiResourceDto = NamedApiResourceDto(),
    @SerialName("damage_class") val damageClass: NamedApiResourceDto = NamedApiResourceDto(),
    @SerialName("effect_entries") val effectEntries: List<VerboseEffectDto> = emptyList(),
    @SerialName("effect_chance") val effectChance: Int? = null,
    @SerialName("flavor_text_entries") val flavorTextEntries: List<FlavorTextEntryDto> = emptyList(),
    @SerialName("learned_by_pokemon") val learnedByPokemon: List<NamedApiResourceDto> = emptyList(),
)

// ---- item/{id} -------------------------------------------------------------

@Serializable
data class ItemDto(
    val id: Int = 0,
    val name: String = "",
    @SerialName("effect_entries") val effectEntries: List<VerboseEffectDto> = emptyList(),
    @SerialName("flavor_text_entries") val flavorTextEntries: List<ItemFlavorTextDto> = emptyList(),
)

@Serializable
data class ItemFlavorTextDto(
    val text: String = "",
    val language: NamedApiResourceDto = NamedApiResourceDto(),
)
