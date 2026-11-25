package com.movieboxbd.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.TimeBar
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

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

        /** ⭐ For You-তে এই সংখ্যক র‍্যান্ডম মুভি (মালিকের নির্দেশ v3.7 — ৩০টা) */
        const val FY_COUNT = 30

        /* 🎬 মিনি↔ফুলস্ক্রিন পজিশন-হ্যান্ডওফ (v3.5) — স্ট্যাটিকে রাখায় অ্যাক্টিভিটি
           রিক্রিয়েট হলেও পজিশন হারায় না; মুভির নাম দিয়ে স্কোপড, ফিরে এসে একবারই কনজিউম হয় */
        var hfMovie = ""
        var hfUrl = ""
        var hfName = ""
        var hfPos = -1L

        /* 🎬 (v3.7) সিমলেস হ্যান্ডওফ — মিনির চলমান ExoPlayer ফুলস্ক্রিনে হস্তান্তর/ফেরত;
           একই প্লেয়ার, তাই ভিডিও চলতেই থাকে — নতুন করে লোড/বাফার হয় না */
        var hfPlayer: ExoPlayer? = null

        /* এই অ্যাক্টিভিটি জীবিত কি না (ফুলস্ক্রিন প্লেয়ার ফেরত দেওয়ার সময় দেখা হয়) */
        private var instance: DetailActivity? = null
        fun alive(): Boolean = instance?.let { !it.isFinishing && !it.isDestroyed } == true
    }

    private var series = false
    private var movie: MovieItem? = null
    private val ui = Handler(Looper.getMainLooper())
    /* 📊 (v4.0) মিনি-প্লেয়ারের হার্টবিট — কোন ফোন কোন মুভি/EP দেখছে প্যানেলে লাইভ যাবে */
    private val watchTick = object : Runnable {
        override fun run() {
            try {
                if (player?.isPlaying == true) Stats.watch(this@DetailActivity, playingName)
            } catch (_: Exception) {}
            ui.postDelayed(this, 45000L)
        }
    }
    private var sheet: BottomSheetDialog? = null

    /* ── 🎬 মিনি-প্লেয়ার ── */
    private var player: ExoPlayer? = null
    private var playingUrl = ""
    private var playingName = ""
    private var miniFailShown = false   /* 🎨 (v4.1) একবার ফেইলে একবারই কার্ড — বারবার স্প্যাম নয় */

    private lateinit var playerBox: FrameLayout
    private lateinit var pv: androidx.media3.ui.PlayerView
    private lateinit var posterCover: FrameLayout
    private lateinit var btnMiniPlay: ImageButton
    private lateinit var miniBar: DefaultTimeBar
    private lateinit var miniTime: TextView
    private lateinit var miniCtrl: View
    private lateinit var seekPreview: View
    /* (v3.6) মিনি-পিল = রিয়েল আইকন + শুধু অঙ্ক — কোনো ইমোজি বা বাংলা শব্দ নেই */
    private lateinit var miniPillIcon: ImageView
    private lateinit var miniPillText: TextView

    /* সিক-বার টানার সময় tick যেন প্রিভিউর সাথে না লড়ে */
    private var scrubbing = false

    /* 🎬 (v3.7) মিনির প্লেয়ার এখন ফুলস্ক্রিনের হাতে — মিনি কিছু নিয়ন্ত্রণ করবে না */
    private var handedOut = false

    /* 🎧 ডুয়াল অডিও — ভাষার কোড → ইংরেজি নাম (সিজনের পাশের Audio চিপ) */
    private var miniAudioPick = -1
    private val LANG_NAMES = mapOf(
        "hi" to "Hindi", "hin" to "Hindi",
        "bn" to "Bangla", "ben" to "Bangla", "bang" to "Bangla",
        "en" to "English", "eng" to "English",
        "ta" to "Tamil", "tam" to "Tamil",
        "te" to "Telugu", "tel" to "Telugu",
        "ur" to "Urdu", "urd" to "Urdu",
        "ml" to "Malayalam", "mal" to "Malayalam",
        "kn" to "Kannada", "kan" to "Kannada",
        "mr" to "Marathi", "mar" to "Marathi",
        "pa" to "Punjabi", "pan" to "Punjabi",
        "ko" to "Korean", "kor" to "Korean",
        "ja" to "Japanese", "jpn" to "Japanese",
        "ne" to "Nepali", "nep" to "Nepali"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val m = intent.getSerializableExtra("item") as? MovieItem ?: run { finish(); return }
        movie = m
        Ads.adultNow = BloggerApi.isAdult(m)   /* 🔞 ১৮+ মুভি হলে গেটের অ্যাডও ১৮+ */
        series = BloggerApi.isWebSeries(m.title, m.labels)
        Stats.view(this, m.title) /* 📊 অ্যাডমিন প্যানেলে এই মুভির ইম্প্রেশন */
        ui.postDelayed(watchTick, 15000L)   /* 📊 (v4.0) হার্টবিট-লুপ চালু */

        playerBox = findViewById(R.id.playerBox)
        pv = findViewById(R.id.playerView)
        posterCover = findViewById(R.id.posterCover)
        btnMiniPlay = findViewById(R.id.btnMiniPlay)
        miniBar = findViewById(R.id.miniBar)
        miniTime = findViewById(R.id.miniTime)
        miniCtrl = findViewById(R.id.miniCtrl)
        seekPreview = findViewById(R.id.seekPreview)
        miniPillIcon = findViewById(R.id.miniPillIcon)
        miniPillText = findViewById(R.id.miniPillText)

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

        /* 💰 (v5.0) ব্যানার সম্পূর্ণ বাদ — মৃত ইমপ্রেশন CPM নামাত;
           স্লটগুলো লুকিয়ে রাখা — ফাঁকা জায়গাও থাকবে না */
        hideAdSlot(R.id.adRect)
        hideAdSlot(R.id.adBanner)

        /* ▶ বড় প্লে-বাটন + পোস্টারের যেকোনো জায়গায় ট্যাপ — প্রথম লিংক (অ্যাড-গেটের পরে) */
        findViewById<View>(R.id.btnMiniPlayBig).setOnClickListener { playFirst() }
        posterCover.setOnClickListener { playFirst() }
        /* ▶ নিচের বারের প্লে-বাটন: মুভি চালু না থাকলে প্রথম লিংক, থাকলে প্লে/পজ */
        btnMiniPlay.setOnClickListener {
            if (player == null) playFirst() else togglePlay()
        }
        /* 🔊 ভলিউম আপ/ডাউন — সরাসরি ফোনের মিডিয়া-ভলিউম */
        findViewById<View>(R.id.btnVolDown).setOnClickListener { stepVol(-1) }
        findViewById<View>(R.id.btnVolUp).setOnClickListener { stepVol(+1) }
        /* (v3.5) ⧉ ফ্লোট-বাটন বাদ — ভিডিওর উপর নিচে টানলেই পপআপে ভাসবে */
        /* ⛶ ফুলস্ক্রিন */
        findViewById<View>(R.id.btnMiniMax).setOnClickListener { openFullscreen() }
        /* 📱 ভিডিওর উপর ট্যাপ → কন্ট্রোল দেখাও/লুকাও; নিচে টানলে পপআপ, উপরে টানলে ফুলস্ক্রিন */
        setupMiniPlayerTouch()
        /* ⏱ সিক-বার টানলে ভিডিওর উপরে সময়-প্রিভিউ, ছাড় দিলে সেখানেই সিক */
        miniBar.addListener(object : TimeBar.OnScrubListener {
            override fun onScrubStart(bar: TimeBar, position: Long) {
                scrubbing = true
                seekPreview.visibility = View.VISIBLE
                miniPillText.text = previewText(position)
            }

            override fun onScrubMove(bar: TimeBar, position: Long) {
                miniPillText.text = previewText(position)
            }

            override fun onScrubStop(bar: TimeBar, position: Long, canceled: Boolean) {
                scrubbing = false
                seekPreview.visibility = View.GONE
                if (!canceled) player?.seekTo(position)
            }
        })

        /* 🎧 ডুয়াল অডিও — সিজনের পাশের Audio চিপ */
        findViewById<View>(R.id.btnAudioDrop).setOnClickListener { showMiniAudioDialog() }

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

    /* 💰 (v5.0) ব্যানার-স্লট লুকানো — ভিউ + তার ধারক দুটোই GONE */
    private fun hideAdSlot(id: Int) {
        try {
            val v = findViewById<View>(id)
            v.visibility = View.GONE
            (v.parent as? View)?.visibility = View.GONE
        } catch (_: Exception) {
        }
    }

    /* 📢 (v5.6) গেট-নির্দেশ — তিন ধাপে পরপর তিনটা ছোট টোস্ট। ফোনের স্কিন (Samsung OneUI,
       Vivo/Oppo Funtouch, OnePlus OxygenOS ইত্যাদি) সাধারণ Toast-কে ২ লাইনে কেটে "…"
       বসিয়ে দেয় — লম্বা লেখা ফুল আসে না (মালিকের রিপোর্ট)। তাই লেখা ভেঙে তিনটা ছোট
       টোস্ট — Toast-কিউ পরপর দেখায়, কোনো লাইনই কাটবে না, আর ~১০ সেকেন্ড ধরে
       অ্যাড-পেজের ওপরেই নির্দেশগুলো ভাসতে থাকে */
    private fun gateToast(action: String) {
        val secs = Ads.gateRemainingSec(this)
        Toast.makeText(this, "অ্যাড খোলা হয়েছে — বাকি $secs সেকেন্ড অ্যাড পেজে থাকুন", Toast.LENGTH_LONG).show()
        Toast.makeText(this, "$secs সেকেন্ড পূর্ণ হলে ফোন নিজে থেকে একবার ভাইব্রেট করবে", Toast.LENGTH_LONG).show()
        Toast.makeText(this, "ভাইব্রেট পেলেই ব্যাক করে ফিরে আসুন — তখনই $action", Toast.LENGTH_LONG).show()
    }

    /** ▶ লিংক প্লে — ইউজারের ট্যাপ হলে আগে অ্যাড-গেট, এরপর মিনি-প্লেয়ার
     *  🆕 (v5.2) টোস্টে বাকি সেকেন্ড দেখায় — আগে কিছু জমা থাকলে ১০ না, বাকিটা
     *  💓 (v5.6) নতুন লেখা (৩ ধাপের ছোট টোস্ট) — সময় পূর্ণ হলে ভাইব্রেশন, ভাইব্রেট পেলেই ব্যাক */
    private fun playLink(link: MediaLink, fromUser: Boolean) {
        if (link.streamUrl.isBlank()) return
        if (fromUser) {
            val free = Ads.requestAction(this, Ads.ACT_PLAY, link.streamUrl, link.name)
            if (!free) {
                gateToast("মুভি চলবে")
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
        FloatPlayer.stopIfActive()   /* 🪟 পপআপ ভাসছিল থাকলে আগে বন্ধ — দুটো ভিডিও একসাথে শব্দ করবে না */
        releaseMini()
        playingUrl = url
        playingName = name
        miniFailShown = false
        /* ✅ এখন যেটা প্লে হচ্ছে শুধু সেটাই সবুজ-মার্ক পাবে */
        refreshPlayingMark()
        posterCover.visibility = View.GONE
        pv.visibility = View.VISIBLE
        /* 🎛 কন্ট্রোল বার এখনই দেখা যাবে — প্লে/পজ, সিক, ভলিউম, ফ্লোট, ফুলস্ক্রিন সব হাতের নাগালে */
        miniCtrl.visibility = View.VISIBLE
        /* applicationContext — পপআপে হাতবদল হলেও প্লেয়ার অ্যাক্টিভিটির সাথে বাঁধা থাকবে না */
        val p = ExoPlayer.Builder(applicationContext, DefaultRenderersFactory(applicationContext).setEnableDecoderFallback(true))
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
                /* 🎨 (v4.1) লোডই না হলে MX/VLC রাস্তা দেখাও — এক লিংকে একবারই */
                if (!miniFailShown) {
                    miniFailShown = true
                    showMiniFallback()
                }
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

    /** ▶ প্রথম স্ট্রিম-লিংক চালাও (পোস্টার-ট্যাপ আর বড় প্লে-বাটন — দুটোই এটাই ডাকে) */
    private fun playFirst() {
        /* ⭐ (v3.9 মালিকের নির্দেশ) ফিডে নতুন লিংক আগে থাকে — তাই raw-first মানে
           বড় EP (134)/1080p আগে চলত! এখন ছোট→বড় সর্ট করা লিস্টের প্রথমটাই:
           সিরিজে সবচেয়ে ছোট EP (129), সিনেমায় সবচেয়ে ছোট কোয়ালিটি (480p) আগে
           প্লে হবে; বাড়াতে চাইলে ইউজার নিজে পরের কোয়ালিটি/EP বাছবে — সার্ভারও বাঁচবে। */
        movie?.let { m2 ->
            sortedEpLinks(m2.links.filter { it.streamUrl.isNotBlank() })
                .firstOrNull()?.let { playLink(it, true) }
        }
    }

    /** 🎛 ভিডিওর উপর ট্যাপ → কন্ট্রোল বার দেখাও/লুকাও */
    private fun toggleMiniCtrl() {
        miniCtrl.visibility = if (miniCtrl.isVisible) View.GONE else View.VISIBLE
    }

    /* ── 📱 মিনি-প্লেয়ার টাচ (ফুলস্ক্রিনের জেসচার হুবহু — v3.4):
       ট্যাপ = কন্ট্রোল; বাঁয়ে ডাবল-ট্যাপ = −১০ সে, ডানে = +১০ সে, মাঝে = প্লে/পজ;
       বাঁ পাশে উপর-নিচ = ব্রাইটনেস; ডান পাশে উপর-নিচ = ভলিউম;
       মাঝ বরাবর নিচে টানা = ভাসমান পপআপ, উপরে টানা = ফুলস্ক্রিন ── */
    private fun setupMiniPlayerTouch() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val slop = 24f
        var downX = 0f
        var downY = 0f
        /* ফুলস্ক্রিনের হুবহু নাম্বারিং: 0=none 1=টেনে-সিক 2=ব্রাইটনেস 3=ভলিউম (v3.5) */
        var mode = 0
        var volBase = 0
        var brightBase = 0f
        var seekBase = 0L
        var seekTarget = 0L
        var seekDur = 0L
        var lastTapAt = 0L

        val hidePill = Runnable { seekPreview.visibility = View.GONE }
        /* (v3.6) পিল = রিয়েল আইকন + শুধু অঙ্ক — ইমোজি বা "সেকেন্ড" শব্দ নেই; লেখা ফাঁকা হলে শুধু আইকন */
        fun pill(icon: Int?, text: String) {
            ui.removeCallbacks(hidePill)
            if (icon != null) {
                miniPillIcon.setImageResource(icon)
                miniPillIcon.visibility = View.VISIBLE
            } else miniPillIcon.visibility = View.GONE
            miniPillText.text = text
            miniPillText.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
            seekPreview.visibility = View.VISIBLE
            ui.postDelayed(hidePill, 800)
        }
        /* সিঙ্গেল-ট্যাপ একটু দেরিতে (ডাবল-ট্যাপ যেন আগে ধরা পড়ে) */
        val pendingTap = Runnable { if (player != null) toggleMiniCtrl() }

        pv.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x; downY = ev.y; mode = 0
                    volBase = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    brightBase = window.attributes.screenBrightness.takeIf { it > 0 } ?: 0.5f
                    seekBase = player?.currentPosition ?: 0L
                    seekTarget = seekBase
                    seekDur = player?.duration?.takeIf { it > 0 } ?: 0L
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (mode == 0 && (abs(dx) > slop || abs(dy) > slop)) {
                        ui.removeCallbacks(pendingTap)   /* টানা শুরু — ট্যাপ বাতিল */
                        /* ফুলস্ক্রিনের মতোই: আড়াআড়ি টানলে সিক, বাঁ পাশে উপর-নিচে ব্রাইটনেস, ডান পাশে ভলিউম */
                        mode = if (abs(dx) > slop && abs(dx) > abs(dy)) 1
                        else if (downX < pv.width / 2f) 2 else 3
                    }
                    when (mode) {
                        /* 🎬 আড়াআড়ি টানা → MX-এর মতো সিক: টানতে থাকলে শুধু সময়+অঙ্ক (আইকনে দিক), ছাড় দিলে একবার সিক */
                        1 -> {
                            val span = pv.width.toFloat().takeIf { it > 0 } ?: 1080f
                            val deltaMs = (dx / span * 120_000f).toLong()
                            seekTarget = if (seekDur > 0)
                                (seekBase + deltaMs).coerceIn(0L, seekDur)
                            else (seekBase + deltaMs).coerceAtLeast(0L)
                            val diff = seekTarget - seekBase
                            val extra = if (seekDur > 0) " / ${fmt(seekDur)}" else ""
                            val sign = if (diff >= 0) "+" else "-"
                            pill(
                                if (diff >= 0) R.drawable.ic_ffwd10 else R.drawable.ic_rew10,
                                "${fmt(seekTarget)}$extra  ${sign}${abs(diff) / 1000}"
                            )
                        }
                        /* বাঁ পাশে উপর-নিচ → ☀ ব্রাইটনেস */
                        2 -> {
                            val f = (brightBase - dy / pv.height.coerceAtLeast(1) * 1.2f).coerceIn(0.01f, 1f)
                            val attr = window.attributes
                            attr.screenBrightness = f
                            window.attributes = attr
                            pill(R.drawable.ic_bright, "${(f * 100).toInt()}%")
                        }
                        /* ডান পাশে উপর-নিচ → 🔊 ভলিউম */
                        3 -> {
                            val nv = (volBase + (-dy / 30f)).toInt().coerceIn(0, maxVol)
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
                            pill(R.drawable.ic_vol, "${nv * 100 / maxVol}%")
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (mode == 0) {
                        val dx = ev.x - downX
                        val dy = ev.y - downY
                        when {
                            /* ⬇ নিচে টানলে ভাসমান পপআপে যায় (টেনে সরানো যায়, বন্ধ করা যায়) */
                            dy > dp(90) && abs(dy) > abs(dx) -> {
                                ui.removeCallbacks(pendingTap)
                                floatToPopup()
                            }
                            /* ⬆ উপরে টানলে ফুলস্ক্রিন প্লেয়ারে যায় */
                            dy < -dp(90) && abs(dy) > abs(dx) -> {
                                ui.removeCallbacks(pendingTap)
                                openFullscreen()
                            }
                            else -> {
                                /* ⚡ ট্যাপ / ডাবল-ট্যাপ */
                                val now = System.currentTimeMillis()
                                if (now - lastTapAt < 320) {
                                    ui.removeCallbacks(pendingTap)
                                    lastTapAt = 0
                                    val p = player
                                    val w = pv.width
                                    if (p != null && w > 0) {
                                        when {
                                            ev.x < w / 3f -> {
                                                p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                                                pill(R.drawable.ic_rew10, "10")
                                            }
                                            ev.x > w * 2f / 3f -> {
                                                p.seekTo(p.currentPosition + 10_000)
                                                pill(R.drawable.ic_ffwd10, "10")
                                            }
                                            else -> {
                                                if (p.isPlaying) p.pause() else p.play()
                                                /* (v3.6) শুধু প্লে/পজ আইকন — কোনো লেখা নেই */
                                                pill(
                                                    if (p.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                                                    ""
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    lastTapAt = now
                                    ui.removeCallbacks(pendingTap)
                                    ui.postDelayed(pendingTap, 320)
                                }
                            }
                        }
                    } else {
                        /* টেনে-সিক ছাড় দিলে একবার সেখানেই যাও (ফুলস্ক্রিনের মতো) */
                        if (mode == 1) player?.seekTo(seekTarget)
                        seekPreview.visibility = View.GONE
                    }
                    true
                }
                else -> false
            }
        }
    }

    /* ── ▶ এখন যে এপিসোড প্লে হচ্ছে শুধু সেটার সবুজ-মার্ক (টাইলে সবুজ গ্রেডিয়েন্ট) ── */

    private fun isNowPlaying(name: String) = name == playingName

    /** চলমান এপিসোড বদলালে চিপ-গ্রিড-লিস্ট সব একসাথে নতুন করে আঁকা হয় */
    private fun refreshPlayingMark() {
        movie?.let { buildLinkChips(it) }
        /* প্যানেল খোলা থাকলে গ্রিডও নতুন করে আঁকা হয় */
        val panel = findViewById<View>(R.id.seasonPanel)
        if (panel.isVisible) {
            panel.visibility = View.GONE
            toggleSeasonPanel()
        }
    }

    /* ── 🎧 ডুয়াল অডিও (সিজনের পাশের Audio চিপ — মিনি-প্লেয়ারের অডিও ট্র্যাক বদলায়) ── */

    private fun showMiniAudioDialog() {
        val p = player
        if (p == null) {
            /* 🪟 পপআপ ভাসছে থাকলে ওর অডিও-ই বদলাবে */
            if (FloatPlayer.isActive) FloatPlayer.showAudioDialogFor(this)
            else Toast.makeText(this, "আগে মুভি চালু করো — তারপর অডিও বাছা যাবে", Toast.LENGTH_SHORT).show()
            return
        }
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (groups.isEmpty()) {
            Toast.makeText(this, "এই ভিডিওতে কোনো অডিও ট্র্যাক নেই", Toast.LENGTH_SHORT).show()
            return
        }
        if (groups.size == 1 && miniAudioPick <= 0) {
            Toast.makeText(this, "এই ভিডিওতে একটাই অডিও ট্র্যাক আছে", Toast.LENGTH_SHORT).show()
        }

        val items = ArrayList<String>()
        items.add("অটো (ডিফল্ট)")
        val dup = HashMap<String, Int>()
        for ((i, g) in groups.withIndex()) {
            val f = g.getTrackFormat(0)
            val base = f.language?.trim()?.lowercase()?.substringBefore('-')
                ?.let { LANG_NAMES[it] } ?: "অডিও ${i + 1}"
            val ch = when (f.channelCount) {
                1 -> "মোনো"; 2 -> "স্টেরিও"; 6 -> "5.1"; 8 -> "7.1"; else -> null
            }
            val label = (listOf(base) + listOfNotNull(ch)).joinToString("  •  ")
            val n = (dup[label] ?: 0) + 1
            dup[label] = n
            items.add(if (n > 1) "$label · ট্র্যাক $n" else label)
        }

        /* সুন্দর কাস্টম লিস্ট — বাছাই করা ট্র্যাকে ● বসবে, সব লেখা স্পষ্ট দেখা যাবে */
        var checked = miniAudioPick.coerceIn(0, items.size - 1)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }
        fun redraw() {
            box.removeAllViews()
            items.forEachIndexed { idx, label ->
                box.addView(TextView(this).apply {
                    text = if (idx == checked) "●  $label" else "○  $label"
                    textSize = 16f
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(48, 30, 32, 30)
                    if (idx == checked) setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setOnClickListener { checked = idx; redraw() }
                })
            }
        }
        redraw()
        val wrap = ScrollView(this)
        wrap.addView(box)

        AlertDialog.Builder(this)
            .setTitle("অডিও ট্র্যাক বাছো")
            .setView(wrap)
            .setPositiveButton("ঠিক আছে") { _, _ ->
                val tb = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                tb.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                if (checked > 0) {
                    val g = groups[checked - 1]
                    if (g.length > 0) tb.addOverride(TrackSelectionOverride(g.mediaTrackGroup, 0))
                }
                p.trackSelectionParameters = tb.build()
                miniAudioPick = checked
                Toast.makeText(
                    this,
                    if (checked == 0) "অডিও: অটো (ডিফল্ট)" else "অডিও: ${items[checked]}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    /** 🔊 ভলিউম আপ/ডাউন — ফোনের মিডিয়া-ভলিউম এক ধাপ করে */
    private fun stepVol(delta: Int) {
        val a = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val cur = a.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        a.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (cur + delta).coerceIn(0, max),
            AudioManager.FLAG_SHOW_UI
        )
    }

    /** 🪟 ⧉ ফ্লোট — চলমান ভিডিও ভাসমান পপআপ হয়ে যাবে; ডিটেইল-পেজ আগের মতো থাকবে */
    private fun floatToPopup() {
        val p = player ?: run {
            Toast.makeText(this, "আগে মুভি চালু করো — তারপর পপআপে ভাসবে", Toast.LENGTH_SHORT).show()
            return
        }
        val u = playingUrl
        val n = playingName
        val t = movie?.title ?: ""
        ui.removeCallbacks(tick)
        player = null
        pv.player = null
        pv.visibility = View.GONE
        posterCover.visibility = View.VISIBLE
        miniCtrl.visibility = View.GONE
        FloatPlayer.floatFromDetail(this, p, u, n, t)
        Toast.makeText(this, "পপআপে চলছে — টেনে সরাও, ⛶ দিলে বড়, ✕ দিলে বন্ধ", Toast.LENGTH_SHORT).show()
    }

    /** ⛶ ফুলস্ক্রিন প্লেয়ার — এখনকার পজিশন সেখানে বসে (ফিরে এলে আবার মিনিতে);
     *  সাথে সাজানো এপিসোড-লিস্টও যায় — ফুলস্ক্রিনেই ⏭ নেক্সট বাটন কাজ করে (v3.5) */
    private fun openFullscreen() {
        val p = player ?: return
        /* 🎬 (v3.7 মালিকের নির্দেশ) সিমলেস হ্যান্ডওফ — মিনির চলমান ExoPlayer-ই ফুলস্ক্রিনে
           চলে যায়: ভিডিও চলতেই থাকে, নতুন করে লোড/বাফার হয় না, পজিশন হাতবদলে যায় না।
           ফুলস্ক্রিন থেকে ব্যাক করলে সেইম প্লেয়ার আবার মিনিতে বসে যায় — তখনও লোড নেই। */
        hfMovie = movie?.title ?: ""
        hfUrl = playingUrl
        hfName = playingName
        hfPos = -1L            /* সিমলেস পথে সিক লাগে না — চলমান পজিশনই থাকবে */
        handedOut = true
        hfPlayer = p
        pv.player = null       /* শুধু ভিউ খুলে নেই — প্লেয়ার প্লে করতেই থাকে */
        try {
            /* 📚 এপিসোড-ক্রম — ছোট→বড় (পুরো সিজন শেষে) */
            val sorted = sortedEpLinks(movie?.links?.filter { it.streamUrl.isNotBlank() } ?: emptyList())
            val i = Intent(this, PlayerActivity::class.java)
            i.putExtra("url", playingUrl)
            i.putExtra("title", playingName)
            i.putExtra("movie_name", movie?.title ?: "")
            i.putExtra("resume_pos", -1L)
            i.putExtra("adopt", true)
            i.putStringArrayListExtra("urls", ArrayList(sorted.map { it.streamUrl }))
            i.putStringArrayListExtra("names", ArrayList(sorted.map { it.name }))
            i.putExtra("idx", sorted.indexOfFirst { it.streamUrl == playingUrl })
            startActivity(i)
        } catch (_: Exception) {
            /* ফুলস্ক্রিন খুলতে না পারলে প্লেয়ার মিনিতেই ফিরে বসে */
            handedOut = false
            hfPlayer = null
            pv.player = p
        }
    }

    /** এক এপিসোড শেষ → পরেরটা অটো-প্লে (গেট ছাড়া) */
    private fun autoNext() {
        if (handedOut) return   /* 🎬 প্লেয়ার ফুলস্ক্রিনের হাতে — মিনি নিজে থেকে কিছু বদলাবে না */
        val links = movie?.links ?: return
        val playable = sortedEpLinks(links.filter { it.streamUrl.isNotBlank() })
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
        if (::miniCtrl.isInitialized) miniCtrl.visibility = View.GONE
    }

    /** সময়/সিক-বার/প্লে-আইকন — অর্ধ সেকেন্ড পরপর (সিক টানার সময় প্রিভিউকে বিরক্ত করে না) */
    private val tick = object : Runnable {
        override fun run() {
            val p = player
            if (p != null) {
                val dur = p.duration.coerceAtLeast(0)
                val pos = p.currentPosition.coerceAtLeast(0)
                miniTime.text = fmt(pos) + " / " + if (dur > 0) fmt(dur) else "…"
                if (!scrubbing) {
                    miniBar.setDuration(if (dur > 0) dur else 0)
                    miniBar.setPosition(pos)
                }
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

    /** ⏱ মিনি-বারে টানলে যে প্রিভিউ দেখায়: "০১:২৩ / ১:১৫:৪০" */
    private fun previewText(position: Long): String {
        val dur = player?.duration?.takeIf { it > 0 } ?: 0L
        return if (dur > 0) "${fmt(position)} / ${fmt(dur)}" else fmt(position)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /** 🎬 ফুলস্ক্রিন প্লেয়ার থেকে ফিরে এলে ওখানকার পজিশন থেকেই মিনি চলবে */
    override fun onResume() {
        super.onResume()
        /* 🪟 ভাসমান পপআপ থাকলে এই পেজে এসে বসবে */
        FloatPlayer.reattach(this)

        /* 🎬 (v3.7) ফুলস্ক্রিন থেকে সেইম প্লেয়ার ফিরে এলে — কোনো রিলোড/সিক নেই,
           যেখানে চলছিল সেখান থেকেই মিনিতে চলতে থাকবে */
        if (hfPlayer != null) {
            player = hfPlayer
            hfPlayer = null
            handedOut = false
            pv.player = player
            if (hfUrl.isNotBlank()) playingUrl = hfUrl
            if (hfName.isNotBlank()) playingName = hfName
            hfPos = -1   /* চলমান পজিশনই থাকবে — সিক দরকার নেই */
            /* 🖼 নতুন TextureView-এ ছবি গ্যারান্টি — ডিকোডার সাথে সাথে রেন্ডার শুরু করে */
            player?.let { it.seekTo(it.currentPosition) }
            /* ⭐ (v3.9) ফুলস্ক্রিনে এপিসোড বদলে ফিরলে চিপের সবুজ মার্ক নতুন EP-তে
               সরে যাবে — আগে 129-এই থেমে যেত, অথচ প্লে হচ্ছিল 133! */
            refreshPlayingMark()
        }

        /* 🎬 ফুলস্ক্রিন থেকে ফিরে এলে ওখানকার পজিশন থেকেই মিনি চলবে (v3.5 —
           মুভি-স্কোপড স্ট্যাটিক হ্যান্ডওফ: অ্যাক্টিভিটি রিক্রিয়েট হলেও পজিশন হারায় না) */
        if (movie?.title == hfMovie && hfUrl.isNotBlank() && hfPos >= 0) {
            if (player != null && playingUrl == hfUrl) {
                player?.seekTo(hfPos)
                player?.play()
            } else {
                /* মিনি-প্লেয়ার বন্ধ হয়ে গিয়ে থাকলে ওই এপিসোডই ওই পজিশনে নতুন করে চলবে */
                playingName = hfName
                startMini(hfUrl, hfName, hfPos)
            }
        }
        hfPos = -1   /* হ্যান্ডওফ একবারই কাজ করবে */

        /* 🛡 রিয়েল-কোয়ালিটি গেট (v5.0): অ্যাড-পেজে কাটানো সময় পূর্ণ হলে ফিরেই কাজ —
           আগে ফিরে এলে সতর্কবার্তা (আরও কত সেকেন্ড বাকি ছিল) */
        when (val r = Ads.onGateResume(this)) {
            is Ads.GateResume.Done -> {
                Stats.gatePass(this)
                finishGate()
            }
            is Ads.GateResume.Early -> showGateWarning(r.remaining)
            Ads.GateResume.None -> {}
        }
    }

    /* ── 📚 এপিসোড/কোয়ালিটি চিপস + ড্রপ-প্যানেল ───────────────── */

    /* 📚 (v3.7 মালিকের নির্দেশ) এপিসোড ছোট→বড় ক্রমে (129, 130, 131…); EP-নম্বর নেই
       এমন লিংক (পুরো সিজন/ফুল মুভি) একদম শেষে থাকবে। আগে "সব লিংকেই EP নম্বর থাকতে
       হবে" শর্ত ছিল — পুরো সিজনের মতো একটা লিংক থাকলেই সর্ট বাদ পড়ে ফিডের উল্টা
       ক্রম (134, 133…) দেখাত — ওই বাগই এখন ঠিক। */
    private fun sortedEpLinks(links: List<MediaLink>): List<MediaLink> {
        if (links.size <= 1) return links
        val epRe = Regex("(?i)EP\\s*(\\d+)")
        val qRe = Regex("(?i)(\\d{3,4})\\s*p")
        val numbered = links.filter { epRe.containsMatchIn(it.name) }
        val quality = links.filter { qRe.containsMatchIn(it.name) }
        return when {
            /* 📚 এপিসোড: ছোট→বড় (129, 130, 131…); নম্বর-ছাড়া (পুরো সিজন) শেষে */
            numbered.size >= 2 ->
                numbered.sortedBy { epRe.find(it.name)!!.groupValues[1].toInt() } +
                    links.filter { !epRe.containsMatchIn(it.name) }
            /* 🎬 কোয়ালিটি: ছোট→বড় (480p → 720p → 1080p) — এপিসোডের মতোই (v3.8) */
            quality.size >= 2 ->
                quality.sortedBy { qRe.find(it.name)!!.groupValues[1].toInt() } +
                    links.filter { !qRe.containsMatchIn(it.name) }
            else -> links
        }
    }

    /** All + এপিসোড (সিরিজ: 01 02 03…) / কোয়ালিটি (মুভি: 720p WEB-DL…) চিপস */
    private fun buildLinkChips(m: MovieItem) {
        val wrap = findViewById<LinearLayout>(R.id.linkChips)
        wrap.removeAllViews()
        val links = m.links
        val sorted = sortedEpLinks(links)

        fun addChip(label: String, link: MediaLink?) {
            val tv = TextView(this)
            tv.text = label
            tv.setPadding(dp(14), dp(9), dp(14), dp(9))
            /* ▶ এখন যেটা প্লে হচ্ছে শুধু সেটার চিপে সবুজ মার্ক */
            tv.background = getDrawableCompat(
                if (link != null && isNowPlaying(link.name)) R.drawable.bg_chip_watched
                else R.drawable.bg_chip
            )
            if (link != null && isNowPlaying(link.name)) {
                tv.setTextColor(0xFF22C55E.toInt())
                tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
                /* ⭐ (v3.9) সবুজ-মার্ক চিপ স্ক্রিনের বাইরে থাকলে স্ক্রল করে এনে দেখাও */
                tv.post {
                    var p: android.view.ViewParent? = wrap.parent
                    while (p != null && p !is android.widget.HorizontalScrollView) p = p.parent
                    (p as? android.widget.HorizontalScrollView)?.smoothScrollTo(
                        maxOf(0, tv.left - dp(70)), 0
                    )
                }
            } else tv.setTextColor(0xFFFFFFFF.toInt())
            tv.textSize = 13f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = dp(8)
            tv.layoutParams = lp
            tv.setOnClickListener {
                if (link == null) showAllLinks(sorted)
                else {
                    findViewById<View>(R.id.seasonPanel).visibility = View.GONE
                    playLink(link, true)
                }
            }
            wrap.addView(tv)
        }
        addChip("All", null)
        /* 📚 (v3.7) ছোট→বড় ক্রমে; সাইটের আসল নামই লেবেল (EP 129, EP 130, … পুরো সিজন শেষে) */
        for (l in sorted) addChip(l.name, l)
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
        val sorted = sortedEpLinks(links)
        grid.adapter = object : RecyclerView.Adapter<GridVH>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridVH {
                val tv = TextView(this@DetailActivity)
                tv.gravity = android.view.Gravity.CENTER
                tv.setPadding(0, dp(14), 0, dp(14))
                /* রেফারেন্স-অ্যাপের মতো গোল-কোণা গাঢ় টাইল */
                tv.background = getDrawableCompat(R.drawable.bg_tile)
                tv.setTextColor(0xFFFFFFFF.toInt())
                tv.textSize = 13f
                val lp = gridCellParams()
                tv.layoutParams = lp
                return GridVH(tv)
            }

            override fun getItemCount() = sorted.size
            override fun onBindViewHolder(h: GridVH, position: Int) {
                val l = sorted[position]
                /* 📚 নম্বরযুক্ত এপিসোড 01 02 03… ক্রমে; নম্বর নেই এমন (পুরো সিজন) আসল নামেই */
                h.tv.text = epRe.find(l.name)?.let {
                    String.format(Locale.US, "%02d", it.groupValues[1].toInt())
                } ?: l.name
                /* ▶ এখন প্লে হচ্ছে যেটা — শুধু সেটার টাইল সবুজ-গ্রেডিয়েন্ট (ভিউ রিসাইকেল হয়, তাই দুই দিকই সেট) */
                val now = isNowPlaying(l.name)
                h.tv.background = getDrawableCompat(
                    if (now) R.drawable.bg_tile_playing else R.drawable.bg_tile
                )
                h.tv.setTextColor(if (now) 0xFF22C55E.toInt() else 0xFFFFFFFF.toInt())
                h.tv.setTypeface(
                    h.tv.typeface,
                    if (now) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                )
                h.tv.setOnClickListener {
                    panel.visibility = View.GONE
                    if (l.streamUrl.isNotBlank()) {
                        playLink(l, true)
                    } else {
                        /* ✈ (v5.2) টাইল থেকে টেলিগ্রাম-ওপেনও এখন গেটের ভেতরে — বাইপাস-প্লাগ */
                        val free = Ads.requestAction(this@DetailActivity, Ads.ACT_TELEGRAM, l.tgUrl, l.name)
                        if (free) {
                            openTelegram(l.tgUrl)
                        } else {
                            /* 💓 (v5.6) নতুন লেখা (৩ ধাপের ছোট টোস্ট) — ভাইব্রেট পেলেই ব্যাক */
                            this@DetailActivity.gateToast("টেলিগ্রাম খুলবে")
                        }
                    }
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
        val box = findViewById<LinearLayout>(R.id.forYouBox)

        fun fill(pool: List<MovieItem>) {
            /* ⭐ (v3.7 মালিকের নির্দেশ) For You = যে কোনো জায়গা থেকে ৩০টা র‍্যান্ডম মুভি —
               ১৮+ আর সাধারণ সব মিলিয়ে; ১৮+ কার্ড ব্লার+18+ ব্যাজে, সাধারণগুলো সোজা পোস্টার।
               প্রতিবার নতুন করে শাফল — প্রতি পেজে আলাদা সেট দেখায়। */
            val picked = pool.filter { it.title != m.title }
                .distinctBy { it.title to it.permalink }
                .shuffled()
                .take(FY_COUNT)
            if (picked.isEmpty()) return
            runOnUiThread { buildForYouGrid(box, picked, BloggerApi.isAdult(m)) }
        }

        val fresh = System.currentTimeMillis() - fyTs < 10 * 60_000L
        if (fresh && fyCache.isNotEmpty()) {
            fill(fyCache)
            return
        }
        Thread {
            /* 🔑 (v3.6 ফিক্স) Blogger-এর start-index = আইটেম-অফসেট — আগে ভুলে "2" দিলে
               প্রায় একই পেজ দুবার আসত, তাই অনেক পেজে সাধারণ মুভি মাত্র ৩টা থেকে যেত!
               এখন পেজ-ধরে আনা হয় (1, 1+PAGE_SIZE, 1+2×PAGE_SIZE …) — ৩০টা না হওয়া পর্যন্ত। */
            val pool = ArrayList<MovieItem>()
            val seen = HashSet<String>()
            var start = 1
            for (page in 1..6) {
                try {
                    for (c in BloggerApi.load(null, null, start).items)
                        if (seen.add(c.title)) pool.add(c)
                } catch (_: Exception) {
                }
                if (pool.size >= 90) break   /* ৩০টা র‍্যান্ডমের জন্য যথেষ্ট পুল */
                start += BloggerApi.PAGE_SIZE
                if (start > 120) break   /* সেফটি লিমিট */
            }
            fyCache = pool
            fyTs = System.currentTimeMillis()
            fill(pool)
        }.start()
    }

    /* ⭐ For You (v3.8 মালিকের নির্দেশ): এক রোতে ৩টা পোস্টার, প্রতি ২ রো (৬ মুভি) পরে
       পুরো-প্রস্থ ব্যানার, মোট ৩০টা র‍্যান্ডম মুভি। ১৮+ কার্ড ব্লার+ব্যাজে, সাধারণগুলো সোজা।
       RecyclerView নয় — ScrollView-এর ভেতরে RV এক সারিতে আটকে যাওয়ার পুরনো বাগই
       "সবসময় ৩টা দেখানোর" আসল কারণ ছিল; সরাসরি রো-বানিয়ে বসালে সেই বাগ থাকেই না। */
    private fun buildForYouGrid(box: LinearLayout, items: List<MovieItem>, pageAdult: Boolean) {
        box.removeAllViews()
        if (items.isEmpty()) return
        val inf = LayoutInflater.from(this)
        var i = 0
        while (i < items.size) {
            /* এক ব্লক = ২ রো (৬ মুভি); রো = ৩টা কার্ড */
            val chunk = items.subList(i, minOf(i + 6, items.size)).chunked(3)
            for (row in chunk) {
                val rl = LinearLayout(this)
                rl.orientation = LinearLayout.HORIZONTAL
                for (mItem in row) {
                    val card = inf.inflate(R.layout.item_for_you, rl, false)
                    card.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    val img = card.findViewById<ImageView>(R.id.fyImg)
                    val scrim = card.findViewById<View>(R.id.fyScrim)
                    val badge = card.findViewById<TextView>(R.id.fyBadge)
                    val title = card.findViewById<TextView>(R.id.fyTitle)
                    title.text = mItem.title
                    if (BloggerApi.isAdult(mItem)) {
                        scrim.visibility = View.VISIBLE
                        badge.visibility = View.VISIBLE
                        badge.text = "18+"
                        ImageLoader.load(img, mItem.poster, pixelate = true)
                    } else {
                        scrim.visibility = View.GONE
                        val lang = mItem.labels.firstOrNull {
                            it.equals("Hindi", true) || it.equals("Hindi Dubbed", true) ||
                                it.equals("Bangla", true) || it.equals("Bengali", true) ||
                                it.equals("Korean", true) || it.equals("English", true) ||
                                it.equals("Anime", true) || it.equals("Horror", true)
                        }
                        if (lang != null) {
                            badge.visibility = View.VISIBLE
                            badge.text = lang
                        } else badge.visibility = View.GONE
                        ImageLoader.load(img, mItem.poster)
                    }
                    card.setOnClickListener { v ->
                        /* (v3.7) পোস্টার-ক্লিকের অ্যাড বাদ — সরাসরি মুভিতে */
                        v.context.startActivity(DetailActivity.intent(v.context, mItem))
                    }
                    rl.addView(card)
                }
                while (rl.childCount < 3) rl.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
                box.addView(rl)
            }
            i += 6
            /* 💰 (v5.0) ব্যানার-রো সম্পূর্ণ বাদ — মৃত ইমপ্রেশন CPM নামাত */
        }
    }

    /* ── 📜 পুরো এপিসোড/ফাইল লিস্ট — সুন্দর স্ক্রলেবল বটম শিট ──── */

    private fun showAllLinks(rawLinks: List<MediaLink>) {
        /* 📚 (v3.7) শিটের লিস্টও ছোট→বড় ক্রমে */
        val links = sortedEpLinks(rawLinks)
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

    private fun finishGate() {
        val a = Ads.consume(this) ?: return
        when (a.type) {
            Ads.ACT_PLAY -> startMini(a.url, a.title, 0)   /* 🎬 মিনি-প্লেয়ারেই চলবে */
            Ads.ACT_TELEGRAM -> openTelegram(a.url)
            Ads.ACT_DOWNLOAD -> download(a.extra, a.title)
            Ads.ACT_BROWSER -> openBrowser(a.url)
        }
    }

    fun cancelGate(v: View) {
        Ads.cancelGate(this)
        Stats.gateCancel(this)
        Toast.makeText(this, "বাতিল করা হয়েছে", Toast.LENGTH_SHORT).show()
    }

    /* 🛡 সময়ের আগে ফিরে এসেছে (v5.0) — সতর্কবার্তা। দুই সেক্টরে দুই লেখা (v5.3):
       ▶ প্লে-গেট (v5.2 নিয়ম): দেখা সেকেন্ড জমা থাকে — বাকিটুকু দেখলেই প্লে; পরে কিছুক্ষণ
         যেকোনো মুভি-প্লে অ্যাড-ফ্রি।
       ⬇ ডাউনলোড-সেক্টর (মালিকের নির্দেশ): জমা থাকে না — প্রতিবার ক্লিকেই গেট, আবার
         পুরো ১০ সেকেন্ড এক নিঃশ্বাসে দেখতে হবে। */
    private fun showGateWarning(remainingSec: Int) {
        Stats.gateFail(this, remainingSec)
        val dlSector = Ads.pendingIsDownloadSector(this)
        val watchSec = RemoteAds.watchSeconds()
        val message = if (dlSector)
            "আরও $remainingSec সেকেন্ড বাকি ছিল — ওই চেষ্টার সেকেন্ডগুলো জমা হয়নি।\n\n" +
                "ডাউনলোড • ব্রাউজার • টেলিগ্রাম সেকশনে প্রতিবার ক্লিকেই অ্যাড লাগে, আর প্রতিবারই অ্যাডের পুরো $watchSec সেকেন্ড এক নিঃশ্বাসে দেখতে হয় — আগের চেষ্টার সেকেন্ড কোনোদিন জমা থাকে না।\n\n" +
                "তাই \"বিজ্ঞাপন আবার খুলুন\" চেপে এবার পুরো $watchSec সেকেন্ড দেখে ফিরে আসুন — তখনই আপনার কাজটা সাথে সাথে হবে।\n\n" +
                "এবার সময় পূর্ণ হলে ফোন নিজে থেকে একবার ভাইব্রেট করবে — ভাইব্রেশন পেলেই ব্যাক করে অ্যাপে ফিরে আসুন।"
        else
            "আরও $remainingSec সেকেন্ড বাকি ছিল — চিন্তা নেই, এতক্ষণের দেখা সেকেন্ডগুলো ধরে রাখা আছে।\n\n" +
                "আবার বিজ্ঞাপনে গিয়ে বাকি $remainingSec সেকেন্ড পূর্ণ করে ফিরে আসুন — তখনই মুভিটা চলবে।\n\n" +
                "পুরো দেখা হলে কিছুক্ষণ যেকোনো মুভি-প্লে অ্যাড-ফ্রি (ডাউনলোড • ব্রাউজার • টেলিগ্রাম আলাদা সেক্টর — প্রতিবার ক্লিকেই গেট)।\n\n" +
                "এবার সময় পূর্ণ হলে ফোন নিজে থেকে একবার ভাইব্রেট করবে — ভাইব্রেশন পেলেই ব্যাক করে অ্যাপে ফিরে আসুন।"
        try {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("অ্যাড পুরো দেখা হয়নি")
                .setMessage(message)
                .setPositiveButton("বিজ্ঞাপন আবার খুলুন") { _, _ -> Ads.reopenGateLink(this) }
                .setNegativeButton("বাতিল") { _, _ ->
                    Ads.cancelGate(this)
                    Stats.gateCancel(this)
                }
                .setCancelable(false)
                .show()
        } catch (_: Exception) {
            Ads.cancelGate(this)
        }
    }

    /* ── 🎨 (v4.1) মিনি-প্লেয়ারের ফলব্যাক কার্ড — ফুলস্ক্রিনের ডিজাইনেই, MX/VLC বাটনসহ ── */
    private fun miniCard(
        icon: String, titleTxt: String, msg: String,
        onMx: () -> Unit, onVlc: () -> Unit
    ): androidx.appcompat.app.AlertDialog {
        val px = { d: Int -> (d * resources.displayMetrics.density).toInt() }
        fun txt(text: String, size: Float, color: Int, bold: Boolean): android.widget.TextView =
            android.widget.TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
            }
        fun rowParams(top: Int): android.widget.LinearLayout.LayoutParams =
            android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(top) }

        val card = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(px(22), px(24), px(22), px(18))
            background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_fallback_card)
        }
        val dot = txt(icon, 32f, 0xFFFFFFFF.toInt(), false)
        dot.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_fallback_icon)
        val dotLp = android.widget.LinearLayout.LayoutParams(px(72), px(72))
        dotLp.gravity = android.view.Gravity.CENTER
        card.addView(dot, dotLp)
        card.addView(txt(titleTxt, 17f, 0xFFE9EEFF.toInt(), true), rowParams(14))
        card.addView(txt(msg, 13.5f, 0xFFB9C2E0.toInt(), false), rowParams(8))

        fun btn(label: String, bgRes: Int, txtColor: Int, top: Int, cb: () -> Unit) {
            val b = txt(label, 14f, txtColor, true)
            b.background = androidx.core.content.ContextCompat.getDrawable(this, bgRes)
            b.setPadding(0, px(13), 0, px(13))
            b.setOnClickListener { cb() }
            card.addView(b, rowParams(top))
        }

        var dlg: androidx.appcompat.app.AlertDialog? = null
        btn("▶  MX Player-এ দেখুন", R.drawable.bg_fallback_btn_mx, 0xFF07280F.toInt(), 20, onMx)
        btn("▶  VLC-তে দেখুন", R.drawable.bg_fallback_btn_vlc, 0xFFB9AFFF.toInt(), 10, onVlc)
        val no = txt("এখন না", 13f, 0xFF8B94B8.toInt(), false)
        no.setPadding(0, px(12), 0, px(2))
        no.setOnClickListener { dlg?.dismiss() }
        card.addView(no)

        val wrap = android.widget.FrameLayout(this)
        wrap.setPadding(px(18), px(10), px(18), px(10))
        wrap.addView(card)

        dlg = androidx.appcompat.app.AlertDialog.Builder(this).setView(wrap).create()
        dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        return dlg
    }

    /* 🎨 (v4.1) লোড-ফেইলে মিনি থেকেই MX/VLC চালাও — পুরনো সিস্টেম, এখন মিনিতেও */
    private fun showMiniFallback() {
        val u = playingUrl
        if (u.isBlank()) return
        val MX1 = "com.mxtech.videoplayer.ad"   /* MX Player (ফ্রি) */
        val MX2 = "com.mxtech.videoplayer.pro"  /* MX Player Pro */
        val VLC = "org.videolan.vlc"

        fun launchIn(pkg: String): Boolean {
            return try {
                val i = Intent(Intent.ACTION_VIEW)
                i.setDataAndType(Uri.parse(u), "video/*")
                i.setPackage(pkg)
                i.putExtra("title", playingName)
                startActivity(i)
                true
            } catch (_: Exception) { false }
        }

        fun openStore(pkg: String) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
                } catch (_: Exception) {}
            }
        }

        miniCard(
            "📺",
            "এই ভিডিওটা লোড হচ্ছে না",
            "এই লিংকটা এই ফোনের প্লেয়ারে চলছে না।\n" +
                "MX Player বা VLC দিয়ে চেষ্টা করো — একই লিংকে স্মুথলি চলতে পারে।",
            {
                if (!launchIn(MX1) && !launchIn(MX2)) {
                    Toast.makeText(this, "MX Player ইনস্টল নেই — Play Store খুলছে… ইনস্টল করে ফিরে এলে আবার চাপ দাও", Toast.LENGTH_LONG).show()
                    openStore(MX1)
                }
            },
            {
                if (!launchIn(VLC)) {
                    Toast.makeText(this, "VLC ইনস্টল নেই — Play Store খুলছে… ইনস্টল করে ফিরে এলে আবার চাপ দাও", Toast.LENGTH_LONG).show()
                    openStore(VLC)
                }
            }
        ).also { it.show() }
    }

    override fun onPause() {
        super.onPause()
        /* 🛡 (v5.0) গেট চলমান থাকলে এই মুহূর্ত থেকেই অ্যাড-পেজের সময় গোনা হবে */
        Ads.onGatePause(this)
        /* 🔇 পেজ সরে গেলে (নতুন For You ডিটেইল / অ্যাড / অ্যাপ-সুইচ) মিনি-প্লেয়ার থেমে
           যাবে — দুই পেজের সাউন্ড একসাথে বাজবে না; ফিরে এলে ওই পজিশন থেকেই প্লে */
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        /* 📊 (v4.1) পেজ চোখের আড়ালে গেলেই মিনি থামাও — নইলে ব্যাকগ্রাউন্ডেও সার্ভারের
           ব্যান্ডউইডথ খেতে থাকে। ফুলস্ক্রিনে হাতবদল (handedOut) হলে ওখানকার ভিডিওই চলছে */
        if (!handedOut) player?.let { if (it.isPlaying) it.pause() }
    }

    override fun onDestroy() {
        super.onDestroy()
        /* 📊 (v4.1) পেজ বন্ধ করলে লাইভ-বক্স থেকে সাথে সাথে নামাও — ফুলস্ক্রিনে হাতবদল হলে ওখানেই দেখা চলছে */
        if (!handedOut && player != null) Stats.watchStop(this)
        ui.removeCallbacksAndMessages(null)
        if (handedOut) {
            /* 🎬 প্লেয়ার এখন ফুলস্ক্রিনের হাতে — এখানে ভাঙলে ওখানকার প্লে বন্ধ হয়ে যাবে */
            player = null
            pv.player = null
        } else {
            releaseMini()
        }
        instance = null
        FloatPlayer.onHostDestroyed(this)   /* 🪟 অ্যাপ থেকে বের হলে পপআপও বন্ধ */
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
            /* ▶ এখন প্লে হচ্ছে যেটা — লিস্টে শুধু সেটার নাম সবুজ */
            if (isNowPlaying(link.name)) h.name.setTextColor(0xFF22C55E.toInt())
            else h.name.setTextColor(
                androidx.core.content.ContextCompat.getColor(this@DetailActivity, R.color.textMain)
            )

            /* 📢 এড খুললে: শিট বন্ধ + মেসেজ। এড-ফ্রি হলে (free=true): শুধু শিট বন্ধ।
               🆕 (v5.2) মেসেজে বাকি সেকেন্ড — জমা থাকলে পুরোটা না, যতটা বাকি ততটা
               💓 (v5.6) নতুন লেখা (৩ ধাপের ছোট টোস্ট) — সময় পূর্ণ হলে ভাইব্রেশন, ভাইব্রেট পেলেই ব্যাক */
            fun beforeAd(free: Boolean) {
                if (closeSheet) closeSheet()
                if (free) return
                this@DetailActivity.gateToast("আপনার কাজটা হবে")
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
        /* 🎬 (v3.7 মালিকের নির্দেশ) ডাউনলোড আবার সরাসরি ক্রোমে — DownloadManager বাদ
           (অনেক সময় সমস্যা করে); ইউজাররা বেশিরভাগ ক্রোমেই ডাউনলোড করে, ক্রেডিট-নাম
           না এলেও সমস্যা নেই। DownloadManager শুধু একদম শেষ ফলব্যাক হিসেবে আছে। */
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
