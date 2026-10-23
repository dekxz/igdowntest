package com.igdownloader.app

import java.util.UUID

object Utils {
    /** Same pattern as the desktop app's clipboard monitor. */
    val IG_URL = Regex(
        """https?://(?:www\.)?instagram\.com/(?:[a-zA-Z0-9._]+/)?(?:p|reel|reels?|tv|stories)/[A-Za-z0-9_-]+"""
    )

    private val BAD_CHARS = Regex("""[\\/*?:"<>|]""")
    private val SPACES = Regex("""\s+""")

    fun findInstagramUrl(text: String?): String? =
        text?.let { IG_URL.find(it)?.value }

    fun sanitizeFilename(name: String?, maxLength: Int = 60): String {
        if (name.isNullOrEmpty()) return "instagram_${UUID.randomUUID().toString().take(8)}"
        var s = BAD_CHARS.replace(name, "")
        s = SPACES.replace(s, "_").trim('_')
        if (s.length > maxLength) s = s.substring(0, maxLength)
        return s.trim('_').ifEmpty { "instagram_${UUID.randomUUID().toString().take(8)}" }
    }

    fun extractUsername(url: String): String? {
        Regex("""https?://(?:www\.)?instagram\.com/stories/([a-zA-Z0-9._]+)/""").find(url)?.let {
            return it.groupValues[1]
        }
        Regex("""https?://(?:www\.)?instagram\.com/([a-zA-Z0-9._]+)/""").find(url)?.let {
            val u = it.groupValues[1]
            if (u !in setOf("p", "reel", "reels", "tv", "stories")) return u
        }
        return null
    }

    fun extractShortcode(url: String): String? =
        Regex("""instagram\.com/(?:[a-zA-Z0-9._]+/)?(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""")
            .find(url)?.groupValues?.get(1)

    /** For story URLs: instagram.com/stories/<user>/<mediaId>/ */
    fun extractStoryMediaId(url: String): String? =
        Regex("""instagram\.com/stories/[a-zA-Z0-9._]+/(\d+)""").find(url)?.groupValues?.get(1)

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun shortcodeToMediaId(shortcode: String): String {
        // Longer shortcodes (private posts) carry a suffix; the id is encoded in the first 11 chars.
        val code = if (shortcode.length > 28) shortcode.substring(0, shortcode.length - 28) else shortcode
        var id = 0L
        for (c in code) id = id * 64 + ALPHABET.indexOf(c)
        return id.toString()
    }
}
