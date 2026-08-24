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

    @Query("UPDATE videos SET durationMs = :durationMs WHERE id = :videoId")
    suspend fun updateDuration(videoId: Long, durationMs: Long)

    @Query("DELETE FROM videos WHERE folderTreeUri = :folderTreeUri AND documentUriString NOT IN (:currentUris)")
    suspend fun deleteMissing(folderTreeUri: String, currentUris: List<String>)
}
