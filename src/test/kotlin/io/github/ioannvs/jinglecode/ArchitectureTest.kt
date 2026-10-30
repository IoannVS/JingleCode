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
}
