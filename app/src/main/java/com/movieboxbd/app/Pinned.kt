package com.movieboxbd.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 📌 পিন করা মুভি (v5.4) — অ্যাডমিন প্যানেল থেকেই পুরো কন্ট্রোল:
 *
 *   GET {ADMIN_API}/api/app/pinned →
 *   {"ok":true,"items":[{"pid":"123456","title":"…","pos":1},…]}   (pos অনুযায়ী সাজানো, সর্বোচ্চ ১০)
 *
 * • হোম লোডের সময় ব্যাকগ্রাউন্ড থ্রেডে টাটকা ফেচ → প্যানেলে পিন বদলালেই রানিং অ্যাপে বসে যায়
 *   (নতুন APK লাগে না); হোমের 🔄 রিলোড দিলেও টাটকা আসে
 * • সার্ভার বন্ধ / নেট নেই → শেষ ক্যাশ-করা লিস্ট চলে; একদম কিছু না থাকলে রোটা দেখাবেই না — কিছুই আটকায় না
 * • অ্যাপ পিন-মুভি চেনে pid (Blogger post-ID) দিয়ে — টাইটেল বদলালেও পিন খুলে যায় না
 */
object Pinned {

    private const val PREFS = "mbx_pinned"

    data class Pin(val pid: String, val title: String, val pos: Int)

    /* ── চলতি লিস্ট (মেমোরি — ক্যাশ থেকে লোড, নেট থেকে আপডেট) ── */
    @Volatile private var cached: List<Pin> = emptyList()

    /* ── অ্যাপ খুললেই ডাকো — ক্যাশ-করা লিস্ট সাথে সাথে মেমোরিতে ── */
    fun loadCached(ctx: Context) {
        cached = parse(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("pins", "") ?: "")
    }

    /* ── হোম বানানোর সময় এটাই ব্যবহার হয় ── */
    fun list(): List<Pin> = cached

    /* ── প্যানেল থেকে টাটকা লিস্ট (ব্যাকগ্রাউন্ড থ্রেড থেকে ডাকতে হবে) ──
       সফল হলে ক্যাশ + মেমোরি দুটোতেই বসে; ফেল হলে পুরনো ক্যাশই ফেরত — কিছুই আটকায় না */
    fun fetchBlocking(ctx: Context): List<Pin> {
        val api = Config.ADMIN_API.trimEnd('/')
        if (api.isBlank()) return cached
        var c: HttpURLConnection? = null
        return try {
            val conn = URL("$api/api/app/pinned").openConnection() as HttpURLConnection
            c = conn
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", "MovieBoxBD/${BuildConfig.VERSION_NAME}")
            if (conn.responseCode !in 200..299) return cached
            val j = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
            if (!j.optBoolean("ok", false)) return cached
            val arr = j.optJSONArray("items") ?: return cached
            val out = ArrayList<Pin>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pid = o.optString("pid", "").trim()
                val title = o.optString("title", "").trim()
                if (pid.isNotEmpty() && title.isNotEmpty()) out.add(Pin(pid, title, o.optInt("pos", 99)))
            }
            if (out.isEmpty()) {
                cached = emptyList()
                saveCache(ctx, out)
                return emptyList()
            }
            out.sortBy { it.pos }
            saveCache(ctx, out)
            cached = out
            out
        } catch (_: Exception) {
            cached
        } finally {
            c?.disconnect()
        }
    }

    private fun saveCache(ctx: Context, list: List<Pin>) {
        try {
            val arr = JSONArray()
            for (p in list) {
                arr.put(JSONObject().put("pid", p.pid).put("title", p.title).put("pos", p.pos))
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString("pins", arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun parse(raw: String): List<Pin> {
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Pin>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pid = o.optString("pid", "").trim()
                val title = o.optString("title", "").trim()
                if (pid.isNotEmpty() && title.isNotEmpty()) out.add(Pin(pid, title, o.optInt("pos", 99)))
            }
            out.sortedBy { it.pos }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
