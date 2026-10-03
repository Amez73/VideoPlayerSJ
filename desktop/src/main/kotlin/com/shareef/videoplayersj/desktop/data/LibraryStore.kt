package com.shareef.videoplayersj.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Holds [LibraryData] in memory as a flow and writes every change through to disk. */
class LibraryStore(private val file: File = AppDirs.libraryFile) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val writeLock = Mutex()
    private val _data = MutableStateFlow(load())
    val data: StateFlow<LibraryData> = _data.asStateFlow()

    val current: LibraryData get() = _data.value

    suspend fun update(transform: (LibraryData) -> LibraryData) {
        writeLock.withLock {
            val updated = transform(_data.value)
            if (updated == _data.value) return
            _data.value = updated
            withContext(Dispatchers.IO) { save(updated) }
        }
    }

    private fun load(): LibraryData {
        if (!file.exists()) return LibraryData()
        return runCatching { json.decodeFromString<LibraryData>(file.readText()) }
            .getOrElse {
                // Keep the unreadable file for inspection rather than silently overwriting it.
                file.copyTo(File(file.parentFile, file.name + ".corrupt"), overwrite = true)
                LibraryData()
            }
    }

    // Write-then-rename, so a crash mid-write can't leave a truncated library behind.
    private fun save(data: LibraryData) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(LibraryData.serializer(), data))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
