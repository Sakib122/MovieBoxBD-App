package com.movieboxbd.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * 💰 Adsterra অ্যাড — পুরোটা Config.kt থেকে চলে (v3.1 — বটের সিস্টেম হুবহু)।
 *
 * ▶ ফ্লো (মালিকের নির্দেশ — বটগুলোর pick_random_link এর মতো):
 *   ১. মুভি পোস্টারে ক্লিক → র‍্যান্ডম Direct Link ব্রাউজারে
 *      (সাধারণ মুভিতে NORMAL_DIRECT_LINKS, ১৮+ মুভিতে ADULT_DIRECT_LINKS)
 *   ২. ▶ প্লে / ⬇ ডাউনলোড / 🌐 ব্রাউজার / ✈ টেলিগ্রাম — র‍্যান্ডম Direct Link
 *      (সর্বশেষ দেখানোটা আবার আসে না) + অ্যাপে ৫ সেকেন্ডের কাউন্টডাউন → শেষ হলেই কাজ
 *   ৩. ⏱ কুলডাউন: একটা অ্যাডের পর AD_COOLDOWN_SECONDS (১ মিনিট) পর্যন্ত কোনো অ্যাড নয় —
 *      ইউজার যা খুশি করতে পারে; সময় শেষে পরের ক্লিকেই আবার র‍্যান্ডম অ্যাড
 *   ৪. ব্যানার ৪টাই রিয়েল Adsterra কোড, রিয়েল ইমপ্রেশন — ফেক/খালি ব্যানার নেই
 */
object Ads {

    /* ── গেটের অ্যাকশন টাইপ ── */
    const val ACT_PLAY = 0
    const val ACT_DOWNLOAD = 1
    const val ACT_TELEGRAM = 2
    const val ACT_BROWSER = 3

    private const val PREF = "mbx_ad"
    private const val K_TS = "gate_ts"
    private const val K_PEND = "gate_pending"
    private const val K_TYPE = "gate_type"
    private const val K_URL = "gate_url"
    private const val K_EXTRA = "gate_extra"
    private const val K_TITLE = "gate_title"

    /* 🔞 এখন যে মুভির পেজ খোলা সেটা ১৮+ কি না — DetailActivity সেট করে */
    var adultNow: Boolean = false

    /* ⏱ অ্যাড কুলডাউন (মালিকের নির্দেশ — আগের সিস্টেমে ছিল ৩ মিনিট, এখন ১ মিনিট):
       একটা অ্যাড দেখার পর Config.AD_COOLDOWN_SECONDS পর্যন্ত আর কোনো ডাইরেক্ট-লিংক
       অ্যাড আসবে না। ইউজার এর ভেতরে যা খুশি করতে পারে — প্লে, এপিসোড চেঞ্জ,
       ডাউনলোড — সব সরাসরি হবে। সময় শেষ হলে পরের ক্লিকেই র‍্যান্ডম অ্যাড। */
    private const val K_LAST = "last_ad_ts"

    /** এখন অ্যাড-ফ্রি সময় চলছে কি না (কুলডাউনের ভেতরে = true → অ্যাড ছাড়াই কাজ) */
    fun adFreeNow(ctx: Context): Boolean {
        val last = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(K_LAST, 0L)
        return System.currentTimeMillis() - last < Config.AD_COOLDOWN_SECONDS * 1000L
    }

    /** অ্যাড দেখানো হলো — কুলডাউন টাইমার এখনই থেকে গোনা হবে */
    fun markAdShown(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong(K_LAST, System.currentTimeMillis()).apply()
    }

    /** গেট শেষ হলে যেটা করতে হবে */
    data class PendingAction(val type: Int, val url: String, val title: String, val extra: String)

    /** সরাসরি র‍্যান্ডম Direct Link ব্রাউজারে খোলা (পোস্টার-ক্লিকের জন্য) */
    fun openDirectLink(ctx: Context, adult: Boolean = false) {
        val url = Config.directLinkNow(adult)
        if (url.isBlank()) return
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    /** 🎬 মুভি পোস্টারে ক্লিক → সাথে সাথে র‍্যান্ডম Direct Link।
     *  ⏱ কুলডাউনের ভেতরে থাকলে কোনো অ্যাড নয় — ইউজার সরাসরি মুভিতে ঢুকবে। */
    fun onPosterOpened(ctx: Context, adult: Boolean) {
        if (adFreeNow(ctx)) return
        markAdShown(ctx)
        openDirectLink(ctx, adult)
    }

    /** ▶ প্লে / ⬇ ডাউনলোড ইত্যাদির আগে অ্যাড-গেট:
     *  ⏱ কুলডাউনের ভেতরে থাকলে অ্যাড ছাড়াই free=true — কাজ সরাসরি হবে।
     *  নাহলে র‍্যান্ডম Direct Link খোলে + অ্যাপে AD_WATCH_SECONDS সেকেন্ডের কাউন্টডাউন,
     *  ফিরে এলে কাউন্টডাউন শেষ করে কাজটা হয়। */
    fun requestAction(activity: Activity, type: Int, url: String, title: String, extra: String = ""): Boolean {
        if (adFreeNow(activity)) return true   /* ⏱ ১ মিনিটের ভেতরে — আর কোনো অ্যাড নয় */
        markAdShown(activity)
        activity.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong(K_TS, System.currentTimeMillis())
            .putBoolean(K_PEND, true)
            .putInt(K_TYPE, type)
            .putString(K_URL, url)
            .putString(K_EXTRA, extra)
            .putString(K_TITLE, title)
            .apply()
        openDirectLink(activity, adultNow)
        return false
    }

    /** onResume-এ ডাকো — বাকি সেকেন্ড রিটার্ন করে; -1 মানে কোনো পেন্ডিং কাজ নেই; 0 মানে এখনই */
    fun pendingRemaining(activity: Activity): Int {
        val p = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!p.getBoolean(K_PEND, false)) return -1
        val ts = p.getLong(K_TS, 0)
        /* 🧹 ৩০ মিনিটের পুরনো পেন্ডিং আর কার্যকর হবে না — নাহলে অ্যাপ খুললেই
           দিন আগে ক্লিক করা মুভি হঠাৎ প্লে/ডাউনলোড হয়ে যেত */
        if (ts > 0 && System.currentTimeMillis() - ts > 30 * 60_000L) {
            p.edit().putBoolean(K_PEND, false).remove(K_URL).remove(K_EXTRA).apply()
            return -1
        }
        val elapsed = System.currentTimeMillis() - ts
        val need = Config.AD_WATCH_SECONDS * 1000L
        val remain = ((need - elapsed) / 1000L).toInt()
        return if (remain > 0) remain else 0
    }

    /** গেট শেষ — পেন্ডিং অ্যাকশন নাও */
    fun consume(activity: Activity): PendingAction? {
        val p = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val type = p.getInt(K_TYPE, ACT_PLAY)
        val url = p.getString(K_URL, null) ?: return null
        val title = p.getString(K_TITLE, "MovieBoxBD") ?: "MovieBoxBD"
        val extra = p.getString(K_EXTRA, "") ?: ""
        p.edit().putBoolean(K_PEND, false).remove(K_URL).remove(K_EXTRA).apply()
        return PendingAction(type, url, title, extra)
    }

    /** ইউজার বাতিল করলে */
    fun cancelGate(activity: Activity) {
        activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(K_PEND, false).remove(K_URL).remove(K_EXTRA).apply()
    }

    /* 🎲 ব্যানারের নিজস্ব নো-রিপিট র‍্যান্ডম (লিংকের মতোই) */
    private val lastBanner = HashMap<String, String>()
    private val rnd = java.util.Random()

    /** 📢 রিয়েল ব্যানার HTML পিকার।
     *  wide=true → 320×50 (হোম-নিচ / ডিটেইল-নিচ), wide=false → 300×250 (ডিটেইল-ভেতরে)।
     *  ১৮+ কনটেন্টে অ্যাডাল্ট ব্যানার, অ্যাডাল্ট ব্যানার খালি থাকলে সাধারণ। প্রতিবার র‍্যান্ডম পিক। */
    fun bannerHtml(adult: Boolean, wide: Boolean): String {
        fun pick(pool: List<String>, key: String): String {
            val p = pool.filter { it.isNotBlank() }
            if (p.isEmpty()) return ""
            if (p.size == 1) return p[0]
            val last = lastBanner[key]
            val sub = p.filter { it != last }.ifEmpty { p }
            val pick = sub[rnd.nextInt(sub.size)]
            lastBanner[key] = pick
            return pick
        }
        val key = if (adult) "a$wide" else "n$wide"
        if (adult) {
            val a = pick(if (wide) Config.BANNER_ADULT_320 else Config.BANNER_ADULT_300, key)
            if (a.isNotBlank()) return a
        }
        return pick(if (wide) Config.BANNER_NORMAL_320 else Config.BANNER_NORMAL_300, key)
    }

    /** ব্যানার WebView-এ অ্যাড কোড বসায় — রিয়েল ইমপ্রেশন; কোড না থাকলে জায়গাটা লুকায় */
    fun applyBanner(view: WebView, html: String) {
        if (html.isBlank()) {
            view.visibility = View.GONE
            (view.parent as? View)?.visibility = View.GONE
            return
        }
        try {
            val s = view.settings
            s.javaScriptEnabled = true
            s.domStorageEnabled = true
            s.loadWithOverviewMode = true
            s.useWideViewPort = true
            view.setBackgroundColor(Color.TRANSPARENT)
            /* অ্যাডে ক্লিক করলে ব্রাউজারে খুলবে */
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, url: String): Boolean {
                    return try {
                        v.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
            }
            /* তোমার সাইটকে base ধরে লোড হয় — যাতে অ্যাড ঠিকমতো সার্ভ হয় */
            view.loadDataWithBaseURL(Config.SITE, doc(html), "text/html", "UTF-8", null)
        } catch (_: Exception) {
        }
    }

    private fun doc(inner: String): String =
        "<!DOCTYPE html><html><head>" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<style>body{margin:0;padding:0;background:transparent;overflow:hidden;" +
            "display:flex;justify-content:center;align-items:center}</style>" +
            "</head><body>" + inner + "</body></html>"
}
