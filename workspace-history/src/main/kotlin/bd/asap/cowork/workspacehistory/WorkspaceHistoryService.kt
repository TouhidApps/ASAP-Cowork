package bd.asap.cowork.workspacehistory

import bd.asap.cowork.orchestrator.ProjectContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.dircache.DirCacheCheckout
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.util.io.DisabledOutputStream
import java.io.ByteArrayOutputStream
import java.io.File

@Serializable
data class CommitInfo(
    val commitId: String,
    val label: String,
    val createdAt: Long,
    val filesChanged: Int,
    val conversationId: String? = null,
    val messageId: String? = null,
)

@Serializable
data class FileDiff(
    val path: String,
    val changeType: String,
    val patch: String,
)

private const val HISTORY_DIR_NAME = ".asap-history"
private const val CONVERSATION_TRAILER = "Conversation-Id"
private const val MESSAGE_TRAILER = "Message-Id"
private const val REVERTED_FROM_TRAILER = "Reverted-From"
private const val LABEL_MAX_LENGTH = 72
private val BOT_IDENT = PersonIdent("ASAP Cowork", "asap-cowork@localhost")

/**
 * A hidden shadow Git repository — separate GIT_DIR under `<workspaceRoot>/.asap-history`,
 * work tree = the workspace root itself — that snapshots the workspace once per AI turn.
 * Completely independent of any `.git` a scaffolded project (or the agent itself, via
 * `run_terminal_command`) might create at the same root: two repositories can share one
 * work tree without conflict. Git is the only store of history here — there is no separate
 * database table. Conversation/message linkage rides along as commit-message trailer lines,
 * the same mechanism git itself uses for `Co-authored-by:`.
 *
 * [projectContext]'s workspace root is re-read on every call rather than cached, since the
 * admin workspace picker (`WorkspaceService.confirm`) can repoint it at runtime.
 */
class WorkspaceHistoryService(private val projectContext: ProjectContext) {

    /**
     * Bootstraps the shadow repo (an "Initial snapshot" commit of whatever's already on
     * disk) if it doesn't exist yet for the current workspace root. Call this before an AI
     * turn starts, not just rely on [commitIfDirty]'s own lazy init — otherwise the very
     * first turn against a workspace that already had files in it would have its own
     * changes silently swallowed into the bootstrap commit instead of getting their own
     * labeled entry.
     */
    suspend fun ensureInitialized(): Unit = withContext(Dispatchers.IO) {
        openOrInitRepo().close()
    }

    suspend fun commitIfDirty(conversationId: String, messageId: String?, label: String): CommitInfo? =
        withContext(Dispatchers.IO) {
            val repo = openOrInitRepo()
            try {
                Git(repo).use { git ->
                    stageAll(git)
                    val status = git.status().call()
                    if (status.isClean) return@withContext null

                    val filesChanged = status.added.size + status.changed.size + status.removed.size +
                        status.modified.size + status.missing.size

                    val message = buildString {
                        appendLine(label.ifBlank { "Workspace changes" }.take(LABEL_MAX_LENGTH))
                        appendLine()
                        appendLine("$CONVERSATION_TRAILER: $conversationId")
                        if (messageId != null) appendLine("$MESSAGE_TRAILER: $messageId")
                    }

                    val commit = git.commit().setMessage(message).setAuthor(BOT_IDENT).setCommitter(BOT_IDENT).call()
                    commit.toCommitInfo(filesChanged, conversationId, messageId)
                }
            } finally {
                repo.close()
            }
        }

    suspend fun listCommits(limit: Int = 200): List<CommitInfo> = withContext(Dispatchers.IO) {
        val repo = openOrInitRepo()
        try {
            val headId = repo.resolve("HEAD") ?: return@withContext emptyList()
            RevWalk(repo).use { walk ->
                walk.markStart(walk.parseCommit(headId))
                walk.take(limit).map { commit ->
                    val trailers = parseTrailers(commit.fullMessage)
                    val parentTree = if (commit.parentCount > 0) walk.parseCommit(commit.getParent(0)).tree else null
                    val filesChanged = diffEntries(repo, parentTree, commit.tree).size
                    commit.toCommitInfo(filesChanged, trailers[CONVERSATION_TRAILER], trailers[MESSAGE_TRAILER])
                }.toList()
            }
        } finally {
            repo.close()
        }
    }

    /** [against] is `"parent"` (the turn's own diff, default) or `"working"` (that point vs. the current on-disk state). */
    suspend fun diff(commitId: String, against: String = "parent"): List<FileDiff> = withContext(Dispatchers.IO) {
        val repo = openOrInitRepo()
        try {
            RevWalk(repo).use { walk ->
                val commit = walk.parseCommit(
                    repo.resolve(commitId) ?: throw NoSuchElementException("Unknown history entry: $commitId"),
                )

                val oldTreeIter: AbstractTreeIterator
                val newTreeIter: AbstractTreeIterator
                if (against == "working") {
                    oldTreeIter = CanonicalTreeParser().also { it.reset(repo.newObjectReader(), commit.tree) }
                    newTreeIter = FileTreeIterator(repo)
                } else {
                    val parent = if (commit.parentCount > 0) walk.parseCommit(commit.getParent(0)) else null
                    oldTreeIter = if (parent != null) {
                        CanonicalTreeParser().also { it.reset(repo.newObjectReader(), parent.tree) }
                    } else {
                        EmptyTreeIterator()
                    }
                    newTreeIter = CanonicalTreeParser().also { it.reset(repo.newObjectReader(), commit.tree) }
                }

                val out = ByteArrayOutputStream()
                DiffFormatter(out).use { formatter ->
                    formatter.setRepository(repo)
                    formatter.isDetectRenames = true
                    formatter.scan(oldTreeIter, newTreeIter).map { entry ->
                        out.reset()
                        formatter.format(entry)
                        FileDiff(
                            path = if (entry.changeType == DiffEntry.ChangeType.DELETE) entry.oldPath else entry.newPath,
                            changeType = entry.changeType.name,
                            patch = out.toString(Charsets.UTF_8),
                        )
                    }
                }
            }
        } finally {
            repo.close()
        }
    }

    /**
     * Restore-and-commit, never a hard reset: forces the working tree + index to exactly
     * match [commitId]'s tree (adding/modifying/deleting files as needed — [DirCacheCheckout]
     * plumbing, not the porcelain checkout command, since that only restores paths that
     * already exist in the target and wouldn't delete files added after it), then records
     * the restoration itself as a brand-new commit. Nothing before it is ever discarded.
     */
    suspend fun revertTo(commitId: String): CommitInfo = withContext(Dispatchers.IO) {
        val repo = openOrInitRepo()
        try {
            RevWalk(repo).use { walk ->
                val targetId = repo.resolve(commitId) ?: throw NoSuchElementException("Unknown history entry: $commitId")
                val target = walk.parseCommit(targetId)
                val headId = repo.resolve("HEAD") ?: throw IllegalStateException("Shadow history repo has no HEAD yet")
                val head = walk.parseCommit(headId)

                val dirCache = repo.lockDirCache()
                val checkout = DirCacheCheckout(repo, head.tree, dirCache, target.tree)
                checkout.setFailOnConflict(true)
                checkout.checkout()

                Git(repo).use { git ->
                    stageAll(git)

                    val filesChanged = diffEntries(repo, head.tree, target.tree).size
                    val originalLabel = target.fullMessage.lineSequence().firstOrNull()?.trim().orEmpty()
                    val message = buildString {
                        appendLine("Revert to \"$originalLabel\" (${target.name.take(7)})")
                        appendLine()
                        appendLine("$REVERTED_FROM_TRAILER: ${target.name}")
                    }

                    val commit = git.commit().setMessage(message).setAuthor(BOT_IDENT).setCommitter(BOT_IDENT).call()
                    commit.toCommitInfo(filesChanged, conversationId = null, messageId = null)
                }
            }
        } finally {
            repo.close()
        }
    }

    private fun workspaceRoot(): File = File(projectContext.workspaceRoot)

    private fun openOrInitRepo(): Repository {
        val root = workspaceRoot()
        val gitDir = File(root, HISTORY_DIR_NAME)
        val isNew = !gitDir.exists()
        val repo = FileRepositoryBuilder().setGitDir(gitDir).setWorkTree(root).build()
        if (isNew) {
            repo.create()
            File(gitDir, "info").mkdirs()
            File(gitDir, "info/exclude").writeText("/$HISTORY_DIR_NAME/\n")
            Git(repo).use { git ->
                stageAll(git)
                if (!git.status().call().isClean) {
                    git.commit().setMessage("Initial snapshot\n").setAuthor(BOT_IDENT).setCommitter(BOT_IDENT).call()
                }
            }
        }
        return repo
    }

    /** `git add -A .` has no single JGit porcelain equivalent — a plain add stages new/modified content, a second `setUpdate(true)` pass stages deletions of already-tracked paths. */
    private fun stageAll(git: Git) {
        git.add().addFilepattern(".").call()
        git.add().addFilepattern(".").setUpdate(true).call()
    }

    private fun diffEntries(repo: Repository, oldTree: ObjectId?, newTree: ObjectId): List<DiffEntry> =
        DiffFormatter(DisabledOutputStream.INSTANCE).use { formatter ->
            formatter.setRepository(repo)
            formatter.isDetectRenames = true
            formatter.scan(oldTree, newTree)
        }

    private fun parseTrailers(fullMessage: String): Map<String, String> =
        fullMessage.lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf(": ")
                if (idx <= 0) return@mapNotNull null
                val key = line.substring(0, idx)
                if (key != CONVERSATION_TRAILER && key != MESSAGE_TRAILER && key != REVERTED_FROM_TRAILER) return@mapNotNull null
                key to line.substring(idx + 2).trim()
            }
            .toMap()

    private fun RevCommit.toCommitInfo(filesChanged: Int, conversationId: String?, messageId: String?) = CommitInfo(
        commitId = name,
        label = fullMessage.lineSequence().firstOrNull()?.trim().orEmpty(),
        createdAt = commitTime.toLong() * 1000,
        filesChanged = filesChanged,
        conversationId = conversationId,
        messageId = messageId,
    )
}
