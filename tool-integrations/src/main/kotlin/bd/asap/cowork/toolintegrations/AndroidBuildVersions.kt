package bd.asap.cowork.toolintegrations

/**
 * Shared version pins for hand-scaffolded Gradle Android/KMP project
 * templates ([AndroidProjectTool], [KmpProjectTool]) — kept in one place so
 * the two don't drift apart. Kotlin 2.x means the Compose compiler is
 * applied via the `org.jetbrains.kotlin.plugin.compose` Gradle plugin
 * (pinned to [KOTLIN_VERSION] itself) rather than the old
 * `composeOptions { kotlinCompilerExtensionVersion = ... }` mechanism,
 * which only ever worked pre-K2.
 */
object AndroidBuildVersions {
    const val GRADLE_WRAPPER_VERSION = "8.11.1"
    const val AGP_VERSION = "8.7.3"
    const val KOTLIN_VERSION = "2.1.0"
    const val COMPOSE_BOM_VERSION = "2024.12.01"
    /** Compose Multiplatform (org.jetbrains.compose) — [KmpProjectTool]'s shared UI. Compatible with [KOTLIN_VERSION]. */
    const val COMPOSE_MULTIPLATFORM_VERSION = "1.7.1"
}
