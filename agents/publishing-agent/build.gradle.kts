plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":agent-sdk"))
    implementation(project(":llm-gateway"))
    implementation(project(":tool-integrations"))
    implementation(project(":firebase-integration"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
