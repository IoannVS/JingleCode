package io.github.ioannvs.jinglecode.drift

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/** How the drift's snowman looks now. */
internal enum class SnowmanLook { HAT, HATLESS, AWAY }

/**
 * Where the drift's snowman is: at home, or off stealing Santa's sleigh (`// ho ho ho`). He comes back
 * when the chase is over, without his hat for a while if he lost it in the snowball fight.
 */
@Service(Service.Level.PROJECT)
class SnowmanState {
    @Volatile
    private var backAt = 0L

    @Volatile
    private var hatBackAt = 0L

    internal fun look(): SnowmanLook {
        val now = System.nanoTime()
        return when {
            now < backAt -> SnowmanLook.AWAY
            now < hatBackAt -> SnowmanLook.HATLESS
            else -> SnowmanLook.HAT
        }
    }

    /** Off for [awayMs]; then back, without his hat for [hatlessMs] more. */
    internal fun goAway(awayMs: Long, hatlessMs: Long) {
        val now = System.nanoTime()
        backAt = now + awayMs * 1_000_000
        hatBackAt = backAt + hatlessMs * 1_000_000
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): SnowmanState = project.service()
    }
}
