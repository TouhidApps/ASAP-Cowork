plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":agent-sdk"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
