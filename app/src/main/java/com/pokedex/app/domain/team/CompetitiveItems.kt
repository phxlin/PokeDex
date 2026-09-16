package com.pokedex.app.domain.team

import com.pokedex.app.core.Sprites

/** Broad grouping used to filter the held-item picker. */
enum class ItemCategory(val label: String) {
    STAPLE("Staples"),
    CHOICE("Choice"),
    OFFENSE("Offense"),
    UTILITY("Utility"),
    WEATHER("Weather & field"),
    TYPE_BOOST("Type boost"),
    BERRY("Berries"),
    MEGA_STONE("Mega Stones"),
}

data class HeldItem(
    val slug: String,
    val display: String,
    val category: ItemCategory,
    /** Whether this item is legal in the current Pokémon Champions regulation (Reg M-C). */
    val championsLegal: Boolean = true,
    /** For a Mega Stone: the base species it works on (PokéAPI species slug). */
    val megaSpecies: String? = null,
    /** For a Mega Stone: the Mega form's PokéAPI pokemon id, used for its sprite (fast path). */
    val megaPokemonId: Int? = null,
    /** X/Y suffix for split Megas, e.g. "-x". */
    val megaSuffix: String = "",
    /**
     * The slug PokéAPI actually serves this item under, when it differs from our
     * stored [slug]. PokéAPI still uses the pre-Gen-VIII name for a few items —
     * e.g. our "leek" is "stick" in the API and the sprite repo.
     */
    val apiSlug: String = slug,
    /**
     * A bundled one-line description, used when PokéAPI has no effect text for
     * this item — true for most Gen VIII / IX items and every Champions-only
     * Mega Stone. PokéAPI's own text is preferred when it exists.
     */
    val blurb: String? = null,
) {
    /** False for items PokéAPI's sprite repo doesn't carry (newer items, custom Megas). */
    val hasSprite: Boolean
        get() = apiSlug !in SPRITELESS_ITEMS &&
            !(category == ItemCategory.MEGA_STONE && megaPokemonId == null)

    val spriteUrl: String?
        get() = if (hasSprite) Sprites.item(apiSlug) else null

    /** The Mega form's PokéAPI pokemon slug, e.g. "raichu-mega-y". */
    val megaPokemonSlug: String?
        get() = megaSpecies?.let { "$it-mega$megaSuffix" }
}

/** PokéAPI's sprite repo stops around Gen VII; these newer items 404 there. */
private val SPRITELESS_ITEMS = setOf(
    "fairy-feather", "clear-amulet", "covert-cloak", "booster-energy", "loaded-dice",
    "punching-glove", "mirror-herb", "ability-shield", "blunder-policy", "heavy-duty-boots",
    "room-service", "throat-spray", "utility-umbrella", "eject-pack",
)

/** The Mega form's sprite for an equipped Mega Stone [itemSlug] via the hardcoded id, or null. */
fun megaSpriteFor(itemSlug: String?, shiny: Boolean): String? {
    val id = itemSlug?.let { s -> HELD_ITEMS.firstOrNull { it.slug == s }?.megaPokemonId } ?: return null
    return Sprites.pokemon(id, shiny)
}

/** The Mega form's pokemon slug for an equipped Mega Stone, e.g. "gyarados-mega" / "raichu-mega-y". */
fun megaPokemonSlugFor(itemSlug: String?): String? =
    itemSlug?.let { s -> HELD_ITEMS.firstOrNull { it.slug == s } }?.megaPokemonSlug

/**
 * A short badge for the special form a member is currently in — from its saved
 * regional form ([formSlug]) or an equipped Mega Stone ([itemSlug]). `null` for base.
 */
fun formBadge(formSlug: String?, itemSlug: String?): String? {
    val stone = itemSlug?.let { i -> HELD_ITEMS.firstOrNull { it.slug == i } }
    if (stone?.category == ItemCategory.MEGA_STONE) {
        return when (stone.megaSuffix) {
            "-x" -> "MEGA X"
            "-y" -> "MEGA Y"
            else -> "MEGA"
        }
    }
    formSlug?.let { s ->
        return when {
            "-mega-x" in s -> "MEGA X"
            "-mega-y" in s -> "MEGA Y"
            "-mega" in s -> "MEGA"
            "-primal" in s -> "PRIMAL"
            "-gmax" in s || "-gigantamax" in s -> "G-MAX"
            "-alola" in s -> "ALOLA"
            "-galar" in s -> "GALAR"
            "-hisui" in s -> "HISUI"
            "-paldea" in s -> "PALDEA"
            else -> null
        }
    }
    return null
}

/** Item-sprite URL for a stored item [slug], or null if PokéAPI has no sprite for it. */
fun heldItemSpriteUrl(slug: String?): String? {
    if (slug == null) return null
    val known = HELD_ITEMS.firstOrNull { it.slug == slug }
    if (known != null) return known.spriteUrl
    return Sprites.item(slug)
}

/** A short description for a stored item [slug] when no live effect text is available. */
fun heldItemBlurb(slug: String?): String? =
    slug?.let { s -> HELD_ITEMS.firstOrNull { it.slug == s }?.blurb }

/**
 * Placeholder glyph for a stored item [slug] when [heldItemSpriteUrl] has
 * nothing to show — most items have a PokéAPI sprite, but the newest
 * Champions-exclusive Mega Stones don't have published icon art yet. Picked
 * by category so a Mega Stone without art still reads as one at a glance
 * instead of a generic bag.
 */
fun heldItemFallbackGlyph(slug: String?): String =
    when (slug?.let { s -> HELD_ITEMS.firstOrNull { it.slug == s }?.category }) {
        ItemCategory.MEGA_STONE -> "💠"
        ItemCategory.BERRY -> "🍒"
        else -> "🎒"
    }

/** The slug PokéAPI serves a stored item [slug] under (for `item/{name}` calls). */
fun heldItemApiSlug(slug: String): String =
    HELD_ITEMS.firstOrNull { it.slug == slug }?.apiSlug ?: slug

/** Human-readable name for a stored item [slug]. */
fun heldItemDisplayName(slug: String?): String? {
    if (slug == null) return null
    return HELD_ITEMS.firstOrNull { it.slug == slug }?.display
        ?: slug.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}

/** Whether [itemSlug] is a Mega Stone that applies to [speciesSlug]. */
fun megaStoneMatches(itemSlug: String?, speciesSlug: String?): Boolean {
    val item = itemSlug?.let { s -> HELD_ITEMS.firstOrNull { it.slug == s } } ?: return false
    return item.category == ItemCategory.MEGA_STONE && item.megaSpecies == speciesSlug
}

private val ITEMS: List<HeldItem> = buildList {
    fun item(
        slug: String,
        display: String,
        category: ItemCategory,
        legal: Boolean = true,
        api: String = slug,
        blurb: String? = null,
    ) = add(HeldItem(slug, display, category, championsLegal = legal, apiSlug = api, blurb = blurb))

    // ---- Reg M-C legal non-Mega items -------------------------------------
    item("leftovers", "Leftovers", ItemCategory.STAPLE)
    item("focus-sash", "Focus Sash", ItemCategory.STAPLE)
    item("focus-band", "Focus Band", ItemCategory.STAPLE)
    item("rocky-helmet", "Rocky Helmet", ItemCategory.STAPLE)
    item("sitrus-berry", "Sitrus Berry", ItemCategory.STAPLE)
    item("shell-bell", "Shell Bell", ItemCategory.STAPLE)
    item("big-root", "Big Root", ItemCategory.STAPLE)
    item("shed-shell", "Shed Shell", ItemCategory.STAPLE)
    item("leek", "Leek", ItemCategory.STAPLE, api = "stick") // PokéAPI still names it "stick"
    item("light-ball", "Light Ball", ItemCategory.STAPLE)

    item("choice-scarf", "Choice Scarf", ItemCategory.CHOICE)

    item("life-orb", "Life Orb", ItemCategory.OFFENSE)
    item("expert-belt", "Expert Belt", ItemCategory.OFFENSE)
    item("muscle-band", "Muscle Band", ItemCategory.OFFENSE)
    item("wise-glasses", "Wise Glasses", ItemCategory.OFFENSE)
    item("metronome", "Metronome", ItemCategory.OFFENSE)
    item("wide-lens", "Wide Lens", ItemCategory.OFFENSE)
    item("zoom-lens", "Zoom Lens", ItemCategory.OFFENSE)
    item("scope-lens", "Scope Lens", ItemCategory.OFFENSE)
    item("kings-rock", "King's Rock", ItemCategory.OFFENSE)
    item("normal-gem", "Normal Gem", ItemCategory.OFFENSE)

    item("bright-powder", "Bright Powder", ItemCategory.UTILITY)
    item("quick-claw", "Quick Claw", ItemCategory.UTILITY)
    item("iron-ball", "Iron Ball", ItemCategory.UTILITY)
    item("red-card", "Red Card", ItemCategory.UTILITY)
    item("eject-button", "Eject Button", ItemCategory.UTILITY)
    item("air-balloon", "Air Balloon", ItemCategory.UTILITY)
    item("mental-herb", "Mental Herb", ItemCategory.UTILITY)
    item("white-herb", "White Herb", ItemCategory.UTILITY)
    item("binding-band", "Binding Band", ItemCategory.UTILITY)
    item("electric-seed", "Electric Seed", ItemCategory.UTILITY)
    item("grassy-seed", "Grassy Seed", ItemCategory.UTILITY)
    item("misty-seed", "Misty Seed", ItemCategory.UTILITY)
    item("psychic-seed", "Psychic Seed", ItemCategory.UTILITY)

    item("light-clay", "Light Clay", ItemCategory.WEATHER)
    item("damp-rock", "Damp Rock", ItemCategory.WEATHER)
    item("heat-rock", "Heat Rock", ItemCategory.WEATHER)
    item("icy-rock", "Icy Rock", ItemCategory.WEATHER)
    item("smooth-rock", "Smooth Rock", ItemCategory.WEATHER)
    item("terrain-extender", "Terrain Extender", ItemCategory.WEATHER)

    item("silk-scarf", "Silk Scarf", ItemCategory.TYPE_BOOST)
    item("charcoal", "Charcoal", ItemCategory.TYPE_BOOST)
    item("mystic-water", "Mystic Water", ItemCategory.TYPE_BOOST)
    item("magnet", "Magnet", ItemCategory.TYPE_BOOST)
    item("miracle-seed", "Miracle Seed", ItemCategory.TYPE_BOOST)
    item("never-melt-ice", "Never-Melt Ice", ItemCategory.TYPE_BOOST)
    item("black-belt", "Black Belt", ItemCategory.TYPE_BOOST)
    item("poison-barb", "Poison Barb", ItemCategory.TYPE_BOOST)
    item("soft-sand", "Soft Sand", ItemCategory.TYPE_BOOST)
    item("sharp-beak", "Sharp Beak", ItemCategory.TYPE_BOOST)
    item("twisted-spoon", "Twisted Spoon", ItemCategory.TYPE_BOOST)
    item("silver-powder", "Silver Powder", ItemCategory.TYPE_BOOST)
    item("hard-stone", "Hard Stone", ItemCategory.TYPE_BOOST)
    item("spell-tag", "Spell Tag", ItemCategory.TYPE_BOOST)
    item("dragon-fang", "Dragon Fang", ItemCategory.TYPE_BOOST)
    item("black-glasses", "Black Glasses", ItemCategory.TYPE_BOOST)
    item("metal-coat", "Metal Coat", ItemCategory.TYPE_BOOST)
    item(
        "fairy-feather", "Fairy Feather", ItemCategory.TYPE_BOOST,
        blurb = "Held: Fairy-type moves from the holder do 20% more damage.",
    )

    listOf(
        "lum-berry" to "Lum Berry", "aspear-berry" to "Aspear Berry", "cheri-berry" to "Cheri Berry",
        "chesto-berry" to "Chesto Berry", "leppa-berry" to "Leppa Berry", "oran-berry" to "Oran Berry",
        "pecha-berry" to "Pecha Berry", "persim-berry" to "Persim Berry", "rawst-berry" to "Rawst Berry",
        "occa-berry" to "Occa Berry", "passho-berry" to "Passho Berry", "wacan-berry" to "Wacan Berry",
        "rindo-berry" to "Rindo Berry", "yache-berry" to "Yache Berry", "chople-berry" to "Chople Berry",
        "kebia-berry" to "Kebia Berry", "shuca-berry" to "Shuca Berry", "coba-berry" to "Coba Berry",
        "payapa-berry" to "Payapa Berry", "tanga-berry" to "Tanga Berry", "charti-berry" to "Charti Berry",
        "kasib-berry" to "Kasib Berry", "haban-berry" to "Haban Berry", "colbur-berry" to "Colbur Berry",
        "babiri-berry" to "Babiri Berry", "chilan-berry" to "Chilan Berry", "roseli-berry" to "Roseli Berry",
    ).forEach { item(it.first, it.second, ItemCategory.BERRY) }

    // ---- Mega Stones -----------------------------------------------------
    // The Mega form's data is resolved live from PokéAPI ("<species>-mega[-x/-y]");
    // the id is only a fast path for the mainline stones.
    fun mega(slug: String, display: String, species: String, pokemonId: Int? = null) {
        val suffix = when {
            slug.endsWith("-x") -> "-x"
            slug.endsWith("-y") -> "-y"
            else -> ""
        }
        val name = species.replaceFirstChar { it.uppercase() }
        val which = when (suffix) {
            "-x" -> "Mega $name X"
            "-y" -> "Mega $name Y"
            else -> "Mega $name"
        }
        add(
            HeldItem(
                slug, display, ItemCategory.MEGA_STONE,
                megaSpecies = species, megaPokemonId = pokemonId, megaSuffix = suffix,
                blurb = "Held: lets $name Mega Evolve into $which in battle.",
            ),
        )
    }

    // Mainline Mega Stones (all legal in Reg M-C).
    mega("venusaurite", "Venusaurite", "venusaur", 10033)
    mega("charizardite-x", "Charizardite X", "charizard", 10034)
    mega("charizardite-y", "Charizardite Y", "charizard", 10035)
    mega("blastoisinite", "Blastoisinite", "blastoise", 10036)
    mega("beedrillite", "Beedrillite", "beedrill", 10090)
    mega("pidgeotite", "Pidgeotite", "pidgeot", 10073)
    mega("alakazite", "Alakazite", "alakazam", 10037)
    mega("slowbronite", "Slowbronite", "slowbro", 10071)
    mega("gengarite", "Gengarite", "gengar", 10038)
    mega("kangaskhanite", "Kangaskhanite", "kangaskhan", 10039)
    mega("pinsirite", "Pinsirite", "pinsir", 10040)
    mega("gyaradosite", "Gyaradosite", "gyarados", 10041)
    mega("aerodactylite", "Aerodactylite", "aerodactyl", 10042)
    mega("ampharosite", "Ampharosite", "ampharos", 10045)
    mega("steelixite", "Steelixite", "steelix", 10072)
    mega("scizorite", "Scizorite", "scizor", 10046)
    mega("heracronite", "Heracronite", "heracross", 10047)
    mega("houndoominite", "Houndoominite", "houndoom", 10048)
    mega("tyranitarite", "Tyranitarite", "tyranitar", 10049)
    mega("sceptilite", "Sceptilite", "sceptile", 10065)
    mega("blazikenite", "Blazikenite", "blaziken", 10050)
    mega("swampertite", "Swampertite", "swampert", 10064)
    mega("gardevoirite", "Gardevoirite", "gardevoir", 10051)
    mega("sablenite", "Sablenite", "sableye", 10066)
    mega("mawilite", "Mawilite", "mawile", 10052)
    mega("aggronite", "Aggronite", "aggron", 10053)
    mega("medichamite", "Medichamite", "medicham", 10054)
    mega("manectite", "Manectite", "manectric", 10055)
    mega("sharpedonite", "Sharpedonite", "sharpedo", 10070)
    mega("cameruptite", "Cameruptite", "camerupt", 10087)
    mega("altarianite", "Altarianite", "altaria", 10067)
    mega("banettite", "Banettite", "banette", 10056)
    mega("absolite", "Absolite", "absol", 10057)
    mega("glalitite", "Glalitite", "glalie", 10074)
    mega("salamencite", "Salamencite", "salamence", 10089)
    mega("lopunnite", "Lopunnite", "lopunny", 10088)
    mega("garchompite", "Garchompite", "garchomp", 10058)
    mega("lucarionite", "Lucarionite", "lucario", 10059)
    mega("abomasite", "Abomasite", "abomasnow", 10060)
    mega("galladite", "Galladite", "gallade", 10068)
    mega("audinite", "Audinite", "audino", 10069)
    mega("metagrossite", "Metagrossite", "metagross", 10076)

    // Champions-added Megas — resolved live if PokéAPI has them, else base sprite.
    mega("raichunite-x", "Raichunite X", "raichu")
    mega("raichunite-y", "Raichunite Y", "raichu")
    mega("absolite-z", "Absolite Z", "absol")
    mega("garchompite-z", "Garchompite Z", "garchomp")
    mega("lucarionite-z", "Lucarionite Z", "lucario")
    mega("meganiumite", "Meganiumite", "meganium")
    mega("feraligite", "Feraligite", "feraligatr")
    mega("chesnaughtite", "Chesnaughtite", "chesnaught")
    mega("delphoxite", "Delphoxite", "delphox")
    mega("greninjite", "Greninjite", "greninja")
    mega("emboarite", "Emboarite", "emboar")
    mega("chandelurite", "Chandelurite", "chandelure")
    mega("chimechite", "Chimechite", "chimecho")
    mega("clefablite", "Clefablite", "clefable")
    mega("crabominite", "Crabominite", "crabominable")
    mega("dragalgeite", "Dragalgeite", "dragalge")
    mega("dragoninite", "Dragoninite", "dragonite")
    mega("drampanite", "Drampanite", "drampa")
    mega("eelektrossite", "Eelektrossite", "eelektross")
    mega("excadrite", "Excadrite", "excadrill")
    mega("falinksite", "Falinksite", "falinks")
    mega("floettite", "Floettite", "floette")
    mega("froslassite", "Froslassite", "froslass")
    mega("glimmoranite", "Glimmoranite", "glimmora")
    mega("golisopite", "Golisopite", "golisopod")
    mega("golurkite", "Golurkite", "golurk")
    mega("hawluchanite", "Hawluchanite", "hawlucha")
    mega("malamarite", "Malamarite", "malamar")
    mega("meowsticite", "Meowsticite", "meowstic")
    mega("pyroarite", "Pyroarite", "pyroar")
    mega("scolipedeite", "Scolipedeite", "scolipede")
    mega("scovillainite", "Scovillainite", "scovillain")
    mega("scraftyite", "Scraftyite", "scrafty")
    mega("skarmorite", "Skarmorite", "skarmory")
    mega("staraptorite", "Staraptorite", "staraptor")
    mega("starminite", "Starminite", "starmie")
    mega("victreebelite", "Victreebelite", "victreebel")
    mega("barbaracleite", "Barbaracleite", "barbaracle")
    mega("baxcalibrite", "Baxcalibrite", "baxcalibur")

    // ---- Commonly-used items that are NOT legal in Reg M-C (shown greyed) --
    item("assault-vest", "Assault Vest", ItemCategory.STAPLE, legal = false)
    item("eviolite", "Eviolite", ItemCategory.STAPLE, legal = false)
    item(
        "heavy-duty-boots", "Heavy-Duty Boots", ItemCategory.STAPLE, legal = false,
        blurb = "Held: the holder ignores entry hazards like Stealth Rock and Spikes.",
    )
    item("black-sludge", "Black Sludge", ItemCategory.STAPLE, legal = false)
    item(
        "clear-amulet", "Clear Amulet", ItemCategory.STAPLE, legal = false,
        blurb = "Held: prevents the holder's stats from being lowered by opponents.",
    )
    item(
        "covert-cloak", "Covert Cloak", ItemCategory.STAPLE, legal = false,
        blurb = "Held: protects the holder from the added effects of attacks.",
    )
    item("safety-goggles", "Safety Goggles", ItemCategory.STAPLE, legal = false)
    item(
        "booster-energy", "Booster Energy", ItemCategory.STAPLE, legal = false,
        blurb = "Held: triggers Protosynthesis or Quark Drive, boosting the holder's highest stat.",
    )
    item("protective-pads", "Protective Pads", ItemCategory.STAPLE, legal = false)
    item(
        "ability-shield", "Ability Shield", ItemCategory.STAPLE, legal = false,
        blurb = "Held: the holder's Ability can't be changed, suppressed, or ignored.",
    )
    item("choice-band", "Choice Band", ItemCategory.CHOICE, legal = false)
    item("choice-specs", "Choice Specs", ItemCategory.CHOICE, legal = false)
    item(
        "loaded-dice", "Loaded Dice", ItemCategory.OFFENSE, legal = false,
        blurb = "Held: multi-hit moves always hit at least four times.",
    )
    item(
        "punching-glove", "Punching Glove", ItemCategory.OFFENSE, legal = false,
        blurb = "Held: punching moves do 10% more damage and no longer make contact.",
    )
    item("weakness-policy", "Weakness Policy", ItemCategory.OFFENSE, legal = false)
    item(
        "blunder-policy", "Blunder Policy", ItemCategory.OFFENSE, legal = false,
        blurb = "Held: sharply raises Speed if the holder misses due to its own accuracy.",
    )
    item(
        "throat-spray", "Throat Spray", ItemCategory.OFFENSE, legal = false,
        blurb = "Held: raises Special Attack by one stage after the holder uses a sound move.",
    )
    item("power-herb", "Power Herb", ItemCategory.OFFENSE, legal = false)
    item(
        "mirror-herb", "Mirror Herb", ItemCategory.UTILITY, legal = false,
        blurb = "Held: copies an opponent's stat boosts once, then is consumed.",
    )
    item(
        "room-service", "Room Service", ItemCategory.UTILITY, legal = false,
        blurb = "Held: lowers the holder's Speed when Trick Room takes effect.",
    )
    item("adrenaline-orb", "Adrenaline Orb", ItemCategory.UTILITY, legal = false)
    item(
        "eject-pack", "Eject Pack", ItemCategory.UTILITY, legal = false,
        blurb = "Held: switches the holder out when one of its stats is lowered.",
    )
    item(
        "utility-umbrella", "Utility Umbrella", ItemCategory.UTILITY, legal = false,
        blurb = "Held: shields the holder from the effects of harsh sunlight and heavy rain.",
    )
    item("toxic-orb", "Toxic Orb", ItemCategory.UTILITY, legal = false)
    item("flame-orb", "Flame Orb", ItemCategory.UTILITY, legal = false)
}

/** All selectable held items, de-duplicated, legal items first. */
val HELD_ITEMS: List<HeldItem> =
    ITEMS.distinctBy { it.slug }.sortedByDescending { it.championsLegal }
