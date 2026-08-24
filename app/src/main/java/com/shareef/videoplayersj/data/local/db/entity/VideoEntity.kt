package com.shareef.videoplayersj.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "videos",
    indices = [Index(value = ["documentUriString"], unique = true), Index(value = ["showId"])],
    foreignKeys = [
        ForeignKey(
            entity = ShowEntity::class,
            parentColumns = ["id"],
            childColumns = ["showId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentUriString: String,
    val folderTreeUri: String,
    val showId: Long?,
    val rawFileName: String,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val displayTitle: String,
    /** Null until the file is opened once in the player and ExoPlayer reports it. */
    val durationMs: Long?,
    val sizeBytes: Long,
    val lastModified: Long,
    val addedAt: Long,
)
