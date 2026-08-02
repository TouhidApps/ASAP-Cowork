package bd.asap.cowork.chatgateway.features.logcat

import bd.asap.cowork.chatgateway.common.ApiResponse
import bd.asap.cowork.chatgateway.common.exceptions.AppException
import bd.asap.cowork.toolintegrations.AppSession
import bd.asap.cowork.toolintegrations.DeviceTargeting
import bd.asap.cowork.toolintegrations.ToolchainEnvironment
import io.ktor.http.ContentType
import io.ktor.server.response.respond
import io.ktor.server.response.respondTextWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.util.cio.ChannelWriteException
import io.ktor.utils.io.ClosedWriteChannelException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

private val logcatJson = Json { encodeDefaults = true }
private val logcatLogger = LoggerFactory.getLogger("LogcatStream")

/** One line of `data: <json>` sent down /logcat/stream — deliberately tiny next to ChatEvent, just a raw line. */
@Serializable
private data class LogcatLine(val line: String)

/** Backs the Device Logs panel's header, so it's clear which device/app a stream of otherwise-generic log lines actually belongs to. */
@Serializable
data class LogcatInfo(val deviceName: String, val packageName: String?)

private const val HEARTBEAT_INTERVAL_MS = 3_000L
private const val POLL_INTERVAL_MS = 200L

/**
 * Streams `adb logcat` for whatever device [DeviceTargeting] resolves — an
 * emulator this server booted via `manage_emulator`, one already open from
 * outside it, or a physical device connected over USB — as Server-Sent
 * Events, so the Device Logs panel can show live device output the same way
 * Android Studio's Logcat pane does. Validates before committing to
 * text/event-stream — a JSON error can't be sent once headers commit — and
 * sends a heartbeat comment to survive idle proxies.
 *
 * Unfiltered `adb logcat` is every process on the device, which drowns the
 * app being worked on in system-service noise. Two query params narrow it,
 * both optional: `scope=app` (the default) resolves the PID of whatever
 * [AppSession] last launched via `launch_app` and passes `--pid` so only
 * that process's lines show — same default Android Studio's Logcat pane
 * uses — `scope=all` skips that. `level=V|D|I|W|E` sets a minimum priority
 * threshold (adb's own `*:<level>` filterspec). If `scope=app` is requested
 * but no PID can be resolved (nothing launched yet, or the app has since
 * been closed/crashed), this errors rather than silently streaming
 * everything unfiltered — a `--pid`-less firehose defeats the point of
 * picking "app only" and looks like the filter is just broken.
 */
fun Route.logcatRoutes() {
    route("/api/v1/logcat") {
        get("/info") {
            val serial = DeviceTargeting.resolveSerial()
                ?: throw AppException.BadRequest("No emulator or device is connected.")
            call.respond(ApiResponse.ok(LogcatInfo(deviceName = resolveDeviceName(serial), packageName = AppSession.current())))
        }

        get("/stream") {
            val serial = DeviceTargeting.resolveSerial()
                ?: throw AppException.BadRequest("No emulator or device is connected. Start one first (manage_emulator in chat, or the Device Logs panel once an emulator is up), or plug in a physical device.")

            val scope = call.request.queryParameters["scope"] ?: "app"
            val level = call.request.queryParameters["level"]?.uppercase()?.takeIf { it in VALID_LEVELS }
            val pid = if (scope == "app") {
                val packageName = AppSession.current()
                    ?: throw AppException.BadRequest("No app has been launched yet — launch one first, or switch to \"All processes\".")
                resolvePid(serial, packageName)
                    ?: throw AppException.BadRequest("\"$packageName\" isn't currently running — launch it again, or switch to \"All processes\".")
            } else {
                null
            }

            call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                val writeLock = Mutex()
                val command = buildList {
                    addAll(listOf("adb", "-s", serial, "logcat", "-v", "time"))
                    pid?.let { add("--pid=$it") }
                    level?.let { add("*:$it") }
                }
                val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
                ToolchainEnvironment.configure(processBuilder)
                val process = processBuilder.start()

                try {
                    coroutineScope {
                        val heartbeat = launch {
                            while (isActive) {
                                delay(HEARTBEAT_INTERVAL_MS)
                                writeLock.withLock {
                                    write(": keep-alive\n\n")
                                    flush()
                                }
                            }
                        }
                        try {
                            val reader = process.inputStream.bufferedReader()
                            while (isActive) {
                                // Polled rather than a blocking readLine() so this loop's
                                // `isActive` check — and therefore reacting to the client
                                // disconnecting — isn't at the mercy of how often the
                                // emulator actually logs something.
                                val ready = withContext(Dispatchers.IO) { reader.ready() }
                                if (!ready) {
                                    delay(POLL_INTERVAL_MS)
                                    continue
                                }
                                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                                writeLock.withLock {
                                    write("data: ${logcatJson.encodeToString(LogcatLine(line))}\n\n")
                                    flush()
                                }
                            }
                        } finally {
                            heartbeat.cancel()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ChannelWriteException) {
                    logcatLogger.info("Client disconnected from logcat stream ({}): {}", serial, e.message)
                } catch (e: ClosedWriteChannelException) {
                    logcatLogger.info("Client disconnected from logcat stream ({}): {}", serial, e.message)
                } catch (e: Exception) {
                    logcatLogger.error("Logcat stream failed for {}", serial, e)
                } finally {
                    process.destroyForcibly()
                }
            }
        }
    }
}

private val VALID_LEVELS = setOf("V", "D", "I", "W", "E")
private const val PID_RESOLVE_TIMEOUT_SECONDS = 5L

/** Human-readable model name (e.g. "Pixel 7", "sdk_gphone64_arm64") — falls back to the bare serial if the property can't be read. */
private fun resolveDeviceName(serial: String): String = try {
    val builder = ProcessBuilder("adb", "-s", serial, "shell", "getprop", "ro.product.model").redirectErrorStream(true)
    ToolchainEnvironment.configure(builder)
    val process = builder.start()
    val finished = process.waitFor(PID_RESOLVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    if (!finished) {
        process.destroyForcibly()
        serial
    } else {
        process.inputStream.bufferedReader().readText().trim().takeIf { it.isNotBlank() } ?: serial
    }
} catch (e: IOException) {
    serial
}

/** `pidof` can report more than one process for an app with a `:remote`-style service — the first is always its main process, which is what Android Studio's Logcat pane shows by default too. */
private fun resolvePid(serial: String, packageName: String): String? = try {
    val builder = ProcessBuilder("adb", "-s", serial, "shell", "pidof", packageName).redirectErrorStream(true)
    ToolchainEnvironment.configure(builder)
    val process = builder.start()
    val finished = process.waitFor(PID_RESOLVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    if (!finished) {
        process.destroyForcibly()
        null
    } else {
        process.inputStream.bufferedReader().readText().trim().split(Regex("\\s+")).firstOrNull()?.takeIf { it.isNotBlank() }
    }
} catch (e: IOException) {
    null
}
