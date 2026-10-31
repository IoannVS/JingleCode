package io.github.ioannvs.jinglecode.village

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.MockupRandom
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.time.LocalDate
import java.time.LocalDateTime

class VillageTest {

    private val scenes = mapOf(
        "v1" to VillageStyle.HILL_FARM,
        "v2" to VillageStyle.VALLEY,
        "v3" to VillageStyle.UPHILL_STREET,
        "v4" to VillageStyle.FOREST_CLEARING,
    )

    private class Drawn(val image: BufferedImage, val houses: List<HouseBox>)

    private fun draw(
        style: VillageStyle,
        width: Int = MOCKUP_WIDTH,
        seed: Int = 7,
        frame: Int = 13,
        evening: Boolean = true,
        stars: Boolean = false,
        leftEdge: Int? = null,
        stoked: Set<Int> = emptySet(),
        residents: Map<String, Int> = emptyMap(),
    ): Drawn {
        val image = BufferedImage(width, VILLAGE_ROWS, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        var houses = emptyList<HouseBox>()
        PixelCanvas.paint(g, 0, 0) { houses = drawVillage(style, width, seed, frame, evening, stars, leftEdge, stoked, residents) }
        g.dispose()
        return Drawn(image, houses)
    }

    private fun BufferedImage.at(x: Int, y: Int): Color = Color(getRGB(x, y), true)

    private fun BufferedImage.colors(): Map<Color, Int> =
        (0 until width).flatMap { x -> (0 until height).map { at(x, it) } }.groupingBy { it }.eachCount()

    /** A reference row from `village-mockup/`: `#rrggbb*count` runs, `.` where the mockup drew nothing. */
    private fun decode(row: String): List<String> = row.split(' ').flatMap { run ->
        val (color, count) = run.split('*')
        List(count.toInt()) { color }
    }

    private fun hex(c: Color): String = if (c.alpha == 0) "." else "#%02x%02x%02x".format(c.red, c.green, c.blue)

    @Test
    fun `the four scenes are the mockup's v1 to v4 pixel for pixel`() {
        // References: the mockup's own code run in node with seed 7 and the sky left out (see DECISIONS.md).
        for ((name, style) in scenes) for ((evening, frame) in listOf(true to 13, false to 77)) {
            val file = "/village-mockup/$name-${if (evening) "evening" else "day"}-$frame.txt"
            val rows = javaClass.getResource(file)!!.readText().trimEnd().lines()
            val image = draw(style, frame = frame, evening = evening).image
            assertEquals(VILLAGE_ROWS, rows.size)
            rows.forEachIndexed { y, row ->
                decode(row).forEachIndexed { x, expected ->
                    // The moon is our crescent, not the mockup's.
                    val moon = evening && x in MOCKUP_WIDTH - 35 until MOCKUP_WIDTH - 26 && y in 3..11
                    if (!moon && hex(image.at(x, y)) != expected) fail("$file: ($x, $y) is ${hex(image.at(x, y))}, the mockup has $expected")
                }
            }
        }
    }

    @Test
    fun `the residents are the mockup's, pixel for pixel`() {
        // References: what each scene adds to the village at a few of its frames, by the mockup's own code (render-references.js).
        for ((name, style) in scenes) {
            val lines = javaClass.getResource("/village-mockup/$name-residents.txt")!!.readText().trimEnd().lines()
            assertEquals(style.scene.residents.size * 5, lines.size)
            for (line in lines) {
                val (id, t, time, pixels) = "$line ".split(' ', limit = 4)
                val evening = time == "evening"
                val base = draw(style, frame = 300, evening = evening).image
                val with = draw(style, frame = 300, evening = evening, residents = mapOf(id to t.toInt())).image
                val diff = buildSet {
                    for (x in 0 until base.width) for (y in 0 until base.height) {
                        if (base.getRGB(x, y) != with.getRGB(x, y)) add("$x,$y,${hex(with.at(x, y))}")
                    }
                }
                val expected = pixels.trim().split(';').filter { it.isNotEmpty() }.toSet()
                if (diff != expected) fail("$name $id at $t ($time): extra ${(diff - expected).take(5)}, missing ${(expected - diff).take(5)}")
            }
        }
    }

    @Test
    fun `the planner runs one to three scenes, never the same one twice at once`() {
        val scenes = HILL_FARM_RESIDENTS
        val planner = ResidentScheduler(kotlin.random.Random(1))
        var most = 0
        var idle = 0
        val started = HashSet<String>()
        for (frame in 0 until 20_000) {
            val playing = planner.at(frame, scenes, ResidentFrequency.OFTEN)
            assertTrue("at most three at $frame: $playing", playing.size <= ResidentScheduler.MAX_ACTIVE)
            for ((id, t) in playing) assertTrue("$id runs its course only", t in 0 until scenes.first { it.id == id }.duration)
            most = maxOf(most, playing.size)
            if (playing.isEmpty()) idle++
            started += playing.keys
        }
        assertEquals("three at a time sometimes", 3, most)
        assertEquals("every scene turns up", scenes.map { it.id }.toSet(), started)
        assertTrue("mostly something plays: idle $idle", idle < 20_000 / 5)
        // The keys of a map can't repeat; the ids must not either.
        for (list in listOf(HILL_FARM_RESIDENTS, VALLEY_RESIDENTS, UPHILL_STREET_RESIDENTS, FOREST_CLEARING_RESIDENTS)) {
            assertEquals(list.size, list.map { it.id }.toSet().size)
        }
    }

    @Test
    fun `the planner starts more often at a higher frequency, and only once per frame`() {
        fun starts(frequency: ResidentFrequency): Int {
            val planner = ResidentScheduler(kotlin.random.Random(7))
            var count = 0
            var before = emptySet<String>()
            for (frame in 0 until 30_000) {
                val now = planner.at(frame, VALLEY_RESIDENTS, frequency).keys
                count += (now - before).size
                before = now
            }
            return count
        }
        val rare = starts(ResidentFrequency.RARE)
        val normal = starts(ResidentFrequency.NORMAL)
        val often = starts(ResidentFrequency.OFTEN)
        assertTrue("$rare < $normal < $often", normal in rare + 1 until often)
        val planner = ResidentScheduler(kotlin.random.Random(3))
        val first = (0 until 200).map { planner.at(50, VALLEY_RESIDENTS, ResidentFrequency.OFTEN) }
        assertTrue("asking again for the same frame changes nothing", first.toSet().size == 1)
    }

    @Test
    fun `each style has its own scenes and the five common ones`() {
        val common = setOf("cat", "birds", "dog", "shovel", "carol")
        for (style in scenes.values) assertTrue("$style", style.scene.residents.map { it.id }.containsAll(common))
        assertTrue("the bathhouse scene on the clearing", FOREST_CLEARING_RESIDENTS.any { it.id == "banya" })
    }

    @Test
    fun `the mockup's random numbers, bit for bit`() {
        // mulberry32 from the mockup, as node prints it for seed 1.
        val r = MockupRandom(1)
        assertEquals(listOf(0.6270739405881613, 0.002735721180215478, 0.5274470399599522), List(3) { r() })
    }

    @Test
    fun `the generator builds all eight house types from their parameters`() {
        val r = MockupRandom(42)
        val specs = HouseType.entries.associateWith { HouseSpec.random(it, r) }
        assertEquals(2, specs.getValue(HouseType.TEREM).storeys)
        assertEquals(Wall.LOGS, specs.getValue(HouseType.IZBA).wall)
        assertTrue(specs.getValue(HouseType.IZBA).carved)
        assertTrue(specs.getValue(HouseType.BANYA).smokeHeavy)
        assertEquals(Wall.RED, specs.getValue(HouseType.BARN).wall)
        assertEquals(0, specs.getValue(HouseType.BARN).wins)
        assertEquals(Deco.NONE, specs.getValue(HouseType.MILL).deco)
        assertEquals(2.2, specs.getValue(HouseType.AFRAME).pitch, 0.0)
        assertEquals("same seed, same house", HouseSpec.random(HouseType.COTTAGE, MockupRandom(5)), HouseSpec.random(HouseType.COTTAGE, MockupRandom(5)))
        // Each type draws.
        for ((type, spec) in specs) {
            val image = BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            PixelCanvas.paint(g, 0, 0) {
                val painter = VillagePainter(this, 40, 7, 0, evening = true, stars = false, leftEdge = null, elastic = emptyList(), stoked = emptySet())
                painter.drawHouse(6, 36, spec)
            }
            g.dispose()
            assertTrue("$type drew", image.colors().size > 4)
        }
    }

    @Test
    fun `the evening lights windows and lamps, and the moon comes out`() {
        for (style in scenes.values) {
            val day = draw(style, evening = false).image.colors()
            val evening = draw(style, evening = true).image.colors()
            assertTrue("$style", (evening[JinglePalette.WINDOW_LIT] ?: 0) > (day[JinglePalette.WINDOW_LIT] ?: 0))
            assertTrue("$style", JinglePalette.STAR in evening && JinglePalette.STAR !in day)
        }
    }

    @Test
    fun `stars only when they may hang there`() {
        val without = draw(VillageStyle.VALLEY, stars = false).image.colors()
        val with = draw(VillageStyle.VALLEY, stars = true).image.colors()
        assertFalse(JinglePalette.STAR_COLD in without)
        assertTrue(JinglePalette.STAR_COLD in with)
    }

    @Test
    fun `every star twinkles on its own`() {
        for (star in 0 until 20) {
            val looks = (0 until 400).map { starLook(star, it) }
            assertTrue("star $star changes", looks.toSet().size >= 2)
            assertTrue("star $star mostly shines", looks.count { it == StarLook.SHINE } > 280)
        }
        val a = draw(VillageStyle.VALLEY, stars = true, frame = 10).image
        val b = draw(VillageStyle.VALLEY, stars = true, frame = 12).image
        assertTrue("the sky changes", (0 until a.width).any { x -> (0 until 26).any { a.getRGB(x, it) != b.getRGB(x, it) } })
    }

    @Test
    fun `things move from frame to frame`() {
        for (style in scenes.values) {
            val a = draw(style, frame = 10).image
            val b = draw(style, frame = 30).image
            assertTrue("$style moves", (0 until a.width).any { x -> (0 until a.height).any { a.getRGB(x, it) != b.getRGB(x, it) } })
        }
    }

    @Test
    fun `the details change with the seed, the composition stays`() {
        val a = draw(VillageStyle.HILL_FARM, seed = 1)
        val b = draw(VillageStyle.HILL_FARM, seed = 2)
        assertEquals(a.houses.size, b.houses.size)
        assertNotEquals(a.image.colors(), b.image.colors())
    }

    @Test
    fun `each scene has its houses, all of them in view at the mockup's width`() {
        val counts = mapOf(VillageStyle.HILL_FARM to 6, VillageStyle.VALLEY to 3, VillageStyle.UPHILL_STREET to 7, VillageStyle.FOREST_CLEARING to 2)
        for ((style, count) in counts) {
            val houses = draw(style).houses
            assertEquals("$style", count, houses.size)
            assertTrue("$style", houses.all { it.x0 >= 0 && it.x1 < MOCKUP_WIDTH })
        }
    }

    @Test
    fun `a wider editor stretches the empty snow, the group on the hill keeps its spacing`() {
        val narrow = draw(VillageStyle.HILL_FARM).houses
        val wide = draw(VillageStyle.HILL_FARM, width = 900).houses
        fun gaps(houses: List<HouseBox>) = houses.drop(1).map { it.x0 }.zipWithNext { a, b -> b - a }
        assertEquals("the group on the hill", gaps(narrow).drop(1), gaps(wide).drop(1))
        assertTrue("the barn stays at the left", wide.first().x1 < 900 / 3)
        assertTrue("the group goes to the right", wide.last().x1 > 900 - 80)
    }

    @Test
    fun `a narrow editor squeezes the empty snow, then cuts the scene at the left`() {
        for (style in scenes.values) for (width in listOf(150, 300, 400, 700, 1600)) {
            val drawn = draw(style, width = width)
            assertTrue("$style at $width keeps its main houses", drawn.houses.any { it.x0 >= 0 && it.x1 < width })
        }
        val squeezed = draw(VillageStyle.HILL_FARM, width = 400).houses
        assertTrue("all still there", squeezed.all { it.x0 >= -2 })
        val cut = draw(VillageStyle.HILL_FARM, width = 200).houses
        assertTrue("the lone barn is cut off", cut.first().x1 < 0)
        assertTrue("the chapel stays", cut.any { it.x0 >= 0 })
    }

    @Test
    fun `columns map both ways`() {
        val map = ColumnMap(listOf(0..38, 110..148), 700)
        for (x in listOf(-5.0, 0.0, 20.0, 60.0, 130.0, 300.0, 459.0, 470.0)) assertEquals(x, map.back(map.at(x)), 1e-9)
        assertEquals("fixed stretches only shift", map.at(60.0) + 10, map.at(70.0), 1e-9)
        assertEquals(700.0, map.at(MOCKUP_WIDTH.toDouble()), 1e-9)
    }

    @Test
    fun `the snow starts at the seam height at the left edge`() {
        val snowy = listOf(JinglePalette.SNOW, JinglePalette.WHITE, JinglePalette.SNOW_SPECK, JinglePalette.SNOW_SHADE)
        for (style in scenes.values) for (width in listOf(MOCKUP_WIDTH, 300)) {
            val image = draw(style, width = width, evening = false, leftEdge = 13).image
            // Under the crest it is snow all the way down; on a narrow editor a prop may stand on the crest.
            for (x in 0 until 4) assertTrue("$style at $width", (VILLAGE_ROWS - 12 until VILLAGE_ROWS).all { image.at(x, it) in snowy })
            if (width == MOCKUP_WIDTH) assertEquals("$style: the crest", JinglePalette.WHITE, image.at(0, VILLAGE_ROWS - 13))
        }
    }

    @Test
    fun `a click lights the stove, thick smoke`() {
        val farm = draw(VillageStyle.HILL_FARM)
        val house = farm.houses.first { it.hasChimney }
        val calm = draw(VillageStyle.HILL_FARM, frame = 40).image.colors()[JinglePalette.SMOKE] ?: 0
        val stoked = draw(VillageStyle.HILL_FARM, frame = 40, stoked = setOf(house.index)).image.colors()[JinglePalette.SMOKE] ?: 0
        assertTrue("$calm -> $stoked", stoked > calm)
    }

    @Test
    fun `random picks a style by the date, the same all day`() {
        val days = (0L until 60L).map { LocalDate.of(2026, 12, 1).plusDays(it) }
        val picked = days.map(VillageStyle.RANDOM::on)
        assertFalse(VillageStyle.RANDOM in picked)
        assertEquals("all four turn up", 4, picked.toSet().size)
        assertEquals(VillageStyle.RANDOM.on(days[3]), VillageStyle.RANDOM.on(days[3]))
        assertEquals(VillageStyle.VALLEY, VillageStyle.VALLEY.on(days[3]))
    }

    @Test
    fun `evening from five, the seed changes daily`() {
        assertFalse(VillageTime.at(LocalDateTime.parse("2026-12-05T16:59")).evening)
        assertTrue(VillageTime.at(LocalDateTime.parse("2026-12-05T17:00")).evening)
        assertTrue("night is evening too", VillageTime.at(LocalDateTime.parse("2026-12-24T02:00")).evening)
        val a = VillageTime.at(LocalDateTime.parse("2026-12-05T09:00"))
        val b = VillageTime.at(LocalDateTime.parse("2026-12-05T21:00"))
        val c = VillageTime.at(LocalDateTime.parse("2026-12-06T09:00"))
        assertEquals(a.seed, b.seed)
        assertNotEquals(a.seed, c.seed)
    }
}
