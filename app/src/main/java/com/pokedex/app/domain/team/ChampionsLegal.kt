package com.pokedex.app.domain.team

/**
 * The Pokémon eligible in the current Pokémon Champions regulation (Regulation
 * Set M-C, Sept–Dec 2026). Champions uses a curated allow-list that grows each
 * regulation rather than a ban-list, so this is a fixed snapshot — it will need
 * updating when the regulation rotates.
 *
 * Source: community-maintained legal lists (MetaVGC / Serebii) for Reg M-C,
 * reduced here to base-species PokéAPI slugs (Mega and regional variants collapse
 * onto their base species, which the team builder keys on).
 */
object ChampionsLegal {

    const val REGULATION = "Regulation M-C"

    /** Base-species slugs legal in the current regulation. */
    val SPECIES: Set<String> = setOf(
        "abomasnow", "absol", "aegislash", "aerodactyl", "aggron", "alakazam", "alcremie",
        "altaria", "ampharos", "annihilape", "appletun", "araquanid", "arbok", "arboliva",
        "arcanine", "archaludon", "ariados", "armarouge", "aromatisse", "audino", "aurorus",
        "avalugg", "azumarill", "banette", "barbaracle", "basculegion", "bastiodon",
        "baxcalibur", "beartic", "beedrill", "bellibolt", "blastoise", "blaziken", "camerupt",
        "castform", "ceruledge", "chandelure", "charizard", "chesnaught", "chimecho",
        "cinderace", "clawitzer", "clefable", "cofagrigus", "conkeldurr", "corviknight",
        "crabominable", "decidueye", "dedenne", "delphox", "diggersby", "ditto", "dragalge",
        "dragapult", "dragonite", "drampa", "eelektross", "emboar", "emolga", "empoleon",
        "espathra", "espeon", "excadrill", "falinks", "farfetchd", "sirfetchd", "farigiraf",
        "feraligatr", "flapple", "flareon", "floette", "florges", "forretress", "froslass",
        "furfrou", "gallade", "garbodor", "garchomp", "gardevoir", "garganacl", "gengar",
        "gholdengo", "glaceon", "glalie", "glimmora", "gliscor", "gogoat", "golisopod",
        "golurk", "goodra", "gourgeist", "grapploct", "greninja", "grimmsnarl", "gyarados",
        "hatterene", "hawlucha", "heliolisk", "heracross", "hippowdon", "houndoom",
        "houndstone", "hydrapple", "hydreigon", "incineroar", "indeedee", "infernape",
        "inteleon", "jolteon", "kangaskhan", "kingambit", "kleavor", "klefki", "kommo-o",
        "krookodile", "leafeon", "liepard", "lopunny", "lucario", "luxray", "lycanroc",
        "mabosstiff", "machamp", "malamar", "mamoswine", "manectric", "maushold", "mawile",
        "medicham", "meganium", "meowscarada", "meowstic", "metagross", "milotic", "mimikyu",
        "morpeko", "mr-mime", "mr-rime", "mudsdale", "musharna", "ninetales", "noivern",
        "oranguru", "orthworm", "overqwil", "palafin", "pangoro", "passimian", "patrat",
        "pawmot", "pelipper", "perrserker", "persian", "pidgeot", "pikachu", "pincurchin",
        "pinsir", "politoed", "polteageist", "primarina", "pyroar", "quaquaval", "qwilfish",
        "raichu", "rampardos", "reuniclus", "rhyperior", "rillaboom", "roserade", "rotom",
        "runerigus", "sableye", "salamence", "salazzle", "samurott", "sandaconda", "sceptile",
        "scizor", "scolipede", "scovillain", "scrafty", "serperior", "sharpedo", "simipour",
        "simisage", "simisear", "sinistcha", "skarmory", "skeledirge", "slowbro", "slowking",
        "slurpuff", "sneasler", "snorlax", "spiritomb", "squawkabilly", "staraptor", "starmie",
        "steelix", "stunfisk", "swalot", "swampert", "sylveon", "talonflame", "tauros",
        "thievul", "tinkaton", "torkoal", "torterra", "toucannon", "toxapex", "toxicroak",
        "toxtricity", "trevenant", "tsareena", "typhlosion", "tyranitar", "tyrantrum",
        "umbreon", "vanilluxe", "vaporeon", "venusaur", "victreebel", "vileplume", "vivillon",
        "volcarona", "weavile", "whimsicott", "wigglytuff", "wyrdeer", "zoroark",
    )

    fun isLegalSpecies(slug: String): Boolean {
        val s = slug.lowercase()
        return s in SPECIES || SPECIES.any { s.startsWith("$it-") }
    }

    /** Held-item slugs legal in Reg M-C (166 items). Derived from [HELD_ITEMS]. */
    val ITEMS: Set<String> by lazy {
        HELD_ITEMS.filter { it.championsLegal }.map { it.slug }.toSet()
    }

    fun isLegalItem(slug: String?): Boolean = slug == null || slug in ITEMS
}
