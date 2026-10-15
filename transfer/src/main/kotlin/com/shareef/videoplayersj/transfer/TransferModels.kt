package com.shareef.videoplayersj.transfer

import kotlinx.serialization.Serializable
import java.io.InputStream

/** Another copy of VideoPlayerSJ seen on the same network. */
data class Peer(
    val id: String,
    val name: String,
    val platform: String,
    val host: String,
    val port: Int,
    val lastSeen: Long,
)

/** A file to send. [open] is called once, when the receiver has accepted. */
class OutgoingFile(val name: String, val size: Long, val open: () -> InputStream)

@Serializable
data class FileInfo(val name: String, val size: Long)

/** Files another device wants to send here, waiting for the user to accept or decline. */
data class IncomingOffer(
    val id: String,
    val fromName: String,
    val fromPlatform: String,
    val files: List<FileInfo>,
) {
    val totalBytes: Long get() = files.sumOf { it.size }
}

enum class TransferDirection { Sending, Receiving }

enum class TransferState { WaitingForAccept, Running, Done, Declined, Failed, Cancelled }

data class Transfer(
    val id: String,
    val direction: TransferDirection,
    val peerName: String,
    val fileCount: Int,
    val currentFile: String?,
    val bytesDone: Long,
    val totalBytes: Long,
    /** Bytes per second, averaged over the transfer so far. */
    val rate: Long,
    val state: TransferState,
    val error: String? = null,
    /** Receiving only: the folder the files went into. */
    val savedTo: String? = null,
) {
    val isActive: Boolean get() = state == TransferState.WaitingForAccept || state == TransferState.Running
    val progress: Float get() = if (totalBytes > 0) (bytesDone.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/** Emitted when files have finished arriving, e.g. to rescan the library. */
data class ReceivedFiles(val dir: String, val names: List<String>)

@Serializable
internal data class Announcement(val id: String, val name: String, val platform: String, val port: Int)

@Serializable
internal data class OfferMessage(val senderId: String, val senderName: String, val senderPlatform: String, val files: List<FileInfo>)

@Serializable
internal data class ReplyMessage(val accepted: Boolean, val reason: String? = null)

@Serializable
internal data class DoneMessage(val ok: Boolean)
