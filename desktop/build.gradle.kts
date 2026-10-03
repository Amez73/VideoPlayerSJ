import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.vlcj)

    testImplementation(kotlin("test"))
}

// --- Bundled VLC (Windows) -------------------------------------------------------------------
// Playback goes through libVLC, which handles the mkv/HEVC/AC3/DTS mix typical of downloaded
// shows. On Windows the libVLC binaries are shipped inside the app so nothing else needs
// installing; on macOS/Linux the app falls back to a system VLC install.
val vlcVersion = "3.0.24"
val vlcSha256 = "fcf30850371ad10c9373cc4f0f4501e7dee49e3e9ae9f20c72fb2661a1ca6323"
val isWindowsHost = System.getProperty("os.name").startsWith("Windows")
val vlcZip = layout.buildDirectory.file("vlc/vlc-$vlcVersion-win64.zip")
val vlcResourcesRoot = layout.buildDirectory.dir("vlc-dist")

val downloadVlc by tasks.registering {
    val zipFile = vlcZip
    outputs.file(zipFile)
    onlyIf { !zipFile.get().asFile.exists() }
    doLast {
        val target = zipFile.get().asFile
        target.parentFile.mkdirs()
        val url = "https://download.videolan.org/pub/videolan/vlc/$vlcVersion/win64/vlc-$vlcVersion-win64.zip"
        val partial = File(target.parentFile, target.name + ".part")
        URI(url).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(partial.readBytes())
            .joinToString("") { "%02x".format(it) }
        if (digest != vlcSha256) {
            partial.delete()
            throw GradleException("VLC download checksum mismatch: expected $vlcSha256, got $digest")
        }
        partial.renameTo(target)
    }
}

// Only libVLC itself and its plugins are needed; VLC's own Qt interface is by far the largest
// plugin and is never loaded when embedding.
val extractVlc by tasks.registering(Sync::class) {
    dependsOn(downloadVlc)
    from(zipTree(vlcZip)) {
        include("*/libvlc.dll", "*/libvlccore.dll", "*/plugins/**")
        exclude("*/plugins/gui/**", "*/plugins/skins2/**")
        eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
        includeEmptyDirs = false
    }
    into(vlcResourcesRoot.map { it.dir("windows/vlc") })
}

compose.desktop {
    application {
        mainClass = "com.shareef.videoplayersj.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "VideoPlayerSJ"
            packageVersion = "1.0.0"
            description = "Plays a folder of downloaded shows and movies"
            vendor = "Shareef"
            modules("java.instrument", "jdk.unsupported")
            if (isWindowsHost) {
                appResourcesRootDir.set(vlcResourcesRoot)
            }
            windows {
                menuGroup = "VideoPlayerSJ"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "5b0c7a52-3f7e-4d0e-9a1c-8f1d2b6e4c31"
            }
        }
    }
}

if (isWindowsHost) {
    tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(extractVlc) }
}

tasks.test {
    useJUnitPlatform()
    // Lets the playback tests find the bundled libVLC the same way the packaged app does.
    if (isWindowsHost) {
        dependsOn(extractVlc)
        systemProperty("compose.application.resources.dir", vlcResourcesRoot.get().dir("windows").asFile.path)
    }
}
