package com.shareef.videoplayersj.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "watch_progress",
    foreignKeys = [
        ForeignKey(
            entity = VideoEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WatchProgressEntity(
    @PrimaryKey val videoId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
    val isFinished: Boolean,
)
