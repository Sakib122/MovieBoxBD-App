package com.movieboxbd.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Blogger-এর ফ্রি JSON API থেকে মুভির লিস্ট আনে।
 * https://www.movieboxbd.xyz/feeds/posts/default?alt=json
 */
object BloggerApi {

    private const val PAGE_SIZE = 20

    /** এক পেজ মুভি আনো (label = ক্যাটাগরি, query = সার্চ, startIndex = কোথা থেকে) */
    fun load(label: String?, query: String?, startIndex: Int): FeedPage {
        val base = Config.SITE.trimEnd('/')
        val sb = StringBuilder(base).append("/feeds/posts/default")
        if (!label.isNullOrBlank()) {
            sb.append("/-/").append(java.net.URLEncoder.encode(label, "UTF-8"))
        }
        sb.append("?alt=json&max-results=").append(PAGE_SIZE)
        sb.append("&start-index=").append(startIndex)
        if (!query.isNullOrBlank()) {
            sb.append("&q=").append(java.net.URLEncoder.encode(query, "UTF-8"))
        }
        val body = httpGet(sb.toString()) ?: throw IOException("সার্ভারে পৌঁছানো যাচ্ছে না")
        return parse(body)
    }

    /* ── JSON পার্স ─────────────────────────────────────────── */

    private fun parse(body: String): FeedPage {
        val feed = JSONObject(body).optJSONObject("feed") ?: JSONObject()
        val entries: JSONArray = feed.optJSONArray("entry") ?: JSONArray()
        val items = ArrayList<MovieItem>(entries.length())
        for (i in 0 until entries.length()) {
            try {
                val e = entries.getJSONObject(i)
                val item = parseEntry(e) ?: continue
                items.add(item)
            } catch (_: Exception) {
                /* একটা পোস্ট খারাপ থাকলে বাকিগুলো চলবে */
            }
        }
        val total = feed.optJSONObject("openSearch\$totalResults")?.optInt("\$t") ?: items.size
        return FeedPage(items, total)
    }

    private fun parseEntry(e: JSONObject): MovieItem? {
        val title = e.optJSONObject("title")?.optString("\$t")?.trim().orEmpty()
        val content = e.optJSONObject("content")?.optString("\$t").orEmpty()
        if (title.isEmpty() || content.isEmpty()) return null

        var permalink = ""
        val links = e.optJSONArray("link")
        if (links != null) {
            for (i in 0 until links.length()) {
                val l = links.getJSONObject(i)
                if (l.optString("rel") == "alternate") {
                    permalink = l.optString("href")
                    break
                }
            }
        }
        val labels = ArrayList<String>()
        val cats = e.optJSONArray("category")
        if (cats != null) {
            for (i in 0 until cats.length()) {
                val t = cats.getJSONObject(i).optString("term")
                if (t.isNotBlank()) labels.add(t)
            }
        }
        val published = e.optJSONObject("published")?.optString("\$t").orEmpty()
        val date = if (published.length >= 10) published.substring(0, 10) else ""

        return MovieItem(
            title = title,
            permalink = permalink,
            poster = findPoster(content),
            labels = labels,
            date = date,
            links = findMediaLinks(content)
        )
    }

    /* ── পোস্টের HTML থেকে পোস্টার + স্ট্রিম লিংক বের করা ───── */

    private val IMG_RE = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val URL_RE = Regex("https?://[^\\s\"'<>()]+", RegexOption.IGNORE_CASE)

    private fun findPoster(content: String): String? {
        val m = IMG_RE.find(content) ?: return null
        return m.groupValues[1].trim()
    }

    private fun findMediaLinks(content: String): List<MediaLink> {
        val out = ArrayList<MediaLink>()
        val seen = HashSet<String>()
        for (m in URL_RE.findAll(content)) {
            val raw = m.value.trim()
            /* শুধু আমাদের স্ট্রিম লিংক (?hash= বা /watch/) — অ্যাড/প্লে-স্টোর বাদ */
            if (!raw.contains("?hash=") && !raw.contains("/watch/")) continue
            val streamUrl = raw.replace("/watch/", "/")
            if (!seen.add(streamUrl)) continue
            out.add(MediaLink(name = prettyName(streamUrl), streamUrl = streamUrl))
            if (out.size >= 8) break
        }
        return out
    }

    private fun prettyName(streamUrl: String): String {
        return try {
            val path = streamUrl.substringBefore('?')
            var name = path.substringAfterLast('/')
            name = URLDecoder.decode(name, Charsets.UTF_8)
            name = name.substringBeforeLast('.')
            /* AV_File_1793527431-এর মতো নাম হলে সুন্দর নাম দাও */
            if (Regex("^AV_File_\\d+$").matches(name)) "ফুল মুভি" else name
        } catch (_: Exception) {
            "ফুল মুভি"
        }
    }

    /* ── HTTP GET ───────────────────────────────────────────── */

    private fun httpGet(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            conn = c
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.requestMethod = "GET"
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MovieBoxBD/1.0")
            if (c.responseCode !in 200..299) null
            else c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
