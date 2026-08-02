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
    mainClass.set("bd.asap.cowork.buildrunner.ApplicationKt")
}

dependencies {
    implementation(project(":agent-sdk"))
    implementation(project(":llm-gateway"))
    implementation(project(":tool-integrations"))

    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-status-pages")
    implementation("io.ktor:ktor-server-call-logging")

    implementation("ch.qos.logback:logback-classic:1.5.16")

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation(kotlin("test"))
}
