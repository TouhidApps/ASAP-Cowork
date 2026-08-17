package bd.asap.cowork.workspacehistory

import bd.asap.cowork.orchestrator.ProjectContext
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceHistoryServiceTest {

    @Test
    fun `commits only when the workspace is actually dirty, and history round-trips through diff and revert`() = runBlocking {
        val root = Files.createTempDirectory("workspace-history-test")
        val projectContext = ProjectContext(root)
        val service = WorkspaceHistoryService(projectContext)
        val file = root.resolve("hello.txt")

        // Mirrors production: the repo is bootstrapped against whatever pre-existing state
        // there is (here: nothing) before the turn's own edits land, so those edits get
        // their own labeled entry instead of being swallowed into the bootstrap commit.
        service.ensureInitialized()

        // First turn: create a file.
        file.toFile().writeText("v1")
        val first = service.commitIfDirty("conv-1", "msg-1", "Add hello.txt")
        assertNotNull(first)
        assertEquals("Add hello.txt", first.label)
        assertEquals("conv-1", first.conversationId)
        assertEquals("msg-1", first.messageId)

        // A no-op turn (nothing changed) must not create an entry.
        assertNull(service.commitIfDirty("conv-1", "msg-1b", "Nothing changed"))

        // Second turn: modify the file.
        file.toFile().writeText("v2")
        val second = service.commitIfDirty("conv-1", "msg-2", "Update hello.txt")
        assertNotNull(second)

        val history = service.listCommits()
        assertEquals(2, history.size)
        assertEquals(second.commitId, history[0].commitId) // newest first
        assertEquals(first.commitId, history[1].commitId)

        val diff = service.diff(second.commitId)
        assertEquals(1, diff.size)
        assertTrue(diff[0].patch.contains("-v1"))
        assertTrue(diff[0].patch.contains("+v2"))

        // Revert to the first entry: file must go back to "v1", and this must show up
        // as a NEW third entry — the original two must still be present (forward-only).
        val revertEntry = service.revertTo(first.commitId)
        assertEquals("v1", file.toFile().readText())

        val historyAfterRevert = service.listCommits()
        assertEquals(3, historyAfterRevert.size)
        assertEquals(revertEntry.commitId, historyAfterRevert[0].commitId)
        assertTrue(historyAfterRevert.map { it.commitId }.containsAll(listOf(first.commitId, second.commitId)))

        // The shadow repo must not leak into a real git repo sharing the same work tree.
        assertFalse(Files.exists(root.resolve(".git")))
        assertTrue(Files.isDirectory(root.resolve(".asap-history")))
    }
}
