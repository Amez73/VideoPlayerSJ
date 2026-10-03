plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure-Kotlin library logic (filename parsing, show clustering, formatting) shared by the
// Android app and the desktop app. Must stay free of Android and desktop dependencies.
kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
