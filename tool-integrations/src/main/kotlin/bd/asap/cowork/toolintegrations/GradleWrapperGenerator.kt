package bd.asap.cowork.toolintegrations

import java.io.File

/**
 * Writes a project's own Gradle wrapper by copying a bundled, version-
 * agnostic `gradle-wrapper.jar`/`gradlew`/`gradlew.bat` (packaged as a
 * tool-integrations resource) and writing a `gradle-wrapper.properties`
 * pointing at the requested distribution — rather than shelling out to
 * whatever `gradle` binary happens to be globally installed and asking
 * *it* to run the `wrapper` task.
 *
 * That used to be exactly how this worked, and it's a real trap: running
 * `gradle wrapper` requires Gradle to fully *configure* the project first
 * (evaluate build.gradle.kts, apply every plugin) using the ambient global
 * Gradle version — not the one being generated for. A KMP project's
 * `shared` module applying `com.android.library` + Kotlin Multiplatform
 * together was enough to break under a newer global Gradle than the
 * pinned AGP/Kotlin versions were built against (`DefaultArtifactPublicationSet`
 * no longer existing), even though the *generated* wrapper's own pinned
 * Gradle version builds the same project fine. The wrapper jar/scripts
 * themselves don't care what plugins a project applies — they're a
 * generic bootstrapper — so there's no reason wrapper generation should
 * ever depend on the host machine's own Gradle install at all.
 */
object GradleWrapperGenerator {
    private const val RESOURCE_DIR = "gradle-wrapper-template"

    fun generate(projectDir: File, gradleVersion: String = AndroidBuildVersions.GRADLE_WRAPPER_VERSION): String = try {
        val wrapperDir = File(projectDir, "gradle/wrapper").apply { mkdirs() }
        copyResource("$RESOURCE_DIR/gradle-wrapper.jar", File(wrapperDir, "gradle-wrapper.jar"))
        File(wrapperDir, "gradle-wrapper.properties").writeText(
            """
            |distributionBase=GRADLE_USER_HOME
            |distributionPath=wrapper/dists
            |distributionUrl=https\://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip
            |networkTimeout=10000
            |validateDistributionUrl=true
            |zipStoreBase=GRADLE_USER_HOME
            |zipStorePath=wrapper/dists
            |
            """.trimMargin(),
        )
        copyResource("$RESOURCE_DIR/gradlew", File(projectDir, "gradlew")).setExecutable(true)
        copyResource("$RESOURCE_DIR/gradlew.bat", File(projectDir, "gradlew.bat"))
        "Generated the Gradle wrapper (Gradle $gradleVersion)."
    } catch (e: Exception) {
        "Warning: couldn't generate the Gradle wrapper (${e.message})."
    }

    private fun copyResource(resourcePath: String, target: File): File {
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: error("Bundled resource not found: $resourcePath")
        stream.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        return target
    }
}
