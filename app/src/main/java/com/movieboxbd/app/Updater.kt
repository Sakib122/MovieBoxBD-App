package com.movieboxbd.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 🚀 অ্যাপ আপডেট চেকার — অ্যাডমিন প্যানেলে নতুন ভার্সন দিলে অ্যাপ নিজেই ধরে ফেলে।
 *
 * সার্ভার: GET {ADMIN_API}/api/app/latest →
 *   {"code": 7, "name": "2.6", "url": "https://github.com/.../MovieBoxBD.apk", "msg": "..."}
 *
 * অ্যাপের versionCode যার চেয়ে কম → নতুন আপডেট আছে:
 *   ① হোমে উপরে সুন্দর ব্যানার (ট্যাপ করলে APK ডাউনলোড পেজ)
 *   ② নোটিফিকেশন (প্রতি নতুন versionCode-এ একবারই)
 */
object Updater {

    data class Info(val code: Int, val name: String, val url: String, val msg: String)

    /** ব্লকিং কল — ব্যাকগ্রাউন্ড থ্রেড থেকেই ডাকো */
    fun fetch(): Info? {
        val api = Config.ADMIN_API.trimEnd('/')
        if (api.isBlank()) return null
        var c: HttpURLConnection? = null
        return try {
            val conn = URL("$api/api/app/latest").openConnection() as HttpURLConnection
            c = conn
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "MovieBoxBD/${BuildConfig.VERSION_NAME}")
            if (conn.responseCode !in 200..299) return null
            val j = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
            val code = j.optInt("code", 0)
            if (code <= 0) return null
            Info(code, j.optString("name", ""), j.optString("url", ""), j.optString("msg", ""))
        } catch (_: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    /** versionCode তুলনা করে আপডেট আছে কিনা */
    fun isNewer(info: Info?): Boolean =
        info != null && info.code > BuildConfig.VERSION_CODE

    /** নোটিফিকেশনে একই ভার্সন বারবার না দেখাতে */
    fun notifyOncePerVersion(ctx: Context, info: Info): Boolean {
        val p = ctx.getSharedPreferences("mbx_notify", Context.MODE_PRIVATE)
        val done = p.getInt("notified_update_code", 0)
        if (done >= info.code) return false
        p.edit().putInt("notified_update_code", info.code).apply()
        return true
    }
}
