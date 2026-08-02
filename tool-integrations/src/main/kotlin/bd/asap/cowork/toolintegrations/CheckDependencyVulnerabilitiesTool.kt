package bd.asap.cowork.toolintegrations

import bd.asap.cowork.llmgateway.ToolResult
import bd.asap.cowork.llmgateway.ToolSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Checks a list of dependencies against [OSV.dev](https://osv.dev), the
 * free, no-API-key-needed open-source vulnerability database — verified
 * live against known-vulnerable packages before relying on it here (a
 * real CVE came back for an old `lodash`/`jackson-databind` version).
 * Same "let a real, free, verifiable service do the actual lookup"
 * reasoning as [BackendProjectTool] deferring to Spring Initializr.
 *
 * Deliberately takes a bare `ecosystem`/`name`/`version` list rather than
 * reading a project's dependency file itself — parsing build.gradle.kts/
 * package.json/requirements.txt/pubspec.yaml correctly for arbitrary
 * projects is exactly the kind of thing [TerminalTool] plus the calling
 * agent's own reading is already good at, and it keeps this tool honest
 * about the one thing it actually owns: the OSV.dev query.
 */
object CheckDependencyVulnerabilitiesTool {
    const val NAME = "check_dependency_vulnerabilities"
    private const val MAX_DEPENDENCIES = 50
    private const val MAX_DETAILS_TO_FETCH = 30
    private const val BASE_URL = "https://api.osv.dev/v1"

    val spec = ToolSpec(
        name = NAME,
        description = "Checks a list of dependencies for known vulnerabilities (CVEs) via the free OSV.dev database — no API key needed. ecosystem must be one OSV.dev recognizes: \"npm\" (Node/React Native), \"PyPI\" (Python), \"Maven\" (Android/Kotlin/KMP/Spring Boot — name is \"groupId:artifactId\"), \"Pub\" (Flutter/Dart), \"Packagist\" (PHP/Composer), \"crates.io\", \"Go\", \"NuGet\", or \"RubyGems\". iOS/CocoaPods isn't a supported ecosystem.",
        parametersSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "dependencies" to mapOf(
                    "type" to "array",
                    "description" to "Up to $MAX_DEPENDENCIES {ecosystem, name, version} entries, extracted from the project's own dependency/lock file.",
                    "items" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "ecosystem" to mapOf("type" to "string"),
                            "name" to mapOf("type" to "string"),
                            "version" to mapOf("type" to "string"),
                        ),
                        "required" to listOf("ecosystem", "name", "version"),
                    ),
                ),
            ),
            "required" to listOf("dependencies"),
        ),
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

    suspend fun execute(input: Map<String, Any?>): ToolResult {
        val rawDeps = input["dependencies"] as? List<*>
        if (rawDeps.isNullOrEmpty()) return ToolResult("\"dependencies\" must be a non-empty array of {ecosystem, name, version}.", isError = true)
        if (rawDeps.size > MAX_DEPENDENCIES) return ToolResult("Too many dependencies (${rawDeps.size}) — check at most $MAX_DEPENDENCIES at a time.", isError = true)

        val deps = rawDeps.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val ecosystem = map["ecosystem"] as? String ?: return@mapNotNull null
            val name = map["name"] as? String ?: return@mapNotNull null
            val version = map["version"] as? String ?: return@mapNotNull null
            Dependency(ecosystem, name, version)
        }
        if (deps.size != rawDeps.size) {
            return ToolResult("Every dependency entry needs \"ecosystem\", \"name\", and \"version\" as strings.", isError = true)
        }

        val batchResponse = try {
            val request = BatchRequest(deps.map { VersionQuery(PackageRef(it.name, it.ecosystem), it.version) })
            val response = http.post("$BASE_URL/querybatch") {
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
            json.decodeFromString<BatchResponse>(response.bodyAsText())
        } catch (e: Exception) {
            return ToolResult("Failed to reach the OSV.dev vulnerability database: ${e.message}", isError = true)
        }

        val allVulnIds = batchResponse.results.flatMap { result -> result.vulns.map { it.id } }.distinct()
        val detailsById = mutableMapOf<String, VulnDetail>()
        for (id in allVulnIds.take(MAX_DETAILS_TO_FETCH)) {
            try {
                val response = http.get("$BASE_URL/vulns/$id")
                detailsById[id] = json.decodeFromString<VulnDetail>(response.bodyAsText())
            } catch (e: Exception) {
                // Best-effort — the dependency-level report below still lists the bare ID.
            }
        }

        val report = StringBuilder()
        var totalVulnCount = 0
        deps.forEachIndexed { index, dep ->
            val vulnIds = batchResponse.results.getOrNull(index)?.vulns?.map { it.id }.orEmpty()
            if (vulnIds.isEmpty()) return@forEachIndexed
            totalVulnCount += vulnIds.size
            report.appendLine("${dep.name}@${dep.version} (${dep.ecosystem}) — ${vulnIds.size} known ${if (vulnIds.size == 1) "vulnerability" else "vulnerabilities"}:")
            vulnIds.forEach { id ->
                val detail = detailsById[id]
                val cve = detail?.aliases?.firstOrNull { it.startsWith("CVE-") }
                val severity = detail?.databaseSpecific?.severity
                report.appendLine("  - $id${cve?.let { " ($it)" }.orEmpty()}${severity?.let { " [$it]" }.orEmpty()}: ${detail?.summary ?: "(details unavailable)"}")
            }
        }

        return if (totalVulnCount == 0) {
            ToolResult("No known vulnerabilities found for any of the ${deps.size} checked dependencies (via OSV.dev — this only knows about publicly disclosed vulnerabilities and doesn't replace a real audit).")
        } else {
            ToolResult(
                "Found $totalVulnCount known vulnerabilit${if (totalVulnCount == 1) "y" else "ies"} across ${deps.size} checked dependencies:\n$report",
                isError = true,
            )
        }
    }

    private data class Dependency(val ecosystem: String, val name: String, val version: String)

    @Serializable
    private data class PackageRef(val name: String, val ecosystem: String)

    @Serializable
    private data class VersionQuery(val `package`: PackageRef, val version: String)

    @Serializable
    private data class BatchRequest(val queries: List<VersionQuery>)

    @Serializable
    private data class VulnRef(val id: String)

    @Serializable
    private data class BatchResultEntry(val vulns: List<VulnRef> = emptyList())

    @Serializable
    private data class BatchResponse(val results: List<BatchResultEntry> = emptyList())

    @Serializable
    private data class DatabaseSpecific(val severity: String? = null)

    @Serializable
    private data class VulnDetail(
        val id: String,
        val summary: String? = null,
        val aliases: List<String> = emptyList(),
        @SerialName("database_specific") val databaseSpecific: DatabaseSpecific? = null,
    )
}
