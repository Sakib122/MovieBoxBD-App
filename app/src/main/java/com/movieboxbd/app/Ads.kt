package com.movieboxbd.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.webkit.WebView

/**
 * 💰 Adsterra অ্যাড — পুরোটা তোমার কনফিগ থেকে চলে (Config.kt দেখো)।
 * - Direct Link: প্লে চাপলে কুলডাউন মেনে ব্রাউজারে খোলে (তোমার সাইটের পপআন্ডারের মতই)
 * - Banner: নিচের ব্যানার জোনে তোমার পেস্ট করা কোড রেন্ডার হয়
 */
object Ads {

    private const val PREF = "mbx_ad"
    private const val KEY_TS = "dl_ts"

    /** ▶ প্লে চাপার সময় ডাকো — কুলডাউন শেষ থাকলে Direct Link খুলবে */
    fun maybeFireDirectLink(ctx: Context) {
        val url = Config.ADSTERRA_DIRECT_LINK
        if (url.isBlank()) return
        try {
            val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val last = p.getLong(KEY_TS, 0L)
            val cooldown = Config.DIRECT_LINK_COOLDOWN_HOURS * 3_600_000L
            if (System.currentTimeMillis() - last < cooldown) return
            p.edit().putLong(KEY_TS, System.currentTimeMillis()).apply()
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    /** ব্যানার WebView-এ তোমার অ্যাড কোড বসায়; কোড না থাকলে ব্যানার লুকায় */
    fun applyBanner(view: WebView) {
        val html = Config.ADSTERRA_BANNER_HTML
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
            view.loadData(doc(html), "text/html", "UTF-8")
        } catch (_: Exception) {
        }
    }

    private fun doc(inner: String): String =
        "<!DOCTYPE html><html><head>" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<style>body{margin:0;padding:0;background:transparent;overflow:hidden}</style>" +
            "</head><body>" + inner + "</body></html>"
}
