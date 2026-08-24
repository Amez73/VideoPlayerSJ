package com.shareef.videoplayersj.model

/** A part-watched video plus the show it belongs to (null for standalone movies). */
data class ContinueWatchingItem(
    val video: LibraryVideo,
    val showTitle: String?,
)
