package io.github.ioannvs.jinglecode.village

import kotlin.random.Random

/** How often the residents start a scene: a factor on the mockup's chances. */
enum class ResidentFrequency(val factor: Double) {
    RARE(0.3),
    NORMAL(1.0),
    OFTEN(2.5),
}

/**
 * The mockup's `runScenes` planner for one village, random mode only (the mockup's "all at once" is a
 * preview, not for the plugin). At most [MAX_ACTIVE] scenes at a time, never the same one twice at
 * once. On each new frame the scenes that ran their course end, and a new one starts with a small
 * chance (bigger while none runs): 0.1, 0.025, 0.008 a frame with 0, 1, 2 running, times the
 * [ResidentFrequency.factor]. Only random: no IDE events, no time of day (Ivan's call).
 */
internal class ResidentScheduler(private val random: Random = Random.Default) {

    /** Running scene ids and the frame each started on, in start order. */
    private val active = LinkedHashMap<String, Int>()
    private var lastFrame: Int? = null

    /**
     * The scenes of [scenes] running at [frame], id to the frame within the scene. Advances the planner
     * once per new frame; asking again for the same frame changes nothing.
     */
    fun at(frame: Int, scenes: List<Resident>, frequency: ResidentFrequency): Map<String, Int> {
        val last = lastFrame
        // Another style (other scenes), or the frame went back (the tick wrapped): start over.
        active.keys.retainAll(scenes.map { it.id }.toSet())
        if (last != null && frame < last) active.clear()
        if (last == null || frame != last) {
            lastFrame = frame
            step(frame, scenes, frequency)
        }
        return active.mapValues { (_, start) -> frame - start }
    }

    private fun step(frame: Int, scenes: List<Resident>, frequency: ResidentFrequency) {
        val byId = scenes.associateBy { it.id }
        active.entries.removeIf { (id, start) -> frame - start >= byId.getValue(id).duration }
        val n = active.size
        if (n >= MAX_ACTIVE) return
        val chance = START_CHANCE[n] * frequency.factor
        if (random.nextDouble() >= chance) return
        val free = scenes.filter { it.id !in active }
        if (free.isNotEmpty()) active[free[random.nextInt(free.size)].id] = frame
    }

    fun clear() {
        active.clear()
        lastFrame = null
    }

    companion object {
        const val MAX_ACTIVE = 3

        /** The mockup's chance a frame to start a scene, by how many run. */
        private val START_CHANCE = doubleArrayOf(0.1, 0.025, 0.008)
    }
}
