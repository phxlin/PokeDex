package com.pokedex.app.ui.list

enum class SortOption(val label: String) {
    DEX_ASC("Lowest №"),
    DEX_DESC("Highest №"),
    NAME_ASC("A → Z"),
    NAME_DESC("Z → A"),
}

/**
 * National Dex number ranges per generation, so generation filtering is a pure
 * local operation (no extra network call).
 */
enum class Generation(val number: Int, val label: String, val region: String, val range: IntRange) {
    I(1, "I", "Kanto", 1..151),
    II(2, "II", "Johto", 152..251),
    III(3, "III", "Hoenn", 252..386),
    IV(4, "IV", "Sinnoh", 387..493),
    V(5, "V", "Unova", 494..649),
    VI(6, "VI", "Kalos", 650..721),
    VII(7, "VII", "Alola", 722..809),
    VIII(8, "VIII", "Galar", 810..905),
    IX(9, "IX", "Paldea", 906..1025),
    ;

    companion object {
        fun ofNumber(n: Int): Generation? = entries.firstOrNull { it.number == n }
    }
}

data class ListControls(
    val query: String = "",
    val sort: SortOption = SortOption.DEX_ASC,
    val types: Set<String> = emptySet(),
    val generations: Set<Int> = emptySet(),
) {
    val activeFilterCount: Int get() = types.size + generations.size
    val hasActiveFilters: Boolean get() = activeFilterCount > 0
}
