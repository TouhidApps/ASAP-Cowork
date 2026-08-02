plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":agent-sdk"))
    api(project(":llm-gateway"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.ktor:ktor-client-core:3.5.0")
    implementation("io.ktor:ktor-client-cio:3.5.0")
    testImplementation(kotlin("test-junit5"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}
