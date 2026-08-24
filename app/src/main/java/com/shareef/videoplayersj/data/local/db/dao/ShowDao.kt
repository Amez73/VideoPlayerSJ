package com.shareef.videoplayersj.data.local.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import com.shareef.videoplayersj.data.local.db.entity.ShowEntity
import kotlinx.coroutines.flow.Flow

data class ShowWithEpisodeCount(
    @Embedded val show: ShowEntity,
    val episodeCount: Int,
    /** Earliest episode, used as the show's thumbnail. Null if the show somehow has no episodes. */
    val thumbnailVideoId: Long?,
    val thumbnailUri: String?,
)

@Dao
interface ShowDao {

    @Insert
    suspend fun insert(show: ShowEntity): Long

    @Query("SELECT * FROM shows WHERE id = :showId")
    fun getById(showId: Long): Flow<ShowEntity?>

    @Query(
        """
        SELECT shows.*,
            COUNT(videos.id) AS episodeCount,
            (
                SELECT v.id FROM videos v
                WHERE v.showId = shows.id ORDER BY v.season, v.episode LIMIT 1
            ) AS thumbnailVideoId,
            (
                SELECT v.documentUriString FROM videos v
                WHERE v.showId = shows.id ORDER BY v.season, v.episode LIMIT 1
            ) AS thumbnailUri
        FROM shows
        LEFT JOIN videos ON videos.showId = shows.id
        GROUP BY shows.id
        ORDER BY shows.canonicalTitle
        """,
    )
    fun getAllWithEpisodeCount(): Flow<List<ShowWithEpisodeCount>>

    @Query("SELECT * FROM shows")
    suspend fun getAllSnapshot(): List<ShowEntity>

    @Query("DELETE FROM shows WHERE id NOT IN (SELECT DISTINCT showId FROM videos WHERE showId IS NOT NULL)")
    suspend fun deleteOrphaned()
}
