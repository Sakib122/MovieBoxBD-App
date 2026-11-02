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
 * ▶ প্লে ফ্লো (প্রতিবার):
 *   ১. প্লে চাপলেই সাথে সাথে Direct Link ব্রাউজারে খোলে
 *   ২. ইউজার অ্যাডে ~১০ সেকেন্ড থাকে
 *   ৩. ফিরে এলে বাকি সেকেন্ড থাকলে অ্যাপে কাউন্টডাউন — শেষ হলেই প্লে
 *   ৪. বাইপাস করা যাবে না (সময় পুরো না হলে প্লে হবে না)
 */
object Ads {

    private const val PREF = "mbx_ad"
    private const val K_TS = "gate_ts"
    private const val K_PEND = "gate_pending"
    private const val K_URL = "gate_url"
    private const val K_TITLE = "gate_title"
    private const val K_MOV = "movie_open_count"

    /** Direct Link ব্রাউজারে খোলো */
    fun openDirectLink(ctx: Context) {
        val url = Config.ADSTERRA_DIRECT_LINK
        if (url.isBlank()) return
        try {
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

    /** ▶ প্লে চাপলে ডাকো — Direct Link খুলে গেট চালু করে */
    fun requestPlay(activity: Activity, url: String, title: String) {
        val p = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        p.edit()
            .putLong(K_TS, System.currentTimeMillis())
            .putBoolean(K_PEND, true)
            .putString(K_URL, url)
            .putString(K_TITLE, title)
            .apply()
        openDirectLink(activity)
    }

    /** onResume-এ ডাকো — বাকি সেকেন্ড রিটার্ন করে; -1 মানে কোনো পেন্ডিং প্লে নেই; 0 মানে এখনই প্লে */
    fun pendingRemaining(activity: Activity): Int {
        val p = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!p.getBoolean(K_PEND, false)) return -1
        val elapsed = System.currentTimeMillis() - p.getLong(K_TS, 0)
        val need = Config.AD_WATCH_SECONDS * 1000L
        val remain = ((need - elapsed) / 1000L).toInt()
        return if (remain > 0) remain else 0
    }

    /** গেট শেষ — পেন্ডিং প্লে নাও */
    fun consume(activity: Activity): Pair<String, String>? {
        val p = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val url = p.getString(K_URL, null) ?: return null
        val title = p.getString(K_TITLE, "MovieBoxBD") ?: "MovieBoxBD"
        p.edit().putBoolean(K_PEND, false).remove(K_URL).apply()
        return Pair(url, title)
    }

    /** ইউজার বাতিল করলে */
    fun cancelGate(activity: Activity) {
        activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(K_PEND, false).remove(K_URL).apply()
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
