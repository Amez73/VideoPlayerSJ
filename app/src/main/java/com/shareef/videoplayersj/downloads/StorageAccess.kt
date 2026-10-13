package com.shareef.videoplayersj.downloads

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

/**
 * libtorrent writes through ordinary file paths, which Android's folder picker doesn't hand out.
 * So downloads need "all files access", and a picked folder is translated back to its real path.
 */
object StorageAccess {

    fun hasFileAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    /** Android 11+: the settings page with the "Allow access to manage all files" switch. */
    fun fileAccessSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))

    fun defaultDownloadDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /**
     * The real path behind a folder picked with the system folder picker, or null for folders
     * that aren't on the device's own storage (cloud drives and the like).
     */
    fun pathForTree(treeUri: Uri): File? {
        if (treeUri.authority != EXTERNAL_STORAGE_AUTHORITY) return null
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val volume = documentId.substringBefore(':')
        val relative = documentId.substringAfter(':', "")
        val root = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory()
        } else {
            File("/storage/$volume")
        }
        return if (relative.isEmpty()) root else File(root, relative)
    }

    /** The folder picker's starting point for [dir], so "add to library" opens right on it. */
    fun treeUriFor(dir: File): Uri? {
        val root = Environment.getExternalStorageDirectory().absolutePath
        val path = dir.absolutePath
        val documentId = when {
            path == root -> "primary:"
            path.startsWith("$root/") -> "primary:" + path.removePrefix("$root/")
            else -> return null
        }
        return DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, documentId)
    }
}
