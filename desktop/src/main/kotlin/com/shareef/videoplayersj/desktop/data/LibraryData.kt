package com.shareef.videoplayersj.desktop.data

import kotlinx.serialization.Serializable

/**
 * Everything the app persists, saved as one JSON file. A personal library is at most a few
 * thousand files, so a single document is simpler than a database and easy to inspect or back up.
 * The shape mirrors the Android app's Room tables.
 */
@Serializable
data class LibraryData(
    val folders: List<FolderRecord> = emptyList(),
    val shows: List<ShowRecord> = emptyList(),
    val videos: List<VideoRecord> = emptyList(),
    val progress: List<ProgressRecord> = emptyList(),
    val nextShowId: Long = 1,
    val nextVideoId: Long = 1,
)

@Serializable
data class FolderRecord(
    val path: String,
    val displayName: String,
    val addedAt: Long,
    val lastScannedAt: Long? = null,
)

@Serializable
data class ShowRecord(
    val id: Long,
    val canonicalTitle: String,
    /** Normalized title tokens, used to re-match this show against future rescans. */
    val matchTokens: List<String>,
)

@Serializable
data class VideoRecord(
    val id: Long,
    val path: String,
    val folderPath: String,
    val showId: Long?,
    val rawFileName: String,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val displayTitle: String,
    /** Null until the file is opened once in the player and VLC reports it. */
    val durationMs: Long? = null,
    val sizeBytes: Long,
    val lastModified: Long,
    val addedAt: Long,
)

@Serializable
data class ProgressRecord(
    val videoId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
    val isFinished: Boolean,
)
