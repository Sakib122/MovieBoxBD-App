package com.movieboxbd.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Blogger-এর ফ্রি JSON API থেকে মুভির লিস্ট আনে।
 * ওয়েব সিরিজের লিংক → "Episode 1, Episode 2..." আর
 * মুভির ২টা ভার্সন থাকলে → "720p, 1080p" নামে দেখায়।
 */
object BloggerApi {

    private const val PAGE_SIZE = 20

    /** এক পেজ মুভি আনো (label = ক্যাটাগরি, query = সার্চ, startIndex = কোথা থেকে) */
    fun load(label: String?, query: String?, startIndex: Int): FeedPage {
        return load(label, query, startIndex, null)
    }

    /** mustLabel দিলে ওই লেবেলও যাদের আছে শুধু তাদের রাখবে (যেমন: Web Series + Hindi) */
    fun load(label: String?, query: String?, startIndex: Int, mustLabel: String?): FeedPage {
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
        var page = parse(body)
        if (mustLabel != null) {
            val filtered = page.items.filter { it.labels.any { l -> l.equals(mustLabel, true) } }
            page = FeedPage(filtered, page.total)
        }
        return page
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

        val isSeries = isWebSeries(title, labels)
        val raw = ArrayList<String>()
        for (m in URL_RE.findAll(content)) raw.add(m.value.trim())
        val media = ArrayList<MediaLink>()
        val seen = HashSet<String>()
        var idx = 0
        for (raw0 in raw) {
            if (!raw0.contains("?hash=") && !raw0.contains("/watch/")) continue
            val streamUrl = raw0.replace("/watch/", "/")
            if (!seen.add(streamUrl)) continue
            val name = prettyName(streamUrl, isSeries, idx)
            media.add(MediaLink(name = name, streamUrl = streamUrl))
            idx++
            if (media.size >= 8) break
        }

        return MovieItem(
            title = title,
            permalink = permalink,
            poster = findPoster(content),
            labels = labels,
            date = date,
            links = media
        )
    }

    /** এই পোস্টটা কি ওয়েব সিরিজ? (লেবেল বা টাইটেল দেখে) */
    fun isWebSeries(title: String, labels: List<String>): Boolean {
        for (wl in Config.WEB_SERIES_LABELS) {
            if (labels.any { it.equals(wl, true) }) return true
        }
        val t = title.lowercase()
        return Regex("s\\d{1,2}\\b|\\bep\\b|episode|ওয়েব সিরিজ|season").containsMatchIn(t)
    }

    /* ── পোস্টের HTML থেকে পোস্টার ───────────────────────────── */

    private val IMG_RE = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val URL_RE = Regex("https?://[^\\s\"'<>()]+", RegexOption.IGNORE_CASE)
    private val Q_RE = Regex("(2160p|1080p|720p|480p|360p)", RegexOption.IGNORE_CASE)
    private val EP_RE = Regex("(?:\\bEP\\b|\\bE\\b|episode)[.\\s_-]*(\\d{1,4})", RegexOption.IGNORE_CASE)
    private val GENERIC_RE = Regex("^(AV[_-]?File[_-]?\\d+|AV\\d*|file\\d*|video\\d*)$", RegexOption.IGNORE_CASE)

    private fun findPoster(content: String): String? {
        val m = IMG_RE.find(content) ?: return null
        return m.groupValues[1].trim()
    }

    /**
     * লিংকের সুন্দর নাম বানায়:
     *  ওয়েব সিরিজ → "Episode 1", "Episode 121 · 720p"
     *  মুভি 720p/1080p থাকলে → "720p", "1080p"
     *  নাহলে → "ফুল মুভি", "ফুল মুভি ২"
     */
    private fun prettyName(streamUrl: String, series: Boolean, index: Int): String {
        val q = Q_RE.find(streamUrl)?.value?.lowercase()
        return try {
            val path = streamUrl.substringBefore('?')
            var name = path.substringAfterLast('/')
            name = URLDecoder.decode(name, Charsets.UTF_8)
            name = name.substringBeforeLast('.')
            val ep = EP_RE.find(name)?.groupValues?.get(1)

            if (series) {
                val epNum = ep ?: (index + 1).toString()
                return if (q != null) "Episode $epNum · $q" else "Episode $epNum"
            }
            if (q != null) return q
            if (GENERIC_RE.matches(name) || name.length < 4) {
                return if (index == 0) "ফুল মুভি" else "ফুল মুভি ${index + 1}"
            }
            name
        } catch (_: Exception) {
            if (index == 0) "ফুল মুভি" else "ফুল মুভি ${index + 1}"
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
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MovieBoxBD/2.0")
            if (c.responseCode !in 200..299) null
            else c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
