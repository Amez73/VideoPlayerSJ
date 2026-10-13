plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// The magnet-link downloader shared by the Android and desktop apps. Plain JVM code over
// libtorrent4j's Java API; each app adds the native libtorrent build for its own platform.
kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.libtorrent4j)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.libtorrent4j.linux)
}

tasks.test {
    useJUnitPlatform()
    // The engine test runs a real libtorrent session, which needs the Linux native build.
    onlyIf { System.getProperty("os.name").startsWith("Linux") }
}
