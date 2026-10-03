package com.shareef.videoplayersj.desktop.ui.common

import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

private val isWindows = System.getProperty("os.name").startsWith("Windows")

/** Shows the native-looking folder chooser and returns the picked folder, or null if cancelled. */
fun chooseFolder(title: String = "Choose a folder of videos"): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/** Opens the file's folder in Explorer/Finder with the file selected where the OS supports it. */
fun revealInFileManager(path: String) {
    val file = File(path)
    runCatching {
        when {
            isWindows -> ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
            Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR) -> Desktop.getDesktop().browseFileDirectory(file)
            else -> Desktop.getDesktop().open(file.parentFile)
        }
    }
}

fun openInBrowser(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}
