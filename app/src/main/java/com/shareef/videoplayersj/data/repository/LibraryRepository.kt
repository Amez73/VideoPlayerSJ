package com.shareef.videoplayersj.data.repository

import android.content.Context
import android.net.Uri
import com.shareef.videoplayersj.data.local.db.dao.LibraryFolderDao
import com.shareef.videoplayersj.data.local.db.dao.ShowDao
import com.shareef.videoplayersj.data.local.db.dao.VideoDao
import com.shareef.videoplayersj.data.local.db.dao.VideoWithProgress
import com.shareef.videoplayersj.data.local.db.entity.ShowEntity
import com.shareef.videoplayersj.data.local.db.entity.VideoEntity
import com.shareef.videoplayersj.data.parser.EpisodeTitleParser
import com.shareef.videoplayersj.data.parser.ParsedFileName
import com.shareef.videoplayersj.data.parser.TitleClusterer
import com.shareef.videoplayersj.data.saf.SafFolderScanner
import com.shareef.videoplayersj.data.saf.ScannedFile
import com.shareef.videoplayersj.model.LibraryShow
import com.shareef.videoplayersj.model.LibraryVideo
import com.shareef.videoplayersj.util.normalizeToTokens
import com.shareef.videoplayersj.util.overlapCoefficient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.IdentityHashMap

private const val SHOW_MATCH_THRESHOLD = 0.75

private data class ScannedEpisode(
    val file: ScannedFile,
    val folderUri: String,
    val parsed: ParsedFileName,
)

class LibraryRepository(
    private val context: Context,
    private val libraryFolderDao: LibraryFolderDao,
    private val showDao: ShowDao,
    private val videoDao: VideoDao,
) {

    fun observeShows(): Flow<List<LibraryShow>> =
        showDao.getAllWithEpisodeCount().map { list ->
            list.map { LibraryShow(id = it.show.id, canonicalTitle = it.show.canonicalTitle, episodeCount = it.episodeCount) }
        }

    fun observeShowTitle(showId: Long): Flow<String?> =
        showDao.getById(showId).map { it?.canonicalTitle }

    fun observeUngroupedVideos(): Flow<List<LibraryVideo>> =
        videoDao.getUngrouped().map { list -> list.map { it.toLibraryVideo() } }

    fun observeEpisodes(showId: Long): Flow<List<LibraryVideo>> =
        videoDao.getByShowId(showId).map { list -> list.map { it.toLibraryVideo() } }

    fun observeVideo(videoId: Long): Flow<LibraryVideo?> =
        videoDao.getById(videoId).map { it?.toLibraryVideo() }

    /** Re-walks every picked folder, re-parses filenames, re-clusters into shows, and syncs Room.
     * Only ever runs on an explicit "add folder" or manual refresh — never on cold start. */
    suspend fun rescanAll() = withContext(Dispatchers.IO) {
        val folders = libraryFolderDao.getAll().first()

        val allEpisodes = mutableListOf<ScannedEpisode>()
        for (folder in folders) {
            val files = SafFolderScanner.scan(context, Uri.parse(folder.treeUriString))
            for (file in files) {
                allEpisodes += ScannedEpisode(
                    file = file,
                    folderUri = folder.treeUriString,
                    parsed = EpisodeTitleParser.parse(file.displayName),
                )
            }
            libraryFolderDao.updateLastScanned(folder.treeUriString, System.currentTimeMillis())
        }

        // Identity (not structural equality) keyed, since two distinct scanned files could
        // legitimately parse to an identical ParsedFileName.
        val parsedToEpisode = IdentityHashMap<ParsedFileName, ScannedEpisode>()
        allEpisodes.forEach { parsedToEpisode[it.parsed] = it }

        val clusters = TitleClusterer.cluster(allEpisodes.map { it.parsed })
        val existingShows = showDao.getAllSnapshot()

        val videosToUpsert = mutableListOf<VideoEntity>()

        for (cluster in clusters) {
            val clusterTokens = normalizeToTokens(cluster.canonicalTitle)
            val bestShow = existingShows
                .map { show -> show to overlapCoefficient(clusterTokens, show.matchTokens.split(",").filter(String::isNotBlank)) }
                .filter { it.second >= SHOW_MATCH_THRESHOLD }
                .maxByOrNull { it.second }
                ?.first

            val showId = bestShow?.id ?: showDao.insert(
                ShowEntity(canonicalTitle = cluster.canonicalTitle, matchTokens = clusterTokens.joinToString(",")),
            )

            for (parsed in cluster.episodes) {
                val scanned = parsedToEpisode[parsed] ?: continue
                videosToUpsert += buildVideoEntity(scanned, showId)
            }
        }

        for (episode in allEpisodes) {
            if (!episode.parsed.isEpisode) {
                videosToUpsert += buildVideoEntity(episode, showId = null)
            }
        }

        videoDao.upsertAll(videosToUpsert)

        val currentUrisByFolder = allEpisodes.groupBy({ it.folderUri }, { it.file.documentUri.toString() })
        for (folder in folders) {
            videoDao.deleteMissing(folder.treeUriString, currentUrisByFolder[folder.treeUriString] ?: emptyList())
        }
        showDao.deleteOrphaned()
    }

    private suspend fun buildVideoEntity(scanned: ScannedEpisode, showId: Long?): VideoEntity {
        val uriString = scanned.file.documentUri.toString()
        // Look up the existing row (if any) so the entity keeps its id/durationMs — required for
        // @Upsert to UPDATE in place rather than create a new row that orphans watch progress.
        val existing = videoDao.getByUri(uriString)
        return VideoEntity(
            id = existing?.id ?: 0,
            documentUriString = uriString,
            folderTreeUri = scanned.folderUri,
            showId = showId,
            rawFileName = scanned.parsed.rawFileName,
            season = scanned.parsed.season,
            episode = scanned.parsed.episode,
            episodeTitle = scanned.parsed.episodeTitle,
            displayTitle = buildDisplayTitle(scanned.parsed),
            durationMs = existing?.durationMs,
            sizeBytes = scanned.file.sizeBytes,
            lastModified = scanned.file.lastModified,
            addedAt = existing?.addedAt ?: System.currentTimeMillis(),
        )
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

    private fun VideoWithProgress.toLibraryVideo() = LibraryVideo(
        id = video.id,
        documentUriString = video.documentUriString,
        displayTitle = video.displayTitle,
        season = video.season,
        episode = video.episode,
        episodeTitle = video.episodeTitle,
        durationMs = video.durationMs,
        positionMs = progress?.positionMs ?: 0L,
        isFinished = progress?.isFinished ?: false,
    )
}
