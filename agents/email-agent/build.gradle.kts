plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":agent-sdk"))
    implementation(project(":llm-gateway"))
    implementation(project(":context-store"))
    implementation(project(":tool-integrations"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    testImplementation(kotlin("test-junit5"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}
