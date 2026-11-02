package com.movieboxbd.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import java.net.HttpURLConnection
import java.net.URL

/**
 * ডিটেইল পেজ v3:
 *  ▶ দেখো / ⬇ ডাউনলোড / 🌐 ব্রাউজার / ✈ টেলিগ্রাম — এই ৪টা বাটনেই আগে অ্যাড আসবে,
 *    অ্যাড শেষ হলে কাজটা হবে (অ্যাড প্রতিবার, বাইপাস নেই)
 *  📜 ফাইল/এপিসোড বেশি থাকলে প্রথম ৪টা উপরে দেখাবে + "সবগুলো দেখো" বাটনে
 *    সুন্দর স্ক্রলেবল বটম-শিটে পুরো লিস্ট
 *  ফাইলের নাম ওয়েবসাইটের আসল ডাটা — ফুল সিরিজ হলে "ফুল সিরিজ (Full Complete)" লেখা থাকবে
 */
class DetailActivity : AppCompatActivity() {

    companion object {
        /** ডিটেইল পেজে সরাসরি কয়টা ফাইল দেখানো হবে (বাকিটা বটম শিটে) */
        const val INLINE_COUNT = 4

        fun intent(ctx: Context, m: MovieItem): Intent {
            val i = Intent(ctx, DetailActivity::class.java)
            i.putExtra("item", m)
            return i
        }
    }

    private var series = false
    private var movie: MovieItem? = null /* 📥 ডাউনলোড ফাইলের নামে মুভির নাম বসাতে */
    private val ui = Handler(Looper.getMainLooper())
    private var gateCount: TextView? = null
    private var gateLeft = 0
    private var sheet: BottomSheetDialog? = null

    private val gateTick = object : Runnable {
        override fun run() {
            if (gateLeft <= 0) {
                finishGate()
                return
            }
            gateCount?.text = gateLeft.toString()
            gateLeft--
            ui.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val m = intent.getSerializableExtra("item") as? MovieItem ?: run { finish(); return }
        movie = m
        series = BloggerApi.isWebSeries(m.title, m.labels)
        Stats.view(this, m.title) /* 📊 অ্যাডমিন প্যানেলে এই মুভির ইম্প্রেশন */

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.dTitle).text = m.title
        findViewById<TextView>(R.id.dMeta).text = buildString {
            append(m.labels.joinToString(" • "))
            if (m.date.isNotBlank()) {
                if (isNotEmpty()) append("  |  ")
                append(m.date)
            }
        }
        findViewById<TextView>(R.id.dBadge).text = if (series) "📺 ওয়েব সিরিজ" else "🎬 মুভি"
        ImageLoader.load(findViewById(R.id.dPoster), m.poster)

        /* ওয়েবসাইটে বিস্তারিত */
        findViewById<View>(R.id.btnWeb).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.permalink))) }
            catch (_: Exception) {}
        }

        /* স্ট্রিম লিংক না থাকলে সুন্দর কার্ড দেখাও — ওয়েবসাইটে যাওয়ার বাটনসহ */
        val hasStream = m.links.any { it.streamUrl.isNotBlank() }
        findViewById<View>(R.id.noVideoNote).visibility =
            if (hasStream) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnNoteWeb).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.permalink.ifBlank { Config.SITE })))
            } catch (_: Exception) {}
        }

        /* ✈️ টেলিগ্রাম চ্যানেল — ডিটেইল পেজে + নো-ভিডিও কার্ডে */
        findViewById<View>(R.id.btnTgChannel).setOnClickListener { openChannel() }
        findViewById<View>(R.id.btnNoteTg).setOnClickListener { openChannel() }

        /* 🔥 সার্ভার আগে থেকে জাগানো — koyeb ঠান্ডা হলেও প্লে চাপলে স্ট্রিম দ্রুত আসে */
        m.links.firstOrNull { it.streamUrl.isNotBlank() }?.streamUrl?.let { preWarm(it) }

        /* ব্যানার অ্যাড (তোমার Adsterra কোড) */
        Ads.applyBanner(findViewById(R.id.adBanner), Config.ADSTERRA_BANNER_320x50)
        Ads.applyBanner(findViewById(R.id.adRect), Config.ADSTERRA_BANNER_300x250)

        /* ফাইল লিস্ট — প্রথম ৪টা সরাসরি, বাকিটা বটম শিটে */
        val list = findViewById<RecyclerView>(R.id.linkList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = LinkAdapter(m.links.take(INLINE_COUNT), closeSheet = false)

        val btnAll = findViewById<MaterialButton>(R.id.btnAllLinks)
        btnAll.isVisible = m.links.size > INLINE_COUNT
        btnAll.text = "📜 সবগুলো দেখো  (${m.links.size} টা ফাইল)"
        btnAll.setOnClickListener { showAllLinks(m.links) }
    }

    /* ── 📜 পুরো এপিসোড/ফাইল লিস্ট — সুন্দর স্ক্রলেবল বটম শিট ──── */

    private fun showAllLinks(links: List<MediaLink>) {
        val v = LayoutInflater.from(this).inflate(R.layout.sheet_links, null)
        v.findViewById<TextView>(R.id.sheetCount).text = "মোট ${links.size} টা ফাইল"
        val rv = v.findViewById<RecyclerView>(R.id.sheetList)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = LinkAdapter(links, closeSheet = true)
        sheet = BottomSheetDialog(this).apply {
            setContentView(v)
            show()
        }
    }

    private fun closeSheet() {
        sheet?.dismiss()
        sheet = null
    }

    /* ── অ্যাড গেট: ফিরে এলে বাকি সেকেন্ড দেখিয়ে কাজটা করা ─────── */

    override fun onResume() {
        super.onResume()
        val remain = Ads.pendingRemaining(this)
        if (remain < 0) return
        if (remain == 0) startGateCountdown(0)
        else startGateCountdown(remain)
    }

    private fun startGateCountdown(seconds: Int) {
        val overlay = findViewById<View>(R.id.gateOverlay)
        gateCount = findViewById(R.id.gateCount)
        /* কোন কাজ অপেক্ষা করছে সেই মেসেজ */
        val p = getSharedPreferences("mbx_ad", Context.MODE_PRIVATE)
        val type = p.getInt("gate_type", Ads.ACT_PLAY)
        findViewById<TextView>(R.id.gateMsg).text = when (type) {
            Ads.ACT_PLAY -> "অ্যাড দেখা শেষ! প্লে শুরু হচ্ছে..."
            Ads.ACT_DOWNLOAD -> "অ্যাড দেখা শেষ! ডাউনলোড শুরু হচ্ছে..."
            Ads.ACT_TELEGRAM -> "অ্যাড দেখা শেষ! টেলিগ্রাম খুলছে..."
            else -> "অ্যাড দেখা শেষ! ওয়েবসাইট খুলছে..."
        }
        overlay.visibility = View.VISIBLE
        gateLeft = seconds
        ui.removeCallbacks(gateTick)
        ui.post(gateTick)
    }

    private fun finishGate() {
        findViewById<View>(R.id.gateOverlay).visibility = View.GONE
        val a = Ads.consume(this) ?: return
        when (a.type) {
            Ads.ACT_PLAY -> openPlayer(a.url, a.title)
            Ads.ACT_TELEGRAM -> openTelegram(a.url)
            Ads.ACT_DOWNLOAD -> download(a.extra, a.title)
            Ads.ACT_BROWSER -> openBrowser(a.url)
        }
    }

    fun cancelGate(v: View) {
        ui.removeCallbacks(gateTick)
        findViewById<View>(R.id.gateOverlay).visibility = View.GONE
        Ads.cancelGate(this)
        Toast.makeText(this, "বাতিল করা হয়েছে", Toast.LENGTH_SHORT).show()
    }

    private fun openPlayer(url: String, name: String) {
        try {
            val i = Intent(this, PlayerActivity::class.java)
            i.putExtra("url", url)
            i.putExtra("title", name)
            startActivity(i)
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(gateTick)
        findViewById<View>(R.id.gateOverlay).visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacksAndMessages(null)
    }

    /* ── ফাইল লিস্ট (ওয়েবসাইটের আসল নাম ও লিংক) ─────────────── */

    inner class LinkAdapter(
        private val links: List<MediaLink>,
        private val closeSheet: Boolean
    ) : RecyclerView.Adapter<LinkAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.linkName)
            val play: View = v.findViewById(R.id.btnPlay)
            val dl: View = v.findViewById(R.id.btnDownload)
            val web: View = v.findViewById(R.id.btnBrowser)
            val tg: View = v.findViewById(R.id.btnTg)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_link, parent, false)
            return VH(v)
        }

        override fun getItemCount() = links.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val link = links[pos]
            h.name.text = link.name

            /* অ্যাড দেখানোর আগে বটম শিট থাকলে বন্ধ */
            fun beforeAd() {
                if (closeSheet) closeSheet()
                Toast.makeText(
                    this@DetailActivity,
                    "📢 অ্যাডটি ${Config.AD_WATCH_SECONDS} সেকেন্ড দেখো — ফিরে এলে চালিয়ে যাবে!",
                    Toast.LENGTH_LONG
                ).show()
            }

            /* ▶ দেখো — স্ট্রিম লিংক থাকলেই শুধু দেখাবে */
            h.play.visibility =
                if (link.streamUrl.isNotBlank()) View.VISIBLE else View.GONE
            h.play.setOnClickListener {
                beforeAd()
                /* 💰 আগে Direct Link → গেট → প্লে */
                Ads.requestAction(
                    this@DetailActivity, Ads.ACT_PLAY,
                    link.streamUrl, link.name
                )
            }

            /* ⬇ ডাউনলোড — সরাসরি ডাউনলোড (ফোনের ডাউনলোডার, Downloads ফোল্ডারে সেভ হবে)।
               টেলিগ্রামে যাবে না — টেলিগ্রামের জন্য ✈ বাটনই আছে। */
            h.dl.setOnClickListener {
                if (link.streamUrl.isBlank() && link.tgUrl.isBlank()) {
                    Toast.makeText(this@DetailActivity, "ডাউনলোড লিংক নেই", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                beforeAd()
                if (link.streamUrl.isNotBlank()) {
                    Ads.requestAction(
                        this@DetailActivity, Ads.ACT_DOWNLOAD,
                        "", link.name, link.streamUrl
                    )
                } else {
                    /* স্ট্রিম লিংক না থাকলেই শুধু টেলিগ্রাম ফলব্যাক */
                    Ads.requestAction(this@DetailActivity, Ads.ACT_TELEGRAM, link.tgUrl, link.name)
                }
            }

            /* 🌐 ব্রাউজার — ওয়েবসাইটের ওই ফাইলের পেজ */
            h.web.setOnClickListener {
                val url = link.pageUrl.ifBlank { link.streamUrl }
                if (url.isBlank()) return@setOnClickListener
                beforeAd()
                Ads.requestAction(this@DetailActivity, Ads.ACT_BROWSER, url, link.name)
            }

            /* ✈ টেলিগ্রাম — বটের আসল ফাইল লিংক থাকলে দেখাবে */
            h.tg.visibility = if (link.tgUrl.isNotBlank()) View.VISIBLE else View.GONE
            h.tg.setOnClickListener {
                beforeAd()
                Ads.requestAction(this@DetailActivity, Ads.ACT_TELEGRAM, link.tgUrl, link.name)
            }
        }
    }

    /* ── ✈️ টেলিগ্রাম চ্যানেল (t.me/SakibMovieBox) ── */

    private fun openChannel() {
        if (Config.TELEGRAM_URL.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.TELEGRAM_URL)))
            Toast.makeText(this, "✈️ টেলিগ্রাম চ্যানেল খুলছে…", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    /** ডিটেইল পেজ খুললেই ব্যাকগ্রাউন্ডে সার্ভারে হালকা রিকোয়েস্ট — koyeb ঘুম থেকে
     *  জেগে ওঠে, তাই অ্যাড শেষে প্লে চাপলে স্ট্রিম সাথে সাথেই আসে (লোড-ফেল কমায়) */
    private fun preWarm(url: String) {
        Thread {
            var c: HttpURLConnection? = null
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                c = conn
                conn.requestMethod = "GET"
                conn.setRequestProperty("Range", "bytes=0-1")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.responseCode
                conn.inputStream?.close()
            } catch (_: Exception) {
            } finally {
                c?.disconnect()
            }
        }.start()
    }

    /* ── ✈ টেলিগ্রাম বট (আসল ফাইল লিংক: t.me/bot?start=file_ID) ── */

    private fun openTelegram(url: String) {
        if (url.isBlank()) {
            Toast.makeText(this, "টেলিগ্রাম লিংক নেই", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            Toast.makeText(
                this,
                "✈️ টেলিগ্রাম খুলছে — বটে START চাপলেই ফাইল পাঠিয়ে দেবে",
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
        }
    }

    /* ── 🌐 ব্রাউজারে ওই ফাইলের পেজ ─────────────────────────── */

    private fun openBrowser(url: String) {
        if (url.isBlank()) return
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
    }

    /* ── ⬇ সরাসরি ডাউনলোড — ফোনের ডাউনলোডার (Chrome-এর একই ইঞ্জিন),
           Downloads ফোল্ডারে সেভ হয়, নোটিফিকেশনে প্রগ্রেস দেখায় ── */

    /** 📥 ডাউনলোড ফাইলের সুন্দর নাম: "MovieBoxBD.xyz — মুভির নাম [720p WEB-DL]"
     *  ইউজার যেন ডাউনলোড করা ফাইলটা দেখেই বুঝতে পারে কোন মুভি */
    private fun downloadName(rawName: String): String {
        val sb = StringBuilder(Config.BRAND)
        val t = movie?.title?.trim().orEmpty()
        if (t.isNotEmpty()) sb.append(" — ").append(t)
        val link = rawName.trim()
        if (link.isNotEmpty() && !link.equals(t, true)) {
            /* "720p WEB-DL · 1.08 GB" → শুধু "720p WEB-DL"; EP/সিজন নাম হলে পুরোটা */
            val q = link.substringBefore("·").trim()
            if (q.startsWith("EP") || q.startsWith("পুরো") || q.startsWith("ফুল") ||
                q.contains("সিজন") || q.contains("সিরিজ")) {
                sb.append(" — ").append(q)
            } else if (q.isNotEmpty()) {
                sb.append(" [").append(q).append("]")
            }
        }
        /* ফাইলনেমে নিষিদ্ধ চিহ্ন বাদ (বাংলা অক্ষর থাকবে — Android সাপোর্ট করে) */
        var s = sb.toString().replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (s.length > 90) s = s.take(90).trim()
        return s
    }

    private fun download(streamUrl: String, rawName: String) {
        if (streamUrl.isBlank()) {
            Toast.makeText(this, "ডাউনলোড লিংক নেই", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val name = downloadName(rawName)
            val ext = if (streamUrl.lowercase().contains(".mp4")) ".mp4" else ".mkv"
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(streamUrl))
                .setTitle(name)
                .setDescription("MovieBoxBD ডাউনলোড")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name + ext)
            dm.enqueue(req)
            Stats.download(this, movie?.title ?: rawName) /* 📊 অ্যাডমিন প্যানেলে ডাউনলোড কাউন্ট */
            Toast.makeText(this, "📥 ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "ডাউনলোড শুরু করা যায়নি", Toast.LENGTH_SHORT).show()
        }
    }
}
