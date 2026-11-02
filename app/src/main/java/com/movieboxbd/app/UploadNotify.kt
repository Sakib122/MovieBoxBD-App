package com.movieboxbd.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject

/**
 * 🔔 ব্লগে নতুন মুভি দিলে অ্যাপ নিজে থেকেই নোটিফিকেশন দেখায়।
 * কোনো সার্ভার/ফায়ারবেস লাগে না — অ্যাপ নিজে ব্লগ চেক করে (সম্পূর্ণ ফ্রি)।
 */
class UploadCheckWorker(context: Context, params: WorkerParameters) :
    Worker(context, params) {

    override fun doWork(): Result {
        try {
            if (Config.NOTIFY_NEW_UPLOADS) checkAndNotify(applicationContext)
        } catch (_: Exception) {
        }
        return Result.success()
    }

    private fun checkAndNotify(ctx: Context) {
        val p = ctx.getSharedPreferences("mbx_notify", Context.MODE_PRIVATE)
        val raw = fetchLatest() ?: return
        try {
            val feed = JSONObject(raw)
            val entry = feed.optJSONObject("feed")?.optJSONArray("entry")?.optJSONObject(0)
                ?: return
            val title = entry.optJSONObject("title")?.optString("\$t") ?: return
            val published = entry.optJSONObject("published")?.optString("\$t") ?: ""
            if (published.isEmpty()) return

            val last = p.getString("last_post", "") ?: ""
            if (last.isEmpty()) {
                /* প্রথমবার — শুধু মনে রাখবে, নোটিফিকেশন দেবে না */
                p.edit().putString("last_post", published).apply()
            } else if (published > last) {
                p.edit().putString("last_post", published).apply()
                notifyNew(ctx, title)
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
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 MovieBoxBD/2.0")
            if (conn.responseCode in 200..299)
                conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            else null
        } catch (_: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    private fun notifyNew(ctx: Context, title: String) {
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
            val n = NotificationCompat.Builder(ctx, "new_upload")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("🎬 নতুন মুভি এসেছে!")
                .setContentText(title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(title))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(1001, n)
        } catch (_: Exception) {
        }
    }
}
