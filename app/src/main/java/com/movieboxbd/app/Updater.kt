package com.movieboxbd.app

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
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

    /* ts = প্যানেলে ঘোষণাটা কখন দেওয়া হলো — প্রতিবার নতুন ঘোষণায় বদলায় */
    data class Info(val code: Int, val name: String, val url: String, val msg: String, val ts: Long = 0)

    private const val CH_ID = "app_update"
    private const val NOTIF_ID = 1002

    /* ── 🆕 ওয়ান-ট্যাপ আপডেট-ফ্লো ── */
    private var pendingId: Long = -1
    private var dlRc: BroadcastReceiver? = null
    private var pendingUrl: String = "" 

    /** ব্লকিং কল — ব্যাকগ্রাউন্ড থ্রেড থেকেই ডাকো */
    fun fetch(): Info? {
        val api = Config.ADMIN_API.trimEnd('/')
        if (api.isBlank()) return null
        var c: HttpURLConnection? = null
        return try {
            /* 🆕 নিজের versionCode পাঠাই — সার্ভার তখন বোঝে নতুন অ্যাপ ডাকছে, APK-লিঙ্ক দেয়।
               পুরনো অ্যাপ (২.x–৪.২) প্যারাম পাঠায় না — ওদের সার্ভার গাইড-পেজ দেয় */
            val conn = URL("$api/api/app/latest?code=${BuildConfig.VERSION_CODE}").openConnection() as HttpURLConnection
            c = conn
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "MovieBoxBD/${BuildConfig.VERSION_NAME}")
            if (conn.responseCode !in 200..299) return null
            val j = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).readText())
            val code = j.optInt("code", 0)
            if (code <= 0) return null
            Info(code, j.optString("name", ""), j.optString("url", ""), j.optString("msg", ""),
                (j.optDouble("ts", 0.0) * 1000).toLong())   /* 🆕 ঘোষণার সময় (মিলিসেকেন্ড) */
        } catch (_: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    /** versionCode তুলনা করে আপডেট আছে কি না */
    fun isNewer(info: Info?): Boolean =
        info != null && info.code > BuildConfig.VERSION_CODE

    /* 🆕 আপডেট-ফাইল কোথায় বসবে (FileProvider-ও এই পথটাই চেনে) */
    private fun apkFile(ctx: Context): File {
        val dir = File(ctx.getExternalFilesDir(null), "update").apply { mkdirs() }
        return File(dir, "movieboxbd-update.apk")
    }

    /**
     * 🚀 ওয়ান-ট্যাপ আপডেট: ট্যাপ করলেই ব্যাকগ্রাউন্ডে ডাউনলোড (প্রোগ্রেস-নোটিফিকেশনসহ),
     * শেষ হলে সিস্টেম-ইনস্টলার নিজেই খুলবে → Install → **Open/Done**।
     * ব্রাউজারে গিয়ে ফাইল খোঁজার ঝামেলা নেই। একই সাইনিং-কি হলে সরাসরি বসে যায়।
     * কোনো কারণে ডাউনলোড শুরু/সম্পন্ন না হলে আগের পথ: ব্রাউজারে APK-লিঙ্ক।
     */
    fun downloadAndInstall(ctx: Context, info: Info) {
        val app = ctx.applicationContext
        try {
            val url = info.url.trim()
            if (url.isBlank()) return
            apkFile(app).delete()   /* আগের ডাউনলোড মুছে ফেলো */
            val dm = app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(url))
                .setTitle("MovieBoxBD ${info.name}")
                .setDescription("আপডেট ডাউনলোড হচ্ছে…")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(app, null, "update/movieboxbd-update.apk")
            pendingUrl = url
            pendingId = dm.enqueue(req)
            registerDlReceiver(app)
        } catch (_: Exception) {
            /* ডাউনলোড-ম্যানেজার না চললে আগের নিয়ম: ব্রাউজার */
            openBrowser(app, info.url)
        }
    }

    /* ডাউনলোড-শেষ হলে ইনস্টলার খোলা */
    private fun registerDlReceiver(app: Context) {
        if (dlRc != null) return
        val rc = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != pendingId) return
                try {
                    val dm = c.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    dm.query(DownloadManager.Query().setFilterById(id)).use { cur ->
                        if (cur.moveToFirst() &&
                            cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                            DownloadManager.STATUS_SUCCESSFUL
                        ) {
                            val f = apkFile(c)
                            if (f.exists() && f.length() > 0) openInstaller(c, f)
                            else openBrowser(c, pendingUrl)
                        } else openBrowser(c, pendingUrl)
                    }
                } catch (_: Exception) {
                    openBrowser(c, pendingUrl)
                }
            }
        }
        dlRc = rc
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33)
            app.registerReceiver(rc, filter, Context.RECEIVER_NOT_EXPORTED)
        else app.registerReceiver(rc, filter)
    }

    private fun openInstaller(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK
                )
        )
    }

    private fun openBrowser(ctx: Context, url: String) {
        if (url.isBlank()) return
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {}
    }

    /** 🔔 নোটিফিকেশন দেখানোর অনুমতি আছে কি না (Android 13+ এ রানটাইম পারমিশন লাগে) */
    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /* এই ঘোষণার (code+ts) নোটিফিকেশন আগে দেখানো হয়েছে কি না (মার্ক করে না)।
       🆕 ts থাকলে প্রতিটা নতুন ঘোষণাই নতুন পিং — একই code আবার দিলেও সবাই আবার পাবে।
       ts না থাকলে (পুরনো সার্ভার) আগের নিয়ম: এক code = একবার। */
    fun wasNotified(ctx: Context, info: Info): Boolean {
        val p = ctx.getSharedPreferences("mbx_notify", Context.MODE_PRIVATE)
        if (info.ts > 0) return p.getLong("notified_update_ts", 0L) == info.ts
        return p.getInt("notified_update_code", 0) >= info.code
    }

    /* নোটিফিকেশন সফলভাবে দেখানোর পরেই ডাকব — তার আগে কখনো না */
    fun markNotified(ctx: Context, info: Info) {
        ctx.getSharedPreferences("mbx_notify", Context.MODE_PRIVATE)
            .edit()
            .putInt("notified_update_code", info.code)
            .putLong("notified_update_ts", info.ts)   /* 🆕 */
            .apply()
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

            /* 🆕 নোটিফিকেশন ট্যাপ করলে অ্যাপ খুলবে — হোম খুলতেই পপআপ আবার আসবে,
               বাটনে ট্যাপ করলেই ডাউনলোড+ইনস্টলার (ব্রাউজার-ঝামেলা নেই) */
            val target = Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
