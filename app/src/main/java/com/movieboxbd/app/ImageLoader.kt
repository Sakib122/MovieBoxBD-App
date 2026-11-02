package com.movieboxbd.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import android.util.LruCache
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** পোস্টার ছবি লোডার — মেমোরি ক্যাসহ (কোনো এক্সটার্নাল লাইব্রেরি লাগে না) */
object ImageLoader {

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(4 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun load(view: ImageView, url: String?) {
        if (url.isNullOrBlank()) return
        view.tag = url
        val hit = cache.get(url)
        if (hit != null) {
            view.setImageBitmap(hit)
            return
        }
        view.setImageDrawable(null)
        Thread {
            val bmp = download(url)
            if (bmp != null) {
                cache.put(url, bmp)
                view.post { if (view.tag == url) view.setImageBitmap(bmp) }
            }
        }.start()
    }

    private fun download(url: String): Bitmap? {
        var conn: HttpURLConnection? = null
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            conn = c
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) MovieBoxBD/1.0")
            if (c.responseCode !in 200..299) return null
            /* BufferedInputStream mark/reset সাপোর্ট করে */
            val ins = java.io.BufferedInputStream(c.inputStream)
            /* প্রথমে সাইজ মেপে প্রয়োজনমতো ছোট করে ডিকোড (মেমোরি বাঁচায়) */
            val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ins.mark(1 shl 20)
            BitmapFactory.decodeStream(ins, null, opt)
            ins.reset()
            var sample = 1
            val target = 400
            while (opt.outWidth / (sample * 2) >= target) sample *= 2
            val real = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeStream(ins, null, real)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
