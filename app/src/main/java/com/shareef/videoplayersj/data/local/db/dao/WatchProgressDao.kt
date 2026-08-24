package com.shareef.videoplayersj.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.shareef.videoplayersj.data.local.db.entity.WatchProgressEntity

@Dao
interface WatchProgressDao {

    @Upsert
    suspend fun upsert(progress: WatchProgressEntity)

    @Query("SELECT * FROM watch_progress WHERE videoId = :videoId")
    suspend fun getForVideo(videoId: Long): WatchProgressEntity?
}
