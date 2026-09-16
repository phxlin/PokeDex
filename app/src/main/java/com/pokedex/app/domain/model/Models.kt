package com.pokedex.app.domain.model

import com.pokedex.app.core.Sprites

private val GENDER_SEGMENTS = setOf("male", "female")

/** One row in the National Dex index shown on the home grid. */
data class PokemonSummary(
    val id: Int,
    val name: String,
) {
    /** Small sprite used in the grid — constructed, so the list needs no per-entry fetch. */
    val spriteUrl: String
        get() = Sprites.pokemon(id)

    val displayName: String
        get() {
            val parts = name.split("-")
            // Drop a trailing gender segment: "indeedee-male" -> "Indeedee".
            val visible = if (parts.size > 1 && parts.last().lowercase() in GENDER_SEGMENTS) {
                parts.dropLast(1)
            } else {
                parts
            }
            return visible.joinToString(" ") { p -> p.replaceFirstChar { it.uppercaseChar() } }
        }

    val dexLabel: String get() = "#" + id.toString().padStart(4, '0')
}

data class PokemonType(val name: String, val slot: Int)

data class PokemonStat(val name: String, val label: String, val base: Int)

data class PokemonAbilitySummary(
    val name: String,
    val displayName: String,
    val isHidden: Boolean,
)

data class PokemonSprites(
    val officialArtwork: String?,
    val officialArtworkShiny: String?,
    val frontDefault: String?,
    val backDefault: String?,
    val frontShiny: String?,
    val backShiny: String?,
) {
    data class Labeled(val label: String, val url: String)

    fun spriteRow(): List<Labeled> = buildList {
        frontDefault?.let { add(Labeled("Front", it)) }
        backDefault?.let { add(Labeled("Back", it)) }
        frontShiny?.let { add(Labeled("Shiny front", it)) }
        backShiny?.let { add(Labeled("Shiny back", it)) }
    }
}

data class PokemonCry(val latest: String?, val legacy: String?) {
    val bestUrl: String? get() = latest ?: legacy
}

data class PokemonDetail(
    val id: Int,
    val name: String,
    val displayName: String,
    val types: List<PokemonType>,
    val heightMeters: Double,
    val weightKg: Double,
    val stats: List<PokemonStat>,
    val abilities: List<PokemonAbilitySummary>,
    val sprites: PokemonSprites,
    val cry: PokemonCry,
    val speciesId: Int,
    val movePool: List<String> = emptyList(),
) {
    val statTotal: Int get() = stats.sumOf { it.base }
    val dexLabel: String get() = "#" + id.toString().padStart(4, '0')
}

data class PokemonSpecies(
    val id: Int,
    val name: String,
    val genus: String?,
    val flavorText: String?,
    val evolutionChainId: Int?,
)

data class AbilityDetail(
    val name: String,
    val displayName: String,
    val effect: String,
    val shortEffect: String,
)

/**
 * One way to evolve into a node. A regional-variety method (Alolan Ninetales via
 * Ice Stone, …) carries [evolvedFormId]/[evolvedFormName], and [baseFormId]/
 * [baseFormName] too when the *parent* also has a regional variety.
 */
data class EvoMethod(
    val text: String?,
    val itemSpriteUrl: String?,
    val priority: Int,
    val baseFormId: Int? = null,
    val baseFormName: String? = null,
    val evolvedFormId: Int? = null,
    val evolvedFormName: String? = null,
)

/** A Pokémon's variety for one region — its own dex/form id and its API slug. */
data class RegionForm(val pokemonId: Int, val slug: String)

private val REGIONAL_SLUG_SUFFIXES = listOf("-alola", "-galar", "-hisui", "-paldea")

/** Whether a form slug names a regional variety (`raichu-alola`, `meowth-galar`, …). */
fun isRegionalFormSlug(slug: String?): Boolean =
    slug != null && REGIONAL_SLUG_SUFFIXES.any { slug.endsWith(it) }

/**
 * A method that neither needs a regional parent nor produces a regional child —
 * shared by [EvolutionNode.methodFor], [EvolutionNode.visibleChildren] and
 * [EvolutionChain.regionSuffixFor], which all need the same "is this the plain
 * line" test.
 */
private val EvoMethod.isDefaultLine: Boolean
    get() = !isRegionalFormSlug(baseFormName) && !isRegionalFormSlug(evolvedFormName)

/** A node in the evolution tree — keeps branches (Eevee, Wurmple, …) intact. */
data class EvolutionNode(
    val speciesId: Int,
    val name: String,
    val displayName: String,
    /** Every documented way to reach this node from its parent. */
    val methods: List<EvoMethod> = emptyList(),
    val children: List<EvolutionNode> = emptyList(),
) {
    /**
     * The method to show for this node. When [regionForms] maps this node's
     * species to a regional variety, pick the method that produces it; otherwise
     * the default (non-regional) line. (PokéAPI sets `base_form` even on the plain
     * Pikachu → Raichu method, so `base_form == null` can't be the "default" test.)
     */
    fun methodFor(regionForms: Map<Int, RegionForm>): EvoMethod? {
        regionForms[speciesId]?.let { rf ->
            methods.firstOrNull { it.evolvedFormId == rf.pokemonId }?.let { return it }
        }
        return methods.filter { it.isDefaultLine }.maxByOrNull { it.priority }
            ?: methods.maxByOrNull { it.priority }
    }

    /** Whether this node (or something it evolves into) has a variety in [regionForms]. */
    fun leadsToRegion(regionForms: Map<Int, RegionForm>): Boolean =
        speciesId in regionForms || children.any { it.leadsToRegion(regionForms) }

    /** Children to show for the variety on screen (see [EvolutionChain.regionForms]). */
    fun visibleChildren(regionForms: Map<Int, RegionForm>, regionSuffix: String?): List<EvolutionNode> {
        val suffix = regionSuffix?.let { "-$it" }
        return children.filter { child ->
            if (regionForms.isEmpty() || suffix == null) {
                child.methods.any { it.isDefaultLine }
            } else {
                child.leadsToRegion(regionForms) ||
                    child.methods.any { it.baseFormName?.endsWith(suffix) == true }
            }
        }
    }
}

data class EvolutionChain(val root: EvolutionNode?) {
    val hasEvolutions: Boolean get() = root != null && root.children.isNotEmpty()

    /**
     * Every species in the chain that has a variety for the region named by
     * [regionSuffix] (`"alola"`, `"galar"`, …), mapped to that variety. Built from
     * the `base_form` / `evolved_form` links PokéAPI puts on regional methods.
     */
    fun regionForms(regionSuffix: String): Map<Int, RegionForm> {
        if (regionSuffix.isBlank()) return emptyMap()
        val suffix = "-$regionSuffix"
        val out = HashMap<Int, RegionForm>()
        fun walk(node: EvolutionNode) {
            node.children.forEach { child ->
                child.methods.forEach { m ->
                    if (m.evolvedFormName?.endsWith(suffix) == true && m.evolvedFormId != null) {
                        out[child.speciesId] = RegionForm(m.evolvedFormId, m.evolvedFormName)
                    }
                    if (m.baseFormName?.endsWith(suffix) == true && m.baseFormId != null) {
                        out[node.speciesId] = RegionForm(m.baseFormId, m.baseFormName)
                    }
                }
                walk(child)
            }
        }
        root?.let(::walk)
        return out
    }

    /**
     * The region suffix (`"hisui"`, `"alola"`, …) required somewhere on the path
     * from the chain's root down to [speciesId], or null if no specific regional
     * ancestor is needed. Most regional variants share their base form's species
     * id (Raichu / Raichu-Alola are both species 26, Arcanine / Hisuian Arcanine
     * are both species 59) — PokéAPI gives that edge *two* methods, one plain and
     * one region-tagged, so the plain one already gets you there and no region is
     * actually "required"; which branch to show is driven by whichever form tab
     * is active instead. A "convergent" Hisuian evolution like Sneasler, Kleavor,
     * Wyrdeer, Overqwil or Basculegion is different: a distinct species with no
     * varieties of its own and only a region-tagged method, so it's genuinely
     * unreachable without that region. This lets the evolution card pick the
     * right branch even when there's no active region tab to ask, without
     * misfiring on species like Arcanine that have a perfectly good plain line.
     */
    fun regionSuffixFor(speciesId: Int): String? {
        fun suffixOf(name: String?): String? =
            REGIONAL_SLUG_SUFFIXES.firstOrNull { name?.endsWith(it) == true }?.removePrefix("-")

        fun walk(node: EvolutionNode, inherited: String?): String? {
            if (node.speciesId == speciesId) return inherited
            node.children.forEach { child ->
                val hasDefaultMethod = child.methods.any { it.isDefaultLine }
                val required = if (hasDefaultMethod) {
                    inherited
                } else {
                    child.methods.firstNotNullOfOrNull { suffixOf(it.baseFormName) } ?: inherited
                }
                walk(child, required)?.let { return it }
            }
            return null
        }
        return root?.let { walk(it, null) }
    }
}

// ---- Alternate forms (Mega, Primal, Gigantamax, regional) -------------------

enum class FormKind {
    MEGA, MEGA_X, MEGA_Y, PRIMAL, GIGANTAMAX, ALOLAN, GALARIAN, HISUIAN, PALDEAN, OTHER;

    val isMega: Boolean get() = this == MEGA || this == MEGA_X || this == MEGA_Y || this == PRIMAL
    val isRegional: Boolean get() = this == ALOLAN || this == GALARIAN || this == HISUIAN || this == PALDEAN
}

/** One alternate variety of a species, with its full detail so a tab can render it. */
data class FormVariant(
    val kind: FormKind,
    val tabLabel: String,
    val detail: PokemonDetail,
)

data class FormsBundle(
    val base: PokemonDetail,
    val alternates: List<FormVariant>,
)
