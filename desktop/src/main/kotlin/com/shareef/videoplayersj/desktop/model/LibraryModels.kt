package com.shareef.videoplayersj.desktop.model

data class LibraryVideo(
    val id: Long,
    val path: String,
    val showId: Long?,
    val displayTitle: String,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val durationMs: Long?,
    val positionMs: Long,
    val isFinished: Boolean,
    val addedAt: Long,
) {
    /** Fraction watched, or null when there's nothing worth drawing a progress bar for. */
    val progressFraction: Float?
        get() {
            val duration = durationMs ?: return null
            if (duration <= 0 || positionMs <= 0) return null
            return (positionMs.toFloat() / duration).coerceIn(0f, 1f).takeIf { it > 0.01f }
        }

    val episodeLabel: String
        get() = buildString {
            if (season != null && episode != null) {
                append("S%02dE%02d".format(season, episode))
            } else if (episode != null) {
                append("Episode $episode")
            }
            if (!episodeTitle.isNullOrBlank()) {
                if (isNotEmpty()) append(" · ")
                append(episodeTitle)
            }
        }.ifBlank { displayTitle }
}

data class LibraryShow(
    val id: Long,
    val canonicalTitle: String,
    val episodeCount: Int,
    val watchedCount: Int,
    /** First episode, used as the show's thumbnail source. */
    val thumbnailVideo: LibraryVideo?,
)

/** A part-watched video plus the show it belongs to (null for standalone movies). */
data class ContinueWatchingItem(
    val video: LibraryVideo,
    val showTitle: String?,
)

data class LibraryView(
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val shows: List<LibraryShow> = emptyList(),
    val movies: List<LibraryVideo> = emptyList(),
    val episodesByShow: Map<Long, List<LibraryVideo>> = emptyMap(),
    val videosById: Map<Long, LibraryVideo> = emptyMap(),
) {
    val isEmpty: Boolean get() = shows.isEmpty() && movies.isEmpty()
}
