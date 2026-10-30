package io.github.ioannvs.jinglecode.pixel

/**
 * The mockups' seeded random numbers `rng(seed)` (mulberry32), in [0, 1). Call it like the mockup's `r()`.
 * Keep it bit for bit as there, so a ported scene picks the same details from the same seed.
 */
class MockupRandom(seed: Int) : () -> Double {
    private var a = seed

    override fun invoke(): Double {
        a += 0x6D2B79F5
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xFFFF_FFFFL) / 4294967296.0
    }
}
