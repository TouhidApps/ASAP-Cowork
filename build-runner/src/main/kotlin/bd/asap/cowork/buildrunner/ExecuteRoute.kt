package bd.asap.cowork.buildrunner

import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.toolintegrations.AndroidProjectTool
import bd.asap.cowork.toolintegrations.BackendProjectTool
import bd.asap.cowork.toolintegrations.BackendServerTool
import bd.asap.cowork.toolintegrations.CheckDependencyVulnerabilitiesTool
import bd.asap.cowork.toolintegrations.DeviceScreenshotTool
import bd.asap.cowork.toolintegrations.EmulatorTool
import bd.asap.cowork.toolintegrations.FlutterBuildTool
import bd.asap.cowork.toolintegrations.FlutterProjectTool
import bd.asap.cowork.toolintegrations.GenerateStoreImageTool
import bd.asap.cowork.toolintegrations.GradleTool
import bd.asap.cowork.toolintegrations.IosLaunchAppTool
import bd.asap.cowork.toolintegrations.IosProjectTool
import bd.asap.cowork.toolintegrations.IosScreenshotTool
import bd.asap.cowork.toolintegrations.IosSimulatorTool
import bd.asap.cowork.toolintegrations.IosVideoTool
import bd.asap.cowork.toolintegrations.KmpProjectTool
import bd.asap.cowork.toolintegrations.LaunchAppTool
import bd.asap.cowork.toolintegrations.MetroTool
import bd.asap.cowork.toolintegrations.ReactNativeProjectTool
import bd.asap.cowork.toolintegrations.ReadDeviceLogsTool
import bd.asap.cowork.toolintegrations.RecordDeviceVideoTool
import bd.asap.cowork.toolintegrations.ScanForSecretsTool
import bd.asap.cowork.toolintegrations.TerminalTool
import bd.asap.cowork.toolintegrations.WriteBrandAssetTool
import bd.asap.cowork.toolintegrations.XcodeBuildTool
import bd.asap.cowork.toolintegrations.buildrunner.ExecuteRequest
import bd.asap.cowork.toolintegrations.buildrunner.ProgressLine
import bd.asap.cowork.toolintegrations.buildrunner.ResultLine
import bd.asap.cowork.toolintegrations.buildrunner.toKotlinMap
import io.ktor.http.ContentType
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

// encodeDefaults is required — [ProgressLine]/[ResultLine]'s "type" field is
// how BuildRunnerClient tells the two apart, but it's a defaulted property,
// which kotlinx.serialization otherwise omits from the encoded JSON.
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val ndjson = ContentType.parse("application/x-ndjson")

/**
 * The only route in the system that actually shells out to Gradle, adb, or
 * the emulator — see [bd.asap.cowork.buildrunner.main]'s doc comment.
 * Callers (currently `AndroidTools.executorFor` via `BuildRunnerClient`)
 * never invoke those processes themselves anymore.
 */
fun Route.executeRoute() {
    post("/execute") {
        val request = json.decodeFromString<ExecuteRequest>(call.receiveText())
        val workspaceRoot = File(request.workspaceRoot)
        val input = request.input.toKotlinMap()
        val writeLock = Mutex()

        call.respondTextWriter(contentType = ndjson) {
            suspend fun writeLine(line: String) = writeLock.withLock {
                write(line)
                write("\n")
                flush()
            }

            val result = try {
                dispatch(request.tool, workspaceRoot, input) { message ->
                    writeLine(json.encodeToString(ProgressLine(message)))
                }
            } catch (e: Exception) {
                ToolResult("Tool \"${request.tool}\" failed unexpectedly: ${e.message}", isError = true)
            }

            writeLine(
                json.encodeToString(
                    ResultLine(
                        summary = result.summary,
                        isError = result.isError,
                        imageUrl = result.imageUrl,
                        videoUrl = result.videoUrl,
                        notice = result.notice,
                    ),
                ),
            )
        }
    }
}

private suspend fun dispatch(
    tool: String,
    workspaceRoot: File,
    input: Map<String, Any?>,
    onProgress: suspend (String) -> Unit,
): ToolResult = when (tool) {
    TerminalTool.NAME -> TerminalTool.execute(workspaceRoot, input, onProgress)
    WriteBrandAssetTool.NAME -> WriteBrandAssetTool.execute(workspaceRoot, input)
    AndroidProjectTool.NAME -> AndroidProjectTool.execute(workspaceRoot, input)
    GradleTool.NAME -> GradleTool.execute(workspaceRoot, input, onProgress)
    EmulatorTool.NAME -> EmulatorTool.execute(workspaceRoot, input)
    LaunchAppTool.NAME -> LaunchAppTool.execute(input)
    DeviceScreenshotTool.NAME -> DeviceScreenshotTool.execute(workspaceRoot, input)
    RecordDeviceVideoTool.NAME -> RecordDeviceVideoTool.execute(workspaceRoot, input)
    IosProjectTool.NAME -> IosProjectTool.execute(workspaceRoot, input)
    XcodeBuildTool.NAME -> XcodeBuildTool.execute(workspaceRoot, input, onProgress)
    IosSimulatorTool.NAME -> IosSimulatorTool.execute(input)
    IosLaunchAppTool.NAME -> IosLaunchAppTool.execute(input)
    IosScreenshotTool.NAME -> IosScreenshotTool.execute(workspaceRoot, input)
    IosVideoTool.NAME -> IosVideoTool.execute(workspaceRoot, input)
    FlutterProjectTool.NAME -> FlutterProjectTool.execute(workspaceRoot, input)
    FlutterBuildTool.NAME -> FlutterBuildTool.execute(workspaceRoot, input, onProgress)
    KmpProjectTool.NAME -> KmpProjectTool.execute(workspaceRoot, input)
    ReactNativeProjectTool.NAME -> ReactNativeProjectTool.execute(workspaceRoot, input, onProgress)
    MetroTool.NAME -> MetroTool.execute(workspaceRoot, input)
    BackendProjectTool.NAME -> BackendProjectTool.execute(workspaceRoot, input, onProgress)
    BackendServerTool.NAME -> BackendServerTool.execute(workspaceRoot, input)
    ReadDeviceLogsTool.NAME -> ReadDeviceLogsTool.execute(input)
    GenerateStoreImageTool.NAME -> GenerateStoreImageTool.execute(workspaceRoot, input)
    CheckDependencyVulnerabilitiesTool.NAME -> CheckDependencyVulnerabilitiesTool.execute(input)
    ScanForSecretsTool.NAME -> ScanForSecretsTool.execute(workspaceRoot, input)
    else -> ToolResult("Unknown tool: $tool", isError = true)
}
