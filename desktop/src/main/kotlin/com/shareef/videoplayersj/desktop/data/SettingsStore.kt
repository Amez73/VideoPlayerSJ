package com.shareef.videoplayersj.desktop.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AppSettings(
    /** Where the last torrent was saved, offered first for the next one. */
    val lastDownloadDir: String? = null,
    /** Identifies this computer to other copies of the app when sending files. */
    val deviceId: String? = null,
)

/** App preferences, kept apart from the library so either can be reset on its own. */
class SettingsStore(private val file: File = AppDirs.settingsFile) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    var settings: AppSettings = load()
        private set

    fun update(transform: (AppSettings) -> AppSettings) {
        settings = transform(settings)
        // A few bytes, so written straight away rather than off the UI thread.
        runCatching { file.writeText(json.encodeToString(AppSettings.serializer(), settings)) }
    }

    private fun load(): AppSettings =
        runCatching { json.decodeFromString<AppSettings>(file.readText()) }.getOrDefault(AppSettings())
}
