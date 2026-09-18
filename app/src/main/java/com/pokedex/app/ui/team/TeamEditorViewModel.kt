package com.pokedex.app.ui.team

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pokedex.app.core.Sprites
import com.pokedex.app.data.repository.PokemonRepository
import com.pokedex.app.data.repository.TeamRepository
import com.pokedex.app.domain.model.FormKind
import com.pokedex.app.domain.model.PokemonDetail
import com.pokedex.app.domain.team.AbilityChoice
import com.pokedex.app.domain.team.DefProfile
import com.pokedex.app.domain.team.Gender
import com.pokedex.app.domain.team.abilityImmuneType
import com.pokedex.app.domain.team.heldItemApiSlug
import com.pokedex.app.domain.team.ItemInfo
import com.pokedex.app.domain.team.MoveInfo
import com.pokedex.app.domain.team.Nature
import com.pokedex.app.domain.team.StatCalc
import com.pokedex.app.domain.team.StatKey
import com.pokedex.app.domain.team.Team
import com.pokedex.app.domain.team.TeamAnalysis
import com.pokedex.app.domain.team.TeamMember
import com.pokedex.app.domain.team.TypeChart
import com.pokedex.app.domain.team.TypeMatchup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One selectable variety of a team member (base form, a Mega, a regional form…),
 * carrying just what the profile card needs to re-render for it.
 */
data class MemberForm(
    val label: String,
    val slug: String,
    val pokemonId: Int,
    val kind: FormKind?,
    val types: List<String>,
    val baseStats: Map<StatKey, Int>,
    val abilities: List<AbilityChoice>,
    val movePool: List<String>,
    val isBase: Boolean,
    val spriteDefault: String? = null,
    val spriteShiny: String? = null,
    val artworkDefault: String? = null,
    val artworkShiny: String? = null,
) {
    val isMega: Boolean get() = kind?.isMega == true

    fun artworkUrl(shiny: Boolean) =
        (if (shiny) artworkShiny ?: artworkDefault else artworkDefault)
            ?: Sprites.artwork(pokemonId, shiny)
    fun spriteUrl(shiny: Boolean) =
        (if (shiny) spriteShiny ?: spriteDefault else spriteDefault)
            ?: Sprites.pokemon(pokemonId, shiny)
}

/**
 * If [item] is a Mega Stone and [forms] contains the matching Mega, its index —
 * so the profile card can default to that Mega form. `null` otherwise.
 */
fun megaFormIndex(item: String?, forms: List<MemberForm>): Int? {
    if (item == null || forms.size < 2) return null
    val isMegaStone = com.pokedex.app.domain.team.HELD_ITEMS
        .firstOrNull { it.slug == item }?.category == com.pokedex.app.domain.team.ItemCategory.MEGA_STONE
    if (!isMegaStone) return null
    val wantsX = item.endsWith("-x")
    val wantsY = item.endsWith("-y")
    val idx = forms.indexOfFirst { f ->
        val l = f.label.lowercase()
        "mega" in l && when {
            wantsX -> l.trimEnd().endsWith("x")
            wantsY -> l.trimEnd().endsWith("y")
            else -> true
        }
    }
    return idx.takeIf { it > 0 }
}

data class TeamEditorUiState(
    val teamId: Long = 0,
    val name: String = "",
    val members: List<TeamMember> = emptyList(),
    val openSlot: Int? = null,
    val chart: TypeChart? = null,
    val moveInfo: Map<String, MoveInfo> = emptyMap(),
    /** Held-item slug -> its effect text, for the item picker. */
    val itemInfo: Map<String, ItemInfo> = emptyMap(),
    /** Ability slug -> its one-line effect, shown under the Ability chips. */
    val abilityInfo: Map<String, String> = emptyMap(),
    /** slot -> its forms (index 0 is always the base form). Empty list = no alternates. */
    val forms: Map<Int, List<MemberForm>> = emptyMap(),
    /** The user's other saved teams (with members), so one can be copied in as a starting point. */
    val otherTeams: List<Team> = emptyList(),
    val isLoading: Boolean = true,
) {
    /** The form tab index currently active for [slot] — an equipped Mega Stone wins, then a saved regional form. */
    fun selectedFormIndex(slot: Int): Int {
        val fs = forms[slot].orEmpty()
        if (fs.size < 2) return 0
        val m = members.firstOrNull { it.slot == slot }
        megaFormIndex(m?.item, fs)?.let { return it }
        m?.formSlug?.let { slug ->
            fs.indexOfFirst { it.slug == slug }.takeIf { it >= 0 }?.let { return it }
        }
        return 0
    }

    /** A member with its types/stats reflecting the form it actually fields (Mega-by-stone, else saved regional). */
    fun effectiveMember(m: TeamMember): TeamMember {
        val fs = forms[m.slot].orEmpty()
        megaFormIndex(m.item, fs)?.let { idx ->
            return fs.getOrNull(idx)?.let { m.copy(types = it.types, baseStats = it.baseStats) } ?: m
        }
        return m // regional form (if any) is already baked into m.types by applyForm
    }

    private val analysisMembers get() = members.map { effectiveMember(it) }

    /** The Mega form [m] is currently fielding, if its equipped item is a matching Mega Stone. */
    private fun megaFormOf(m: TeamMember): MemberForm? {
        val fs = forms[m.slot].orEmpty()
        return megaFormIndex(m.item, fs)?.let { fs.getOrNull(it) }
    }

    /** The ability actually in play for [m] right now: the Mega's fixed ability when
     *  one is equipped (it overrides whatever ability the base form had), else the
     *  member's own chosen ability. */
    private fun effectiveAbility(m: TeamMember): String? =
        megaFormOf(m)?.abilities?.singleOrNull()?.name ?: m.ability

    /**
     * Per member: the type-sets it can present defensively plus any type its Ability
     * negates. A member that can Mega contributes *both* its base and Mega type-combos
     * (either form's resist counts); a plain or regional member contributes just one.
     */
    private val defensiveProfiles: List<DefProfile>
        get() = members.mapNotNull { m ->
            val fs = forms[m.slot].orEmpty()
            val current = effectiveMember(m).types
            val megaForm = megaFormOf(m)
            val megaTypes = megaForm?.types?.takeIf { it.isNotEmpty() }
            val sets = buildList {
                if (current.isNotEmpty()) add(current)
                if (megaTypes != null) {
                    if (megaTypes != current) add(megaTypes)
                    fs.firstOrNull()?.types?.let { if (it.isNotEmpty() && it != current && it !in this) add(it) }
                }
            }
            if (sets.isEmpty()) return@mapNotNull null
            val immunities = setOfNotNull(
                abilityImmuneType(m.ability),
                megaForm?.abilities?.singleOrNull()?.let { abilityImmuneType(it.name) },
            )
            DefProfile(sets, immunities)
        }

    // Defensive coverage is driven by the team's TYPES (counting base + Mega forms).
    val defensive: List<TypeMatchup>
        get() = chart?.let { TeamAnalysis.defensiveMatchups(defensiveProfiles, it) } ?: emptyList()

    val sharedWeaknesses: List<TypeMatchup>
        get() = TeamAnalysis.sharedWeaknesses(defensive)

    /**
     * Per-Pokémon defensive breakdown. A member with a Mega Stone appears twice —
     * once as its base form and once as the Mega — since they have different typings.
     */
    val defensiveByMon: List<MonDefense>
        get() {
            val c = chart ?: return emptyList()
            return members.flatMap { m ->
                val fs = forms[m.slot].orEmpty()
                val megaForm = megaFormIndex(m.item, fs)?.let { fs.getOrNull(it) }
                val entries = buildList {
                    if (megaForm != null) {
                        val base = fs.firstOrNull()
                        add(
                            MonEntry(
                                m.displayName,
                                base?.spriteUrl(m.shiny) ?: m.spriteUrl,
                                base?.types ?: m.types,
                                m.ability,
                            ),
                        )
                        add(
                            MonEntry(
                                "Mega ${m.displayName}",
                                megaForm.spriteUrl(m.shiny),
                                megaForm.types,
                                megaForm.abilities.singleOrNull()?.name ?: m.ability,
                            ),
                        )
                    } else {
                        val em = effectiveMember(m)
                        val regForm = m.formSlug?.let { s -> fs.firstOrNull { it.slug == s && !it.isBase } }
                        val label = regForm?.let { "${it.label.replaceFirstChar { c -> c.uppercase() }} ${m.displayName}" }
                            ?: m.displayName
                        add(MonEntry(label, regForm?.spriteUrl(m.shiny) ?: m.spriteUrl, em.types, m.ability))
                    }
                }
                entries.filter { it.types.isNotEmpty() }.map { e ->
                    val imm = abilityImmuneType(e.ability)
                    val weak4 = mutableListOf<String>()
                    val weak2 = mutableListOf<String>()
                    val resist = mutableListOf<String>()
                    val immune = mutableListOf<String>()
                    TypeChart.TYPES.forEach { atk ->
                        if (atk == imm) { immune += atk; return@forEach }
                        val mult = c.multiplierAgainst(atk, e.types)
                        when {
                            mult == 0.0 -> immune += atk
                            mult >= 4.0 -> weak4 += atk
                            mult > 1.0 -> weak2 += atk
                            mult < 1.0 -> resist += atk
                        }
                    }
                    MonDefense(e.label, e.spriteUrl, weak4, weak2, resist, immune, imm != null)
                }
            }
        }

    // Offensive coverage is driven by the team's MOVESETS (Normal-type moves count as
    // whatever type an "-ate" ability — Aerilate on a Mega Salamence, say — converts them to).
    val offensiveCoverage: Set<String>
        get() = chart?.let { c ->
            val attackingTypes = members.flatMap { m ->
                TeamAnalysis.attackingTypesFor(m.spentMoves, moveInfo, effectiveAbility(m))
            }.toSet()
            TeamAnalysis.offensiveCoverage(attackingTypes, c)
        } ?: emptySet()

    /**
     * Per-Pokémon offensive breakdown: for each member, which defending types its
     * damaging moves hit super-effectively vs. which resist / negate every attack. A
     * member with a Mega Stone equipped appears twice — once as its base form and once
     * as the Mega — since the two can have different abilities (an "-ate" ability like
     * Aerilate retypes Normal-type moves only while it's the one actually active).
     */
    val offensiveByMon: List<MonOffense>
        get() {
            val c = chart ?: return emptyList()
            fun row(label: String, sprite: String, ability: String?, moves: List<String>): MonOffense {
                val atkMoves = moves.mapNotNull { moveInfo[it] }.filter { it.isAttacking }
                val atkTypes = TeamAnalysis.attackingTypesFor(moves, moveInfo, ability).toList()
                val se = mutableListOf<String>()
                val resisted = mutableListOf<String>()
                if (atkTypes.isNotEmpty()) {
                    TypeChart.TYPES.forEach { def ->
                        val best = atkTypes.maxOf { c.multiplier(it, def) }
                        when {
                            best > 1.0 -> se += def
                            best < 1.0 -> resisted += def
                        }
                    }
                }
                return MonOffense(label, sprite, atkMoves.map { it.displayName }, atkTypes, se, resisted, atkTypes.isEmpty())
            }
            return members.flatMap { m ->
                val fs = forms[m.slot].orEmpty()
                val megaForm = megaFormOf(m)
                if (megaForm != null) {
                    val base = fs.firstOrNull()
                    listOf(
                        row(m.displayName, base?.spriteUrl(m.shiny) ?: m.spriteUrl, m.ability, m.spentMoves),
                        row(
                            "Mega ${m.displayName}",
                            megaForm.spriteUrl(m.shiny),
                            megaForm.abilities.singleOrNull()?.name ?: m.ability,
                            m.spentMoves,
                        ),
                    )
                } else {
                    val label = m.formSlug?.let { s -> fs.firstOrNull { it.slug == s && !it.isBase } }
                        ?.let { "${it.label.replaceFirstChar { c2 -> c2.uppercase() }} ${m.displayName}" }
                        ?: m.displayName
                    listOf(row(label, memberSprite(m), m.ability, m.spentMoves))
                }
            }
        }

    val speedOrder get() = TeamAnalysis.speedOrder(analysisMembers)

    /** The sprite for the form a member actually fields (Mega-by-stone, else regional, else base). */
    fun memberSprite(m: TeamMember): String {
        val fs = forms[m.slot].orEmpty()
        megaFormIndex(m.item, fs)?.let { idx -> fs.getOrNull(idx)?.let { return it.spriteUrl(m.shiny) } }
        m.formSlug?.let { s -> fs.firstOrNull { it.slug == s && !it.isBase }?.let { return it.spriteUrl(m.shiny) } }
        return m.spriteUrl
    }
}

private data class MonEntry(val label: String, val spriteUrl: String, val types: List<String>, val ability: String?)

/** One Pokémon's offensive coverage. */
data class MonOffense(
    val label: String,
    val spriteUrl: String,
    val moveNames: List<String>,
    val moveTypes: List<String>,
    val superEffective: List<String>,
    val resisted: List<String>,
    val noMoves: Boolean,
)

/** One Pokémon's (or Mega form's) defensive type profile. */
data class MonDefense(
    val label: String,
    val spriteUrl: String,
    val weak4: List<String>,
    val weak2: List<String>,
    val resist: List<String>,
    val immune: List<String>,
    val hasAbilityImmunity: Boolean,
)

@HiltViewModel
class TeamEditorViewModel @Inject constructor(
    private val teamRepo: TeamRepository,
    private val pokeRepo: PokemonRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val teamId: Long = savedStateHandle.get<String>("teamId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(TeamEditorUiState(teamId = teamId))
    val state: StateFlow<TeamEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            pokeRepo.getTypeChart().onSuccess { c -> _state.update { it.copy(chart = c) } }
        }
        viewModelScope.launch {
            val team: Team = teamRepo.observeTeam(teamId).first() ?: run {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            // Show the slots straight away (each card renders a spinner until its
            // types/stats/abilities arrive), then fill them in one at a time.
            _state.update { it.copy(name = team.name, members = team.members, isLoading = false) }
            val enriched = team.members.mapNotNull { m ->
                val result = enrich(m)?.let { e -> applyEnrichment(m.speciesId, e) }
                // m.slot is where this species was BEFORE the enrichment fetch above
                // suspended — a swap while it was in flight can have moved it since,
                // so re-resolve its current slot rather than assume m.slot still
                // holds it (ensureForms would otherwise silently no-op on an empty
                // slot and this Pokémon's forms would never load).
                _state.value.members.firstOrNull { it.speciesId == m.speciesId }?.let { ensureForms(it.slot) }
                result
            }
            enriched.flatMap { it.spentMoves }.distinct().forEach { ensureMoveInfo(it) }
            enriched.mapNotNull { it.item }.distinct().forEach { loadItemInfo(listOf(it)) }
            enriched.flatMap { m -> m.abilityChoices.map { it.name } }.distinct().forEach { ensureAbilityInfo(it) }
        }
        viewModelScope.launch {
            teamRepo.observeTeams().collect { teams ->
                val others = teams.filter { it.id != teamId && it.members.isNotEmpty() }
                _state.update { it.copy(otherTeams = others) }
            }
        }
    }

    /**
     * Fetches base-form metadata for [member]'s species, or null if the fetch
     * failed. The result is only ever consumed through [applyEnrichment], which
     * merges the fetched fields onto whatever member is *currently* on the team
     * (and recomputes ability against that live member) — so [member] here just
     * needs to carry the species to look up, and any field on the returned copy
     * besides displayName / types / baseStats / movePool / abilityChoices is
     * never read. Returning null on failure (rather than [member] unchanged,
     * which [applyEnrichment] can't tell apart from "nothing to fetch") matters:
     * an empty [TeamMember.abilityChoices] would otherwise read as "this species
     * has no valid abilities" and wipe out a perfectly good saved ability.
     */
    private suspend fun enrich(member: TeamMember): TeamMember? {
        val d = pokeRepo.getPokemon(member.speciesName).getOrNull() ?: return null
        return member.copy(
            displayName = d.displayName,
            types = d.types.map { it.name },
            baseStats = d.stats.associate { s ->
                (StatKey.entries.firstOrNull { it.apiName == s.name } ?: StatKey.HP) to s.base
            },
            movePool = d.movePool,
            abilityChoices = d.abilities.map { AbilityChoice(it.name, it.displayName, it.isHidden) },
        )
    }

    private fun ensureMoveInfo(name: String) {
        if (_state.value.moveInfo.containsKey(name)) return
        viewModelScope.launch {
            pokeRepo.getMoveInfo(name).onSuccess { info ->
                _state.update { it.copy(moveInfo = it.moveInfo + (name to info)) }
            }
        }
    }

    private var movePoolJob: Job? = null

    /** Loads type / power / accuracy for every move in a pool so the picker can filter and sort. */
    fun loadMovePool(pool: List<String>) {
        movePoolJob?.cancel()
        movePoolJob = viewModelScope.launch {
            pool.filterNot { _state.value.moveInfo.containsKey(it) }
                .chunked(10)
                .forEach { chunk ->
                    val loaded = coroutineScope {
                        chunk.map { name -> async { pokeRepo.getMoveInfo(name).getOrNull()?.let { name to it } } }
                            .awaitAll()
                            .filterNotNull()
                    }
                    if (loaded.isNotEmpty()) _state.update { it.copy(moveInfo = it.moveInfo + loaded) }
                }
        }
    }

    /** Slugs we've already tried to fetch item info for — success or not, don't ask again. */
    private val itemInfoRequested = mutableSetOf<String>()

    /** Loads effect text for a batch of held items so the item picker can show descriptions. */
    fun loadItemInfo(slugs: List<String>) {
        val missing = slugs.filterNot { it in itemInfoRequested }.distinct()
        if (missing.isEmpty()) return
        itemInfoRequested += missing
        viewModelScope.launch {
            missing.chunked(8).forEach { chunk ->
                val loaded = coroutineScope {
                    chunk.map { slug ->
                        async { pokeRepo.getItemInfo(heldItemApiSlug(slug)).getOrNull()?.let { slug to it } }
                    }
                        .awaitAll()
                        .filterNotNull()
                }
                if (loaded.isNotEmpty()) _state.update { it.copy(itemInfo = it.itemInfo + loaded) }
            }
        }
    }

    private fun ensureAbilityInfo(slug: String) {
        if (slug.isBlank() || _state.value.abilityInfo.containsKey(slug)) return
        viewModelScope.launch {
            pokeRepo.getAbility(slug).onSuccess { d ->
                val text = d.shortEffect.ifBlank { d.effect }
                if (text.isNotBlank()) {
                    _state.update { it.copy(abilityInfo = it.abilityInfo + (slug to text)) }
                }
            }
        }
    }

    private fun persist() {
        viewModelScope.launch { teamRepo.saveMembers(teamId, _state.value.members) }
    }

    private fun mutate(slot: Int, persist: Boolean = true, block: (TeamMember) -> TeamMember) {
        _state.update { s -> s.copy(members = s.members.map { if (it.slot == slot) block(it) else it }) }
        if (persist) persist()
    }

    fun openSlot(slot: Int) {
        _state.update { it.copy(openSlot = slot) }
        ensureForms(slot)
    }

    fun closeSlot() = _state.update { it.copy(openSlot = null) }

    /**
     * The user picked a form tab.
     *  - Regional form → the Pokémon *is* that form; persist it.
     *  - Base → revert to base and drop any equipped Mega Stone.
     *  - Mega → not a persisted form choice; equip the matching Mega Stone so the
     *    Mega is "real" (it happens in battle via the item).
     */
    fun selectForm(slot: Int, index: Int) {
        val fs = _state.value.forms[slot].orEmpty()
        val form = fs.getOrNull(index) ?: return
        val member = _state.value.members.firstOrNull { it.slot == slot } ?: return
        when {
            form.isMega -> {
                val stone = com.pokedex.app.domain.team.HELD_ITEMS.firstOrNull {
                    it.category == com.pokedex.app.domain.team.ItemCategory.MEGA_STONE &&
                        it.megaSpecies == member.speciesName &&
                        megaFormIndex(it.slug, fs) == index
                }
                if (stone != null && member.item != stone.slug) setItem(slot, stone.slug)
                // A Mega is item-driven, not a persisted form — clear any regional choice.
                mutate(slot) { it.copy(formSlug = null) }
            }
            form.isBase -> {
                val removeStone = com.pokedex.app.domain.team.HELD_ITEMS
                    .firstOrNull { it.slug == member.item }?.category == com.pokedex.app.domain.team.ItemCategory.MEGA_STONE
                applyForm(slot, form, fs.firstOrNull())
                if (removeStone) setItem(slot, null)
            }
            else -> {
                // Regional form — incompatible with a Mega Stone; drop it.
                val hasMegaStone = com.pokedex.app.domain.team.HELD_ITEMS
                    .firstOrNull { it.slug == member.item }?.category == com.pokedex.app.domain.team.ItemCategory.MEGA_STONE
                if (hasMegaStone) setItem(slot, null)
                applyForm(slot, form, fs.firstOrNull())
            }
        }
    }

    private fun applyForm(slot: Int, form: MemberForm, base: MemberForm?, persist: Boolean = true) {
        val src = if (form.isBase) base else form
        mutate(slot, persist = persist) { m ->
            val abilities = src?.abilities ?: m.abilityChoices
            m.copy(
                formSlug = if (form.isBase) null else form.slug,
                types = src?.types ?: m.types,
                baseStats = src?.baseStats ?: m.baseStats,
                abilityChoices = abilities,
                movePool = src?.movePool?.takeIf { it.isNotEmpty() } ?: m.movePool,
                ability = when {
                    abilities.any { it.name == m.ability } -> m.ability
                    else -> abilities.firstOrNull { !it.isHidden }?.name ?: m.ability
                },
            )
        }
    }

    /** Loads the Mega / regional / other varieties for a slot's Pokémon, once. */
    private fun ensureForms(slot: Int) {
        if (_state.value.forms.containsKey(slot)) return
        val member = _state.value.members.firstOrNull { it.slot == slot } ?: return
        val speciesId = member.speciesId
        _state.update { it.copy(forms = it.forms + (slot to emptyList())) }
        viewModelScope.launch {
            val bundle = pokeRepo.getFormsBundle(speciesId).getOrNull()
            val list = if (bundle == null) {
                emptyList()
            } else {
                buildList {
                    add(bundle.base.toMemberForm("Base", isBase = true))
                    bundle.alternates.forEach { add(it.detail.toMemberForm(it.tabLabel, isBase = false, kind = it.kind)) }
                }
            }
            // The species may have moved to a different slot (swapSlots) — or been
            // removed entirely — while this fetch was in flight. Attach the fetched
            // forms wherever it currently lives, rather than the slot this fetch
            // started for, so a swap doesn't discard a load that's about to finish;
            // if it's gone from the team altogether, there's nowhere left to put them.
            val currentSlot = _state.value.members.firstOrNull { it.speciesId == speciesId }?.slot ?: return@launch
            _state.update { it.copy(forms = it.forms + (currentSlot to list)) }
            list.flatMap { f -> f.abilities.map { it.name } }.distinct().forEach { ensureAbilityInfo(it) }

            // Re-apply a previously-saved regional form choice — this only hydrates
            // transient UI fields (types/stats/movePool aren't columns in TeamMemberEntity
            // at all), so it must not persist: every team-open would otherwise write an
            // unchanged row back to Room purely to redisplay data that was never actually
            // edited, bumping updatedAt and pushing the team to the top of the list.
            val m = _state.value.members.firstOrNull { it.slot == currentSlot }
            val stored = m?.formSlug?.let { slug -> list.firstOrNull { it.slug == slug } }
            stored?.let {
                if (it.isMega) {
                    mutate(currentSlot) { member -> member.copy(formSlug = null) } // legacy
                } else {
                    applyForm(currentSlot, it, list.firstOrNull(), persist = false)
                }
            }
        }
    }

    private fun forgetForms(slot: Int) = _state.update {
        it.copy(forms = it.forms - slot)
    }

    fun renameTeam(name: String) {
        _state.update { it.copy(name = name) }
        viewModelScope.launch { teamRepo.renameTeam(teamId, name) }
    }

    /** Champions allows only one Pokémon per species. Returns false if it's a dupe. */
    fun addPokemon(slot: Int, speciesId: Int, speciesName: String): Boolean =
        placeMember(slot, TeamMember(slot = slot, speciesId = speciesId, speciesName = speciesName, displayName = speciesName))

    /**
     * Copies [source]'s full build — item, moves, ability, Stat Points, alignment,
     * shiny, gender, and any saved regional form — from another team into [slot] as
     * an independent starting point. Editing it here never touches the team [source]
     * came from. Returns false if that species is already on this team.
     */
    fun addFromMember(slot: Int, source: TeamMember): Boolean =
        placeMember(slot, source.copy(slot = slot, displayName = source.speciesName))

    /** Shared by [addPokemon] and [addFromMember]: place [member] in [slot], then enrich it. */
    private fun placeMember(slot: Int, member: TeamMember): Boolean {
        if (_state.value.members.any { it.speciesId == member.speciesId && it.slot != slot }) return false
        forgetForms(slot)
        _state.update { s ->
            s.copy(members = (s.members.filterNot { it.slot == slot } + member).sortedBy { it.slot })
        }
        // Persist the raw placement immediately, before the slower network-backed
        // enrichment below — otherwise leaving the screen while that fetch is still
        // in flight cancels viewModelScope and the newly added Pokémon is never saved.
        persist()
        viewModelScope.launch {
            val e = enrich(member) ?: return@launch
            val result = applyEnrichment(member.speciesId, e) ?: return@launch
            persist()
            result.abilityChoices.forEach { ensureAbilityInfo(it.name) }
            result.spentMoves.forEach { ensureMoveInfo(it) }
            result.item?.let { loadItemInfo(listOf(it)) }
        }
        ensureForms(slot)
        return true
    }

    /**
     * Merges [enriched]'s fetched fields (display name, types, base stats, move
     * pool, ability choices) onto whatever member of species [speciesId] is
     * currently on the team — wherever that is. A user can edit that member,
     * or move it to a different slot (`swapSlots`), while the network fetch
     * behind [enriched] was in flight; looking it up by species rather than the
     * slot the fetch started for means a swap doesn't strand the result at a
     * slot that species no longer occupies, and merging onto the *current*
     * member (rather than overwriting with [enriched] wholesale, which reflects
     * only what the slot held when the fetch started) means we never restore
     * stale data over a newer edit. Returns the merged member, or null if that
     * species is no longer on the team at all.
     */
    private fun applyEnrichment(speciesId: Int, enriched: TeamMember): TeamMember? {
        var merged: TeamMember? = null
        _state.update { s ->
            val current = s.members.firstOrNull { it.speciesId == speciesId } ?: return@update s
            // While a regional form is active, applyForm already set types/baseStats/
            // movePool/abilityChoices to that form's data — a base-species enrichment
            // fetch resolving later must not reset them back to the base species.
            val m = if (current.formSlug != null) {
                current.copy(displayName = enriched.displayName)
            } else {
                val ability = if (current.ability != null && enriched.abilityChoices.any { it.name == current.ability }) {
                    current.ability
                } else {
                    enriched.abilityChoices.firstOrNull { !it.isHidden }?.name
                }
                current.copy(
                    displayName = enriched.displayName,
                    ability = ability,
                    types = enriched.types,
                    baseStats = enriched.baseStats,
                    movePool = enriched.movePool,
                    abilityChoices = enriched.abilityChoices,
                )
            }
            merged = m
            s.copy(members = s.members.map { if (it.speciesId == speciesId) m else it })
        }
        return merged
    }

    fun removeMember(slot: Int) {
        _state.update { s -> s.copy(members = s.members.filterNot { it.slot == slot }, openSlot = null) }
        forgetForms(slot)
        persist()
    }

    /** Long-press-drag reordering: trade the Pokémon (and any loaded form data) in slots [a] and [b]. */
    fun swapSlots(a: Int, b: Int) {
        if (a == b) return
        val current = _state.value.members
        val ma = current.firstOrNull { it.slot == a }
        val mb = current.firstOrNull { it.slot == b }
        if (ma == null && mb == null) return
        val rest = current.filterNot { it.slot == a || it.slot == b }
        val moved = listOfNotNull(ma?.copy(slot = b), mb?.copy(slot = a))
        _state.update { s ->
            val fa = s.forms[a]
            val fb = s.forms[b]
            s.copy(
                members = (rest + moved).sortedBy { it.slot },
                forms = s.forms.toMutableMap().apply {
                    if (fb != null) put(a, fb) else remove(a)
                    if (fa != null) put(b, fa) else remove(b)
                },
            )
        }
        persist()
    }

    /** Long-press-drag reordering: trade a member's moves at indices [a] and [b]. */
    fun swapMoves(slot: Int, a: Int, b: Int) {
        if (a == b) return
        mutate(slot) { m ->
            val list = m.moves.toMutableList()
            while (list.size < 4) list.add(null)
            val tmp = list[a]
            list[a] = list[b]
            list[b] = tmp
            m.copy(moves = list)
        }
    }

    fun setAbility(slot: Int, ability: String) = mutate(slot) { it.copy(ability = ability) }
    fun setNature(slot: Int, nature: Nature) = mutate(slot) { it.copy(nature = nature) }
    fun setShiny(slot: Int, shiny: Boolean) = mutate(slot) { it.copy(shiny = shiny) }
    fun setGender(slot: Int, gender: Gender) = mutate(slot) { it.copy(gender = gender) }
    fun setItem(slot: Int, item: String?) {
        mutate(slot) { it.copy(item = item) }
        item?.let { loadItemInfo(listOf(it)) }
    }

    fun setMove(slot: Int, index: Int, move: String?) {
        mutate(slot) { m ->
            val list = m.moves.toMutableList()
            while (list.size < 4) list.add(null)
            list[index] = move
            m.copy(moves = list)
        }
        move?.let(::ensureMoveInfo)
    }

    fun setSp(slot: Int, stat: StatKey, value: Int) {
        mutate(slot) { m ->
            val others = m.sp.filterKeys { it != stat }.values.sum()
            val allowed = (StatCalc.MAX_SP_TOTAL - others).coerceAtMost(StatCalc.MAX_SP_PER_STAT)
            m.copy(sp = m.sp + (stat to value.coerceIn(0, allowed)))
        }
    }

    fun clearSp(slot: Int) = mutate(slot) { m -> m.copy(sp = StatKey.entries.associateWith { 0 }) }

    private fun PokemonDetail.toMemberForm(label: String, isBase: Boolean, kind: FormKind? = null) = MemberForm(
        label = label,
        slug = name,
        pokemonId = id,
        kind = kind,
        types = types.map { it.name },
        baseStats = stats.associate { s ->
            (StatKey.entries.firstOrNull { it.apiName == s.name } ?: StatKey.HP) to s.base
        },
        abilities = abilities.map { AbilityChoice(it.name, it.displayName, it.isHidden) },
        movePool = movePool,
        isBase = isBase,
        spriteDefault = sprites.frontDefault,
        spriteShiny = sprites.frontShiny,
        artworkDefault = sprites.officialArtwork,
        artworkShiny = sprites.officialArtworkShiny,
    )
}
