package bd.asap.cowork.chatgateway.features.history

import bd.asap.cowork.chatgateway.common.ApiResponse
import bd.asap.cowork.chatgateway.plugins.configureSerialization
import bd.asap.cowork.chatgateway.plugins.configureStatusPages
import bd.asap.cowork.orchestrator.ProjectContext
import bd.asap.cowork.workspacehistory.CommitInfo
import bd.asap.cowork.workspacehistory.FileDiff
import bd.asap.cowork.workspacehistory.WorkspaceHistoryService
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end check of the REST surface backing the chat page's "Code
 * Changes" drawer, against a real (temp-directory) shadow git repo — no
 * mocking of WorkspaceHistoryService, since the whole point is to prove the
 * routing/DI/serialization wiring actually surfaces real JGit-backed data.
 */
class HistoryRoutesTest {

    @Test
    fun `lists, diffs, and reverts real history entries over HTTP`() = testApplication {
        val root = Files.createTempDirectory("history-routes-test")
        val projectContext = ProjectContext(root)
        val service = WorkspaceHistoryService(projectContext)

        application {
            install(Koin) { modules(module { single { service } }) }
            configureSerialization()
            configureStatusPages()
            routing { historyRoutes() }
        }

        val client = createClient { install(ContentNegotiation) { json() } }

        // Empty history before any turn.
        val emptyBody = client.get("/api/v1/history").body<ApiResponse<List<CommitInfo>>>()
        assertTrue(emptyBody.success)
        assertEquals(emptyList(), emptyBody.data)

        // Simulate a turn's file edit + the Routing.kt commit hook, directly
        // through the service (this is exactly what the WS handler does).
        runBlocking {
            service.ensureInitialized()
            root.resolve("hello.txt").toFile().writeText("v1")
            service.commitIfDirty("conv-1", "msg-1", "Add hello.txt")
        }

        val listBody = client.get("/api/v1/history").body<ApiResponse<List<CommitInfo>>>()
        assertTrue(listBody.success)
        val entries = listBody.data.orEmpty()
        assertEquals(1, entries.size)
        val commitId = entries[0].commitId

        val diffResponse = client.get("/api/v1/history/$commitId/diff")
        assertEquals(HttpStatusCode.OK, diffResponse.status)
        val diffBody = Json.decodeFromString<ApiResponse<List<FileDiff>>>(diffResponse.body())
        assertTrue(diffBody.success)
        assertEquals(1, diffBody.data?.size)
        assertEquals("hello.txt", diffBody.data?.first()?.path)

        // Unknown commit id -> 404 via AppException.NotFound + StatusPages, not a raw 500.
        val notFound = client.get("/api/v1/history/does-not-exist/diff")
        assertEquals(HttpStatusCode.NotFound, notFound.status)

        // Revert (through the REST endpoint this time) creates a new, forward-only entry.
        val revertResponse = client.post("/api/v1/history/$commitId/revert")
        assertEquals(HttpStatusCode.OK, revertResponse.status)
        val revertBody = Json.decodeFromString<ApiResponse<CommitInfo>>(revertResponse.body())
        assertTrue(revertBody.success)

        val afterRevert = client.get("/api/v1/history").body<ApiResponse<List<CommitInfo>>>()
        assertEquals(2, afterRevert.data?.size)
    }
}
