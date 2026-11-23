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
 *   ৩. ⏱ (v3.7) সব অ্যাকশনে একই গেট: ২ মিনিট পর পর — প্লে/নেক্সট/ডাউনলোড যা-ই হোক,
 *      চিরতরে বন্ধ নয়; আর চলমান গেটের মধ্যে দ্বিতীয় লিংক-ওপেন নয় (স্প্যাম-প্রোটেকশন)।
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

    /* ⏱ অ্যাড কুলডাউন (মালিকের নির্দেশ v3.4):
       ▶ প্লে (মুভি/এপিসোড — যে কোনোটা): প্রতি ১ মিনিট পর পর ৫-সেকেন্ডের অ্যাড-গেট —
         চিরতরে বন্ধ হয় না। ১ মিনিটের ভেতরে আরেকবার প্লে করলে অ্যাড ছাড়াই চলবে।
         এই টাইমার পোস্টার-ক্লিকের সাথে জড়াতে পারে না — নাহলে পোস্টার ক্লিক করে
         সাথে সাথে প্লে চাপলে গেট চুপি চুপি স্কিপ হয়ে যায় (আগের বাগ)।
       ⬇ ডাউনলোড / ✈ টেলিগ্রাম / 🌐 ব্রাউজার: প্রতিবারই অ্যাড — কোনো কুলডাউন নয়। */
    private const val K_LAST = "last_ad_ts"          /* পোস্টার-ক্লিকের টাইমার */
    private const val K_LAST_GATE = "last_play_gate_ts" /* ▶ প্লে-গেটের নিজস্ব টাইমার */

    /** এখন অ্যাড-ফ্রি সময় চলছে কি না (কুলডাউনের ভেতরে = true → অ্যাড ছাড়াই কাজ) */
    fun adFreeNow(ctx: Context): Boolean {
        val last = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(K_LAST, 0L)
        return System.currentTimeMillis() - last < Config.AD_COOLDOWN_SECONDS * 1000L
    }

    /** গেট এখন ফ্রি কি না — শেষ অ্যাড দেখানোর ২ মিনিটের ভেতরে হলে ফ্রি (v3.7: সব অ্যাকশন) */
    private fun playGateFree(ctx: Context): Boolean {
        val last = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(K_LAST_GATE, 0L)
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

    /* ── ⏮⏭ এপিসোড-সুইচের গেট (v3.6 — মালিকের নির্দেশ) ──
       ইউজার যেন এপিসোড স্কিপ করে অ্যাড এড়াতে না পারে: ▶ প্লে-গেটের হুবহু নিয়মই —
       শেষ অ্যাডের ১ মিনিটের ভেতরে সুইচ = অ্যাড নেই; ১ মিনিট পার = ৫-সেকেন্ডের গেট + র‍্যান্ডম লিংক।
       এটা PlayerActivity-এর নিজস্ব গেট — শেয়ারড পেন্ডিং-প্রেফ নয়, তাই ডিটেইল-পেজের
       গেট-ফ্লোর সাথে জড়ায় না। */

    /** true মানে গেট দেখাতে হবে (১ মিনিট পার); false মানে অ্যাড ছাড়াই সুইচ করো */
    fun episodeGateNeeded(activity: Activity): Boolean {
        if (playGateFree(activity)) return false
        /* ⏱ এই মিনিটের অ্যাড গোনা হলো — পরের গেট এর ১ মিনিট পরে */
        activity.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong(K_LAST_GATE, System.currentTimeMillis()).apply()
        return true
    }

    /** গেট দেখানোর সময় র‍্যান্ডম ডিরেক্ট-লিংক খোলে (প্লে-গেটের মতোই) */
    fun openEpisodeGateAd(activity: Activity) {
        markAdShown(activity)
        openDirectLink(activity, adultNow)
    }

    /** ▶ প্লে / ⏮⏭ এপিসোড-সুইচ / ⬇ ডাউনলোড / ✈ টেলিগ্রাম / 🌐 ব্রাউজার — সবার আগে অ্যাড-গেট:
     *  (v3.7) সব অ্যাকশনে একই নিয়ম: শেষ অ্যাডের ২ মিনিটের ভেতরে হলে free=true (অ্যাড ছাড়াই);
     *    ২ মিনিট পার হলে ৫-সেকেন্ডের গেট — প্রতিবার, চিরতরে বন্ধ নয়।
     *  গেট হলে র‍্যান্ডম Direct Link খোলে (চলমান গেট থাকলে আর খোলে না) +
     *  অ্যাপে AD_WATCH_SECONDS সেকেন্ডের কাউন্টডাউন, ফিরে এলে কাউন্টডাউন শেষ করে কাজটা হয়। */
    fun requestAction(activity: Activity, type: Int, url: String, title: String, extra: String = ""): Boolean {
        /* ⏱ (v3.7 মালিকের নির্দেশ) সব অ্যাকশনে একই নিয়ম — শেষ অ্যাডের ২ মিনিটের ভেতরে
           হলে অ্যাড ছাড়াই কাজ; ২ মিনিট পার হলে গেট + র‍্যান্ডম ডিরেক্ট-লিংক।
           প্লে হোক, ⏮⏭ এপিসোড-সুইচ হোক, ⬇ ডাউনলোড হোক — সব জায়গায় ২ মিনিট পর পর অ্যাড।
           বেশি ক্লিকে বারবার লিংক খুললে অ্যাড-নেটওয়ার্ক স্প্যাম ধরে — এই থ্রটলই সেটা আটকায়। */
        if (playGateFree(activity)) return true
        val p = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        /* 💡 চলমান গেটের ৫ সেকেন্ডের মধ্যে আরেকটা ক্লিক এলে নতুন লিংক খোলা হয় না —
           এক গেট = একটাই ওপেন (ইমপ্রেশন ফুলে যাওয়া বন্ধ) */
        val gateRunning = p.getBoolean(K_PEND, false) &&
            System.currentTimeMillis() - p.getLong(K_TS, 0L) < (Config.AD_WATCH_SECONDS + 3) * 1000L
        p.edit()
            .putLong(K_LAST_GATE, System.currentTimeMillis())
            .putLong(K_TS, System.currentTimeMillis())
            .putBoolean(K_PEND, true)
            .putInt(K_TYPE, type)
            .putString(K_URL, url)
            .putString(K_EXTRA, extra)
            .putString(K_TITLE, title)
            .apply()
        if (!gateRunning) openDirectLink(activity, adultNow)
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
