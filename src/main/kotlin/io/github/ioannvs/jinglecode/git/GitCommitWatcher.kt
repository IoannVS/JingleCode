package io.github.ioannvs.jinglecode.git

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import git4idea.GitUserRegistry
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.push.GitPushListener
import git4idea.push.GitPushRepoResult
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import io.github.ioannvs.jinglecode.events.JingleEvent
import io.github.ioannvs.jinglecode.events.JingleEvents
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Watches the user's commits that are not pushed yet in all of the project's Git repositories and
 * tells [JingleEvents]: [JingleEvent.UnpushedCommits] after every count; [JingleEvent.Commit] when
 * there are more than last time; [JingleEvent.Push] when there are fewer and the remote branches
 * moved (a push, from the IDE or a terminal; fewer without that is a reset, not a push);
 * [JingleEvent.PushRejected] when a push from the IDE is rejected or fails.
 *
 * Counts at startup and after any change of a repository (a commit moves HEAD, a push moves the
 * remote branches). Only this package knows about Git; it is loaded through the optional `Git4Idea` dependency.
 */
@Service(Service.Level.PROJECT)
class GitCommitWatcher(private val project: Project) : Disposable {

    private val pending = AtomicBoolean(false)

    @Volatile
    private var last: Count? = null

    private class Count(val unpushed: Int, val remotes: Map<String, String>)

    init {
        project.messageBus.connect(this).subscribe(GitPushListener.TOPIC, object : GitPushListener {
            override fun onCompleted(repository: GitRepository, pushResult: GitPushRepoResult) {
                if (pushResult.type in REJECTED) JingleEvents.publish(project, JingleEvent.PushRejected)
            }
        })
    }

    /** Counts again on a pooled thread; requests while a count is running are merged into it. */
    fun recount() {
        if (!pending.compareAndSet(false, true)) return
        ApplicationManager.getApplication().executeOnPooledThread {
            pending.set(false)
            if (project.isDisposed) return@executeOnPooledThread
            val count = try {
                count()
            }
            catch (e: Exception) {
                thisLogger().info("Jingle Code: can't count unpushed commits", e)
                null
            } ?: return@executeOnPooledThread
            val previous = last
            last = count
            JingleEvents.publish(project, JingleEvent.UnpushedCommits(count.unpushed))
            if (previous == null) return@executeOnPooledThread
            when {
                count.unpushed > previous.unpushed -> JingleEvents.publish(project, JingleEvent.Commit)
                count.unpushed < previous.unpushed && count.remotes != previous.remotes ->
                    JingleEvents.publish(project, JingleEvent.Push(previous.unpushed))
            }
        }
    }

    /**
     * `null` when there is nothing to count yet. A project without Git at all says so with
     * [JingleEvent.NoRepositories]; one whose repositories are mapped but not loaded yet just waits
     * for them, they will tell about themselves with a repository change.
     */
    private fun count(): Count? {
        val repositories = GitRepositoryManager.getInstance(project).repositories
        if (repositories.isEmpty()) {
            val mapped = ProjectLevelVcsManager.getInstance(project).getDirectoryMappings(GitVcs.getInstance(project)).isNotEmpty()
            if (!mapped) JingleEvents.publish(project, JingleEvent.NoRepositories)
            return null
        }
        val remotes = HashMap<String, String>()
        for (repository in repositories) {
            val branches = repository.branches
            for (branch in branches.remoteBranches) {
                remotes["${repository.root.path} ${branch.name}"] = branches.getHash(branch)?.asString().orEmpty()
            }
        }
        return Count(repositories.sumOf(::countUnpushed), remotes)
    }

    /**
     * `git log --branches --not --remotes --author=<me>`: the user's commits on local branches that
     * no remote branch has. A branch without an upstream counts as not pushed, which it is.
     */
    private fun countUnpushed(repository: GitRepository): Int {
        val user = GitUserRegistry.getInstance(project).getOrReadUser(repository.root) ?: return 0
        val me = user.email.ifBlank { user.name }
        if (me.isBlank()) return 0
        val handler = GitLineHandler(project, repository.root, GitCommand.LOG)
        handler.addParameters("--branches", "--not", "--remotes", "--author=$me", "--fixed-strings", "--format=%H")
        handler.setSilent(true)
        val result = Git.getInstance().runCommand(handler)
        return if (result.success()) result.output.count { it.isNotBlank() } else 0
    }

    override fun dispose() = Unit

    companion object {
        private val REJECTED = setOf(
            GitPushRepoResult.Type.REJECTED_NO_FF,
            GitPushRepoResult.Type.REJECTED_STALE_INFO,
            GitPushRepoResult.Type.REJECTED_OTHER,
            GitPushRepoResult.Type.ERROR,
        )

        @JvmStatic
        fun getInstance(project: Project): GitCommitWatcher = project.service()
    }
}

/** Repositories changed: maybe a commit or a push. */
internal class GitCommitListener(private val project: Project) : GitRepositoryChangeListener {
    override fun repositoryChanged(repository: GitRepository) = GitCommitWatcher.getInstance(project).recount()
}

/** The first count, when the project opens. */
internal class GitCommitStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) = GitCommitWatcher.getInstance(project).recount()
}
