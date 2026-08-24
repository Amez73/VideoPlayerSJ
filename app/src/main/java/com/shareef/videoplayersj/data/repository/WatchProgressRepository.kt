package com.shareef.videoplayersj.data.repository

import com.shareef.videoplayersj.data.local.db.dao.VideoDao
import com.shareef.videoplayersj.data.local.db.dao.WatchProgressDao
import com.shareef.videoplayersj.data.local.db.entity.WatchProgressEntity

class WatchProgressRepository(
    private val watchProgressDao: WatchProgressDao,
    private val videoDao: VideoDao,
) {
    suspend fun getProgress(videoId: Long): WatchProgressEntity? = watchProgressDao.getForVideo(videoId)

    suspend fun saveProgress(videoId: Long, positionMs: Long, durationMs: Long, isFinished: Boolean) {
        watchProgressDao.upsert(
            WatchProgressEntity(
                videoId = videoId,
                positionMs = positionMs,
                durationMs = durationMs,
                lastWatchedAt = System.currentTimeMillis(),
                isFinished = isFinished,
            ),
        )
        if (durationMs > 0) {
            videoDao.updateDuration(videoId, durationMs)
        }
    }
}
