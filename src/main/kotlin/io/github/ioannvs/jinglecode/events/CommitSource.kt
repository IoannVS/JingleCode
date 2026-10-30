package io.github.ioannvs.jinglecode.events

import com.intellij.openapi.application.ApplicationManager

/**
 * Something that counts the user's commits and publishes [JingleEvent.UnpushedCommits]: the Git
 * module registers itself as this application service in its optional config file. Decorations ask
 * [isPresent] to know whether commits will be counted, without touching any Git class.
 */
interface CommitSource {
    companion object {
        /** Whether a commit source is installed (Git is there); `false` without an application, as in unit tests. */
        fun isPresent(): Boolean = ApplicationManager.getApplication()?.getService(CommitSource::class.java) != null
    }
}
