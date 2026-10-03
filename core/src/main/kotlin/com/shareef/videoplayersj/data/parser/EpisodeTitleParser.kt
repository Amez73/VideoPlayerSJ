package com.shareef.videoplayersj.data.parser

/**
 * Parses scene/torrent-release style filenames, e.g.:
 *   "rick.and.morty.s09e02.1080p.web.h264-fourchette[EZTVx.to].mkv"
 *   "Rick and Morty S09E10 Field of Dreams 1080p WEB-DL DDP5.1 H264-FLUX[EZTVx.to].mkv"
 *
 * Strategy: find the SxxEyy marker first (the one reliably consistent signal), derive the
 * title from everything before it, and trim known release-tag junk from the edges rather
 * than assuming a clean delimiter between title and junk.
 */
object EpisodeTitleParser {

    private val bracketedGroup = Regex("""\[[^\[\]]*]|\([^()]*\)""")
    private val sxxexxPattern = Regex("""(?i)\bS(\d{1,2})E(\d{1,3})\b""")
    private val altXEpisodePattern = Regex("""(?i)\b(\d{1,2})x(\d{2,3})\b""")
    private val bareEpisodePattern = Regex("""(?i)\bepisode\s*(\d{1,3})\b""")
    private val resolutionPattern = Regex("""^\d{3,4}p$""")
    private val numericPattern = Regex("""^\d+$""")

    // Source / codec / audio release tags. A trailing "-releasegroup" suffix on any of these
    // (e.g. "h264-fourchette") is also treated as junk via the hyphen check below.
    private val junkKeywords = setOf(
        "480p", "576p", "720p", "1080p", "1440p", "2160p", "4k",
        "web", "webdl", "web-dl", "webrip", "bluray", "blu-ray", "brrip", "bdrip",
        "hdtv", "dvdrip", "hdrip", "pdtv", "hdcam", "cam",
        "h264", "h265", "hevc", "avc", "x264", "x265", "xvid", "divx",
        "aac", "ac3", "dts", "ddp5", "dd5", "ddp2", "dd2", "atmos", "truehd",
    )

    fun parse(rawFileName: String): ParsedFileName {
        val nameWithoutExt = rawFileName.substringBeforeLast('.', rawFileName)

        sxxexxPattern.find(nameWithoutExt)?.let { match ->
            return buildEpisodeResult(
                rawFileName, nameWithoutExt, match,
                season = match.groupValues[1].toIntOrNull(),
                episode = match.groupValues[2].toIntOrNull(),
            )
        }
        altXEpisodePattern.find(nameWithoutExt)?.let { match ->
            return buildEpisodeResult(
                rawFileName, nameWithoutExt, match,
                season = match.groupValues[1].toIntOrNull(),
                episode = match.groupValues[2].toIntOrNull(),
            )
        }
        bareEpisodePattern.find(nameWithoutExt)?.let { match ->
            return buildEpisodeResult(
                rawFileName, nameWithoutExt, match,
                season = null,
                episode = match.groupValues[1].toIntOrNull(),
            )
        }

        // Movie / one-off fallback: no reliable episode marker found.
        return ParsedFileName(
            rawFileName = rawFileName,
            title = cleanTitle(nameWithoutExt),
            season = null,
            episode = null,
            episodeTitle = null,
            isEpisode = false,
        )
    }

    private fun buildEpisodeResult(
        rawFileName: String,
        nameWithoutExt: String,
        match: MatchResult,
        season: Int?,
        episode: Int?,
    ): ParsedFileName {
        val titlePrefix = nameWithoutExt.substring(0, match.range.first)
        val afterMarker = nameWithoutExt.substring((match.range.last + 1).coerceAtMost(nameWithoutExt.length))
        return ParsedFileName(
            rawFileName = rawFileName,
            title = cleanTitle(titlePrefix),
            season = season,
            episode = episode,
            episodeTitle = extractEpisodeTitle(afterMarker),
            isEpisode = true,
        )
    }

    private fun tokenize(raw: String): List<String> {
        val noBrackets = bracketedGroup.replace(raw, " ")
        val spaced = noBrackets.replace('.', ' ').replace('_', ' ')
        return spaced.split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    /** Junk check used for trimming the title prefix — deliberately excludes bare numbers,
     * since a numeric token could legitimately be part of a title (e.g. "The 100"). */
    private fun isJunkTag(token: String): Boolean {
        val normalized = token.trim('-', ',', '.').lowercase()
        if (normalized.isEmpty()) return true
        if (normalized in junkKeywords) return true
        if (resolutionPattern.matches(normalized)) return true
        if (normalized.contains('-')) {
            val codecPart = normalized.substringBefore('-')
            if (codecPart in junkKeywords) return true
        }
        return false
    }

    /** Stricter junk check used when scanning forward for an episode title, where stopping
     * early on a stray number (e.g. an audio channel count fragment) is low-cost. */
    private fun isJunkTagOrNumeric(token: String): Boolean {
        val normalized = token.trim('-', ',', '.').lowercase()
        return isJunkTag(token) || numericPattern.matches(normalized)
    }

    private fun cleanTitle(raw: String): String {
        val tokens = tokenize(raw)
        var trimmed = tokens
        while (trimmed.isNotEmpty() && isJunkTag(trimmed.last())) {
            trimmed = trimmed.dropLast(1)
        }
        val candidate = trimmed.joinToString(" ").trim()
        return candidate.ifBlank { tokens.joinToString(" ").trim().ifBlank { raw.trim() } }
    }

    private fun extractEpisodeTitle(afterMarker: String): String? {
        val kept = mutableListOf<String>()
        for (token in tokenize(afterMarker)) {
            if (isJunkTagOrNumeric(token)) break
            kept += token
        }
        return kept.joinToString(" ").trim().ifBlank { null }
    }
}
