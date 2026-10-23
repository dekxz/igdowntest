package com.igdownloader.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/**
 * instagram_download_history.log, stored next to the downloaded media in Downloads/IGDownloader.
 * One "<url>|<format identifier>" line per completed download.
 *
 * Note: Android only lets an app read files it created itself. If you uninstall the app, a log left
 * in the folder can no longer be read by a fresh install (it starts a new log instead).
 */
class DownloadHistory(private val context: Context) {

    companion object {
        const val NAME = "instagram_download_history.log"
    }

    fun uri(): Uri? = MediaExporter.find(context, NAME)

    private fun readText(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()

    @Synchronized
    fun contains(url: String, identifier: String): Boolean {
        val uri = uri() ?: return false
        val entry = "$url|$identifier"
        return try {
            readText(uri).lines().any { it.trim() == entry }
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    fun append(url: String, identifier: String) {
        val line = "$url|$identifier\n"
        val resolver = context.contentResolver
        val existing = uri()
        if (existing == null) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, NAME)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/${MediaExporter.FOLDER}")
            }
            val created = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(created)?.use { it.write(line.toByteArray(Charsets.UTF_8)) }
        } else {
            val old = try { readText(existing) } catch (e: Exception) { "" }
            // "wt" = truncate and rewrite the whole (small) file
            resolver.openOutputStream(existing, "wt")?.use { it.write((old + line).toByteArray(Charsets.UTF_8)) }
        }
    }
}
