package com.shareef.videoplayersj.torrent

import kotlinx.serialization.Serializable

enum class DownloadState {
    /** Added, waiting for libtorrent to pick it up. */
    Starting,

    /** Asking peers for the torrent's file list; nothing is known about its size yet. */
    FetchingInfo,

    /** Verifying data already on disk, e.g. after a restart. */
    Checking,

    /** Waiting for another download to finish before starting. */
    Queued,
    Downloading,
    Paused,
    Error,
}

/** A live snapshot of one download, refreshed about once a second. */
data class DownloadItem(
    val id: String,
    val name: String,
    val savePath: String,
    val state: DownloadState,
    /** 0..1, of the wanted data. */
    val progress: Float,
    val downloadedBytes: Long,
    /** 0 until the torrent's metadata arrives. */
    val totalBytes: Long,
    val downloadRate: Int,
    val uploadRate: Int,
    val peers: Int,
    val seeds: Int,
    /** Null when there's no download rate to estimate from. */
    val etaSeconds: Long?,
    val error: String?,
)

/** A download that completed. Its torrent is gone; only the files remain. */
@Serializable
data class FinishedDownload(
    val id: String,
    val name: String,
    val savePath: String,
    val sizeBytes: Long,
    val finishedAt: Long,
)

/** What's needed to pick a download back up after a restart, alongside its resume data. */
@Serializable
internal data class ActiveEntry(
    val id: String,
    val magnet: String,
    val savePath: String,
    val name: String,
    val addedAt: Long,
    val paused: Boolean = false,
)

@Serializable
internal data class TorrentState(
    val active: List<ActiveEntry> = emptyList(),
    val finished: List<FinishedDownload> = emptyList(),
)
