package com.shareef.videoplayersj.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shows")
data class ShowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val canonicalTitle: String,
    /** Comma-joined normalized tokens, used to re-match this show against future rescans. */
    val matchTokens: String,
)
