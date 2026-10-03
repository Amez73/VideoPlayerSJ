package com.shareef.videoplayersj.desktop.data

import com.shareef.videoplayersj.data.parser.EpisodeTitleParser
import com.shareef.videoplayersj.data.parser.ParsedFileName
import com.shareef.videoplayersj.data.parser.TitleClusterer
import com.shareef.videoplayersj.desktop.model.ContinueWatchingItem
import com.shareef.videoplayersj.desktop.model.LibraryShow
import com.shareef.videoplayersj.desktop.model.LibraryVideo
import com.shareef.videoplayersj.desktop.model.LibraryView
import com.shareef.videoplayersj.util.normalizeToTokens
import com.shareef.videoplayersj.util.overlapCoefficient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.IdentityHashMap

private const val SHOW_MATCH_THRESHOLD = 0.75
private const val CONTINUE_WATCHING_LIMIT = 12
private const val RESUME_MIN_MS = 5_000L
private const val RESUME_MAX_FRACTION = 0.95

private data class ScannedEpisode(
    val file: ScannedFile,
    val folderPath: String,
    val parsed: ParsedFileName,
)

private val episodeOrder = compareBy<LibraryVideo, Int?>(nullsFirst()) { it.season }
    .thenBy(nullsFirst()) { it.episode }
    .thenBy { it.displayTitle.lowercase() }

class LibraryRepository(private val store: LibraryStore, scope: CoroutineScope) {

    val folders: Flow<List<FolderRecord>> = store.data.map { it.folders }

    private val scanMutex = Mutex()
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val view: StateFlow<LibraryView> = store.data
        .map(::buildView)
        .stateIn(scope, SharingStarted.Eagerly, buildView(store.current))

    fun nextEpisodeAfter(videoId: Long): LibraryVideo? {
        val current = view.value
        val showId = current.videosById[videoId]?.showId ?: return null
        val episodes = current.episodesByShow[showId] ?: return null
        val index = episodes.indexOfFirst { it.id == videoId }
        return episodes.getOrNull(index + 1)
    }

    /** Where to start playback: the saved position, unless it's too early or near the end to be useful. */
    fun resumePositionFor(videoId: Long): Long {
        val progress = store.current.progress.firstOrNull { it.videoId == videoId } ?: return 0L
        if (progress.isFinished || progress.durationMs <= 0L) return 0L
        val resumeCeiling = (progress.durationMs * RESUME_MAX_FRACTION).toLong()
        return if (progress.positionMs in RESUME_MIN_MS until resumeCeiling) progress.positionMs else 0L
    }

    suspend fun addFolder(dir: File) {
        val path = dir.absoluteFile.normalize().path
        store.update { data ->
            if (data.folders.any { it.path == path }) {
                data
            } else {
                data.copy(
                    folders = data.folders + FolderRecord(
                        path = path,
                        displayName = dir.name.ifBlank { path },
                        addedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
        rescanAll()
    }

    suspend fun removeFolder(path: String) {
        store.update { data ->
            val videos = data.videos.filter { it.folderPath != path }
            data.copy(folders = data.folders.filter { it.path != path }).withVideos(videos)
        }
    }

    suspend fun saveProgress(videoId: Long, positionMs: Long, durationMs: Long, isFinished: Boolean) {
        store.update { data ->
            if (data.videos.none { it.id == videoId }) return@update data
            val record = ProgressRecord(
                videoId = videoId,
                positionMs = positionMs,
                durationMs = durationMs,
                lastWatchedAt = System.currentTimeMillis(),
                isFinished = isFinished,
            )
            data.copy(
                progress = data.progress.filter { it.videoId != videoId } + record,
                videos = if (durationMs > 0) {
                    data.videos.map { if (it.id == videoId) it.copy(durationMs = durationMs) else it }
                } else {
                    data.videos
                },
            )
        }
    }

    /** Fills in a length found before the video was ever played; never overrides a known one. */
    suspend fun recordDuration(videoId: Long, durationMs: Long) {
        if (durationMs <= 0) return
        store.update { data ->
            data.copy(videos = data.videos.map { if (it.id == videoId && it.durationMs == null) it.copy(durationMs = durationMs) else it })
        }
    }

    suspend fun setWatched(videoId: Long, watched: Boolean) {
        store.update { data ->
            val video = data.videos.firstOrNull { it.id == videoId } ?: return@update data
            val duration = video.durationMs ?: 0L
            val record = ProgressRecord(
                videoId = videoId,
                positionMs = if (watched) duration else 0L,
                durationMs = duration,
                lastWatchedAt = System.currentTimeMillis(),
                isFinished = watched,
            )
            data.copy(progress = data.progress.filter { it.videoId != videoId } + record)
        }
    }

    /**
     * Re-walks every folder, re-parses filenames, re-clusters into shows and syncs the store.
     * Existing videos keep their ids (and so their watch progress) by matching on path, and new
     * clusters are matched against existing shows by title tokens, as on Android.
     *
     * A folder that can't be reached right now (an unplugged external drive, a sleeping NAS) is
     * left exactly as it was — treating it as empty would throw away its whole watch history.
     */
    suspend fun rescanAll() {
        scanMutex.withLock {
            _isScanning.update { true }
            try {
                rescanFolders()
            } finally {
                _isScanning.update { false }
            }
        }
    }

    private suspend fun rescanFolders() {
        val folders = store.current.folders
        val scannedByFolder = folders.associate { folder ->
            val root = File(folder.path)
            folder.path to if (root.isDirectory) FolderScanner.scan(root) else null
        }
        val now = System.currentTimeMillis()

        store.update { data ->
            val reachable = scannedByFolder.filterValues { it != null }.keys
            val seenPaths = HashSet<String>()
            val entries = folders.filter { it.path in reachable }.flatMap { folder ->
                scannedByFolder.getValue(folder.path).orEmpty()
                    // Nested library folders would otherwise list the same file twice.
                    .filter { seenPaths.add(it.path) }
                    .map { ScannedEpisode(it, folder.path, EpisodeTitleParser.parse(it.displayName)) }
            }

            val existingByPath = data.videos.associateBy { it.path }
            var nextShowId = data.nextShowId
            var nextVideoId = data.nextVideoId
            val newShows = mutableListOf<ShowRecord>()
            val scannedVideos = mutableListOf<VideoRecord>()

            fun buildVideo(entry: ScannedEpisode, showId: Long?): VideoRecord {
                val existing = existingByPath[entry.file.path]
                return VideoRecord(
                    id = existing?.id ?: nextVideoId++,
                    path = entry.file.path,
                    folderPath = entry.folderPath,
                    showId = showId,
                    rawFileName = entry.parsed.rawFileName,
                    season = entry.parsed.season,
                    episode = entry.parsed.episode,
                    episodeTitle = entry.parsed.episodeTitle,
                    displayTitle = buildDisplayTitle(entry.parsed),
                    durationMs = existing?.durationMs,
                    sizeBytes = entry.file.sizeBytes,
                    lastModified = entry.file.lastModified,
                    addedAt = existing?.addedAt ?: now,
                )
            }

            // Identity (not structural equality) keyed, since two distinct files could
            // legitimately parse to an identical ParsedFileName.
            val parsedToEntry = IdentityHashMap<ParsedFileName, ScannedEpisode>()
            entries.forEach { parsedToEntry[it.parsed] = it }

            for (cluster in TitleClusterer.cluster(entries.map { it.parsed })) {
                val clusterTokens = normalizeToTokens(cluster.canonicalTitle)
                val bestShow = data.shows
                    .map { show -> show to overlapCoefficient(clusterTokens, show.matchTokens) }
                    .filter { it.second >= SHOW_MATCH_THRESHOLD }
                    .maxByOrNull { it.second }
                    ?.first
                val showId = bestShow?.id ?: nextShowId++.also { id ->
                    newShows += ShowRecord(id, cluster.canonicalTitle, clusterTokens)
                }
                for (parsed in cluster.episodes) {
                    val entry = parsedToEntry[parsed] ?: continue
                    scannedVideos += buildVideo(entry, showId)
                }
            }
            entries.filter { !it.parsed.isEpisode }.forEach { scannedVideos += buildVideo(it, showId = null) }

            val untouched = data.videos.filter { it.folderPath !in reachable }
            data.copy(
                folders = data.folders.map { if (it.path in reachable) it.copy(lastScannedAt = now) else it },
                shows = data.shows + newShows,
                nextShowId = nextShowId,
                nextVideoId = nextVideoId,
            ).withVideos(untouched + scannedVideos)
        }
    }

    private fun buildDisplayTitle(parsed: ParsedFileName): String {
        if (!parsed.isEpisode) return parsed.title.ifBlank { parsed.rawFileName }
        val seasonEpisode = if (parsed.season != null && parsed.episode != null) {
            "S%02dE%02d".format(parsed.season, parsed.episode)
        } else {
            parsed.episode?.let { "Episode $it" }.orEmpty()
        }
        return listOfNotNull(seasonEpisode.ifBlank { null }, parsed.episodeTitle)
            .joinToString(" - ")
            .ifBlank { parsed.title.ifBlank { parsed.rawFileName } }
    }

    private fun buildView(data: LibraryData): LibraryView {
        val progressById = data.progress.associateBy { it.videoId }
        val videos = data.videos.map { record ->
            val progress = progressById[record.id]
            LibraryVideo(
                id = record.id,
                path = record.path,
                showId = record.showId,
                displayTitle = record.displayTitle,
                season = record.season,
                episode = record.episode,
                episodeTitle = record.episodeTitle,
                durationMs = record.durationMs ?: progress?.durationMs?.takeIf { it > 0 },
                positionMs = progress?.positionMs ?: 0L,
                isFinished = progress?.isFinished ?: false,
                addedAt = record.addedAt,
            )
        }
        val videosById = videos.associateBy { it.id }
        val episodesByShow = videos.filter { it.showId != null }
            .groupBy { it.showId!! }
            .mapValues { (_, list) -> list.sortedWith(episodeOrder) }

        val shows = data.shows.mapNotNull { show ->
            val episodes = episodesByShow[show.id] ?: return@mapNotNull null
            LibraryShow(
                id = show.id,
                canonicalTitle = show.canonicalTitle,
                episodeCount = episodes.size,
                watchedCount = episodes.count { it.isFinished },
                thumbnailVideo = episodes.firstOrNull(),
            )
        }.sortedBy { it.canonicalTitle.lowercase() }

        val showTitles = data.shows.associate { it.id to it.canonicalTitle }
        val continueWatching = data.progress
            .filter { !it.isFinished && it.positionMs > 0 }
            .sortedByDescending { it.lastWatchedAt }
            .mapNotNull { progress ->
                val video = videosById[progress.videoId] ?: return@mapNotNull null
                ContinueWatchingItem(video, video.showId?.let(showTitles::get))
            }
            .take(CONTINUE_WATCHING_LIMIT)

        return LibraryView(
            continueWatching = continueWatching,
            shows = shows,
            movies = videos.filter { it.showId == null }.sortedBy { it.displayTitle.lowercase() },
            episodesByShow = episodesByShow,
            videosById = videosById,
        )
    }
}

/** Replaces the video list and drops anything that pointed at a video or show no longer present. */
private fun LibraryData.withVideos(videos: List<VideoRecord>): LibraryData {
    val videoIds = videos.mapTo(HashSet()) { it.id }
    val showIds = videos.mapNotNullTo(HashSet()) { it.showId }
    return copy(
        videos = videos,
        shows = shows.filter { it.id in showIds },
        progress = progress.filter { it.videoId in videoIds },
    )
}
