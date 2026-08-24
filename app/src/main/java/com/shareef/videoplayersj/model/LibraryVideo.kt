package com.shareef.videoplayersj.model

data class LibraryVideo(
    val id: Long,
    val documentUriString: String,
    val displayTitle: String,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val durationMs: Long?,
    val positionMs: Long,
    val isFinished: Boolean,
)
