package com.igdownloader.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Android replacement for gallery-dl's image mode: reads the post's media info from Instagram's
 * mobile API with the user's cookies and downloads every image of the post/carousel/story.
 */
class InstagramImages(private val cookies: CookieStore) {

    data class ImageItem(val url: String, val username: String, val caption: String, val shortcode: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val userAgent =
        "Instagram 275.0.0.27.98 Android (33/13; 420dpi; 1080x2400; samsung; SM-G991B; o1s; exynos2100; en_US; 458229237)"

    suspend fun fetch(url: String): List<ImageItem> = withContext(Dispatchers.IO) {
        val header = cookies.instagramCookieHeader()
            ?: error("Image downloads need your cookies.txt (Instagram requires a logged-in session).")

        val storyId = Utils.extractStoryMediaId(url)
        val shortcode = Utils.extractShortcode(url)
        val mediaId = storyId ?: shortcode?.let { Utils.shortcodeToMediaId(it) }
            ?: error("Could not read a post or story id from that URL.")

        val req = Request.Builder()
            .url("https://i.instagram.com/api/v1/media/$mediaId/info/")
            .header("User-Agent", userAgent)
            .header("X-IG-App-ID", "567067343352427")
            .header("Cookie", header)
            .apply { cookies.csrfToken()?.let { header("X-CSRFToken", it) } }
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 400 || resp.code == 401 || resp.code == 403) {
                error("ERROR: Instagram refused the request (HTTP ${resp.code}). Your cookies may be expired – re-export cookies.txt from Firefox.")
            }
            if (!resp.isSuccessful) error("ERROR: Instagram returned HTTP ${resp.code}.")

            val item = JSONObject(body).optJSONArray("items")?.optJSONObject(0)
                ?: error("ERROR: No media found for this URL.")
            val username = item.optJSONObject("user")?.optString("username").orEmpty().ifEmpty { "instagram" }
            val caption = item.optJSONObject("caption")?.optString("text").orEmpty()
            val code = shortcode ?: item.optString("code", mediaId)

            val nodes = item.optJSONArray("carousel_media")
            val list = mutableListOf<ImageItem>()
            if (nodes != null) {
                for (i in 0 until nodes.length()) bestImage(nodes.getJSONObject(i))?.let {
                    list += ImageItem(it, username, caption, code)
                }
            } else {
                bestImage(item)?.let { list += ImageItem(it, username, caption, code) }
            }
            if (list.isEmpty()) error("ERROR: This post contains no downloadable images.")
            list
        }
    }

    private fun bestImage(node: JSONObject): String? =
        node.optJSONObject("image_versions2")?.optJSONArray("candidates")
            ?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotEmpty() }

    suspend fun download(imageUrl: String, target: File) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(imageUrl).header("User-Agent", userAgent).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("ERROR: Image download failed (HTTP ${resp.code}).")
            resp.body!!.byteStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }
}
