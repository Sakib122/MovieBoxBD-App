package com.movieboxbd.app

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 📊 অ্যাডমিন প্যানেলের জন্য হালকা ট্র্যাকিং — তোমার নিজের koyeb সার্ভারে পাঠায়।
 *
 * কী পাঠায়:
 *  install  → অ্যাপ প্রথমবার খুললে (নতুন ইউজার জয়েন)
 *  app_open → অ্যাপ খোলা হলো (আজ কতজন অ্যাক্টিভ)
 *  play     → কোন মুভি প্লে হলো (প্রতি মুভির ভিউ)
 *  download → কোন মুভি ডাউনলোড হলো (প্রতি মুভির ডাউনলোড)
 *
 * 🔒 শুধু অ্যাপের নিজের আইডি + ইভেন্টের নাম + মুভির নাম যায় — কোনো ব্যক্তিগত
 *    তথ্য নেই। সার্ভার বন্ধ/ইন্টারনেট না থাকলে সাইলেন্টলি বাদ যায়, অ্যাপ আটকায় না।
 */
object Stats {

    private const val PREFS = "mbx_stats"
    private const val ID_KEY = "install_id"

    /** এই ফোনের গোপন আইডি — প্রথমবার বানালে "install" ইভেন্টও যায় */
    private fun ensureId(ctx: Context): String {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var id = p.getString(ID_KEY, null)
        if (id.isNullOrBlank()) {
            id = java.util.UUID.randomUUID().toString().replace("-", "").take(24)
            p.edit().putString(ID_KEY, id).apply()
            send(ctx, "install", "")
        }
        return id
    }

    fun appOpen(ctx: Context) {
        ensureId(ctx)
        send(ctx, "app_open", "")
    }

    /** ডিটেইল পেজ খোলা = এই মুভির এক ইম্প্রেশন */
    fun view(ctx: Context, movieTitle: String) = send(ctx, "view", movieTitle)

    fun play(ctx: Context, movieTitle: String) = send(ctx, "play", movieTitle)

    /** 🎬 (v4.0) প্লে চলাকালীন হার্টবিট — প্যানেলের "▶️ এখন যা দেখা হচ্ছে" লাইভ-বক্সের জন্য */
    fun watch(ctx: Context, label: String) = send(ctx, "watch", label)

    /** 🎬 (v4.1) দেখা বন্ধ — প্যানেলের লাইভ-বক্স থেকে সাথে সাথে নামিয়ে দেয় (২ মিনিট অপেক্ষা নেই) */
    fun watchStop(ctx: Context) = send(ctx, "watch_stop", "")

    fun download(ctx: Context, movieTitle: String) = send(ctx, "download", movieTitle)

    /** 💓 হার্টবিট — অ্যাপ চালু আছে প্রমাণ। এটার জন্যই প্যানেলের "🔴 এখন লাইভ" কাজ করে */
    fun beat(ctx: Context) = send(ctx, "beat", "")

    /** ব্যাকগ্রাউন্ড থ্রেডে ছোট POST — কোনো এরর হলে চুপচাপ বাদ (অ্যাপে কোনো প্রভাব নেই) */
    private fun send(ctx: Context, event: String, label: String) {
        val api = Config.ADMIN_API.trimEnd('/')
        if (api.isBlank()) return
        Thread {
            var c: HttpURLConnection? = null
            try {
                val body = JSONObject().apply {
                    put("id", ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getString(ID_KEY, "").orEmpty())
                    put("event", event)
                    put("label", label.take(120))
                    put("model", "${Build.MANUFACTURER} ${Build.MODEL}".take(48))
                    put("ver", BuildConfig.VERSION_NAME)
                }
                val conn = URL("$api/api/track").openConnection() as HttpURLConnection
                c = conn
                conn.requestMethod = "POST"
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("User-Agent", "MovieBoxBD/${BuildConfig.VERSION_NAME}")
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
                conn.responseCode
            } catch (_: Exception) {
            } finally {
                c?.disconnect()
            }
        }.start()
    }
}
