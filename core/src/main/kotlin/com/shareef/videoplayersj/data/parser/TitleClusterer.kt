package com.shareef.videoplayersj.data.parser

import com.shareef.videoplayersj.util.normalizeToTokens
import com.shareef.videoplayersj.util.overlapCoefficient

data class ShowCluster(
    val canonicalTitle: String,
    val episodes: List<ParsedFileName>,
)

private class MutableShowCluster(
    var matchTokens: List<String>,
    val canonicalTitle: String,
    val members: MutableList<ParsedFileName>,
)

/**
 * Groups episode-type parsed filenames into shows using token-overlap similarity on the
 * derived title, so "rick.and.morty...fourchette" and "Rick and Morty...FLUX" cluster together
 * even though the surrounding release junk differs. Pure, offline, no network involved.
 */
object TitleClusterer {

    private const val SIMILARITY_THRESHOLD = 0.75

    fun cluster(parsedFiles: List<ParsedFileName>): List<ShowCluster> {
        val episodeFiles = parsedFiles.filter { it.isEpisode }.sortedBy { it.rawFileName }
        val clusters = mutableListOf<MutableShowCluster>()

        for (file in episodeFiles) {
            val tokens = normalizeToTokens(file.title)
            val bestMatch = clusters
                .map { it to overlapCoefficient(tokens, it.matchTokens) }
                .maxByOrNull { it.second }

            if (tokens.isNotEmpty() && bestMatch != null && bestMatch.second >= SIMILARITY_THRESHOLD) {
                val cluster = bestMatch.first
                cluster.members += file
                // Shrink toward the running intersection so the cluster converges on the stable "core" title.
                val shrunk = cluster.matchTokens.filter { it in tokens }
                if (shrunk.isNotEmpty()) {
                    cluster.matchTokens = shrunk
                }
            } else {
                val title = file.title.ifBlank { file.rawFileName }
                clusters += MutableShowCluster(tokens, title, mutableListOf(file))
            }
        }

        return clusters.map { ShowCluster(it.canonicalTitle, it.members.toList()) }
    }
}
