package com.shareef.videoplayersj.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

data class ScannedFile(
    val path: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

object FolderScanner {

    private val videoExtensions = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "ts", "m2ts", "3gp", "flv", "wmv", "mpg", "mpeg", "ogv",
    )

    fun isVideoFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in videoExtensions

    /** Walks the whole tree, skipping (rather than aborting on) folders it isn't allowed to read. */
    suspend fun scan(root: File): List<ScannedFile> = withContext(Dispatchers.IO) {
        if (!root.isDirectory) return@withContext emptyList()
        val results = mutableListOf<ScannedFile>()
        Files.walkFileTree(
            root.toPath(),
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val name = dir.fileName?.toString().orEmpty()
                    // Hidden and system folders (".git", "$RECYCLE.BIN") never hold the user's media.
                    return if (dir != root.toPath() && (name.startsWith(".") || name.startsWith("$"))) {
                        FileVisitResult.SKIP_SUBTREE
                    } else {
                        FileVisitResult.CONTINUE
                    }
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val name = file.fileName.toString()
                    if (attrs.isRegularFile && isVideoFile(name)) {
                        results += ScannedFile(
                            path = file.toAbsolutePath().toString(),
                            displayName = name,
                            sizeBytes = attrs.size(),
                            lastModified = attrs.lastModifiedTime().toMillis(),
                        )
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult =
                    FileVisitResult.CONTINUE
            },
        )
        results
    }
}
