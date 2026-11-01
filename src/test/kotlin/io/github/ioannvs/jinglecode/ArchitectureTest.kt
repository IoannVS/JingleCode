package io.github.ioannvs.jinglecode

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Rules from docs/DECISIONS.md that the compiler doesn't check. */
class ArchitectureTest {

    private val sources = File("src/main/kotlin/io/github/ioannvs/jinglecode")

    /**
     * Git is an optional dependency: a class outside the `git` package that touches it would fail
     * with NoClassDefFoundError in an IDE without Git.
     */
    @Test
    fun `only the git package knows about Git`() {
        val offenders = sources.walk()
            .filter { it.isFile && it.extension == "kt" && !it.path.replace('\\', '/').contains("/jinglecode/git/") }
            .filter { file -> file.readLines().any { it.startsWith("import git4idea.") || it.startsWith("import io.github.ioannvs.jinglecode.git.") } }
            .map { it.name }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }

    /**
     * The Build action (`com.intellij.task`) lives in `com.intellij.modules.lang`, an optional dependency,
     * and runs and builds are heard in one place: decorations only see `JingleEvent.RunFinished`.
     */
    @Test
    fun `only the runs package listens to runs and builds`() {
        val offenders = sources.walk()
            .filter { it.isFile && it.extension == "kt" && !it.path.replace('\\', '/').contains("/jinglecode/runs/") }
            .filter { file ->
                file.readLines().any {
                    it.startsWith("import com.intellij.task.") || it.startsWith("import com.intellij.execution.") ||
                        it.startsWith("import io.github.ioannvs.jinglecode.runs.")
                }
            }
            .map { it.name }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }

    /**
     * Catching other people's popups leans on how Swing and the platform show them (docs/POPUPS.md):
     * all of it stays in the `popups` package, so that breaking it turns off only the popup snow.
     */
    @Test
    fun `only the popups package intercepts popups and menus`() {
        val markers = listOf(
            "import java.awt.event.WindowEvent",
            "import javax.swing.JPopupMenu",
            "import com.intellij.openapi.ui.popup.util.PopupUtil",
            "import com.intellij.notification.Notifications",
            "getParentBalloonFor",
            "WINDOW_EVENT_MASK",
        )
        val offenders = sources.walk()
            .filter { it.isFile && it.extension == "kt" && !it.path.replace('\\', '/').contains("/jinglecode/popups/") }
            .filter { file -> file.readText().let { text -> markers.any { it in text } } }
            .map { it.name }
            .toList()
        assertEquals(emptyList<String>(), offenders)
    }
}
