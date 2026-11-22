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
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * ডিটেইল পেজ v4 (v3.0 — রেফারেন্স-অ্যাপের ডিজাইন):
 *  🎬 উপরে মিনি-প্লেয়ার: পোস্টার + বড় ▶ → ট্যাপ করলে (অ্যাড-গেটের পরে) ওখানেই মুভি চলে;
 *     ⛶ দিলে ফুলস্ক্রিন প্লেয়ার (PlayerActivity), 🗕 দিলে ছোট বার — আবার বড় করা যায়;
 *     এক এপিসোড শেষ হলে পরেরটা নিজে থেকেই চলে (অটো-নেক্সট)
 *  📚 "Season ∨" ড্রপ-চিপ → এপিসোড-গ্রিড প্যানেল (সিরিজ) / কোয়ালিটি-গ্রিড (মুভি);
 *     নিচে এপিসোড/কোয়ালিটি চিপস: All + 01 02 03…
 *  ⬇ ফ্লোটিং Download পিল + Download চিপ → পুরো ডাউনলোড-লিস্ট (আগের মতো অ্যাড-গেটসহ)
 *  📤 Share / ✈ Telegram / 🌐 Website চিপস
 *  ⭐ For You — অন্য মুভিগুলোর রিকমেন্ডেশন-গ্রিড (৩ কলাম, ভাষা-ব্যাজসহ)
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

        /* ⭐ For You ক্যাশ — প্রতি ডিটেইল-ওপেনে নেটওয়ার্ক যেন না লাগে (১০ মিনিট ফ্রেশ) */
        private var fyCache: List<MovieItem> = emptyList()
        private var fyTs: Long = 0
    }

    private var series = false
    private var movie: MovieItem? = null
    private val ui = Handler(Looper.getMainLooper())
    private var gateCount: TextView? = null
    private var gateLeft = 0
    private var sheet: BottomSheetDialog? = null

    /* ── 🎬 মিনি-প্লেয়ার ── */
    private var player: ExoPlayer? = null
    private var playingUrl = ""
    private var playingName = ""
    private var minimized = false
    private var miniRequested = false   /* গেট শেষ হলে মিনি-প্লেয়ারে চালাতে হবে */

    private lateinit var playerBox: FrameLayout
    private lateinit var pv: androidx.media3.ui.PlayerView
    private lateinit var posterCover: FrameLayout
    private lateinit var btnMiniPlay: ImageButton
    private lateinit var miniBar: ProgressBar
    private lateinit var miniTime: TextView

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
        Ads.adultNow = BloggerApi.isAdult(m)   /* 🔞 ১৮+ মুভি হলে গেটের অ্যাডও ১৮+ */
        series = BloggerApi.isWebSeries(m.title, m.labels)
        Stats.view(this, m.title) /* 📊 অ্যাডমিন প্যানেলে এই মুভির ইম্প্রেশন */

        playerBox = findViewById(R.id.playerBox)
        pv = findViewById(R.id.playerView)
        posterCover = findViewById(R.id.posterCover)
        btnMiniPlay = findViewById(R.id.btnMiniPlay)
        miniBar = findViewById(R.id.miniBar)
        miniTime = findViewById(R.id.miniTime)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.dTitle).text = m.title
        findViewById<TextView>(R.id.dMeta).text = buildString {
            append(m.labels.joinToString(" • "))
            if (m.date.isNotBlank()) {
                if (isNotEmpty()) append("  |  ")
                append(m.date)
            }
        }
        findViewById<TextView>(R.id.btnSeasonDrop).text =
            if (series) "Season" else "Quality"

        /* ℹ️ Info > — ওয়েবসাইটে বিস্তারিত */
        findViewById<View>(R.id.btnInfo).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.permalink))) } catch (_: Exception) {}
        }
        findViewById<View>(R.id.btnWebChip).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.permalink))) } catch (_: Exception) {}
        }

        /* 📤 শেয়ার */
        findViewById<View>(R.id.btnShare).setOnClickListener {
            try {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${m.title}\n${m.permalink}\n\nMovieBoxBD অ্যাপে সব মুভি ফ্রি!")
                }
                startActivity(Intent.createChooser(i, "শেয়ার করো"))
            } catch (_: Exception) {}
        }

        /* ⬇ Download চিপ + ফ্লোটিং পিল — দুটোই পুরো ডাউনলোড-লিস্ট খোলে (অ্যাড-গেটসহ) */
        findViewById<View>(R.id.btnDlChip).setOnClickListener { showAllLinks(m.links) }
        findViewById<View>(R.id.btnFloatDl).setOnClickListener { showAllLinks(m.links) }

        /* ✈️ টেলিগ্রাম */
        findViewById<View>(R.id.btnTgChip).setOnClickListener { openChannel() }
        findViewById<View>(R.id.btnTgChannel).setOnClickListener { openChannel() }

        /* স্ট্রিম লিংক না থাকলে সুন্দর কার্ড দেখাও */
        val hasStream = m.links.any { it.streamUrl.isNotBlank() }
        findViewById<View>(R.id.noVideoNote).visibility =
            if (hasStream) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnNoteWeb).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.permalink.ifBlank { Config.SITE })))
            } catch (_: Exception) {}
        }
        findViewById<View>(R.id.btnNoteTg).setOnClickListener { openChannel() }

        /* 🔥 সার্ভার আগে থেকে জাগানো — প্লে চাপলে স্ট্রিম দ্রুত আসে */
        m.links.firstOrNull { it.streamUrl.isNotBlank() }?.streamUrl?.let { preWarm(it) }

        /* 💰 ব্যানার: ভেতরে 300×250, নিচে 320×50 — রিয়েল Adsterra কোড (১৮+ মুভিতে অ্যাডাল্ট ব্যানার) */
        Ads.applyBanner(findViewById(R.id.adRect), Ads.bannerHtml(BloggerApi.isAdult(m), false))
        Ads.applyBanner(findViewById(R.id.adBanner), Ads.bannerHtml(BloggerApi.isAdult(m), true))

        /* ▶ বড় প্লে-বাটন — প্রথম লিংক মিনি-প্লেয়ারে (অ্যাড-গেটের পরে) */
        findViewById<View>(R.id.btnMiniPlayBig).setOnClickListener {
            m.links.firstOrNull { it.streamUrl.isNotBlank() }?.let { playLink(it, true) }
        }
        /* 🎛 মিনি-কন্ট্রোল */
        btnMiniPlay.setOnClickListener { togglePlay() }
        findViewById<View>(R.id.btnMiniMin).setOnClickListener { toggleMiniSize() }
        findViewById<View>(R.id.btnMiniMax).setOnClickListener { openFullscreen() }

        /* 📚 এপিসোড/কোয়ালিটি চিপস + ড্রপ-প্যানেল */
        buildLinkChips(m)
        findViewById<View>(R.id.btnSeasonDrop).setOnClickListener { toggleSeasonPanel() }
        findViewById<View>(R.id.btnPanelClose).setOnClickListener {
            findViewById<View>(R.id.seasonPanel).visibility = View.GONE
        }

        /* ⭐ For You — রিকমেন্ডেশন গ্রিড */
        loadForYou(m)
    }

    /* ══════════ 🎬 মিনি-প্লেয়ার ══════════ */

    /** ▶ লিংক প্লে — ইউজারের ট্যাপ হলে আগে অ্যাড-গেট, এরপর মিনি-প্লেয়ার */
    private fun playLink(link: MediaLink, fromUser: Boolean) {
        if (link.streamUrl.isBlank()) return
        if (fromUser) {
            val free = Ads.requestAction(this, Ads.ACT_PLAY, link.streamUrl, link.name)
            if (!free) {
                miniRequested = true
                Toast.makeText(this, "অ্যাড খোলা হয়েছে — ফিরে এসে ${Config.AD_WATCH_SECONDS} সেকেন্ডের কাউন্টডাউন শেষ করলেই চলবে", Toast.LENGTH_LONG).show()
                return
            }
        }
        startMini(link.streamUrl, link.name, 0)
    }

    /** মিনি-প্লেয়ারে চালাও — poster সরে গিয়ে ভিডিও বসে; শেষ হলে পরের এপিসোড অটো-প্লে */
    private fun startMini(url: String, name: String, startPos: Long) {
        if (playingUrl == url && player != null) {
            player?.play(); return
        }
        releaseMini()
        playingUrl = url
        playingName = name
        posterCover.visibility = View.GONE
        pv.visibility = View.VISIBLE
        val p = ExoPlayer.Builder(this, DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultHttpDataSource.Factory()
                        .setConnectTimeoutMs(30_000)
                        .setReadTimeoutMs(30_000)
                        .setAllowCrossProtocolRedirects(true)
                )
            )
            .build()
        player = p
        pv.player = p
        p.setMediaItem(MediaItem.fromUri(url))
        p.playWhenReady = true
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    if (startPos > 0) p.seekTo(startPos)
                    p.play()
                }
                if (state == Player.STATE_ENDED) autoNext()
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(this@DetailActivity, "লোড হচ্ছে না — আবার চেষ্টা করো বা ডাউনলোড করো", Toast.LENGTH_LONG).show()
            }
        })
        p.prepare()
        ui.removeCallbacks(tick)
        ui.post(tick)
    }

    /** ▶/⏸ টগল */
    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    /** 🗕 মিনিমাইজ — প্লেয়ার ছোট বারে নামে; আবার ট্যাপ করলে বড় হয় */
    private fun toggleMiniSize() {
        minimized = !minimized
        playerBox.layoutParams = playerBox.layoutParams.also {
            it.height = dp(if (minimized) 64 else 230)
        }
        findViewById<ImageButton>(R.id.btnMiniMin).setImageResource(
            if (minimized) R.drawable.ic_fullscreen else R.drawable.ic_minimize
        )
    }

    /** ⛶ ফুলস্ক্রিন প্লেয়ার — এখনকার পজিশন সেখানে বসে (ফিরে এলে আবার মিনিতে) */
    private fun openFullscreen() {
        val p = player ?: return
        val pos = p.currentPosition
        p.pause()
        try {
            val i = Intent(this, PlayerActivity::class.java)
            i.putExtra("url", playingUrl)
            i.putExtra("title", playingName)
            i.putExtra("movie_name", movie?.title ?: "")
            i.putExtra("resume_pos", pos)
            startActivity(i)
        } catch (_: Exception) {}
    }

    /** এক এপিসোড শেষ → পরেরটা অটো-প্লে (গেট ছাড়া) */
    private fun autoNext() {
        val links = movie?.links ?: return
        val playable = links.filter { it.streamUrl.isNotBlank() }
        if (playable.size < 2) return
        val idx = playable.indexOfFirst { it.streamUrl == playingUrl }
        val next = playable.getOrNull(idx + 1) ?: return
        startMini(next.streamUrl, next.name, 0)
        Toast.makeText(this, "পরের এপিসোড: ${next.name}", Toast.LENGTH_SHORT).show()
    }

    private fun releaseMini() {
        ui.removeCallbacks(tick)
        player?.release()
        player = null
        pv.player = null
    }

    /** সময়/প্রগ্রেস/প্লে-আইকন — অর্ধ সেকেন্ড পরপর */
    private val tick = object : Runnable {
        override fun run() {
            val p = player
            if (p != null) {
                val dur = p.duration.coerceAtLeast(0)
                val pos = p.currentPosition.coerceAtLeast(0)
                miniTime.text = fmt(pos) + " / " + if (dur > 0) fmt(dur) else "…"
                miniBar.progress = if (dur > 0) ((pos * 100L) / dur).toInt() else 0
                btnMiniPlay.setImageResource(if (p.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            }
            ui.postDelayed(this, 500)
        }
    }

    private fun fmt(ms: Long): String {
        val t = ms.coerceAtLeast(0L) / 1000
        val h = t / 3600
        val mnt = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, mnt, s)
        else String.format(Locale.US, "%02d:%02d", mnt, s)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** 🎬 ফুলস্ক্রিন প্লেয়ার থেকে ফিরে এলে ওখানকার পজিশন থেকেই মিনি চলবে */
    override fun onResume() {
        super.onResume()
        if (playingUrl.isNotBlank() && PlayerActivity.handoffUrl == playingUrl &&
            PlayerActivity.handoffPos >= 0 && player != null
        ) {
            player?.seekTo(PlayerActivity.handoffPos)
            player?.play()
        }
        PlayerActivity.handoffUrl = ""
        PlayerActivity.handoffPos = -1

        /* ⏳ অ্যাড-গেট: ফিরে এলে বাকি সেকেন্ড দেখিয়ে কাজটা করা */
        val remain = Ads.pendingRemaining(this)
        if (remain < 0) return
        startGateCountdown(remain)
    }

    /* ── 📚 এপিসোড/কোয়ালিটি চিপস + ড্রপ-প্যানেল ───────────────── */

    /** All + এপিসোড (সিরিজ: 01 02 03…) / কোয়ালিটি (মুভি: 720p WEB-DL…) চিপস */
    private fun buildLinkChips(m: MovieItem) {
        val wrap = findViewById<LinearLayout>(R.id.linkChips)
        wrap.removeAllViews()
        val links = m.links
        val epRe = Regex("(?i)EP\\s*(\\d+)")
        val numbered = links.mapNotNull { l -> epRe.find(l.name)?.groupValues?.get(1)?.toInt() }
        val sorted: List<MediaLink> =
            if (series && links.size > 1 && numbered.size == links.size)
                links.sortedBy { epRe.find(it.name)?.groupValues?.get(1)?.toInt() ?: 0 }
            else links

        fun addChip(label: String, link: MediaLink?) {
            val tv = TextView(this)
            tv.text = label
            tv.setPadding(dp(14), dp(9), dp(14), dp(9))
            tv.background = getDrawableCompat(R.drawable.bg_chip)
            tv.setTextColor(0xFFFFFFFF.toInt())
            tv.textSize = 13f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = dp(8)
            tv.layoutParams = lp
            tv.setOnClickListener {
                if (link == null) showAllLinks(links)
                else {
                    findViewById<View>(R.id.seasonPanel).visibility = View.GONE
                    playLink(link, true)
                }
            }
            wrap.addView(tv)
        }
        addChip("All", null)
        if (series && links.size > 1 && numbered.size == links.size) {
            for (l in sorted) {
                val n = epRe.find(l.name)?.groupValues?.get(1)?.toInt() ?: 0
                addChip(String.format(Locale.US, "%02d", n), l)
            }
        } else {
            for (l in links) addChip(l.name, l)
        }
    }

    /** "Season ∨" ট্যাপ → নিচে এপিসোড-গ্রিড প্যানেল (রেফারেন্স-অ্যাপের মতো) */
    private fun toggleSeasonPanel() {
        val panel = findViewById<View>(R.id.seasonPanel)
        if (panel.isVisible) {
            panel.visibility = View.GONE
            return
        }
        val m = movie ?: return
        findViewById<TextView>(R.id.seasonTitle).text =
            if (series) "Episodes" else "Quality"
        val grid = findViewById<RecyclerView>(R.id.epGrid)
        grid.layoutManager = GridLayoutManager(this, 5)
        grid.isNestedScrollingEnabled = false
        val links = m.links.filter { it.streamUrl.isNotBlank() || it.tgUrl.isNotBlank() }
        val epRe = Regex("(?i)EP\\s*(\\d+)")
        val numbered = links.mapNotNull { l -> epRe.find(l.name)?.groupValues?.get(1)?.toInt() }
        val useNumbers = series && links.size > 1 && numbered.size == links.size
        val sorted: List<MediaLink> =
            if (useNumbers) links.sortedBy { epRe.find(it.name)?.groupValues?.get(1)?.toInt() ?: 0 }
            else links
        grid.adapter = object : RecyclerView.Adapter<GridVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridVH {
                val tv = TextView(this@DetailActivity)
                tv.gravity = android.view.Gravity.CENTER
                tv.setPadding(0, dp(14), 0, dp(14))
                tv.background = getDrawableCompat(R.drawable.bg_chip)
                tv.setTextColor(0xFFFFFFFF.toInt())
                tv.textSize = 13f
                val lp = gridCellParams()
                tv.layoutParams = lp
                return GridVH(tv)
            }

            override fun getItemCount() = sorted.size
            override fun onBindViewHolder(h: GridVH, position: Int) {
                val l = sorted[position]
                h.tv.text = if (useNumbers)
                    String.format(Locale.US, "%02d", epRe.find(l.name)?.groupValues?.get(1)?.toInt() ?: 0)
                else l.name
                h.tv.setOnClickListener {
                    panel.visibility = View.GONE
                    if (l.streamUrl.isNotBlank()) playLink(l, true) else openTelegram(l.tgUrl)
                }
            }
        }
        panel.visibility = View.VISIBLE
    }

    class GridVH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    /* RecyclerView-র প্যারাম হেল্পার — GridLayoutManager-এর সেল-মার্জিন */
    private fun gridCellParams(): RecyclerView.LayoutParams {
        val lp = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(dp(4), dp(4), dp(4), dp(4))
        return lp
    }

    private fun getDrawableCompat(res: Int): android.graphics.drawable.Drawable? =
        androidx.core.content.ContextCompat.getDrawable(this, res)

    /* ── ⭐ For You — রিকমেন্ডেশন গ্রিড ─────────────────────────── */

    private fun loadForYou(m: MovieItem) {
        val grid = findViewById<RecyclerView>(R.id.forYouGrid)
        grid.layoutManager = GridLayoutManager(this, 3)
        grid.isNestedScrollingEnabled = false

        fun fill(pool: List<MovieItem>) {
            val others = pool.filter {
                it.title != m.title && BloggerApi.isAdult(it) == BloggerApi.isAdult(m)
            }.take(9)
            if (others.isEmpty()) return
            runOnUiThread { if (grid.adapter == null) grid.adapter = ForYouAdapter(others) }
        }

        val fresh = System.currentTimeMillis() - fyTs < 10 * 60_000L
        if (fresh && fyCache.isNotEmpty()) {
            fill(fyCache)
            return
        }
        Thread {
            try {
                val p1 = BloggerApi.load(null, null, 1)
                fyCache = p1.items
                fyTs = System.currentTimeMillis()
                fill(p1.items)
            } catch (_: Exception) {
            }
        }.start()
    }

    inner class ForYouAdapter(private val items: List<MovieItem>) :
        RecyclerView.Adapter<ForYouAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.fyImg)
            val scrim: View = v.findViewById(R.id.fyScrim)
            val badge: TextView = v.findViewById(R.id.fyBadge)
            val title: TextView = v.findViewById(R.id.fyTitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_for_you, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val m = items[position]
            h.title.text = m.title
            val adult = BloggerApi.isAdult(m)
            if (adult) {
                h.scrim.visibility = View.VISIBLE
                h.badge.visibility = View.VISIBLE
                h.badge.text = "18+"
                ImageLoader.load(h.img, m.poster, pixelate = true)
            } else {
                h.scrim.visibility = View.GONE
                val lang = m.labels.firstOrNull {
                    it.equals("Hindi", true) || it.equals("Hindi Dubbed", true) ||
                        it.equals("Bangla", true) || it.equals("Bengali", true) ||
                        it.equals("Korean", true) || it.equals("English", true) ||
                        it.equals("Anime", true) || it.equals("Horror", true)
                }
                if (lang != null) {
                    h.badge.visibility = View.VISIBLE
                    h.badge.text = lang
                } else h.badge.visibility = View.GONE
                ImageLoader.load(h.img, m.poster)
            }
            h.itemView.setOnClickListener {
                val ctx = h.itemView.context
                Ads.onPosterOpened(ctx, adult) /* 💰 পোস্টার-ক্লিকে সাথে সাথে র‍্যান্ডম লিংক */
                ctx.startActivity(DetailActivity.intent(ctx, m))
                (ctx as? android.app.Activity)?.finish()
            }
        }
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

    private fun startGateCountdown(seconds: Int) {
        val overlay = findViewById<View>(R.id.gateOverlay)
        gateCount = findViewById(R.id.gateCount)
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
            Ads.ACT_PLAY -> startMini(a.url, a.title, 0)   /* 🎬 মিনি-প্লেয়ারেই চলবে */
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

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(gateTick)
        findViewById<View>(R.id.gateOverlay).visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacksAndMessages(null)
        releaseMini()
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

            /* 📢 এড খুললে: শিট বন্ধ + মেসেজ। এড-ফ্রি হলে (free=true): শুধু শিট বন্ধ */
            fun beforeAd(free: Boolean) {
                if (closeSheet) closeSheet()
                if (free) return
                Toast.makeText(
                    this@DetailActivity,
                    "অ্যাড খোলা হয়েছে — ফিরে এসে ${Config.AD_WATCH_SECONDS} সেকেন্ডের কাউন্টডাউন শেষ করলেই চলবে",
                    Toast.LENGTH_LONG
                ).show()
            }

            /* ▶ দেখো — মিনি-প্লেয়ারেই চলবে (অ্যাড-গেটের পরে) */
            h.play.visibility =
                if (link.streamUrl.isNotBlank()) View.VISIBLE else View.GONE
            h.play.setOnClickListener {
                val free = Ads.requestAction(
                    this@DetailActivity, Ads.ACT_PLAY,
                    link.streamUrl, link.name
                )
                beforeAd(free)
                if (free) startMini(link.streamUrl, link.name, 0)
            }

            /* ⬇ ডাউনলোড — ফোনের ডাউনলোডার (Downloads ফোল্ডারে MovieBoxBD.xyz ক্রেডিটসহ সেভ) */
            h.dl.setOnClickListener {
                if (link.streamUrl.isBlank() && link.tgUrl.isBlank()) {
                    Toast.makeText(this@DetailActivity, "ডাউনলোড লিংক নেই", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val free = if (link.streamUrl.isNotBlank())
                    Ads.requestAction(
                        this@DetailActivity, Ads.ACT_DOWNLOAD,
                        "", link.name, link.streamUrl
                    )
                else
                    Ads.requestAction(this@DetailActivity, Ads.ACT_TELEGRAM, link.tgUrl, link.name)
                beforeAd(free)
                if (free) {
                    if (link.streamUrl.isNotBlank()) download(link.streamUrl, link.name)
                    else openTelegram(link.tgUrl)
                }
            }

            /* 🌐 ব্রাউজার — ওয়েবসাইটের ওই ফাইলের পেজ */
            h.web.setOnClickListener {
                val url = link.pageUrl.ifBlank { link.streamUrl }
                if (url.isBlank()) return@setOnClickListener
                val free = Ads.requestAction(this@DetailActivity, Ads.ACT_BROWSER, url, link.name)
                beforeAd(free)
                if (free) openBrowser(url)
            }

            /* ✈ টেলিগ্রাম — বটের আসল ফাইল লিংক থাকলে দেখাবে */
            h.tg.visibility = if (link.tgUrl.isNotBlank()) View.VISIBLE else View.GONE
            h.tg.setOnClickListener {
                val free = Ads.requestAction(this@DetailActivity, Ads.ACT_TELEGRAM, link.tgUrl, link.name)
                beforeAd(free)
                if (free) openTelegram(link.tgUrl)
            }
        }
    }

    /* ── ✈️ টেলিগ্রাম চ্যানেল (t.me/SakibMovieBox) ── */

    private fun openChannel() {
        if (Config.TELEGRAM_URL.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.TELEGRAM_URL)))
            Toast.makeText(this, "টেলিগ্রাম চ্যানেল খুলছে", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    /** ডিটেইল পেজ খুললেই ব্যাকগ্রাউন্ডে সার্ভারে হালকা রিকোয়েস্ট — koyeb জেগে ওঠে */
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
                "টেলিগ্রাম খুলছে — বটে START চাপলেই ফাইল পাঠিয়ে দেবে",
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

    /* ── ⬇ সরাসরি ডাউনলোড — ক্রোমে খুলে যায় (পজ/রিজিউম + নোটিফিকেশন) ── */

    /* 🧹 ডাউনলোড ফাইলের সুন্দর নাম: "MovieBoxBD.xyz — মুভির নাম [720p WEB-DL]" —
       ডাউনলোড করা প্রতিটা ফাইলে সাইটের ক্রেডিট থাকবে */
    private fun downloadName(raw: String): String {
        val credit = Config.BRAND
        val title = movie?.title?.takeIf { it.isNotBlank() } ?: ""
        val body = if (title.isBlank()) raw
                   else if (raw.isBlank() || title.contains(raw)) title
                   else "$title [$raw]"
        val bad = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
        val clean = if (body.isBlank()) credit else "$credit — $body"
        val out = clean.filter { it !in bad }.trim()
        return if (out.isBlank()) credit else out.take(60)
    }

    private fun download(streamUrl: String, rawName: String) {
        if (streamUrl.isBlank()) {
            Toast.makeText(this, "ডাউনলোড লিংক নেই", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val i = Intent(Intent.ACTION_VIEW, Uri.parse(streamUrl))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            i.setPackage("com.android.chrome")
            startActivity(i)
        } catch (_: Exception) {
            try {
                val i = Intent(Intent.ACTION_VIEW, Uri.parse(streamUrl))
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            } catch (_: Exception) {
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
                } catch (_: Exception) {}
            }
        }
        Stats.download(this, movie?.title ?: rawName) /* 📊 অ্যাডমিন প্যানেলে ডাউনলোড কাউন্ট */
        Toast.makeText(this, "ক্রোমে খুলেছে — ভিডিও চললে প্লেয়ারের মেনু থেকে Download চাপো (পজ/রিজিউম করা যায়)", Toast.LENGTH_LONG).show()
    }
}
