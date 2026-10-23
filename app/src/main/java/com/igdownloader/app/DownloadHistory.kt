package com.igdownloader.app

import android.content.Context
import java.io.File

/**
 * Port of instagram_download_history.log: one "<url>|<format identifier>" line per completed download.
 */
class DownloadHistory(context: Context) {

    val file = File(context.filesDir, "instagram_download_history.log")

    @Synchronized
    fun contains(url: String, identifier: String): Boolean {
        if (!file.exists()) return false
        val entry = "$url|$identifier"
        return file.readLines().any { it.trim() == entry }
    }

    @Synchronized
    fun append(url: String, identifier: String) {
        file.appendText("$url|$identifier\n", Charsets.UTF_8)
    }
}
