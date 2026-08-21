plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin")
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("bd.asap.cowork.chatgateway.ApplicationKt")
}

tasks.named<JavaExec>("run") {
    // DotEnv reads a relative ".env" — Gradle's `run` task defaults workingDir
    // to this subproject's directory, not the root where .env actually lives.
    workingDir = rootDir
}

val webUiDir = rootDir.resolve("web-ui")
val webUiStaticDir = layout.buildDirectory.dir("generated/webUiStatic")

// Bundles the built React chat UI into this module's own jar, at "static/"
// on the classpath, so a packaged chat-gateway-all.jar serves the whole app
// from one process/port (see Routing.kt's staticResources("/", "static")) —
// an end user never needs Node/npm installed to run the packaged jar, only
// to rebuild the distribution from source.
val buildWebUi by tasks.registering(Exec::class) {
    workingDir = webUiDir
    commandLine("npm", "run", "build")
    inputs.dir(webUiDir.resolve("src"))
    inputs.file(webUiDir.resolve("package.json"))
    outputs.dir(webUiDir.resolve("dist"))
}

val copyWebUi by tasks.registering(Copy::class) {
    dependsOn(buildWebUi)
    from(webUiDir.resolve("dist"))
    into(webUiStaticDir.map { it.dir("static") })
}

sourceSets.main {
    resources.srcDir(webUiStaticDir)
}

tasks.named("processResources") {
    dependsOn(copyWebUi)
}

val koinVersion = "4.1.0"

dependencies {
    implementation(project(":agent-sdk"))
    implementation(project(":orchestrator-core"))
    implementation(project(":llm-gateway"))
    implementation(project(":tool-integrations"))
    implementation(project(":firebase-integration"))
    implementation(project(":context-store"))
    implementation(project(":workspace-history"))
    implementation(project(":agents:requirements-agent"))
    implementation(project(":agents:architecture-advisor-agent"))
    implementation(project(":agents:techstack-agent"))
    implementation(project(":agents:scaffolding-agent"))
    implementation(project(":agents:android-agent"))
    implementation(project(":agents:ios-agent"))
    implementation(project(":agents:flutter-agent"))
    implementation(project(":agents:kmp-agent"))
    implementation(project(":agents:react-native-agent"))
    implementation(project(":agents:backend-agent"))
    implementation(project(":agents:testing-agent"))
    implementation(project(":agents:debugging-agent"))
    implementation(project(":agents:cicd-agent"))
    implementation(project(":agents:branding-agent"))
    implementation(project(":agents:legal-doc-agent"))
    implementation(project(":agents:landing-page-agent"))
    implementation(project(":agents:store-asset-agent"))
    implementation(project(":agents:documentation-agent"))
    implementation(project(":agents:analytics-agent"))
    implementation(project(":agents:security-review-agent"))
    implementation(project(":agents:performance-agent"))
    implementation(project(":agents:publishing-agent"))
    implementation(project(":agents:notes-agent"))
    implementation(project(":agents:workspace-agent"))

    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-websockets")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-config-yaml")

    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

    implementation("ch.qos.logback:logback-classic:1.5.16")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-content-negotiation")
    testImplementation(kotlin("test"))
}
