package com.shareef.videoplayersj.desktop.torrent

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Makes VideoPlayerSJ the app that opens magnet links on Windows. Everything goes under the
 * current user's registry hive, so no admin rights are needed.
 */
object MagnetHandler {

    private const val PROG_ID = "VideoPlayerSJ.Magnet"
    private const val USER_CHOICE_KEY = "HKCU\\Software\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\magnet\\UserChoice"

    private val isWindows = System.getProperty("os.name").startsWith("Windows")

    /** The installed app's exe; null when running from a build, where there's nothing to register. */
    private val exePath: String? = System.getProperty("jpackage.app-path")?.takeIf { File(it).isFile }

    val isSupported: Boolean get() = isWindows && exePath != null

    fun isDefault(): Boolean {
        val exe = exePath ?: return false
        val choice = queryValue(USER_CHOICE_KEY, "ProgId")
        if (choice != null) return choice == PROG_ID
        val command = queryValue("HKCU\\Software\\Classes\\magnet\\shell\\open\\command", null) ?: return false
        return command.contains(exe, ignoreCase = true)
    }

    /**
     * Registers the handler. Returns true when that's enough; false when Windows has a choice of
     * app saved for magnet links, which only the user can change, in which case Default Apps is
     * opened for them to pick VideoPlayerSJ.
     */
    fun register(): Boolean {
        val exe = exePath ?: return false
        val command = "\"$exe\" \"%1\""
        val reg = buildString {
            appendLine("Windows Registry Editor Version 5.00")
            appendLine()
            for (key in listOf("HKEY_CURRENT_USER\\Software\\Classes\\$PROG_ID", "HKEY_CURRENT_USER\\Software\\Classes\\magnet")) {
                appendLine("[$key]")
                appendLine("@=${quote("URL:Magnet link")}")
                appendLine("\"URL Protocol\"=\"\"")
                appendLine()
                appendLine("[$key\\DefaultIcon]")
                appendLine("@=${quote("$exe,0")}")
                appendLine()
                appendLine("[$key\\shell\\open\\command]")
                appendLine("@=${quote(command)}")
                appendLine()
            }
            appendLine("[HKEY_CURRENT_USER\\Software\\VideoPlayerSJ\\Capabilities]")
            appendLine("\"ApplicationName\"=\"VideoPlayerSJ\"")
            appendLine("\"ApplicationDescription\"=\"Plays your videos and downloads magnet links\"")
            appendLine()
            appendLine("[HKEY_CURRENT_USER\\Software\\VideoPlayerSJ\\Capabilities\\URLAssociations]")
            appendLine("\"magnet\"=${quote(PROG_ID)}")
            appendLine()
            appendLine("[HKEY_CURRENT_USER\\Software\\RegisteredApplications]")
            appendLine("\"VideoPlayerSJ\"=${quote("Software\\VideoPlayerSJ\\Capabilities")}")
        }
        val file = File.createTempFile("videoplayersj-magnet", ".reg")
        try {
            // regedit's format: UTF-16LE with a byte order mark, CRLF line endings.
            file.writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + reg.replace("\n", "\r\n").toByteArray(StandardCharsets.UTF_16LE))
            run("reg", "import", file.absolutePath)
        } finally {
            file.delete()
        }
        if (isDefault()) return true
        run("cmd", "/c", "start", "", "ms-settings:defaultapps?registeredAppUser=VideoPlayerSJ")
        return false
    }

    private fun quote(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun queryValue(key: String, name: String?): String? {
        val args = if (name == null) listOf("reg", "query", key, "/ve") else listOf("reg", "query", key, "/v", name)
        val output = run(*args.toTypedArray()) ?: return null
        val line = output.lines().firstOrNull { it.contains("REG_SZ") } ?: return null
        return line.substringAfter("REG_SZ").trim()
    }

    private fun run(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroy()
        output.takeIf { process.exitValue() == 0 }
    }.getOrNull()
}
