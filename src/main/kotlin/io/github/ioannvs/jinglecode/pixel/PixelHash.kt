package io.github.ioannvs.jinglecode.pixel

import kotlin.math.floor
import kotlin.math.sin

/**
 * The mockup's deterministic noise `hash(a, b)`, in [0, 1).
 * Keep it exactly as in the mockup so ported sprites flicker the same way.
 */
fun hash(a: Double, b: Double): Double {
    val s = sin(a * 12.9898 + b * 78.233) * 43758.5453
    return s - floor(s)
}

fun hash(a: Int, b: Int): Double = hash(a.toDouble(), b.toDouble())
