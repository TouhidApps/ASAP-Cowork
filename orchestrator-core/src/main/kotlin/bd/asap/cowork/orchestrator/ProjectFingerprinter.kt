package bd.asap.cowork.orchestrator

import java.nio.file.Files
import java.nio.file.Path

/**
 * Inspects a workspace for signature files and infers which stack(s) are
 * present (PLAN.md §2). A project can match more than one signature at
 * once (e.g. a KMP app plus a Spring Boot backend in one repo) — the
 * orchestrator activates every agent whose signature matched, and nothing
 * else.
 */
object ProjectFingerprinter {
    fun detect(workspaceRoot: Path): Set<String> {
        if (!Files.isDirectory(workspaceRoot)) return emptySet()

        val stacks = mutableSetOf<String>()
        val entries = Files.list(workspaceRoot).use { it.map { p -> p.fileName.toString() }.toList() }

        fun exists(name: String) = entries.contains(name)

        if (exists("build.gradle.kts") || exists("build.gradle")) {
            if (hasKmpSourceSets(workspaceRoot)) stacks += "kmp"
            if (exists("AndroidManifest.xml") || Files.exists(workspaceRoot.resolve("src/main/AndroidManifest.xml"))) {
                stacks += "android"
            }
            if (containsDependency(workspaceRoot, "spring-boot")) stacks += "backend-spring-boot"
        }
        if (exists("pubspec.yaml")) stacks += "flutter"
        if (entries.any { it.endsWith(".xcodeproj") || it.endsWith(".xcworkspace") } || exists("Podfile")) {
            stacks += "ios"
        }
        if (exists("package.json")) {
            val pkg = workspaceRoot.resolve("package.json")
            val text = runCatching { Files.readString(pkg) }.getOrDefault("")
            if ("react-native" in text) stacks += "react-native"
            if ("express" in text || "fastify" in text) stacks += "backend-node"
        }

        return stacks
    }

    private fun hasKmpSourceSets(workspaceRoot: Path): Boolean {
        val settings = workspaceRoot.resolve("settings.gradle.kts")
        if (!Files.exists(settings)) return false
        val text = runCatching { Files.readString(settings) }.getOrDefault("")
        return "kotlin(\"multiplatform\")" in text || "kotlin-multiplatform" in text
    }

    private fun containsDependency(workspaceRoot: Path, needle: String): Boolean {
        val build = workspaceRoot.resolve("build.gradle.kts").takeIf { Files.exists(it) }
            ?: workspaceRoot.resolve("build.gradle").takeIf { Files.exists(it) }
            ?: return false
        val text = runCatching { Files.readString(build) }.getOrDefault("")
        return needle in text
    }
}
