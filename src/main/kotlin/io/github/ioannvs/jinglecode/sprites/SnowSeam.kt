package io.github.ioannvs.jinglecode.sprites

import com.intellij.ui.scale.JBUIScale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How high the snow lies where the Project panel's drift meets the editor's village, user pixels at
 * 100 %. The two scenes have pixels of different sizes (one follows the UI font, the other the editor
 * line), so each turns this into its own pixels: then the snow line runs on at the same height.
 */
private const val SNOW_SEAM = 20f

/** How many columns at the seam lie at exactly that height, and over how many more each scene blends into its own snow. */
internal const val SEAM_FLAT = 5
internal const val SEAM_BLEND = 20

/** The seam height in logical pixels of [pixelUser] user pixels each. */
internal fun seamRows(pixelUser: Float): Int = max(1, (JBUIScale.scale(SNOW_SEAM) / pixelUser).roundToInt())

/**
 * The snow height [distance] columns away from the seam: [seam] right at it, the scene's own
 * [height] from [SEAM_FLAT] + [SEAM_BLEND] on, smoothly in between.
 */
internal fun towardsSeam(height: Double, seam: Int, distance: Int): Double {
    if (distance < SEAM_FLAT) return seam.toDouble()
    val p = ((distance - SEAM_FLAT).toDouble() / SEAM_BLEND).coerceIn(0.0, 1.0)
    val s = p * p * (3 - 2 * p)
    return seam + (height - seam) * s
}
