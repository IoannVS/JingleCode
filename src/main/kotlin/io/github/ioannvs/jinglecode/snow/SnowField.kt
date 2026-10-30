package io.github.ioannvs.jinglecode.snow

import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import kotlin.math.sin
import kotlin.random.Random

/**
 * Mockup editor snow (`drawEditorSnow`) without the IDE: flakes fall through a view of
 * `width × height` logical pixels, swaying a little, and start again at the top.
 * Positions are relative to the view, so the snow stays in place while the editor scrolls.
 */
internal class SnowField(private val random: Random = Random.Default) {

    class Flake(var x: Double, var y: Double, val vy: Double, val phase: Double)

    val flakes = ArrayList<Flake>()

    private var width = 0
    private var height = 0

    /**
     * Moves the snow by [dt] seconds, one flake per [areaPerFlake] logical pixels, falling [speed] times
     * as fast as in the mockup; [time] (seconds) sways the flakes.
     * Returns whether the picture changed: some flake moved to another pixel, or the flakes changed.
     */
    fun step(
        width: Int,
        height: Int,
        dt: Double,
        time: Double,
        areaPerFlake: Int = SnowAmount.NORMAL.areaPerFlake,
        speed: Double = SnowSpeed.NORMAL.factor,
    ): Boolean {
        val resized = width != this.width || height != this.height
        this.width = width
        this.height = height
        if (width <= 0 || height <= 0) {
            val had = flakes.isNotEmpty()
            flakes.clear()
            return had
        }
        val target = (width.toLong() * height / areaPerFlake.coerceAtLeast(1)).toInt().coerceAtMost(MAX_FLAKES)
        var changed = resized || flakes.size != target
        while (flakes.size > target) flakes.removeAt(flakes.size - 1)
        while (flakes.size < target) flakes += newFlake()

        for (flake in flakes) {
            val px = PixelCanvas.jsRound(flake.x)
            val py = PixelCanvas.jsRound(flake.y)
            flake.y += flake.vy * speed * dt
            flake.x += sin(time / 0.9 + flake.phase) * 4 * dt
            if (flake.x < 0) flake.x += width
            if (flake.x >= width) flake.x -= width
            if (flake.y > height) {
                flake.x = random.nextDouble() * width
                flake.y = top()
            }
            if (!changed && (PixelCanvas.jsRound(flake.x) != px || PixelCanvas.jsRound(flake.y) != py)) changed = true
        }
        return changed
    }

    /** Mockup `newFlake(true)`, anywhere in the view: 7–16 pixels a second. */
    private fun newFlake() = Flake(
        x = random.nextDouble() * width,
        y = random.nextDouble() * height,
        vy = 7 + random.nextDouble() * 9,
        phase = random.nextDouble() * 6,
    )

    private fun top(): Double = -2 - random.nextDouble() * 20

    private companion object {
        /** Enough for a big screen with "many" snow. */
        const val MAX_FLAKES = 2000
    }
}
