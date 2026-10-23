package com.igdownloader.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Android replacement for gallery-dl's image mode. Your cookies come from a Firefox *web* session,
 * so requests must look like the Instagram website (web User-Agent, web app id, www host).
 * Two methods are tried in order; if both fail the error lists why.
 */
class InstagramImages(private val cookies: CookieStore) {

    data class ImageItem(val url: String, val username: String, val caption: String, val shortcode: String)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val webUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:150.0) Gecko/20100101 Firefox/150.0"
    private val webAppId = "936619743392459"

    private fun webRequest(url: String, cookieHeader: String, referer: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("User-Agent", webUserAgent)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("X-IG-App-ID", webAppId)
            .header("X-ASBD-ID", "129477")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", referer)
            .header("Origin", "https://www.instagram.com")
            .header("Cookie", cookieHeader)
            .apply { cookies.csrfToken()?.let { header("X-CSRFToken", it) } }

    suspend fun fetch(url: String): List<ImageItem> = withContext(Dispatchers.IO) {
        val cookieHeader = cookies.instagramCookieHeader()
            ?: error("Image downloads need your cookies.txt (Instagram requires a logged-in session).")

        val storyId = Utils.extractStoryMediaId(url)
        val shortcode = Utils.extractShortcode(url)
        val mediaId = storyId ?: shortcode?.let { Utils.shortcodeToMediaId(it) }
            ?: error("Could not read a post or story id from that URL.")

        val errors = mutableListOf<String>()

        try {
            return@withContext viaMediaInfo(mediaId, shortcode, cookieHeader, url)
        } catch (e: Exception) {
            errors += "media info: ${e.message}"
        }

        if (shortcode != null) {
            try {
                return@withContext viaGraphql(shortcode, cookieHeader)
            } catch (e: Exception) {
                errors += "graphql: ${e.message}"
            }
        }

        error("ERROR: could not get images.\n" + errors.joinToString("\n"))
    }

    // ---- Method 1: /api/v1/media/<id>/info/ (works for posts, reels and stories) ----
    private fun viaMediaInfo(mediaId: String, shortcode: String?, cookieHeader: String, pageUrl: String): List<ImageItem> {
        val req = webRequest("https://www.instagram.com/api/v1/media/$mediaId/info/", cookieHeader, pageUrl).build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code} ${body.take(160).replace('\n', ' ')}")
            val item = JSONObject(body).optJSONArray("items")?.optJSONObject(0)
                ?: error("no items in response: ${body.take(160)}")

            val username = item.optJSONObject("user")?.optString("username").orEmpty().ifEmpty { "instagram" }
            val caption = item.optJSONObject("caption")?.optString("text").orEmpty()
            val code = shortcode ?: item.optString("code", mediaId)

            val nodes = item.optJSONArray("carousel_media")
            val list = mutableListOf<ImageItem>()
            if (nodes != null) {
                for (i in 0 until nodes.length()) {
                    bestImage(nodes.getJSONObject(i))?.let { list += ImageItem(it, username, caption, code) }
                }
            } else {
                bestImage(item)?.let { list += ImageItem(it, username, caption, code) }
            }
            if (list.isEmpty()) error("post has no images")
            return list
        }
    }

    private fun bestImage(node: JSONObject): String? =
        node.optJSONObject("image_versions2")?.optJSONArray("candidates")
            ?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotEmpty() }

    // ---- Method 2: web GraphQL post query (posts / reels only) ----
    private fun viaGraphql(shortcode: String, cookieHeader: String): List<ImageItem> {
        val form = FormBody.Builder()
            .add("av", cookies.parse().firstOrNull { it.name == "ds_user_id" }?.value ?: "0")
            .add("__a", "1")
            .add("__d", "www")
            .add("fb_api_req_friendly_name", "PolarisPostActionLoadPostQueryQuery")
            .add("variables", """{"shortcode":"$shortcode","fetch_tagged_user_count":null,"hoisted_comment_id":null,"hoisted_reply_id":null}""")
            .add("server_timestamps", "true")
            .add("doc_id", "8845758582119845")
            .build()
        val req = webRequest("https://www.instagram.com/graphql/query", cookieHeader, "https://www.instagram.com/p/$shortcode/")
            .post(form).build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code} ${body.take(160).replace('\n', ' ')}")
            val media = JSONObject(body).optJSONObject("data")?.optJSONObject("xdt_shortcode_media")
                ?: error("no media in response: ${body.take(160)}")

            val username = media.optJSONObject("owner")?.optString("username").orEmpty().ifEmpty { "instagram" }
            val caption = media.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")
                ?.optJSONObject(0)?.optJSONObject("node")?.optString("text").orEmpty()

            val edges = media.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
            val list = mutableListOf<ImageItem>()
            if (edges != null) {
                for (i in 0 until edges.length()) {
                    edges.getJSONObject(i).optJSONObject("node")?.optString("display_url")
                        ?.takeIf { it.isNotEmpty() }?.let { list += ImageItem(it, username, caption, shortcode) }
                }
            } else {
                media.optString("display_url").takeIf { it.isNotEmpty() }
                    ?.let { list += ImageItem(it, username, caption, shortcode) }
            }
            if (list.isEmpty()) error("post has no images")
            return list
        }
    }

    suspend fun download(imageUrl: String, target: File) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(imageUrl).header("User-Agent", webUserAgent).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Image download failed (HTTP ${resp.code}).")
            resp.body!!.byteStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        }
    }
}
