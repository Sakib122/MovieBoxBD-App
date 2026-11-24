package com.movieboxbd.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * ☁️ রিমোট অ্যাড-কনফিগ (v5.1) — অ্যাডমিন প্যানেল থেকেই সব অ্যাড-কন্ট্রোল:
 *
 *   GET {ADMIN_API}/api/app/ads-config →
 *   {"ok":true,"gate_enabled":true,"gate_seconds":10,"cooldown_seconds":120,
 *    "normal_links":["…"],"adult_links":["…"],
 *    "poster_ad_enabled":false,"poster_ad_every_n":5,"ts":…}
 *
 * • অ্যাপ খুললেই ব্যাকগ্রাউন্ডে ফেচ → SharedPreferences-এ ক্যাশ → রানিং অ্যাপে সাথে সাথে বসে
 * • সার্ভার বন্ধ / ইন্টারনেট নেই / প্রথমবার চালু → Config.kt-র বিল্ট-ইন ডিফল্টই চলে, কিছুই আটকায় না
 * • 🔞 নরমাল আর ১৮+ লিংক পুল সবসময় আলাদা — নরমাল মুভিতে অ্যাডাল্ট লিংক যাওয়ার কোনো পথ নেই
 *   (১৮+ পুল খালি হলে সাধারণ লিংক চলে — উল্টো দিকটা কখনোই নয়)
 * • ♾ v5.1: আনলিমিটেড লিংক — প্যানেলে যতগুলো চাও ততগুলো (স্যানিটি-ক্যাপ ২০০), রোটেশনে চলে
 * • 🎬 v5.1: পোস্টার-ক্লিক অ্যাড — প্যানেল থেকে চালু/বন্ধ + ফ্রিকোয়েন্সি; ডিফল্ট বন্ধ
 */
object RemoteAds {

    private const val PREFS = "mbx_remote_ads"

    /* ── চলতি ভ্যালু (মেমোরি — অ্যাপ খুললে ক্যাশ থেকে লোড, নেট থেকে আপডেট) ── */
    @Volatile private var gateEnabled = true
    @Volatile private var gateSeconds = Config.AD_WATCH_SECONDS          /* ডিফল্ট ১০ সেকেন্ড */
    @Volatile private var cooldownSeconds = Config.AD_COOLDOWN_SECONDS   /* ডিফল্ট ১২০ সেকেন্ড */

    /* 🎲 লিংক পুল — প্যানেল খালি দিলে বিল্ট-ইন ডিফল্ট চলে */
    @Volatile private var normalLinks: List<String> = Config.NORMAL_DIRECT_LINKS
    @Volatile private var adultLinks: List<String> = Config.ADULT_DIRECT_LINKS

    /* 🎬 (v5.1) পোস্টার-ক্লিক অ্যাড — প্যানেল থেকে চালু/বন্ধ + ফ্রিকোয়েন্সি; ডিফল্ট বন্ধ */
    @Volatile private var posterAdEnabled = false
    @Volatile private var posterEveryN = Config.POSTER_AD_EVERY_N_DEFAULT

    /* 🎲 নো-রিপিট র‍্যান্ডম — শেষ দেখানো লিংক পরপর দুইবার আসে না (মালিকের র‍্যান্ডম সিস্টেম) */
    private val lastServed = HashMap<String, String>()
    private val rnd = java.util.Random()

    /* ── অ্যাপ খুললেই ডাকো — ক্যাশ-করা সেটিং সাথে সাথে মেমোরিতে (নেট না থাকলেও চলে) ── */
    fun loadCached(ctx: Context) {
        try {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            gateEnabled = p.getBoolean("gate_enabled", true)
            gateSeconds = p.getInt("gate_seconds", Config.AD_WATCH_SECONDS).coerceIn(5, 60)
            cooldownSeconds = p.getInt("cooldown_seconds", Config.AD_COOLDOWN_SECONDS).coerceIn(30, 3600)
            normalLinks = sanitize(split(p.getString("normal_links", "")))
                .ifEmpty { Config.NORMAL_DIRECT_LINKS }
            adultLinks = sanitize(split(p.getString("adult_links", "")))
                .ifEmpty { Config.ADULT_DIRECT_LINKS }
            posterAdEnabled = p.getBoolean("poster_ad_enabled", false)
            posterEveryN = p.getInt("poster_every_n", Config.POSTER_AD_EVERY_N_DEFAULT).coerceIn(2, 50)
        } catch (_: Exception) {
        }
    }

    /* ── ব্যাকগ্রাউন্ড থ্রেডে প্যানেল থেকে টাটকা সেটিং — সফল হলে মেমোরি+ক্যাশ দুটোতেই বসে ── */
    fun refreshAsync(ctx: Context) {
        val api = Config.ADMIN_API.trimEnd('/')
        if (api.isBlank()) return
        Thread {
            var c: HttpURLConnection? = null
            try {
                val conn = URL("$api/api/app/ads-config").openConnection() as HttpURLConnection
                c = conn
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "MovieBoxBD/${BuildConfig.VERSION_NAME}")
                if (conn.responseCode !in 200..299) return@Thread
                val j = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
                if (!j.optBoolean("ok", false)) return@Thread
                val nLinks = sanitize(j.optJSONArray("normal_links"))
                val aLinks = sanitize(j.optJSONArray("adult_links"))
                val gOn = j.optBoolean("gate_enabled", true)
                val gSec = j.optInt("gate_seconds", Config.AD_WATCH_SECONDS).coerceIn(5, 60)
                val cSec = j.optInt("cooldown_seconds", Config.AD_COOLDOWN_SECONDS).coerceIn(30, 3600)
                val pOn = j.optBoolean("poster_ad_enabled", false)   /* 🎬 v5.1 */
                val pN = j.optInt("poster_ad_every_n", Config.POSTER_AD_EVERY_N_DEFAULT).coerceIn(2, 50)
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean("gate_enabled", gOn)
                    .putInt("gate_seconds", gSec)
                    .putInt("cooldown_seconds", cSec)
                    .putBoolean("poster_ad_enabled", pOn)
                    .putInt("poster_every_n", pN)
                    .putString("normal_links", join(nLinks))
                    .putString("adult_links", join(aLinks))
                    .apply()
                /* ☁️ মেমোরিতেও — পরের যেকোনো গেট সাথে সাথে নতুন সেটিং মানবে */
                gateEnabled = gOn
                gateSeconds = gSec
                cooldownSeconds = cSec
                posterAdEnabled = pOn
                posterEveryN = pN
                if (nLinks.isNotEmpty()) normalLinks = nLinks
                if (aLinks.isNotEmpty()) adultLinks = aLinks
            } catch (_: Exception) {
            } finally {
                c?.disconnect()
            }
        }.start()
    }

    /* ── getters — Ads.kt এগুলোই ব্যবহার করে ── */
    fun gateOn(): Boolean = gateEnabled
    fun watchSeconds(): Int = gateSeconds
    fun cooldownSeconds(): Int = cooldownSeconds
    fun posterAdOn(): Boolean = posterAdEnabled   /* 🎬 v5.1 */
    fun posterEveryN(): Int = posterEveryN        /* 🎬 v5.1 */

    /* ── 🎲 র‍্যান্ডম ডিরেক্ট-লিংক — নরমাল আর ১৮+ পুল কখনো মেশে না ──
       নরমাল মুভি → শুধু normal_links; ১৮+ মুভি → শুধু adult_links।
       প্রতিবার র‍্যান্ডম পিক, তবে সর্বশেষ দেখানো লিংক পরপর দুইবার আসে না। */
    fun directLinkNow(adult: Boolean): String {
        val pool = (if (adult) adultLinks else normalLinks).filter { it.isNotBlank() }
        if (pool.isEmpty()) return ""
        if (pool.size == 1) return pool[0]
        val key = if (adult) "adult" else "normal"
        val last = lastServed[key]
        val sub = pool.filter { it != last }.ifEmpty { pool }
        val pick = sub[rnd.nextInt(sub.size)]
        lastServed[key] = pick
        return pick
    }

    /* ── স্যানিটাইজ: শুধু http(s) লিংক — ফাঁকা/ডুপ্লিকেট বাদ, সর্বোচ্চ ২০০টা (v5.1: আনলিমিটেড) ── */
    private fun sanitize(raw: List<String>): List<String> {
        val out = ArrayList<String>()
        for (u in raw) {
            val s = u.trim()
            if (s.length < 12) continue
            if (!s.startsWith("http://") && !s.startsWith("https://")) continue
            if (!out.contains(s)) out.add(s)
            if (out.size >= 200) break
        }
        return out
    }

    private fun sanitize(arr: JSONArray?): List<String> {
        val out = ArrayList<String>()
        if (arr == null) return out
        for (i in 0 until arr.length()) out.add(arr.optString(i, ""))
        return sanitize(out)
    }

    private fun split(s: String?): List<String> = (s ?: "").split("\n")
    private fun join(l: List<String>): String = l.joinToString("\n")
}
