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

    fun load(view: ImageView, url: String?, pixelate: Boolean = false) {
        if (url.isNullOrBlank()) return
        val key = if (pixelate) "$url|px" else url
        view.tag = key
        val hit = cache.get(key)
        if (hit != null) {
            view.setImageBitmap(hit)
            return
        }
        view.setImageDrawable(null)
        Thread {
            var bmp = download(url) ?: return@Thread
            if (pixelate) bmp = pixelate(bmp)   /* 🔞 এডাল্ট — ঝাপসা */
            cache.put(key, bmp)
            view.post { if (view.tag == key) view.setImageBitmap(bmp) }
        }.start()
    }

    /* 🔞 এডাল্ট পোস্টার — পিক্সেল করে ঝাপসা, কেউ চিনতে পারবে না (কোনো লাইব্রেরি লাগে না) */
    private fun pixelate(src: Bitmap): Bitmap {
        val w = (src.width / 16).coerceIn(6, 40)
        val h = (src.height / 16).coerceIn(6, 40)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        return Bitmap.createScaledBitmap(small, src.width, src.height, true)
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
