plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("com.anthropic:anthropic-java:2.52.0")
    implementation("com.openai:openai-java:4.45.0")
    implementation("com.google.genai:google-genai:1.63.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}
