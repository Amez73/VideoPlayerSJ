package com.shareef.videoplayersj.data.local.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import com.shareef.videoplayersj.data.local.db.entity.VideoEntity
import com.shareef.videoplayersj.data.local.db.entity.WatchProgressEntity
import kotlinx.coroutines.flow.Flow

data class VideoWithProgress(
    @Embedded val video: VideoEntity,
    @Relation(parentColumn = "id", entityColumn = "videoId")
    val progress: WatchProgressEntity?,
)

/** Adds the owning show's title, so "Continue watching" can name the show rather than
 * showing a bare "S01E02" with no context about what it belongs to. */
data class VideoWithProgressAndShow(
    @Embedded val video: VideoEntity,
    @Relation(parentColumn = "id", entityColumn = "videoId")
    val progress: WatchProgressEntity?,
    val showTitle: String?,
)

@Dao
interface VideoDao {

    // @Upsert (not INSERT OR REPLACE): a unique-index conflict under REPLACE deletes the old
    // row first, cascading away its watch_progress via the FK before the "same" row comes back
    // with a new id. Upsert does a real in-place UPDATE when the primary key matches, which the
    // repository ensures by looking up the existing row's id via getByUri before building the entity.
    @Upsert
    suspend fun upsertAll(videos: List<VideoEntity>)

    @Query("SELECT * FROM videos WHERE documentUriString = :uri LIMIT 1")
    suspend fun getByUri(uri: String): VideoEntity?

    @Transaction
    @Query("SELECT * FROM videos WHERE showId = :showId ORDER BY season, episode")
    fun getByShowId(showId: Long): Flow<List<VideoWithProgress>>

    @Transaction
    @Query("SELECT * FROM videos WHERE showId IS NULL ORDER BY displayTitle")
    fun getUngrouped(): Flow<List<VideoWithProgress>>

    @Transaction
    @Query("SELECT * FROM videos WHERE id = :videoId LIMIT 1")
    fun getById(videoId: Long): Flow<VideoWithProgress?>

    // Started but not finished, most recently watched first — the "pick up where you left off" list.
    @Transaction
    @Query(
        """
        SELECT videos.*, shows.canonicalTitle AS showTitle
        FROM videos
        INNER JOIN watch_progress ON watch_progress.videoId = videos.id
        LEFT JOIN shows ON shows.id = videos.showId
        WHERE watch_progress.isFinished = 0 AND watch_progress.positionMs > 0
        ORDER BY watch_progress.lastWatchedAt DESC
        LIMIT :limit
        """,
    )
    fun getContinueWatching(limit: Int): Flow<List<VideoWithProgressAndShow>>

    @Query("UPDATE videos SET durationMs = :durationMs WHERE id = :videoId")
    suspend fun updateDuration(videoId: Long, durationMs: Long)

    @Query("DELETE FROM videos WHERE folderTreeUri = :folderTreeUri AND documentUriString NOT IN (:currentUris)")
    suspend fun deleteMissing(folderTreeUri: String, currentUris: List<String>)
}
