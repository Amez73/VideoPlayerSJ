package com.shareef.videoplayersj.model

data class LibraryShow(
    val id: Long,
    val canonicalTitle: String,
    val episodeCount: Int,
    /** First episode, used as the show's thumbnail source. */
    val thumbnailVideoId: Long?,
    val thumbnailUri: String?,
)
