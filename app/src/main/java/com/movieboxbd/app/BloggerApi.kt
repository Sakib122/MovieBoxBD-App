package com.movieboxbd.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Blogger-এর ফ্রি JSON API থেকে মুভির লিস্ট আনে।
 *
 * 🔑 ওয়েবসাইটের আসল ডাটা পড়ে:
 *  — ফাইল লিংকগুলো `data-link="..."` অ্যাট্রিবিউটে থাকে (ওয়েবসাইটের ডাউনলোড বাটনের ভেতরে)
 *  — ওয়েব সিরিজের আসল এপিসোড নাম `mbd-ep-name` ট্যাগে (যেমন: EP 128) — নিজে নাম্বার বানায় না
 *  — মুভির আসল কোয়ালিটি `mbd-dl-quality` ট্যাগে (যেমন: 720p WEB-DL) আর সাইজ `mbd-dl-meta`-তে
 *  — স্ট্রিম লিংক = /watch/ বাদ দিলেই সরাসরি ভিডিও আসে (সার্ভারে লাইভ টেস্ট করা)
 *  — ডাউনলোড = টেলিগ্রাম বটের লিংক: t.me/bot?start=file_<ID> (watch/ID থেকে)
 */
object BloggerApi {

    private const val PAGE_SIZE = 20
    private const val MAX_LINKS = 30

    /* ── 🔒 ক্রেডিট স্ক্রাবার ──────────────────────────────────────────
     *  ব্লগের নাম/টাইটেল কোথাও যদি অন্য কারো ক্রেডিট ঝুলে থাকে
     *  (Encoder By: CineDoze.TV / Included By … / Movie Link BD.one …) —
     *  ইউজারের চোখে পড়ার আগেই মুছে MovieBoxBD বসিয়ে দেয়। প্রতিটা
     *  ইউজার-দৃশ্যমান নাম এই ফাংশন দিয়ে যায়। */
    private val CREDIT_SEP_RE = Regex(
        """(?i)\b(encoder|encoded|include|included|rip|ripped|ripper|rippers|upload|uploaded|credit|credits|presented|shared|distributed)\b\s*(by)?\s*[:：\-–—]\s*[^\n|<>"\[\]]{0,42}"""
    )
    private val CREDIT_WORD_BY_RE = Regex(
        """(?i)\b(encoder|encoded|include|included|rip|ripped|ripper|rippers|upload|uploaded|credit|credits|presented|shared|distributed)\b\s+by\s+[^\n|<>"\[\]]{0,42}"""
    )
    private val DOMAIN_RE = Regex(
        """(?i)\b[a-z0-9][a-z0-9_\-]{1,28}\.(tv|xyz|site|online|club|icu|cyou|top|buzz|pro|info|shop|store|live|fun|cfd)\b"""
    )
    private val MOVIELINK_RE = Regex(
        """(?i)movie\s*link\s*bd(\s*\.\s*(one|com|net|org|xyz|info))?"""
    )

    fun scrub(s0: String): String {
        if (s0.isBlank()) return s0
        var s = s0
        s = s.replace(CREDIT_SEP_RE, "MovieBoxBD")
        s = s.replace(CREDIT_WORD_BY_RE, "MovieBoxBD")
        s = s.replace(DOMAIN_RE, "MovieBoxBD")
        s = s.replace(MOVIELINK_RE, "MovieBoxBD")
        s = s.replace(Regex("\\s{2,}"), " ").trim()
        return s
    }

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
        val title = scrub(e.optJSONObject("title")?.optString("\$t")?.trim().orEmpty())
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
        return MovieItem(
            title = title,
            permalink = permalink,
            poster = findPoster(content),
            labels = labels,
            date = date,
            links = parseLinks(content, isSeries)
        )
    }

    /* ── 🔗 লিংক পার্স (ওয়েবসাইটের আসল ডাটা) ─────────────────── */

    /** ওয়েবসাইটের ডাউনলোড বাটনের ভেতরের লিংক: data-link="https://...koyeb.app/watch/..." */
    private val DL_ATTR_RE = Regex("""data-link="([^"]+)"""")
    /** এপিসোডের আসল নাম: <div class="mbd-ep-name">EP 128<span...> */
    private val EP_NAME_RE = Regex("""mbd-ep-name">([^<]{1,100})""")
    /** মুভির আসল কোয়ালিটি: <div class="mbd-dl-quality">720p WEB-DL</div> */
    private val Q_LABEL_RE = Regex("""mbd-dl-quality">([^<]{1,100})""")
    /** মুভির ভাষা/সাইজ: <div class="mbd-dl-meta">Hindi | 1.08 GB</div> */
    private val META_RE = Regex("""mbd-dl-meta">([^<]{1,100})""")
    /** পোস্টে সরাসরি দেওয়া টেলিগ্রাম ডাউনলোড লিংক */
    private val TM_RE = Regex("""https://t\.me/[A-Za-z0-9_]+\?start=[^"'\s<>]+""")
    /** /watch/1655/... → 1655 (টেলিগ্রাম বটের file_ID) */
    private val WATCH_ID_RE = Regex("""/watch/(\d+)/""")
    private val PATH_ID_RE = Regex("""/(\d+)/[^/]+\?hash=""")
    /** ফলব্যাক: সাধারণ URL (ব্র্যাকেট-সহ ফাইলনেম যেন কাটা না পড়ে!) */
    private val URL_RE = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    private fun parseLinks(content: String, isSeries: Boolean): List<MediaLink> {
        val media = ArrayList<MediaLink>()
        val seen = HashSet<String>()

        fun add(l: MediaLink?): Boolean {
            if (l == null || media.size >= MAX_LINKS) return false
            val key = l.streamUrl.ifBlank { l.tgUrl }
            if (key.isEmpty() || !seen.add(key)) return false
            media.add(l)
            return true
        }

        /* ① ওয়েবসাইটের data-link (আসল ফাইল লিংক — প্রতিটার আগের ট্যাগ থেকে আসল নাম) */
        for (m in DL_ATTR_RE.findAll(content)) {
            val rawUrl = unescapeHtml(m.groupValues[1].trim())
            if (!rawUrl.contains("koyeb.app") && !rawUrl.contains("?hash=")) continue
            val winStart = maxOf(0, m.range.first - 200)
            val isSeason = content.substring(winStart, m.range.first)
                .contains("mbd-season-btn")
            val label = if (isSeason) Triple("পুরো সিজন", null, null)
            else labelBefore(content, m.range.first)
            add(buildMediaLink(rawUrl, label, isSeries, media.size))
        }

        /* ② পোস্টে সরাসরি t.me ডাউনলোড লিংক থাকলে (ভবিষ্যতের পোস্টের জন্য) */
        if (media.isNotEmpty()) {
            for (m in TM_RE.findAll(content)) {
                add(MediaLink(name = "⬇ টেলিগ্রাম ডাউনলোড", streamUrl = "", tgUrl = m.value.trim()))
            }
        }

        /* ③ ফলব্যাক: পুরনো কাঠামো — সাধারণ URL স্ক্যান */
        if (media.none { it.streamUrl.isNotBlank() }) {
            for (m in URL_RE.findAll(content)) {
                val u = cleanUrl(m.value.trim())
                if (!u.contains("?hash=") && !u.contains("/watch/")) continue
                add(buildMediaLink(u, Triple(null, null, null), isSeries, media.size))
            }
        }
        return media
    }

    /** data-link-এর আগে সবচেয়ে কাছের এপিসোড-নাম / কোয়ালিটি / মেটা লেখা খোঁজে */
    private fun labelBefore(content: String, pos: Int): Triple<String?, String?, String?> {
        if (pos <= 0) return Triple(null, null, null)
        val before = content.substring(0, pos)
        val ep = EP_NAME_RE.findAll(before).lastOrNull()?.groupValues?.get(1)?.trim()
        val q = Q_LABEL_RE.findAll(before).lastOrNull()?.groupValues?.get(1)?.trim()
        val meta = META_RE.findAll(before).lastOrNull()?.groupValues?.get(1)?.trim()
        return Triple(ep, q, meta)
    }

    /**
     * একটা লিংক থেকে অ্যাপের মিডিয়া-লিংক বানায়:
     *  ▶ স্ট্রিম  = /watch/ বাদ (সার্ভার তখন সরাসরি ভিডিও দেয় — লাইভ টেস্টেড)
     *  🌐 পেজ    = /watch/ সহ (ওয়েবসাইটের ডাউনলোড পেজ)
     *  ⬇ টেলিগ্রাম = t.me/bot?start=file_<watch-ID>
     */
    private fun buildMediaLink(
        rawUrl: String,
        label: Triple<String?, String?, String?>,
        isSeries: Boolean,
        index: Int
    ): MediaLink? {
        val streamUrl = rawUrl.replace("/watch/", "/")
        if (!streamUrl.contains("?hash=")) return null

        val id = WATCH_ID_RE.find(rawUrl)?.groupValues?.get(1)
            ?: PATH_ID_RE.find(streamUrl)?.groupValues?.get(1)
        val bot = Config.TELEGRAM_BOT_USERNAME
        val tgUrl = if (bot.isNotBlank() && id != null) "https://t.me/$bot?start=file_$id" else ""

        return MediaLink(
            name = realName(label, streamUrl, isSeries, index),
            streamUrl = streamUrl,
            pageUrl = rawUrl,
            tgUrl = tgUrl
        )
    }

    /** ওয়েবসাইটের আসল নাম — না পেলে ফাইলনেম থেকে সুন্দর নাম */
    private fun realName(
        label: Triple<String?, String?, String?>,
        streamUrl: String,
        isSeries: Boolean,
        index: Int
    ): String {
        val (ep, quality, meta) = label
        /* ওয়েব সিরিজ → ওয়েবসাইটের আসল এপিসোড নাম (যেমন "EP 128") */
        if (!ep.isNullOrBlank()) return scrub(ep)
        /* মুভি → ওয়েবসাইটের আসল কোয়ালিটি + সাইজ (যেমন "720p WEB-DL · 1.08 GB") */
        if (!quality.isNullOrBlank()) {
            val size = meta?.split("|")?.lastOrNull()?.trim()
            return scrub(
                if (!size.isNullOrBlank() && size.any { it.isDigit() })
                    "$quality  ·  $size" else quality
            )
        }
        /* না পেলে ফাইলনেম থেকে */
        return prettyName(streamUrl, isSeries, index)
    }

    /** এই পোস্টটা কি ওয়েব সিরিজ? (লেবেল বা টাইটেল দেখে) */
    fun isWebSeries(title: String, labels: List<String>): Boolean {
        for (wl in Config.WEB_SERIES_LABELS) {
            if (labels.any { it.equals(wl, true) }) return true
        }
        val t = title.lowercase()
        return Regex("s\\d{1,2}\\b|\\bep\\b|episode|ওয়েব সিরিজ|season").containsMatchIn(t)
    }

        /* 🔞 এই পোস্টটা কি এডাল্ট? (ব্লগে "Adult" লেবেল দিলেই ধরা পড়বে) */
    fun isAdult(m: MovieItem): Boolean =
        m.labels.any { l -> Config.ADULT_LABELS.any { a -> l.equals(a, true) } }

    /* ── পোস্টের HTML থেকে পোস্টার ───────────────────────────── */

    private val IMG_RE = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val Q_RE = Regex("(2160p|1080p|720p|480p|360p)", RegexOption.IGNORE_CASE)
    private val EP_RE = Regex("(?:\\bEP\\b|\\bE\\b|episode)[.\\s_-]*(\\d{1,4})", RegexOption.IGNORE_CASE)
    private val GENERIC_RE = Regex("^(AV[_-]?File[_-]?\\d+|AV\\d*|file\\d*|video\\d*)$", RegexOption.IGNORE_CASE)
    /** ফুল/কমপ্লিট সিরিজ চেনার শব্দ (ফাইলনেম/টাইটেলে) */
    private val FULL_RE = Regex("(full|complete|combo|all[.\\s_-]*ep|season[.\\s_-]*pack|s\\d{1,2}[.\\s_-]*full)", RegexOption.IGNORE_CASE)

    private fun findPoster(content: String): String? {
        val m = IMG_RE.find(content) ?: return null
        return m.groupValues[1].trim()
    }

    /**
     * ফলব্যাক নাম — 🔑 নিজের মতো EP1/EP2 বানানো যাবে না, ওয়েবসাইটের আসল ডাটাই দেখাবে:
     *  ওয়েব সিরিজ → ফাইলনেমে আসল EP থাকলে সেটা (যেমন EP 81);
     *              ফুল/কমপ্লিট থাকলে "ফুল সিরিজ (Full Complete)";
     *              নাহলে ফাইলনেমটাই (আসল ডাটা)
     *  মুভি → ফাইলনেমে 720p/1080p থাকলে সেটা, নাহলে "ফুল মুভি"
     */
    private fun prettyName(streamUrl: String, series: Boolean, index: Int): String {
        return scrub(prettyNameRaw(streamUrl, series, index))
    }

    private fun prettyNameRaw(streamUrl: String, series: Boolean, index: Int): String {
        return try {
            val path = streamUrl.substringBefore('?')
            var name = path.substringAfterLast('/')
            name = URLDecoder.decode(name, Charsets.UTF_8)
            name = name.substringBeforeLast('.')
            val q = Q_RE.find(name)?.value?.lowercase()
            val ep = EP_RE.find(name)?.groupValues?.get(1)

            if (series) {
                /* ফাইলনেমে আসল EP নাম্বার থাকলে সেটাই — বানানো নয় */
                if (ep != null) return if (q != null) "EP $ep · $q" else "EP $ep"
                /* ফুল/কমপ্লিট সিরিজ — ওয়েবসাইটের মতোই লেখা */
                if (FULL_RE.containsMatchIn(name)) return "ফুল সিরিজ (Full Complete)"
                /* নাহলে ফাইলনেমটাই আসল ডাটা হিসেবে দেখাও */
                val clean = cleanFileName(name)
                return if (clean.length >= 4 && !GENERIC_RE.matches(clean)) clean
                else "ফুল সিরিজ (Full Complete)"
            }
            if (q != null) return q
            val clean = cleanFileName(name)
            if (clean.length >= 4 && !GENERIC_RE.matches(clean)) clean else "ফুল মুভি"
        } catch (_: Exception) {
            if (series) "ফুল সিরিজ (Full Complete)" else "ফুল মুভি"
        }
    }

    /** ফাইলনেম সুন্দর করে: Bachelor.Point.S05.720p → "Bachelor Point S05 720p" */
    private fun cleanFileName(name: String): String {
        var n = name.replace(Regex("[._]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (n.length > 48) {
            n = n.take(48).substringBeforeLast(" ")
        }
        return n
    }

    /* ── HTML ইউটিলিটি ───────────────────────────────────────── */

    private fun unescapeHtml(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")

    /** URL-এর শেষে ঝুলে থাকা অপ্রয়োজনীয় ব্র্যাকেট/চিহ্ন ছাঁটে */
    private fun cleanUrl(u0: String): String {
        var u = unescapeHtml(u0)
        while (u.endsWith(")") && u.count { it == '(' } < u.count { it == ')' }) u = u.dropLast(1)
        while (u.endsWith(",") || u.endsWith(".") || u.endsWith(";")) u = u.dropLast(1)
        return u
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
