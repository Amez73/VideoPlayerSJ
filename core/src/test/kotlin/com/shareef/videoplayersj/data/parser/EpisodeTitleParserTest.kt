package com.shareef.videoplayersj.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpisodeTitleParserTest {

    @Test
    fun parsesDottedSceneRelease() {
        val parsed = EpisodeTitleParser.parse("rick.and.morty.s09e02.1080p.web.h264-fourchette[EZTVx.to].mkv")
        assertTrue(parsed.isEpisode)
        assertEquals("rick and morty", parsed.title)
        assertEquals(9, parsed.season)
        assertEquals(2, parsed.episode)
        assertEquals(null, parsed.episodeTitle)
    }

    @Test
    fun parsesSpacedReleaseWithEpisodeTitle() {
        val parsed = EpisodeTitleParser.parse("Rick and Morty S09E10 Field of Dreams 1080p WEB-DL DDP5.1 H264-FLUX[EZTVx.to].mkv")
        assertEquals("Rick and Morty", parsed.title)
        assertEquals(10, parsed.episode)
        assertEquals("Field of Dreams", parsed.episodeTitle)
    }

    @Test
    fun treatsFilesWithoutEpisodeMarkerAsMovies() {
        val parsed = EpisodeTitleParser.parse("Sequoia.2024.1080p.BluRay.x264.mp4")
        assertFalse(parsed.isEpisode)
        assertEquals("Sequoia 2024", parsed.title)
    }

    @Test
    fun clustersDifferentReleaseStylesIntoOneShow() {
        val clusters = TitleClusterer.cluster(
            listOf(
                "rick.and.morty.s09e02.1080p.web.h264-fourchette[EZTVx.to].mkv",
                "Rick and Morty S09E10 Field of Dreams 1080p WEB-DL DDP5.1 H264-FLUX[EZTVx.to].mkv",
                "The.Office.US.S02E03.Office.Olympics.720p.WEB-DL.mp4",
            ).map(EpisodeTitleParser::parse),
        )
        assertEquals(2, clusters.size)
        assertEquals(2, clusters.single { it.canonicalTitle.lowercase().contains("rick") }.episodes.size)
    }
}
