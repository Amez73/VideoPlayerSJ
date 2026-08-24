package com.shareef.videoplayersj.data.saf

object VideoFileFilter {
    // Extension allowlist is required as a fallback: some SAF providers report generic
    // mime types (e.g. application/octet-stream) for containers like .mkv/.avi.
    private val videoExtensions = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "ts", "3gp", "flv", "wmv",
    )

    fun isVideoFile(displayName: String, mimeType: String?): Boolean {
        if (mimeType?.startsWith("video/") == true) return true
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return extension in videoExtensions
    }
}
