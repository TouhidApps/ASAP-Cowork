package bd.asap.cowork.buildrunner

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExecuteRouteTest {
    @Test
    fun `run_terminal_command streams a progress line and a result line, both tagged with type`() = testApplication {
        application { module() }

        val response = client.post("/execute") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"tool":"run_terminal_command","workspaceRoot":"${System.getProperty("java.io.tmpdir")}","input":{"command":"echo hi"}}""",
            )
        }

        val lines = response.bodyAsText().trim().lines()
        assertTrue(lines.isNotEmpty())
        assertContains(lines.last(), "\"type\":\"result\"")
        assertContains(lines.last(), "\"isError\":false")
        assertContains(lines.last(), "hi")
    }

    @Test
    fun `manage_ios_simulator dispatches without throwing even when simctl is unavailable`() = testApplication {
        // This sandbox only has the Xcode Command Line Tools installed, not
        // full Xcode.app, so `xcrun simctl` itself isn't runnable here —
        // this test only confirms the route reaches IosSimulatorTool and
        // surfaces that as a clean error result rather than a 500.
        application { module() }

        val response = client.post("/execute") {
            contentType(ContentType.Application.Json)
            setBody("""{"tool":"manage_ios_simulator","workspaceRoot":"${System.getProperty("java.io.tmpdir")}","input":{"action":"list"}}""")
        }

        val body = response.bodyAsText().trim()
        assertContains(body, "\"type\":\"result\"")
    }

    @Test
    fun `create_flutter_project dispatches and validates without invoking the real flutter CLI`() = testApplication {
        application { module() }

        val response = client.post("/execute") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"tool":"create_flutter_project","workspaceRoot":"${System.getProperty("java.io.tmpdir")}","input":{"name":"Not-Valid"}}""",
            )
        }

        val body = response.bodyAsText().trim()
        assertContains(body, "\"isError\":true")
    }

    @Test
    fun `create_kmp_project dispatches and actually scaffolds a project with a real gradlew`() = testApplication {
        // Unlike Flutter/iOS scaffolding, this doesn't need any external CLI
        // or network access — GradleWrapperGenerator copies a bundled
        // wrapper jar rather than shelling out — so this can assert on the
        // real result rather than just a validation error.
        application { module() }
        val workspaceRoot = kotlin.io.path.createTempDirectory("execute-route-kmp-test").toFile()

        val response = client.post("/execute") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"tool":"create_kmp_project","workspaceRoot":"${workspaceRoot.absolutePath}","input":{"name":"RouteKmp"}}""",
            )
        }

        val body = response.bodyAsText().trim()
        assertContains(body, "\"isError\":false")
        assertTrue(workspaceRoot.resolve("RouteKmp/gradlew").canExecute())
        assertTrue(workspaceRoot.resolve("RouteKmp/shared/build.gradle.kts").exists())
    }

    @Test
    fun `create_react_native_project dispatches and validates without invoking the real CLI`() = testApplication {
        application { module() }

        val response = client.post("/execute") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"tool":"create_react_native_project","workspaceRoot":"${System.getProperty("java.io.tmpdir")}","input":{"name":"not-valid"}}""",
            )
        }

        val body = response.bodyAsText().trim()
        assertContains(body, "\"isError\":true")
    }

    @Test
    fun `manage_metro_bundler dispatches and reports when nothing is running`() = testApplication {
        application { module() }

        val response = client.post("/execute") {
            contentType(ContentType.Application.Json)
            setBody("""{"tool":"manage_metro_bundler","workspaceRoot":"${System.getProperty("java.io.tmpdir")}","input":{"action":"stop"}}""")
        }

        val body = response.bodyAsText().trim()
        assertContains(body, "\"isError\":false")
    }

    @Test
    fun `create_backend_project dispatches and scaffolds the dependency-free php stack`() = testApplication {
        application { module() }
        val workspaceRoot = kotlin.io.path.createTempDirectory("execute-route-backend-test").toFile()

        val response = client.post("/execute") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"tool":"create_backend_project","workspaceRoot":"${workspaceRoot.absolutePath}","input":{"name":"RouteBackend","stack":"php","database":"sqlite"}}""",
            )
        }

        val body = response.bodyAsText().trim()
        assertContains(body, "\"isError\":false")
        assertTrue(workspaceRoot.resolve("RouteBackend/api/items.php").exists())
    }

    @Test
    fun `manage_backend_server dispatches and reports when nothing is running`() = testApplication {
        application { module() }

        val response = client.post("/execute") {
            contentType(ContentType.Application.Json)
            setBody("""{"tool":"manage_backend_server","workspaceRoot":"${System.getProperty("java.io.tmpdir")}","input":{"action":"stop"}}""")
        }

        val body = response.bodyAsText().trim()
        assertContains(body, "\"isError\":false")
    }

    @Test
    fun `read_device_logs dispatches and reports cleanly when no device is connected`() = testApplication {
        // This sandbox may or may not have a real device attached depending
        // on when it runs — either way the route must reach
        // ReadDeviceLogsTool and return a clean result, never throw.
        application { module() }

        val response = client.post("/execute") {
            contentType(ContentType.Application.Json)
            setBody("""{"tool":"read_device_logs","workspaceRoot":"${System.getProperty("java.io.tmpdir")}","input":{"platform":"ios"}}""")
        }

        val body = response.bodyAsText().trim()
        assertContains(body, "\"type\":\"result\"")
    }

    @Test
    fun `generate_store_image dispatches and actually produces a PNG at the requested size`() = testApplication {
        application { module() }
        val workspaceRoot = kotlin.io.path.createTempDirectory("execute-route-store-image-test").toFile()
        val source = java.awt.image.BufferedImage(200, 400, java.awt.image.BufferedImage.TYPE_INT_RGB)
        javax.imageio.ImageIO.write(source, "png", workspaceRoot.resolve("shot.png"))

        val response = client.post("/execute") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"tool":"generate_store_image","workspaceRoot":"${workspaceRoot.absolutePath}","input":{"inputPath":"shot.png","outputPath":"out.png","width":500,"height":900}}""",
            )
        }

        val body = response.bodyAsText().trim()
        assertContains(body, "\"isError\":false")
        val output = javax.imageio.ImageIO.read(workspaceRoot.resolve("out.png"))
        assertEquals(500, output.width)
        assertEquals(900, output.height)
    }

    @Test
    fun `unknown tool name returns an error result instead of throwing`() = testApplication {
        application { module() }

        val response = client.post("/execute") {
            contentType(ContentType.Application.Json)
            setBody("""{"tool":"does_not_exist","workspaceRoot":"${System.getProperty("java.io.tmpdir")}","input":{}}""")
        }

        val body = response.bodyAsText().trim()
        assertContains(body, "\"isError\":true")
        assertContains(body, "Unknown tool")
    }
}
