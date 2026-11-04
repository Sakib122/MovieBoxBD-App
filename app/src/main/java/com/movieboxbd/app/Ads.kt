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
 * 💰 Adsterra অ্যাড — পুরোটা Config.kt থেকে চলে।
 *
 * ▶ অ্যাড গেট ফ্লো (৪টা বাটনেই — ▶ প্লে / ⬇ ডাউনলোড / 🌐 ব্রাউজার / ✈ টেলিগ্রাম):
 *   ১. বাটন চাপলেই সাথে সাথে Direct Link ব্রাউজারে খোলে
 *      (Direct Link ঘণ্টায় ঘণ্টায় বদলায় — Config.DIRECT_LINKS দেখো)
 *   ২. ইউজার অ্যাডে ~১০ সেকেন্ড থাকে
 *   ৩. ফিরে এলে বাকি সেকেন্ড থাকলে অ্যাপে কাউন্টডাউন — শেষ হলেই কাজটা হয়
 *   ৪. বাইপাস করা যাবে না (সময় পুরো না হলে প্লে/ডাউনলোড হবে না)
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
    private const val K_MOV = "movie_open_count"
    private const val K_DIRECT_TS = "direct_link_ts"

    /* 🔞 এখন যে মুভির পেজ খোলা সেটা ১৮+ কি না — DetailActivity সেট করে */
    var adultNow: Boolean = false

    /** গেট শেষ হলে যেটা করতে হবে */
    data class PendingAction(val type: Int, val url: String, val title: String, val extra: String)

    /* এডিট ২: পুরনো openDirectLink ফাংশনটা পুরোটা মুছে এটা বসাও */
    fun openDirectLink(ctx: Context, adult: Boolean = false) {
        val url = Config.directLinkNow(adult)
        if (url.isBlank()) return
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putLong(K_DIRECT_TS, System.currentTimeMillis()).apply()
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    /** মুভি কার্ডে ক্লিক — প্রতি N বারে ১ বার পপআন্ডার */
    fun onMovieOpened(ctx: Context) {
        try {
            val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val n = p.getInt(K_MOV, 0) + 1
            p.edit().putInt(K_MOV, n).apply()
            if (Config.POPUNDER_EVERY_N > 0 && n % Config.POPUNDER_EVERY_N == 0) {
                openDirectLink(ctx)
            }
        } catch (_: Exception) {
        }
    }

    /* এডিট ৩ (ফিক্স): true রিটার্ন মানে "৩ মিনিটের মধ্যে এড দেখা হয়েছে — কলার এখনই নিজে কাজটা করবে"।
       false মানে "এড খুলছে — ফিরে এসে গেট শেষ হলে কাজটা হবে"। */
    fun requestAction(activity: Activity, type: Int, url: String, title: String, extra: String = ""): Boolean {
        val p = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        /* 🆓 সদ্য (৩ মিনিটের মধ্যে) Direct Link খোলা হলে আর বিরক্ত নয়।
           আগের বাগ এখানেই ছিল: কাজটা পেন্ডিং সেভ হয়ে ঝুলে থাকত, এড খুলত না
           বলে কাজ শুরুই হতো না; পরে অন্য মুভির পেজ খুললে পুরনো পেন্ডিংটাই
           ফায়ার হয়ে ভুল মুভি প্লে/ডাউনলোড হতো।
           এখন এড-ফ্রি হলে কিছুই সেভ হয় না — কলার সাথে সাথেই কাজটা করে। */
        val recent = now - p.getLong(K_DIRECT_TS, 0L) < Config.DIRECT_GAP_MIN * 60_000L
        if (recent) return true
        p.edit()
            .putLong(K_TS, now)
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

    /** ব্যানার WebView-এ অ্যাড কোড বসায়; কোড না থাকলে জায়গাটা লুকায় */
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
