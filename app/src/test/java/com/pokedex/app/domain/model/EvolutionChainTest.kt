package com.pokedex.app.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Regional-variety evolution lines. PokéAPI folds them into the base species'
 * chain and marks the regional methods with `base_form` / `evolved_form`; these
 * tests cover reconstructing "Alolan Vulpix → Alolan Ninetales" from that.
 */
class EvolutionChainTest {

    // Vulpix: Fire Stone → Ninetales, or Ice Stone → Alolan Ninetales.
    private val vulpix = EvolutionChain(
        EvolutionNode(
            speciesId = 37, name = "vulpix", displayName = "Vulpix",
            children = listOf(
                EvolutionNode(
                    speciesId = 38, name = "ninetales", displayName = "Ninetales",
                    methods = listOf(
                        EvoMethod("Use Fire Stone", null, priority = 5),
                        EvoMethod(
                            "Use Ice Stone", null, priority = 5,
                            baseFormId = 10103, baseFormName = "vulpix-alola",
                            evolvedFormId = 10104, evolvedFormName = "ninetales-alola",
                        ),
                    ),
                ),
            ),
        ),
    )

    // Meowth: Kanto → Persian, Alola → Alolan Persian, Galar → Perrserker.
    private val meowth = EvolutionChain(
        EvolutionNode(
            speciesId = 52, name = "meowth", displayName = "Meowth",
            children = listOf(
                EvolutionNode(
                    speciesId = 53, name = "persian", displayName = "Persian",
                    methods = listOf(
                        EvoMethod("High friendship", null, priority = 6),
                        EvoMethod(
                            "High friendship", null, priority = 6,
                            baseFormId = 10107, baseFormName = "meowth-alola",
                            evolvedFormId = 10108, evolvedFormName = "persian-alola",
                        ),
                    ),
                ),
                EvolutionNode(
                    speciesId = 863, name = "perrserker", displayName = "Perrserker",
                    methods = listOf(
                        EvoMethod(
                            "Lv. 28", null, priority = 6,
                            baseFormId = 10161, baseFormName = "meowth-galar",
                        ),
                    ),
                ),
            ),
        ),
    )

    // Pichu → Pikachu → Raichu / Alolan Raichu. Only Raichu has an Alolan variety,
    // and PokéAPI sets base_form ("pikachu") even on the plain Thunder Stone method.
    private val pikachu = EvolutionChain(
        EvolutionNode(
            speciesId = 172, name = "pichu", displayName = "Pichu",
            children = listOf(
                EvolutionNode(
                    speciesId = 25, name = "pikachu", displayName = "Pikachu",
                    methods = listOf(EvoMethod("High friendship", null, priority = 6)),
                    children = listOf(
                        EvolutionNode(
                            speciesId = 26, name = "raichu", displayName = "Raichu",
                            methods = listOf(
                                EvoMethod(
                                    "Use Thunder Stone", null, priority = 5,
                                    baseFormId = 25, baseFormName = "pikachu",
                                ),
                                EvoMethod(
                                    "Use Thunder Stone", null, priority = 5,
                                    baseFormId = 25, baseFormName = "pikachu",
                                    evolvedFormId = 10100, evolvedFormName = "raichu-alola",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    // Cubone: Lv. 28 → Marowak, or Lv. 28 at night → Alolan Marowak (Cubone has no Alolan form).
    private val cubone = EvolutionChain(
        EvolutionNode(
            speciesId = 104, name = "cubone", displayName = "Cubone",
            children = listOf(
                EvolutionNode(
                    speciesId = 105, name = "marowak", displayName = "Marowak",
                    methods = listOf(
                        EvoMethod("Lv. 28", null, priority = 6),
                        EvoMethod(
                            "Lv. 28 (night)", null, priority = 6,
                            evolvedFormId = 10115, evolvedFormName = "marowak-alola",
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `regionForms maps both stages of a fully-regional line`() {
        val alola = vulpix.regionForms("alola")
        assertThat(alola[37]).isEqualTo(RegionForm(10103, "vulpix-alola"))
        assertThat(alola[38]).isEqualTo(RegionForm(10104, "ninetales-alola"))
    }

    @Test
    fun `regionForms is empty for a region the line has no variety in`() {
        assertThat(vulpix.regionForms("galar")).isEmpty()
        assertThat(vulpix.regionForms("")).isEmpty()
    }

    @Test
    fun `regionForms picks the right branch per region`() {
        assertThat(meowth.regionForms("alola").keys).containsExactly(52, 53)
        assertThat(meowth.regionForms("alola")[53]).isEqualTo(RegionForm(10108, "persian-alola"))

        // Galarian Meowth becomes Perrserker (not a "-galar" species), so only
        // Meowth itself has a Galarian variety.
        assertThat(meowth.regionForms("galar").keys).containsExactly(52)
        assertThat(meowth.regionForms("galar")[52]).isEqualTo(RegionForm(10161, "meowth-galar"))
    }

    @Test
    fun `regionForms records only the evolved stage when the base has no regional variety`() {
        val alola = cubone.regionForms("alola")
        assertThat(alola.keys).containsExactly(105)
        assertThat(alola[105]).isEqualTo(RegionForm(10115, "marowak-alola"))
    }

    @Test
    fun `methodFor picks the regional method when the node has a regional variety`() {
        val ninetales = vulpix.root!!.children.first()
        val method = ninetales.methodFor(vulpix.regionForms("alola"))
        assertThat(method?.text).isEqualTo("Use Ice Stone")
    }

    @Test
    fun `methodFor picks the default method with no active region`() {
        val ninetales = vulpix.root!!.children.first()
        assertThat(ninetales.methodFor(emptyMap())?.text).isEqualTo("Use Fire Stone")
    }

    @Test
    fun `methodFor falls back to the plain line for a species with no regional variety`() {
        val perrserker = meowth.root!!.children.first { it.speciesId == 863 }
        assertThat(perrserker.methodFor(meowth.regionForms("galar"))?.text).isEqualTo("Lv. 28")
    }

    // ---- intermediate stages with no regional variety (Pichu → Pikachu → Alolan Raichu) ----

    @Test
    fun `regionForms only records the stage that actually has the regional variety`() {
        val alola = pikachu.regionForms("alola")
        assertThat(alola.keys).containsExactly(26)
        assertThat(alola[26]).isEqualTo(RegionForm(10100, "raichu-alola"))
    }

    @Test
    fun `an intermediate stage stays visible because it leads to the regional form`() {
        val alola = pikachu.regionForms("alola")
        val pichu = pikachu.root!!
        assertThat(pichu.visibleChildren(alola, "alola").map { it.name }).containsExactly("pikachu")

        val pika = pichu.children.first()
        assertThat(pika.visibleChildren(alola, "alola").map { it.name }).containsExactly("raichu")
    }

    @Test
    fun `the plain Raichu line stays visible even though base_form is set`() {
        val pika = pikachu.root!!.children.first()
        // No active region: base_form == "pikachu" must not hide Raichu.
        assertThat(pika.visibleChildren(emptyMap(), null).map { it.name }).containsExactly("raichu")

        val raichu = pika.children.first()
        assertThat(raichu.methodFor(emptyMap())?.text).isEqualTo("Use Thunder Stone")
        assertThat(raichu.methodFor(pikachu.regionForms("alola"))?.evolvedFormName).isEqualTo("raichu-alola")
    }

    // Growlithe → Arcanine: PokéAPI gives this single edge two methods — a plain
    // one and a Hisuian-tagged one — since Hisuian Arcanine is a variety of the
    // *same* species (59) as plain Arcanine, exactly like Ninetales/Raichu above.
    private val growlithe = EvolutionChain(
        EvolutionNode(
            speciesId = 58, name = "growlithe", displayName = "Growlithe",
            children = listOf(
                EvolutionNode(
                    speciesId = 59, name = "arcanine", displayName = "Arcanine",
                    methods = listOf(
                        EvoMethod("Use Fire Stone", null, priority = 5),
                        EvoMethod(
                            "Use Fire Stone", null, priority = 5,
                            baseFormId = 10229, baseFormName = "growlithe-hisui",
                            evolvedFormId = 10230, evolvedFormName = "arcanine-hisui",
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `regionSuffixFor is null when the species has a plain method on the same edge`() {
        // A naive "does any method on the path require a region" check would
        // wrongly say "hisui" here, since Arcanine's edge also carries a
        // Hisuian-tagged method — but the plain Fire Stone method reaches the
        // very same species id, so no region is actually required.
        assertThat(growlithe.regionSuffixFor(59)).isNull()
    }

    // ---- a "convergent" Hisuian evolution: a distinct species (Sneasler) with no
    // varieties of its own, reachable only through a regional pre-evolution ----

    // Sneasel: plain → Weavile at night; only the Hisuian variety → Sneasler, by
    // day. Unlike Ninetales/Raichu, Sneasler doesn't share a species id with any
    // variety of Sneasel, so nothing in its own detail page can tell you it needs
    // a region context — that has to come from the chain itself.
    private val sneasel = EvolutionChain(
        EvolutionNode(
            speciesId = 215, name = "sneasel", displayName = "Sneasel",
            children = listOf(
                EvolutionNode(
                    speciesId = 461, name = "weavile", displayName = "Weavile",
                    methods = listOf(EvoMethod("Hold Razor Claw, level up (night)", null, priority = 5)),
                ),
                EvolutionNode(
                    speciesId = 903, name = "sneasler", displayName = "Sneasler",
                    methods = listOf(
                        EvoMethod(
                            "Hold Razor Claw, level up (day)", null, priority = 5,
                            baseFormId = 10235, baseFormName = "sneasel-hisui",
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `regionSuffixFor finds the region required to reach a convergent evolution`() {
        assertThat(sneasel.regionSuffixFor(903)).isEqualTo("hisui")
    }

    @Test
    fun `regionSuffixFor is null for the plain line and the root itself`() {
        assertThat(sneasel.regionSuffixFor(461)).isNull()
        assertThat(sneasel.regionSuffixFor(215)).isNull()
    }

    @Test
    fun `visibleChildren picks the Hisuian branch once regionSuffixFor supplies the suffix`() {
        val suffix = sneasel.regionSuffixFor(903)
        assertThat(suffix).isNotNull()
        val forms = sneasel.regionForms(suffix!!)
        assertThat(sneasel.root!!.visibleChildren(forms, suffix).map { it.name }).containsExactly("sneasler")
    }
}
