package com.shareef.videoplayersj.torrent

import java.util.Locale

/** Human-readable sizes, speeds and times for the downloads screens on both platforms. */
object DownloadFormat {

    fun bytes(count: Long): String {
        if (count < 1024) return "$count B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = count / 1024.0
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (value >= 100) "%.0f %s".format(Locale.US, value, units[unit]) else "%.1f %s".format(Locale.US, value, units[unit])
    }

    fun rate(bytesPerSecond: Int): String = "${bytes(bytesPerSecond.toLong())}/s"

    fun eta(seconds: Long): String = when {
        seconds < 60 -> "under a minute left"
        seconds < 3600 -> "${seconds / 60} min left"
        seconds < 86_400 -> "${seconds / 3600} h ${(seconds % 3600) / 60} min left"
        else -> "${seconds / 86_400} days left"
    }

    /** The one-line status under a download's name, e.g. "120 MB of 1.4 GB · 2.1 MB/s · 9 min left". */
    fun summary(item: DownloadItem): String = when (item.state) {
        DownloadState.Starting -> "Starting…"
        DownloadState.FetchingInfo -> "Finding peers and fetching file list…" + peers(item)
        DownloadState.Checking -> "Checking files already downloaded…"
        DownloadState.Queued -> "Queued"
        DownloadState.Paused -> if (item.totalBytes > 0) "Paused · ${progressText(item)}" else "Paused"
        DownloadState.Error -> "Error: ${item.error ?: "unknown"}"
        DownloadState.Downloading -> buildString {
            append(progressText(item))
            append(" · ").append(rate(item.downloadRate))
            item.etaSeconds?.let { append(" · ").append(eta(it)) }
            append(peers(item))
        }
    }

    private fun progressText(item: DownloadItem) = "${bytes(item.downloadedBytes)} of ${bytes(item.totalBytes)}"

    private fun peers(item: DownloadItem) = if (item.peers > 0) " · ${item.peers} peers" else ""
}
