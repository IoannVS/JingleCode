package io.github.ioannvs.jinglecode.drift

import io.github.ioannvs.jinglecode.pixel.JinglePalette
import io.github.ioannvs.jinglecode.pixel.PixelCanvas

/** Where gifts land in the sleigh: the middle of its bed, behind Santa. Relative to `drawSleigh`'s x, y. */
internal val SLEIGH_BED = At(6, -1)

/**
 * Mockup `drawSleigh`: Santa with his own little sack in a red sleigh on golden runners, pulled by
 * two reindeer; the lead one has a red nose (no name: that one is somebody else's character).
 * [x], [y] as in the mockup; [gallop] alternates the legs and blinks the nose. [cargo] gifts are
 * piled up in the bed behind Santa, and [riders] elves sit on top.
 */
internal fun PixelCanvas.drawSleigh(x: Int, y: Int, gallop: Int, cargo: Int = 0, riders: Boolean = false) {
    if (cargo > 0) {
        rect(x + 6, y, 3, 2, JinglePalette.SANTA_RED)
        px(x + 7, y, JinglePalette.GOLD)
    }
    if (cargo > 3) {
        rect(x + 6, y - 2, 3, 2, JinglePalette.GIFT_TEAL)
        px(x + 7, y - 2, JinglePalette.SNOW)
    }
    if (cargo > 6) {
        rect(x + 7, y - 4, 2, 2, JinglePalette.GOLD)
    }
    if (riders) {
        drawElfHead(x + 9, y - 1)
        drawElfHead(x - 1, y - 1)
    }
    rect(x, y - 2, 3, 3, JinglePalette.SACK)
    rect(x + 3, y - 1, 3, 3, JinglePalette.SANTA_RED)
    px(x + 5, y - 1, JinglePalette.SKIN)
    px(x + 3, y - 2, JinglePalette.SANTA_RED)
    px(x + 4, y - 2, JinglePalette.SANTA_RED)
    px(x + 2, y - 3, JinglePalette.WHITE)
    px(x + 5, y, JinglePalette.WHITE)
    rect(x, y + 2, 9, 3, JinglePalette.SANTA_RED)
    rect(x, y + 2, 9, 1, JinglePalette.GOLD)
    rect(x, y + 6, 11, 1, JinglePalette.GOLD)
    px(x + 11, y + 5, JinglePalette.GOLD)
    px(x + 2, y + 5, JinglePalette.GOLD)
    px(x + 7, y + 5, JinglePalette.GOLD)
    rect(x + 10, y + 4, 3, 1, JinglePalette.GOLD)
    rect(x + 21, y + 3, 2, 1, JinglePalette.GOLD)
    val leg = gallop % 2
    drawReindeer(x + 13, y - 1, leg, lead = false, blink = false)
    drawReindeer(x + 23, y - 1, 1 - leg, lead = true, blink = gallop % 2 == 0)
}

/** An elf riding in the sleigh: a pointed red hat with a white pom and a face, its bottom row at [y]. */
private fun PixelCanvas.drawElfHead(x: Int, y: Int) {
    px(x + 2, y - 3, JinglePalette.WHITE)
    px(x + 1, y - 2, JinglePalette.SANTA_RED)
    rect(x, y - 1, 3, 1, JinglePalette.SANTA_RED)
    rect(x, y, 3, 1, JinglePalette.SKIN)
}

/** Mockup `drawReindeer`. */
private fun PixelCanvas.drawReindeer(x: Int, y: Int, leg: Int, lead: Boolean, blink: Boolean) {
    rect(x + 1, y + 3, 6, 2, JinglePalette.REINDEER)
    rect(x + 6, y + 1, 2, 3, JinglePalette.REINDEER)
    rect(x + 7, y + 1, 2, 2, JinglePalette.REINDEER_LIGHT)
    px(x + 6, y, JinglePalette.ANTLER)
    px(x + 8, y, JinglePalette.ANTLER)
    px(x + 5, y, JinglePalette.ANTLER)
    px(x + 9, y + 2, if (lead) (if (blink) JinglePalette.RED_NOSE_LIT else JinglePalette.RED_NOSE) else JinglePalette.NOSE_DARK)
    px(x, y + 3, JinglePalette.TAIL)
    if (leg == 1) {
        px(x, y + 6, JinglePalette.STICK)
        px(x + 1, y + 5, JinglePalette.STICK)
        px(x + 6, y + 5, JinglePalette.STICK)
        px(x + 7, y + 6, JinglePalette.STICK)
    }
    else {
        rect(x + 1, y + 5, 1, 2, JinglePalette.STICK)
        rect(x + 6, y + 5, 1, 2, JinglePalette.STICK)
    }
}

/**
 * An elf, 5 wide and 7 high, its feet on row [bottom]: a red hat with a white pom, pointed ears,
 * a green coat with a golden belt and dark boots. Running it moves its legs; tossing it has its
 * hands up; jumping it tucks its legs in.
 */
internal fun PixelCanvas.drawElf(x: Int, bottom: Int, pose: ElfPose, step: Int) {
    val top = bottom - 6
    px(x + 3, top, JinglePalette.WHITE)
    rect(x + 1, top + 1, 3, 1, JinglePalette.SANTA_RED)
    px(x + 2, top, JinglePalette.SANTA_RED)
    rect(x + 1, top + 2, 3, 1, JinglePalette.SKIN)
    px(x, top + 2, JinglePalette.SKIN)
    px(x + 4, top + 2, JinglePalette.SKIN)
    rect(x + 1, top + 3, 3, 1, JinglePalette.FIR)
    if (pose == ElfPose.TOSS) {
        rect(x + 1, top + 4, 3, 1, JinglePalette.FIR)
        px(x, top + 1, JinglePalette.SKIN)
        px(x + 4, top + 1, JinglePalette.SKIN)
    }
    else {
        rect(x, top + 4, 5, 1, JinglePalette.FIR)
    }
    rect(x + 1, top + 5, 3, 1, JinglePalette.GOLD)
    // Feet: tucked in for a jump, apart in two steps for a run, side by side standing.
    val feet = when (pose) {
        ElfPose.JUMP -> listOf(1, 2, 3)
        ElfPose.RUN -> if (step % 2 == 0) listOf(0, 3) else listOf(1, 4)
        ElfPose.STAND, ElfPose.TOSS -> listOf(1, 3)
    }
    for (foot in feet) px(x + foot, top + 6, JinglePalette.COAL)
}
