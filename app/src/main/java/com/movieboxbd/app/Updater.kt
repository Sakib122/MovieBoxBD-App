package com.movieboxbd.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
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
 *   ① হোম খুললেই 🪟 পপআপ (ক্লোজ বাটনসহ — আপডেট না করা পর্যন্ত প্রতিবার অ্যাপ খুললেই আসবে)
 *   ② হোমের উপরে ব্যানার (ট্যাপ করলে APK ডাউনলোড)
 *   ③ 🔔 নোটিফিকেশন (প্রতি নতুন versionCode-এ একবারই)
 *
 * 🛠 v2.6 ফিক্স (v2.5-এর "নোটিফিকেশন আসতেছে না" বাগ):
 *   আগে নোটিফিকেশন আসলে পাঠানোর *আগেই* "পাঠানো হয়ে গেছে" মার্ক হয়ে যেত —
 *   ইউজার পারমিশন Allow চাপার আগে চেক হয়ে গেলে নোটিফিকেশন আর কখনোই আসত না।
 *   এখন: পারমিশন না থাকলে মার্ক হবে না (পরের চেকে আবার চেষ্টা হবে),
 *   আর আসল নোটিফিকেশন সফলভাবে পাঠানোর *পরেই* মার্ক হবে।
 */
object Updater {

    data class Info(val code: Int, val name: String, val url: String, val msg: String)

    private const val CH_ID = "app_update"
    private const val NOTIF_ID = 1002

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

    /** 🔔 নোটিফিকেশন দেখানোর অনুমতি আছে কি না (Android 13+ এ রানটাইম পারমিশন লাগে) */
    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** এই ভার্সনের নোটিফিকেশন আগে দেখানো হয়েছে কি না (মার্ক করে না) */
    fun wasNotified(ctx: Context, info: Info): Boolean =
        ctx.getSharedPreferences("mbx_notify", Context.MODE_PRIVATE)
            .getInt("notified_update_code", 0) >= info.code

    /** নোটিফিকেশন সফলভাবে দেখানোর পরেই ডাকব — তার আগে কখনো না */
    fun markNotified(ctx: Context, info: Info) {
        ctx.getSharedPreferences("mbx_notify", Context.MODE_PRIVATE)
            .edit().putInt("notified_update_code", info.code).apply()
    }

    /** 🚀 আপডেট নোটিফিকেশন বানিয়ে দেখায়। সফল হলে true, ব্যর্থ হলে false (পরে আবার চেষ্টা হবে)। */
    fun postUpdateNotification(ctx: Context, info: Info): Boolean {
        return try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(CH_ID, "অ্যাপ আপডেট", NotificationManager.IMPORTANCE_HIGH)
                )
            }
            if (!hasPermission(ctx)) return false

            val target: Intent = if (info.url.isNotBlank())
                Intent(Intent.ACTION_VIEW, Uri.parse(info.url))
            else Intent(ctx, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                ctx, 2, target,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(ctx, CH_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("🚀 MovieBoxBD-এর নতুন আপডেট এসেছে!")
                .setContentText("নতুন ভার্সন ${info.name} — ট্যাপ করে আপডেট করো")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(
                            "নতুন ভার্সন ${info.name} এসেছে।\n" +
                                (if (info.msg.isNotBlank()) info.msg + "\n" else "") +
                                "ট্যাপ করলেই আপডেট ফাইল ডাউনলোড হবে।"
                        )
                )
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIF_ID, n)
            true
        } catch (_: Exception) {
            false
        }
    }
}
