package io.github.ioannvs.jinglecode.popups

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/** Installs the snow on popups; waits a little if the project frame isn't created yet. */
internal class PopupSnowStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        repeat(ATTEMPTS) {
            val done = withContext(Dispatchers.EDT) {
                project.isDisposed || PopupSnow.getInstance(project).install()
            }
            if (done) return
            delay(RETRY)
        }
        thisLogger().info("Jingle Code: no project frame after ${RETRY * ATTEMPTS}, no snow on popups for this project")
    }
}

private const val ATTEMPTS = 20
private val RETRY = 500.milliseconds
