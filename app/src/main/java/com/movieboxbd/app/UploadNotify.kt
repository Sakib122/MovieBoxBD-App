package com.movieboxbd.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject

/**
 * 🔔 ব্লগে নতুন মুভি দিলে অ্যাপ নিজে থেকেই নোটিফিকেশন দেখায় (সম্পূর্ণ ফ্রি)।
 *
 * v2.5:
 *  — 🖼 নোটিফিকেশনে এখন মুভির পোস্টারও থাকে (ছোট করে নাম + ছবি —
 *    স্ক্রিনে টানলে বড় ছবিসহ খুলে) — ইউজার দেখেই বুঝে কোন মুভি এসেছে
 *  — 🚀 অ্যাডমিন প্যানেলে নতুন আপডেট দিলে সেটারও নোটিফিকেশন যায় (প্রতি ভার্সনে একবার)
 */
class UploadCheckWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {

    override fun doWork(): Result {
        try {
            if (Config.NOTIFY_NEW_UPLOADS) checkAndNotify(applicationContext)
        } catch (_: Exception) {
        }
        try {
            checkAppUpdate(applicationContext)
        } catch (_: Exception) {
        }
        return Result.success()
    }

    /* ── 🎬 নতুন মুভি চেক + রিচ নোটিফিকেশন ─────────────────── */

    private fun checkAndNotify(ctx: Context) {
        val p = ctx.getSharedPreferences("mbx_notify", Context.MODE_PRIVATE)
        val raw = fetchLatest() ?: return
        try {
            val feed = JSONObject(raw)
            val entry = feed.optJSONObject("feed")?.optJSONArray("entry")?.optJSONObject(0)
                ?: return
            val title = entry.optJSONObject("title")?.optString("\$t") ?: return
            val content = entry.optJSONObject("content")?.optString("\$t").orEmpty()
            val published = entry.optJSONObject("published")?.optString("\$t") ?: ""
            if (published.isEmpty()) return

            val last = p.getString("last_post", "") ?: ""
            if (last.isEmpty()) {
                /* প্রথমবার — শুধু মনে রাখবে, নোটিফিকেশন দেবে না */
                p.edit().putString("last_post", published).apply()
            } else if (published > last) {
                p.edit().putString("last_post", published).apply()
                val poster = findPoster(content)
                notifyNew(ctx, title, poster)
            }
        } catch (_: Exception) {
        }
    }

    private fun fetchLatest(): String? {
        var c: java.net.HttpURLConnection? = null
        return try {
            val url = java.net.URL(
                Config.SITE.trimEnd('/') +
                    "/feeds/posts/default?alt=json&max-results=1&orderby=published"
            )
            val conn = url.openConnection() as java.net.HttpURLConnection
            c = conn
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 MovieBoxBD/2.5")
            if (conn.responseCode in 200..299)
                conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            else null
        } catch (_: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    /** পোস্টের HTML থেকে প্রথম ছবিটা (পোস্টার) */
    private fun findPoster(html: String): String? {
        if (html.isEmpty()) return null
        val m = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)
            ?: return null
        var u = m.groupValues[1].trim()
        u = u.replace("&amp;", "&")
        /* Blogger-এর ছবি ছোট ভার্সনে চাই — =w250 জাতীয় সাফিক্স থাকলে বড় করে নিই */
        u = Regex("=w\\d+-h\\d+.*$").replace(u, "=w600")
        return u
    }

    /** URL থেকে বিটম্যাপ (maxDim-এর চেয়ে বড় হলে ছোট করে) */
    private fun loadBitmap(urlStr: String, maxDim: Int): Bitmap? {
        var c: java.net.HttpURLConnection? = null
        return try {
            val conn = java.net.URL(urlStr).openConnection() as java.net.HttpURLConnection
            c = conn
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) return null
            val raw = BitmapFactory.decodeStream(conn.inputStream) ?: return null
            val big = maxOf(raw.width, raw.height)
            if (big <= maxDim) raw
            else {
                val k = big.toFloat() / maxDim
                Bitmap.createScaledBitmap(
                    raw,
                    (raw.width / k).toInt().coerceAtLeast(1),
                    (raw.height / k).toInt().coerceAtLeast(1),
                    true
                )
            }
        } catch (_: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    private fun notifyNew(ctx: Context, title: String, posterUrl: String?) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        "new_upload", "নতুন মুভি",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
            if (Build.VERSION.SDK_INT >= 33 &&
                ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return

            val intent = Intent(ctx, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val b = NotificationCompat.Builder(ctx, "new_upload")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("🎬 নতুন মুভি এসেছে!")
                .setContentText(title)
                .setContentIntent(pi)
                .setAutoCancel(true)

            /* 🖼 মুভির ছবি — ছোট (লার্জ আইকন) + টানলে বড় (BigPicture) */
            if (!posterUrl.isNullOrBlank()) {
                val big = loadBitmap(posterUrl, 640)
                if (big != null) {
                    val small = if (maxOf(big.width, big.height) > 160)
                        Bitmap.createScaledBitmap(big, 160, 160 * big.height / big.width, true)
                    else big
                    b.setLargeIcon(small)
                    b.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(big)
                            .setBigContentTitle("🎬 নতুন মুভি এসেছে!")
                            .setSummaryText("MovieBoxBD — ${title.take(60)}")
                    )
                } else {
                    b.setStyle(NotificationCompat.BigTextStyle().bigText(title))
                }
            } else {
                b.setStyle(NotificationCompat.BigTextStyle().bigText(title))
            }

            nm.notify(1001, b.build())
        } catch (_: Exception) {
        }
    }

    /* ── 🚀 অ্যাপ আপডেট নোটিফিকেশন (অ্যাডমিন প্যানেল কন্ট্রোলড) ──
       🛠 v2.6 ফিক্স: আগে পারমিশন না থাকলেও "পাঠানো হয়েছে" মার্ক হয়ে যেত —
       ফলে নোটিফিকেশন কখনোই আসত না। এখন: পারমিশন না থাকলে মার্ক ছাড়াই ফেরত
       (পরের চেকে আবার চেষ্টা হবে), আর আসলেই পাঠানোর পরে মার্ক হয়। */

    private fun checkAppUpdate(ctx: Context) {
        val info = Updater.fetch() ?: return
        if (!Updater.isNewer(info)) return
        if (!Updater.hasPermission(ctx)) return          /* পরের চেকে আবার চেষ্টা হবে */
        if (Updater.wasNotified(ctx, info)) return
        if (Updater.postUpdateNotification(ctx, info)) {
            Updater.markNotified(ctx, info)
        }
    }
}
