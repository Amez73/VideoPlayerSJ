package com.shareef.videoplayersj.desktop.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AppSettings(
    /** Keeps the whole app fullscreen, for use from the couch with a controller. */
    val couchMode: Boolean = false,
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
