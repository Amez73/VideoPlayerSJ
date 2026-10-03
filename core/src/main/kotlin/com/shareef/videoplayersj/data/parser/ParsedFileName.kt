package com.shareef.videoplayersj.data.parser

data class ParsedFileName(
    val rawFileName: String,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val isEpisode: Boolean,
)
