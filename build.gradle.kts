plugins {
    kotlin("jvm") version "2.4.0" apply false
    kotlin("plugin.serialization") version "2.4.0" apply false
    id("io.ktor.plugin") version "3.5.0" apply false
}

allprojects {
    repositories {
        mavenCentral()
    }
}

// Assembles the "just run a jar" distribution PLAN.md's easy-setup mode
// calls for: both fat jars, launcher scripts, and an end-user README, in
// one folder a non-technical user can unzip and run — no source checkout,
// no Gradle, no Node/npm needed at runtime (web-ui is already bundled into
// chat-gateway-all.jar by chat-gateway's own copyWebUi task).
val distDir = layout.buildDirectory.dir("dist/asap-cowork")

val assembleDist by tasks.registering(Sync::class) {
    dependsOn(":build-runner:buildFatJar", ":chat-gateway:buildFatJar")
    into(distDir)

    from(project(":build-runner").layout.buildDirectory.file("libs/build-runner-all.jar"))
    from(project(":chat-gateway").layout.buildDirectory.file("libs/chat-gateway-all.jar"))
    from(rootDir.resolve(".env.example"))
    from(rootDir.resolve("packaging/DIST_README.md")) { rename { "README.md" } }
    from(rootDir.resolve("packaging/start.bat"))
    from(rootDir.resolve("packaging/start.sh")) {
        fileMode = 0b111101101 // rwxr-xr-x — must stay executable for double-click/./start.sh
    }
    from(rootDir.resolve("packaging/start.sh")) {
        rename { "start.command" } // macOS Finder only double-click-runs .command
        fileMode = 0b111101101
    }
}

val distZip by tasks.registering(Zip::class) {
    dependsOn(assembleDist)
    archiveBaseName.set("asap-cowork")
    archiveVersion.set("")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
    from(distDir)
}
