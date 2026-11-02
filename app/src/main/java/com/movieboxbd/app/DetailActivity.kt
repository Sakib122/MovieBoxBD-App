package com.movieboxbd.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * ডিটেইল পেজ v2:
 *  ▶ দেখো      → ওয়েবসাইটের আসল স্ট্রিম লিংক (না থাকলে বাটন লুকাবে)
 *                প্রতিবার Direct Link খুলবে → ১০ সেকেন্ড গেট → তারপর প্লে
 *  ⬇ ডাউনলোড  → ওয়েবসাইটের আসল ডাউনলোড লিংক = টেলিগ্রাম বট (t.me/bot?start=file_ID)
 *                বট সেট না থাকলে ফলব্যাক: সরাসরি ডাউনলোড
 *  🌐 ব্রাউজার  → ওয়েবসাইটের সেই ফাইলের পেজ (/watch/ — ওখানে ডাউনলোড পেজও আছে)
 *  ✈ টেলিগ্রাম → টেলিগ্রাম বটের লিংক
 *  স্ট্রিম লিংক না থাকলে উপরে লেখা দেখাবে: "এখানে ভিডিও নেই — ডাউনলোড করে দেখো"
 */
class DetailActivity : AppCompatActivity() {

    companion object {
        fun intent(ctx: Context, m: MovieItem): Intent {
            val i = Intent(ctx, DetailActivity::class.java)
            i.putExtra("item", m)
            return i
        }
    }

    private var series = false
    private val ui = Handler(Looper.getMainLooper())
    private var gateCount: TextView? = null
    private var gateLeft = 0

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
        series = BloggerApi.isWebSeries(m.title, m.labels)

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

        /* স্ট্রিম লিংক না থাকলে নোটিশ দেখাও */
        val hasStream = m.links.any { it.streamUrl.isNotBlank() }
        findViewById<View>(R.id.noVideoNote).visibility =
            if (hasStream) View.GONE else View.VISIBLE

        /* ব্যানার অ্যাড (তোমার Adsterra কোড) */
        Ads.applyBanner(findViewById(R.id.adBanner), Config.ADSTERRA_BANNER_320x50)
        Ads.applyBanner(findViewById(R.id.adRect), Config.ADSTERRA_BANNER_300x250)

        /* ফাইল লিস্ট */
        val list = findViewById<RecyclerView>(R.id.linkList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = LinkAdapter(m.links)
    }

    /* ── অ্যাড গেট: ফিরে এলে বাকি সেকেন্ড দেখিয়ে প্লে ─────────── */

    override fun onResume() {
        super.onResume()
        val remain = Ads.pendingRemaining(this)
        if (remain < 0) return
        if (remain == 0) startGateCountdown(3)
        else startGateCountdown(remain)
    }

    private fun startGateCountdown(seconds: Int) {
        val overlay = findViewById<View>(R.id.gateOverlay)
        gateCount = findViewById(R.id.gateCount)
        overlay.visibility = View.VISIBLE
        gateLeft = seconds
        ui.removeCallbacks(gateTick)
        ui.post(gateTick)
    }

    private fun finishGate() {
        findViewById<View>(R.id.gateOverlay).visibility = View.GONE
        val pending = Ads.consume(this)
        if (pending != null) openPlayer(pending.first, pending.second)
    }

    fun cancelGate(v: View) {
        ui.removeCallbacks(gateTick)
        findViewById<View>(R.id.gateOverlay).visibility = View.GONE
        Ads.cancelGate(this)
        Toast.makeText(this, "প্লে বাতিল হয়েছে", Toast.LENGTH_SHORT).show()
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

    inner class LinkAdapter(private val links: List<MediaLink>) :
        RecyclerView.Adapter<LinkAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.linkName)
            val play: View = v.findViewById(R.id.btnPlay)
            val dl: View = v.findViewById(R.id.btnDownload)
            val web: View = v.findViewById(R.id.btnBrowser)
            val tg: View = v.findViewById(R.id.btnTg)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_link, parent, false)
            return VH(v)
        }

        override fun getItemCount() = links.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val link = links[pos]
            h.name.text = link.name

            /* ▶ দেখো — স্ট্রিম লিংক থাকলেই শুধু দেখাবে */
            h.play.visibility =
                if (link.streamUrl.isNotBlank()) View.VISIBLE else View.GONE
            h.play.setOnClickListener {
                /* 💰 প্রতিবার প্লে চাপলেই Direct Link খুলবে → গেট → প্লে */
                Ads.requestPlay(this@DetailActivity, link.streamUrl, link.name)
                Toast.makeText(
                    this@DetailActivity,
                    "📢 অ্যাডটি ${Config.AD_WATCH_SECONDS} সেকেন্ড দেখো — ফিরে এলে প্লে শুরু হবে!",
                    Toast.LENGTH_LONG
                ).show()
            }

            /* ⬇ ডাউনলোড — ওয়েবসাইটের আসল ডাউনলোড = টেলিগ্রাম বট; না থাকলে সরাসরি ডাউনলোড */
            h.dl.setOnClickListener {
                if (link.tgUrl.isNotBlank()) openTelegram(link.tgUrl) else download(link)
            }

            /* 🌐 ব্রাউজার — ওয়েবসাইটের ওই ফাইলের পেজ */
            h.web.setOnClickListener { openBrowser(link) }

            /* ✈ টেলিগ্রাম — বটের আসল ফাইল লিংক থাকলে দেখাবে */
            h.tg.visibility = if (link.tgUrl.isNotBlank()) View.VISIBLE else View.GONE
            h.tg.setOnClickListener { openTelegram(link.tgUrl) }
        }
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

    private fun openBrowser(link: MediaLink) {
        val url = link.pageUrl.ifBlank { link.streamUrl }
        if (url.isBlank()) return
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
    }

    /* ── ⬇ ফলব্যাক: সরাসরি ডাউনলোড (টেলিগ্রাম লিংক না থাকলে) ── */

    private fun download(link: MediaLink) {
        try {
            val name = (link.name.ifBlank { "movie" }).replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val ext = if (link.streamUrl.lowercase().contains(".mp4")) ".mp4" else ".mkv"
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(link.streamUrl))
                .setTitle(name)
                .setDescription("MovieBoxBD ডাউনলোড")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name + ext)
            dm.enqueue(req)
            Toast.makeText(this, "📥 ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "ডাউনলোড শুরু করা যায়নি", Toast.LENGTH_SHORT).show()
        }
    }
}
