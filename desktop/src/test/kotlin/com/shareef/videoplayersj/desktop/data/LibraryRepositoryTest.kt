package com.shareef.videoplayersj.desktop.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryRepositoryTest {

    private val root: File = Files.createTempDirectory("vpsj-test").toFile()
    private val media = File(root, "media").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        root.deleteRecursively()
    }

    private fun touch(relativePath: String) = File(media, relativePath).apply {
        parentFile.mkdirs()
        writeText("x")
    }

    private fun newRepository() = LibraryRepository(LibraryStore(File(root, "library.json")), scope)

    @Test
    fun groupsEpisodesIntoShowsAndKeepsProgressAcrossRescans() = runBlocking {
        touch("Rick/rick.and.morty.s09e01.1080p.web.h264-fourchette[EZTVx.to].mkv")
        touch("Rick/Rick and Morty S09E10 Field of Dreams 1080p WEB-DL DDP5.1 H264-FLUX[EZTVx.to].mkv")
        touch("Movies/Sequoia.2024.1080p.BluRay.x264.mp4")
        touch("Movies/notes.txt")

        val repository = newRepository()
        repository.addFolder(media)

        val view = repository.view.value
        assertEquals(1, view.shows.size)
        assertEquals(2, view.shows.single().episodeCount)
        assertEquals(listOf("Sequoia 2024"), view.movies.map { it.displayTitle })

        val firstEpisode = view.episodesByShow.values.single().first()
        assertEquals(1, firstEpisode.episode)
        repository.saveProgress(firstEpisode.id, positionMs = 60_000, durationMs = 1_200_000, isFinished = false)
        assertEquals(60_000, repository.resumePositionFor(firstEpisode.id))

        touch("Rick/rick.and.morty.s09e02.1080p.web.h264-fourchette[EZTVx.to].mkv")
        repository.rescanAll()

        val rescanned = repository.view.value
        assertEquals(3, rescanned.shows.single().episodeCount)
        assertEquals(60_000, rescanned.videosById.getValue(firstEpisode.id).positionMs)
        assertEquals(firstEpisode.id, rescanned.continueWatching.single().video.id)
        assertEquals(2, repository.nextEpisodeAfter(firstEpisode.id)?.episode)

        // Reloading from disk gives the same library back.
        assertEquals(rescanned.videosById.keys, newRepository().view.value.videosById.keys)
    }

    @Test
    fun unreachableFolderKeepsItsVideos() = runBlocking {
        touch("Show.S01E01.mkv")
        val repository = newRepository()
        repository.addFolder(media)
        assertEquals(1, repository.view.value.videosById.size)

        val moved = File(root, "unplugged")
        assertTrue(media.renameTo(moved))
        repository.rescanAll()
        assertEquals(1, repository.view.value.videosById.size)
    }

    @Test
    fun removingFolderDropsItsVideosAndShows() = runBlocking {
        touch("Show.S01E01.mkv")
        val repository = newRepository()
        repository.addFolder(media)
        repository.removeFolder(media.absoluteFile.normalize().path)
        assertTrue(repository.view.value.isEmpty)
    }
}
