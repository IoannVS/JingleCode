package io.github.ioannvs.jinglecode.icicles

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.ioannvs.jinglecode.core.FeatureGate
import io.github.ioannvs.jinglecode.core.JingleFeature
import io.github.ioannvs.jinglecode.events.JingleEvent
import io.github.ioannvs.jinglecode.events.JingleEventListener
import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas
import io.github.ioannvs.jinglecode.pixel.hash
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A row of icicles that can fall: where its row 0 starts, root pane coordinates, how wide it is,
 * which columns stay empty, what [share] of its icicles falls, and a [salt] so that rows don't
 * lose the same columns.
 */
internal class IcicleRow(
    val x: Int,
    val y: Int,
    val width: Int,
    val share: Double,
    val salt: Int,
    val skip: (Int) -> Boolean = { false },
)

/** How one icicle is doing in a fall. */
internal data class IcicleDrop(
    /** Rows of it at its place: all of them before it lets go, none while it falls, then growing back. */
    val hanging: Int,
    /** While it falls: how far below its place its top is, logical pixels; else `null`. */
    val fallen: Int?,
    /** Milliseconds since it hit the bottom, while its shards fly; else `null`. */
    val shattered: Long?,
)

/**
 * Icicles falling after a failed run, by time. A fall picks a [share][IcicleRow.share] of the icicles;
 * they let go one after another within [SPREAD_MS], fall with [GRAVITY] down to the bottom of the
 * window, over everything (Ivan's call), and break there into a few shards. Right as one breaks, it
 * grows back at its place, a row a frame.
 *
 * Pure: everything is a function of the time, the fall's seed and the icicle.
 */
internal object IcicleFall {
    const val SPREAD_MS = 500L

    /** Logical pixels per second squared. */
    const val GRAVITY = 900.0

    const val SHATTER_MS = 420L
    const val GROW_ROW_MS = 120L

    /** Under the toolbar a bit more than half falls, under the tab most of them: a fall must be seen. */
    const val TOOLBAR_SHARE = 0.55
    const val TAB_SHARE = 0.8

    /** Longer than any fall takes, even down a tall window. */
    const val LONGEST_MS = 4000L

    /** How high the shards fly, logical pixels. */
    const val SHARD_HEIGHT = 5

    /** How far to the side the shards fly, logical pixels. */
    const val SHARD_SPREAD = 5

    /**
     * Icicle [key] ([length] rows, its place [bottom] rows above the bottom of the window) [ageMs] into
     * the fall [seed] that takes [share] of the icicles; `null` if it stays where it is.
     */
    fun drop(key: Int, length: Int, bottom: Int, ageMs: Long, seed: Int, share: Double): IcicleDrop? {
        if (hash(key, seed) >= share) return null
        val since = ageMs - (hash(key, seed + 1) * SPREAD_MS).toLong()
        if (since < 0) return IcicleDrop(length, null, null)
        // Until its tip touches the bottom.
        val fallMs = fallMs(max(0, bottom - length))
        if (since < fallMs) return IcicleDrop(0, fallen(since), null)
        val hit = since - fallMs
        return IcicleDrop(min(length.toLong(), hit / GROW_ROW_MS).toInt(), null, hit.takeIf { it < SHATTER_MS })
    }

    fun fallMs(distance: Int): Long = (sqrt(2.0 * distance / GRAVITY) * 1000).toLong()

    private fun fallen(ms: Long): Int = floor(GRAVITY * (ms / 1000.0) * (ms / 1000.0) / 2).toInt()
}

/** A falling icicle in [column], its top [fallen] rows below row 0: ice, with a blue tip. */
internal fun PixelCanvas.drawFallingIcicle(column: Int, length: Int, fallen: Int) {
    for (y in 0 until length) px(column, fallen + y, if (y == length - 1) JinglePalette.ICE_TIP else JinglePalette.ICE)
}

/**
 * The shards of an icicle of [length] that hit the bottom, row [bottom], under [column], [ms] ago:
 * a few bits of ice fly up and to the sides and fall back.
 */
internal fun PixelCanvas.drawShards(column: Int, bottom: Int, length: Int, ms: Long, seed: Int) {
    val p = ms.toDouble() / IcicleFall.SHATTER_MS
    for (k in 0 until 2 + length) {
        val shard = column * 7 + k
        val side = (hash(shard, seed + 2) * 2 - 1) * IcicleFall.SHARD_SPREAD
        val height = 1 + hash(shard, seed + 3) * (IcicleFall.SHARD_HEIGHT - 1)
        val x = column + PixelCanvas.jsRound(side * p)
        val y = bottom - 1 - PixelCanvas.jsRound(height * 4 * p * (1 - p))
        val color = when (k % 3) {
            0 -> JinglePalette.ICE
            1 -> JinglePalette.ICE_TIP
            else -> JinglePalette.WHITE
        }
        px(x, y, color)
    }
}

/** A fall going on: its seed and how long ago it began. */
internal data class FallNow(val seed: Int, val ageMs: Long)

/**
 * The icicle falls after failed runs and builds ([JingleEvent.RunFinished]). Every failure starts its
 * own fall, with its own icicles; falls may overlap.
 */
@Service(Service.Level.PROJECT)
class IcicleFalls {
    private class Started(val seed: Int, val at: Long)

    @Volatile
    private var falls: List<Started> = emptyList()
    private var count = 0

    internal fun current(): List<FallNow> {
        val now = System.nanoTime()
        return falls.map { FallNow(it.seed, (now - it.at) / 1_000_000) }.filter { it.ageMs < IcicleFall.LONGEST_MS }
    }

    internal fun onEvent(event: JingleEvent) {
        if (event is JingleEvent.RunFinished && !event.succeeded) start()
    }

    @Synchronized
    private fun start() {
        val now = System.nanoTime()
        // Seeds stay small (the hash is a sine) and apart: a fall uses four in a row.
        val seed = FIRST_SEED + SEED_STEP * (count++ % SEEDS)
        falls = falls.filter { (now - it.at) / 1_000_000 < IcicleFall.LONGEST_MS } + Started(seed, now)
    }

    /**
     * How many rows of each icicle of [row] hang at its place now, `(column, length) -> rows`;
     * [bottom] is the bottom of the window in the row's logical pixels.
     */
    internal fun hanging(row: IcicleRow, bottom: Int): (Int, Int) -> Int {
        val falls = if (FeatureGate.getInstance().isActive(JingleFeature.RUN_REACTIONS)) current() else emptyList()
        if (falls.isEmpty()) return { _, length -> length }
        return { x, length ->
            falls.minOf { IcicleFall.drop(x + row.salt, length, bottom, it.ageMs, it.seed, row.share)?.hanging ?: length }
        }
    }

    companion object {
        /** Above the seeds the icicles themselves use (7, 8, 9). */
        private const val FIRST_SEED = 20
        private const val SEED_STEP = 5
        private const val SEEDS = 200

        @JvmStatic
        fun getInstance(project: Project): IcicleFalls = project.service()
    }
}

/** Declared in plugin.xml, so it hears runs even before the icicles have asked for the falls. */
internal class IcicleFallsListener(private val project: Project) : JingleEventListener {
    override fun onEvent(event: JingleEvent) = IcicleFalls.getInstance(project).onEvent(event)
}
