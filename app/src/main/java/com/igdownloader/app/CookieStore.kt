package com.igdownloader.app

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Keeps a private copy of the user's Firefox-exported cookies.txt (Netscape format).
 * yt-dlp reads it directly (--cookies); the image downloader builds a Cookie header from it.
 */
class CookieStore(private val context: Context) {

    val file = File(context.filesDir, "cookies.txt")

    fun hasCookies(): Boolean = file.exists() && file.length() > 0

    data class Cookie(val domain: String, val name: String, val value: String)

    /** Returns a short status message for the UI. Throws on unreadable input. */
    fun import(uri: Uri): String {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Could not open the selected file.")
        val normalized = text.replace("\r\n", "\n")
        val body = if (normalized.lines().firstOrNull { it.isNotBlank() }?.contains("Netscape", true) == true) {
            normalized
        } else {
            "# Netscape HTTP Cookie File\n$normalized"
        }
        file.writeText(body)
        val igCookies = parse().filter { it.domain.contains("instagram.com") }
        if (igCookies.isEmpty()) {
            file.delete()
            error("No instagram.com cookies found in that file.")
        }
        return statusText()
    }

    fun clear() { file.delete() }

    fun statusText(): String {
        if (!hasCookies()) return "No cookies.txt imported"
        val ig = parse().filter { it.domain.contains("instagram.com") }
        val hasSession = ig.any { it.name == "sessionid" }
        return "cookies.txt loaded: ${ig.size} Instagram cookies" +
            if (hasSession) "" else " (no sessionid – you may not be logged in)"
    }

    fun parse(): List<Cookie> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { raw ->
            var line = raw.trim()
            if (line.isEmpty()) return@mapNotNull null
            if (line.startsWith("#HttpOnly_")) line = line.removePrefix("#HttpOnly_")
            else if (line.startsWith("#")) return@mapNotNull null
            val f = line.split('\t')
            if (f.size < 7) return@mapNotNull null
            Cookie(f[0], f[5], f[6])
        }
    }

    fun instagramCookieHeader(): String? {
        val ig = parse().filter { it.domain.contains("instagram.com") }
        if (ig.isEmpty()) return null
        return ig.joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun csrfToken(): String? =
        parse().firstOrNull { it.domain.contains("instagram.com") && it.name == "csrftoken" }?.value
}
