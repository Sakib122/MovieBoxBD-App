package com.movieboxbd.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * ▶ ফুলস্ক্রিন প্লেয়ার v6 (অ্যাপ v3.0 — রেফারেন্স-অ্যাপের ডিজাইন):
 *  — ✅ উপরে: টাইটেল + ❓Help + ⚙Setting; মাঝে: ⏪10 ▶ ⏩10; নিচে: Fit / Language / 1x / Quality
 *  — ✅ বাঁ মাঝে 🔒 লক + "Tap to Lock" — লক হলে শুধু উপরের-বাঁ কোণার 🔓 খুলবে
 *  — ✅ বাঁ পাশে উপর-নিচ টানলে ☀️ ব্রাইটনেস, ডান পাশে টানলে 🔊 ভলিউম —
 *     উপরে-মাঝে গোল পিলে (আইকন + সাদা বার) অ্যানিমেশনের মতো দেখায়
 *  — ✅ চেপে ধরে রাখলে 2x ফরওয়ার্ড প্লে, ছাড় দিলে আগের স্পিডে ফেরে
 *  — ✅ MX-এর মতো টেনে সিক: টানতে থাকলে শুধু সময় দেখায়, ছাড় দিলে একবার সিক
 *  — ✅ 🎬 কোয়ালিটি ক্যাপ: Auto/2160P/1080P/720P/480P/360P (সার্ভার যে রেজোলিউশন দেয় তার মধ্যেই)
 *  — 🎧 অডিও ট্র্যাক ভাষার পুরো নামে; ফাইলের ক্রেডিট কখনোই দেখায় না;
 *     সাউন্ড না চললে সাপোর্টেড ট্র্যাকে অটো-সুইচ / MX Player ফলব্যাক
 *  — 🔄 স্ট্রিম ফেল হলে ৩ বার অটো-রিট্রাই; ডিটেইল-পেজের মিনি-প্লেয়ারের পজিশন থেকে চলে
 */
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var trackSel: DefaultTrackSelector? = null
    private var audioDisabled = false
    private var subsDisabled = false
    private var retryCount = 0
    private var url: String = ""
    private var title: String = ""
    private var movieName: String = ""   /* 🏷️ আসল মুভির নাম (ক্রেডিট + প্যানেল) */
    private var resumePos = 0L           /* ⏱ মিনি-প্লেয়ার থেকে আসলে ওখানকার পজিশন */

    /* 📚 এপিসোড-চেইন (v3.5) — ফুলস্ক্রিনের ⏭ নেক্সট বাটন আর অটো-নেক্সটের জন্য */
    private var playUrls: ArrayList<String> = ArrayList()
    private var playNames: ArrayList<String> = ArrayList()
    private var playIdx = -1

    private lateinit var pv: PlayerView
    private lateinit var gestureHint: TextView
    private lateinit var hintBox: View
    private lateinit var hintIcon: ImageView
    private lateinit var gesturePill: View
    private lateinit var pillIcon: ImageView
    private lateinit var pillBar: ProgressBar
    private lateinit var pill2x: TextView
    private lateinit var errorBox: View
    private lateinit var errText: TextView
    private lateinit var btnErrTg: View
    private lateinit var lockRow: View
    private lateinit var btnMUnlock: ImageButton
    private lateinit var btnMSpeed: TextView
    private lateinit var btnMAudio: TextView
    private lateinit var btnMScale: TextView
    private lateinit var lockLayer: View
    /* 🎬 মাঝের ⏪10 ▶/⏸ ⏩10 (v3.5 — মালিকের নির্দেশে ফিরে এলো) + ⏮ ⏭ প্রিভিয়াস/নেক্সট (v3.6) */
    private lateinit var btnPRew: ImageButton
    private lateinit var btnPPlay: ImageButton
    private lateinit var btnPFwd: ImageButton
    private lateinit var btnPrevEp: ImageButton
    private lateinit var btnNextEp: ImageButton
    /* ⏮⏭ সুইচের অ্যাড-গেট (v3.6) — প্লে-গেটের মতোই ৫ সেকেন্ড, ১-মিনিট নিয়মে */
    private lateinit var epGateBox: View
    private lateinit var epGateMsg: TextView
    private lateinit var epGateCount: TextView
    private var pendingEpIdx = -1
    private var pendingEpAuto = false
    /* 🛡 (v5.0) রিয়েল-কোয়ালিটি গেট — সময় শুধু অ্যাড-পেজে থাকার সময়ই গোনা হয়:
       epWatchMs = অ্যাড-পেজে কাটানো মোট মিলিসেকেন্ড; epAwayTs = অ্যাপ শেষ কবে লুকালো */
    private var epWatchMs = 0L
    private var epAwayTs = 0L
    private var started = false
    private var locked = false
    private var controllerVisible = false
    /* অডিও কম্প্যাট: READY হলেই একবার চেক (ইউজার নিজে বাছলে আর নাক গলানো হয় না) */
    private var audioChecked = false
    private var audioFallbackDone = false
    /* 🔊 সাউন্ড সাপোর্ট নেই এমন ফোনে MX Player ফলব্যাক ডায়ালগ (বারবার দেখানো যায়) */
    private var soundHelpDialog: AlertDialog? = null
    private var extPlayerUsed = false
    private var deviceDialog: AlertDialog? = null
    private var deviceNotSupportedShown = false
    /* 📊 (v4.0) প্লে চলাকালীন হার্টবিট — প্যানেলের "▶️ এখন যা দেখা হচ্ছে" বক্স এটা দিয়েই জানে
       কোন ফোন কোন মুভি কত কোয়ালিটিতে দেখছে (পজ করলে হার্টবিট বন্ধ → ২ মিনিট পর বক্স থেকে বাদ) */
    private val watchTick = object : Runnable {
        override fun run() {
            try {
                if (player?.isPlaying == true) Stats.watch(this@PlayerActivity, statLabel())
            } catch (_: Exception) {}
            ui.postDelayed(this, 45000L)
        }
    }
    /* 🎬 (v3.7) মিনি-প্লেয়ারের চলমান ExoPlayer গ্রহণ করা হলে true — সিমলেস, কোনো রিলোড নেই */
    private var adopted = false

    private val SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private val SPEED_LABELS = arrayOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x")
    private var speedIndex = 2

    /* ⛶ স্ক্রিন মোড: ফিট → জুম → টানাটানি */
    private val SCALE_MODES = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    )
    private val SCALE_LABELS = arrayOf("Fit", "Zoom", "Stretch")
    private val SCALE_HINTS = arrayOf(
        "ফিট — অনুপাত ঠিক রেখে পুরো ভিডিও দেখা যায়",
        "জুম — পুরো স্ক্রিন ভরে যায় (কিছু অংশ কাটা যায়)",
        "টানাটানি — স্ক্রিনের মাপে ভরে যায় (মুখ লম্বা/চ্যাপ্টা লাগতে পারে)"
    )
    private var scaleIndex = 0

    /* 🎬 ভিডিও কোয়ালিটি-ক্যাপ (সার্ভারের সর্বোচ্চ রেজোলিউশন যেটা দেয়) */
    /* 🎬 ভিডিও কোয়ালিটি-ক্যাপ (ফেক 720P/480P বাদ — v3.4): কোয়ালিটি বাটন/ডায়ালগ নেই,
       স্ট্রিম যা, সেটাই সরাসরি চলে */

    /** ম্যানুয়ালি বাছা অডিও ট্র্যাক (-1 = অটো) */
    private var audioPick = -1

    /** ভাষার কোড → ইংরেজি নাম (২ ও ৩ অক্ষরের দুই রকম কোডই) */
    private val LANG_NAMES = mapOf(
        "hi" to "Hindi", "hin" to "Hindi",
        "bn" to "Bangla", "ben" to "Bangla", "bang" to "Bangla",
        "ta" to "Tamil", "tam" to "Tamil",
        "te" to "Telugu", "tel" to "Telugu",
        "en" to "English", "eng" to "English",
        "ml" to "Malayalam", "mal" to "Malayalam",
        "kn" to "Kannada", "kan" to "Kannada",
        "mr" to "Marathi", "mar" to "Marathi",
        "pa" to "Punjabi", "pan" to "Punjabi",
        "ur" to "Urdu", "urd" to "Urdu",
        "gu" to "Gujarati", "guj" to "Gujarati",
        "ko" to "Korean", "kor" to "Korean",
        "ja" to "Japanese", "jpn" to "Japanese",
        "zh" to "Chinese", "chi" to "Chinese", "zho" to "Chinese",
        "th" to "Thai", "tha" to "Thai",
        "ar" to "Arabic", "ara" to "Arabic",
        "es" to "Spanish", "spa" to "Spanish",
        "ne" to "Nepali", "nep" to "Nepali",
        "si" to "Sinhala", "sin" to "Sinhala",
        "fr" to "French", "fra" to "French", "fre" to "French",
        "de" to "German", "deu" to "German", "ger" to "German",
        "ru" to "Russian", "rus" to "Russian",
        "pt" to "Portuguese", "por" to "Portuguese",
        "id" to "Indonesian", "ind" to "Indonesian",
        "my" to "Burmese", "bur" to "Burmese", "mya" to "Burmese"
    )

    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        setContentView(R.layout.activity_player)

        url = intent.getStringExtra("url") ?: run { finish(); return }
        title = intent.getStringExtra("title") ?: "MovieBoxBD"
        movieName = intent.getStringExtra("movie_name") ?: ""
        ui.postDelayed(watchTick, 15000L)   /* 📊 (v4.0) হার্টবিট-লুপ চালু (প্রথম বিট ১৫ সে পরে) */
        resumePos = intent.getLongExtra("resume_pos", 0L).coerceAtLeast(0L)
        playUrls = intent.getStringArrayListExtra("urls") ?: ArrayList()
        playNames = intent.getStringArrayListExtra("names") ?: ArrayList()
        playIdx = intent.getIntExtra("idx", -1)

        pv = findViewById(R.id.playerView)
        gestureHint = findViewById(R.id.gestureHint)
        hintBox = findViewById(R.id.gestureHintBox)
        hintIcon = findViewById(R.id.hintIcon)
        gesturePill = findViewById(R.id.gesturePill)
        pillIcon = findViewById(R.id.pillIcon)
        pillBar = findViewById(R.id.pillBar)
        pill2x = findViewById(R.id.pill2x)
        errorBox = findViewById(R.id.errorBox)
        errText = findViewById(R.id.errText)
        btnErrTg = findViewById(R.id.btnErrTg)
        lockRow = findViewById(R.id.lockRow)
        btnMUnlock = findViewById(R.id.btnMUnlock)
        btnMSpeed = findViewById(R.id.btnMSpeed)
        btnMAudio = findViewById(R.id.btnMAudio)
        btnMScale = findViewById(R.id.btnMScale)
        lockLayer = findViewById(R.id.lockLayer)
        findViewById<TextView>(R.id.pTitle).text = creditTitle()
        findViewById<ImageButton>(R.id.btnPBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnRetry).setOnClickListener { retry() }
        btnErrTg.setOnClickListener { openTelegramChannel() }
        findViewById<TextView>(R.id.btnHelp).setOnClickListener { openTelegramChannel() }
        findViewById<TextView>(R.id.btnSetting).setOnClickListener { showSettings() }

        /* (v3.5) ⏪10 ▶/⏸ ⏩10 আবার ফিরে এলো — মালিকের নির্দেশ; ডাবল-ট্যাপ জেসচারও আগের মতোই চলবে
           (v3.6) হিন্টে শুধু রিয়েল আইকন + অঙ্ক — কোনো ইমোজি বা "সেকেন্ড" লেখা নেই */
        btnPRew = findViewById(R.id.btnPRew)
        btnPPlay = findViewById(R.id.btnPPlay)
        btnPFwd = findViewById(R.id.btnPFwd)
        btnPRew.setOnClickListener {
            val p = player ?: return@setOnClickListener
            p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
            showHint(R.drawable.ic_rew10, "10")
        }
        btnPFwd.setOnClickListener {
            val p = player ?: return@setOnClickListener
            p.seekTo(p.currentPosition + 10_000)
            showHint(R.drawable.ic_ffwd10, "10")
        }
        btnPPlay.setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (p.isPlaying) p.pause() else p.play()
        }
        /* ⏮ ⏭ প্রিভিয়াস/নেক্সট — সারির বাঁ দিকে (মালিকের নির্দেশ v3.6);
           সুইচের আগে প্লে-গেটের মতোই ১-মিনিট অ্যাড-গেট — স্কিপ করে অ্যাড এড়ানো যাবে না */
        btnPrevEp = findViewById(R.id.btnPrevEp)
        btnNextEp = findViewById(R.id.btnNextEp)
        btnPrevEp.setOnClickListener { switchEpisode(playIdx - 1) }
        btnNextEp.setOnClickListener { switchEpisode(playIdx + 1) }
        epGateBox = findViewById(R.id.epGateBox)
        epGateMsg = findViewById(R.id.epGateMsg)
        epGateCount = findViewById(R.id.epGateCount)
        findViewById<View>(R.id.btnEpGateCancel).setOnClickListener { cancelEpGate() }
        refreshEpButtons()

        /* ═══ ইনসেট: কন্ট্রোল বার নেভবার/নোটচের নিচে চাপা পড়বে না +
               বার কোথাও উঠে এলে আবার লুকিয়ে দেওয়া হয় (আসল ইমারসিভ) ═══ */
        val ctrlTop = findViewById<View>(R.id.ctrlTop)
        val ctrlBottom = findViewById<View>(R.id.ctrlBottom)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.playerRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val top = max(bars.top, cut.top)
            val bottom = max(bars.bottom, cut.bottom)
            ctrlTop.setPadding(0, top, 0, 0)
            ctrlBottom.setPadding(ctrlBottom.paddingLeft, 0, ctrlBottom.paddingRight, bottom)
            /* 🔓 আনলক বাটনটাও নোটচ/স্ট্যাটাস বারের নিচে ঢুকে যাবে না */
            btnMUnlock.layoutParams = (btnMUnlock.layoutParams as android.widget.FrameLayout.LayoutParams).also {
                it.topMargin = 10 + top
            }
            /* জেসচার-পিলও নোটচের নিচে ঢুকবে না */
            gesturePill.layoutParams = (gesturePill.layoutParams as android.widget.FrameLayout.LayoutParams).also {
                it.topMargin = 28 + top
            }
            pill2x.layoutParams = (pill2x.layoutParams as android.widget.FrameLayout.LayoutParams).also {
                it.topMargin = 28 + top
            }
            /* স্ট্যাটাস/নেভিগেশন বার স্থায়ীভাবে ফিরে এলে আবার লুকাও */
            if (insets.isVisible(WindowInsetsCompat.Type.systemBars())) hideSystemBars()
            WindowInsetsCompat.CONSUMED
        }

        /* 🔒 লক + বাকি মেনু */
        findViewById<View>(R.id.btnMLock).setOnClickListener { lockScreen() }
        btnMUnlock.setOnClickListener { unlockScreen() }
        lockLayer.setOnClickListener { showHint(null, "স্ক্রিন লক করা — খুলতে উপরের বাঁ কোণার আনলক বাটন চাপো") }
        btnMSpeed.setOnClickListener { showSpeedDialog() }
        btnMAudio.setOnClickListener { showAudioDialog() }
        btnMScale.setOnClickListener { cycleScale() }

        /* লক থাকলে ব্যাক চাপলে বের হবে না — MX-এর মতো */
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (locked) {
                    Toast.makeText(
                        this@PlayerActivity,
                        "আগে উপরের কোণার আনলক বাটন দিয়ে আনলক করো",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    finish()
                }
            }
        })

        setupGestures()
        /* 🎬 (v3.7 মালিকের নির্দেশ) মিনি-প্লেয়ারের চলমান প্লেয়ার এলে সেটাই গ্রহণ — ভিডিও
           চলতেই থাকে, নতুন করে লোড/বাফার হয় না (সিমলেস মিনি↔ফুলস্ক্রিন) */
        val ext = DetailActivity.hfPlayer
        if (ext != null) {
            DetailActivity.hfPlayer = null
            adopted = true
            started = true          /* READY-তে আবার seek/Stats হবে না */
            player = ext
            setupPlayerView()
            pv.player = ext
            ext.addListener(playerListener)
            ext.play()              /* চলতেই থাকবে */
            /* 🖼 নতুন TextureView-এ ছবি গ্যারান্টি — একই পজিশনে ছোট্ট সিক দিলে
               ডিকোডার নতুন সারফেসে সাথে সাথে রেন্ডার শুরু করে (কালো-ফ্রেম বাগ বন্ধ)
               ⚡ (v4.2) তবে প্লে চলাকালীন সিক দিলে স্লো ডিভাইসে ছোট্ট রি-বাফার-স্পিনার
               আসত — চলমান ফ্রেম নতুন সারফেসে আপনিই আসে, তাই পজড হলে তবেই সিক */
            if (!ext.isPlaying) ext.seekTo(ext.currentPosition)
        } else {
            buildPlayer()
        }
    }

    private fun hideSystemBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideSystemBars()
    }

    /* ── ✈️ টেলিগ্রাম চ্যানেল (Config.TELEGRAM_URL) ── */
    private fun openTelegramChannel() {
        val u = Config.TELEGRAM_URL
        if (u.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
            Toast.makeText(this, "টেলিগ্রাম চ্যানেল খুলছে", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    /* ── ⛶ স্ক্রিন-মোড সাইকেল ── */
    private fun cycleScale() {
        scaleIndex = (scaleIndex + 1) % SCALE_MODES.size
        pv.resizeMode = SCALE_MODES[scaleIndex]
        btnMScale.text = SCALE_LABELS[scaleIndex]
        showHint(null, SCALE_HINTS[scaleIndex])
    }

    /* ── 🔒 স্ক্রিন লক / আনলক ───────────────────────────────── */

    private fun lockScreen() {
        locked = true
        pv.hideController()
        controllerVisible = false
        lockRow.visibility = View.GONE
        lockLayer.visibility = View.VISIBLE
        btnMUnlock.visibility = View.VISIBLE
        Toast.makeText(this, "স্ক্রিন লক হয়েছে — খুলতে উপরের বাঁ কোণার আনলক বাটন চাপো", Toast.LENGTH_SHORT).show()
    }

    private fun unlockScreen() {
        locked = false
        lockLayer.visibility = View.GONE
        btnMUnlock.visibility = View.GONE
        pv.showController()
    }

    /* ── ⚡ প্লে স্পিড ───────────────────────────────────────── */

    private fun showSpeedDialog() {
        AlertDialog.Builder(this)
            .setTitle("প্লে স্পিড")
            .setItems(SPEED_LABELS) { _, w ->
                speedIndex = w
                player?.playbackParameters = PlaybackParameters(SPEEDS[w])
                btnMSpeed.text = SPEED_LABELS[w]
            }
            .show()
    }

    /* ── ⚙ সেটিংস-ডায়ালগ (রেফারেন্সের Setting) ─────────────── */

    private fun showSettings() {
        val opts = arrayOf(
            "প্লে স্পিড", "অডিও (ভাষা)",
            "সাবটাইটেল অন/অফ", "স্ক্রিন মোড"
        )
        AlertDialog.Builder(this)
            .setTitle("সেটিংস")
            .setItems(opts) { _, w ->
                when (w) {
                    0 -> showSpeedDialog()
                    1 -> showAudioDialog()
                    2 -> toggleSubs()
                    3 -> cycleScale()
                }
            }
            .show()
    }

    /* ── 🎧 অডিও ট্র্যাক (ভাষার পুরো নাম — ফাইলের ক্রেডিট কখনো দেখাবে না) ── */

    /** এই ফোনে ওই ট্র্যাকের কোডেক আছে কি না: true/false; জানা না গেলে null */
    private fun trackSupported(g: Tracks.Group): Boolean? {
        return try {
            val f = g.getTrackFormat(0)
            val mime = f.sampleMimeType ?: return null
            if (!MimeTypes.isAudio(mime)) return null
            val info = MediaCodecUtil.getDecoderInfo(mime, false, false) ?: return false
            info.isFormatSupported(f)
        } catch (_: Exception) {
            null
        }
    }

    /** ট্র্যাকের নাম = ভাষার ইংরেজি নাম (Hindi/Tamil/Bangla/Telugu…)।
     *  ভাষা না জানা গেলে "অডিও N"। ফাইলের ভেতরের রমরমা লেবেল কখনোই দেখানো হয় না। */
    private fun audioRowLabel(g: Tracks.Group, idx: Int): String {
        val f = g.getTrackFormat(0)
        val langCode = f.language?.trim()?.lowercase()?.substringBefore('-')
        val base = langCode?.let { LANG_NAMES[it] } ?: "অডিও ${idx + 1}"
        val ch = when (f.channelCount) {
            1 -> "মোনো"; 2 -> "স্টেরিও"; 6 -> "5.1"; 8 -> "7.1"; else -> null
        }
        val bits = ArrayList<String>()
        bits.add(base)
        if (ch != null) bits.add(ch)
        if (trackSupported(g) == false) bits.add("(এই ফোনে চলবে না)")
        return bits.joinToString("  •  ")
    }

    /* ── 🔊 সব ফোনে সাউন্ড চলার ব্যবস্থা ────────────────────── */
    private fun ensureAudioCompat() {
        val p = player ?: return
        if (audioChecked || audioDisabled || audioPick != -1) return
        audioChecked = true
        try {
            val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            if (groups.isEmpty()) return
            val sel = groups.firstOrNull { it.isSelected } ?: return
            if (trackSupported(sel) != false) return
            val alt = groups.filter { it !== sel }.firstOrNull { trackSupported(it) == true }
            if (alt != null) {
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .addOverride(TrackSelectionOverride(alt.mediaTrackGroup, 0))
                    .build()
                audioPick = groups.indexOf(alt) + 1
                Toast.makeText(
                    this,
                    "এই ফোনে ওই সাউন্ড ফরম্যাট চলে না — অন্য অডিও ট্র্যাক চালু করা হলো",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                audioDisabled = true
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
                Toast.makeText(
                    this,
                    "এই ফোনে ওই সাউন্ড ফরম্যাট সাপোর্ট নেই — ছবি চলছে",
                    Toast.LENGTH_LONG
                ).show()
                /* 🔊 এই ফোনে সাউন্ড হবে না — MX Player/VLC দিয়ে চালানোর সুন্দর ডায়ালগ */
                showSoundHelp()
            }
        } catch (_: Exception) {
        }
    }

    /** এরর হলে: অন্য সাপোর্টেড অডিও ট্র্যাক থাকলে সেটায় সুইচ (সাউন্ড বাঁচায়) */
    private fun tryAudioFallback(): Boolean {
        val p = player ?: return false
        return try {
            val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            val alt = groups.firstOrNull { !it.isSelected && trackSupported(it) == true }
                ?: return false
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .addOverride(TrackSelectionOverride(alt.mediaTrackGroup, 0))
                .build()
            audioPick = groups.indexOf(alt) + 1
            Toast.makeText(this, "সাউন্ড অন্য অডিও ট্র্যাকে চালু করা হলো", Toast.LENGTH_LONG).show()
            true
        } catch (_: Exception) {
            false
        }
    }

    /* ── 🏷️ প্লেয়ারের উপরের ক্রেডিট + প্যানেলের লেবেল ──────────────
     *  ক্রেডিট: MovieBoxBD.xyz ✦ মুভির নাম (720p) / (EP 128) */
    private fun mediaTag(): String? {
        val q = Regex("""\b\d{3,4}\s?p\b""", RegexOption.IGNORE_CASE)
            .find(title)?.value?.replace(" ", "")?.lowercase()
        if (q != null) return q
        return Regex("""(?i)\bEP\s*\d+""").find(title)?.value
    }

    private fun creditTitle(): String {
        val tag = mediaTag()
        val name = movieName.ifBlank { title }
        return if (tag != null && !name.equals(title, true))
            "${Config.BRAND} ✦ $name ($tag)"
        else "${Config.BRAND} ✦ $name"
    }

    private fun statLabel(): String {
        if (movieName.isBlank() || title.equals(movieName, true)) return title
        val tag = mediaTag()
        return if (tag != null) "$movieName ($tag)" else "$movieName ($title)"
    }

    /* ── 📱 ডিভাইস-সাপোর্ট ফলব্যাক (v3.9 — v3.4-এর পুরনো ফিচার ফেরত):
       যে ফোনে এই ভিডিও ফরম্যাট চলে না — MX/BS Player-এ সরাসরি দেখার ডায়ালগ ── */
    private fun showDeviceNotSupported() {
        if (deviceDialog?.isShowing == true || deviceNotSupportedShown) return
        deviceNotSupportedShown = true
        val MX1 = "com.mxtech.videoplayer.ad"      /* MX Player (ফ্রি) */
        val MX2 = "com.mxtech.videoplayer.pro"     /* MX Player Pro */
        val BS = "com.bsplayer.bspandroid.free"    /* BS Player */

        fun launchIn(pkg: String): Boolean {
            return try {
                val i = Intent(Intent.ACTION_VIEW)
                i.setDataAndType(Uri.parse(url), "video/*")
                i.setPackage(pkg)
                i.putExtra("title", title)
                startActivity(i)
                extPlayerUsed = true
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

        deviceDialog = fallbackCard(
            "📺",
            "এই ডিভাইসে সাপোর্ট হচ্ছে না",
            "এই ভিডিওর ফরম্যাট আপনার ফোনের প্লেয়ার চালাতে পারছে না।\n" +
                "দয়া করে MX Player বা VLC দিয়ে সরাসরি দেখুন — একই লিংকে স্মুথলি চলবে।",
            {
                if (!launchIn(MX1) && !launchIn(MX2)) {
                    Toast.makeText(this, "MX Player ইনস্টল নেই — Play Store খুলছে… ইনস্টল করে ফিরে এলেই মুভি চলবে", Toast.LENGTH_LONG).show()
                    openStore(MX1)
                }
            },
            {
                if (!launchIn(BS)) {
                    Toast.makeText(this, "VLC/BS Player ইনস্টল নেই — Play Store খুলছে… ইনস্টল করে ফিরে এলেই মুভি চলবে", Toast.LENGTH_LONG).show()
                    openStore(BS)
                }
            }
        ).also { it.show() }
    }

    /* ── 🔊 MX Player ফলব্যাক (সাউন্ড সাপোর্ট নেই এমন ফোনের জন্য) ────── */
    /* ── 🎨 (v4.0) ফলব্যাক ডায়ালগ রিডিজাইন: সিস্টেম পুরনোটাই, লুক নতুন ──
       গোল-গাল ডার্ক কার্ড + বড় আইকন + MX/VLC দুটো বড় বাটন + "এখন না" */
    private fun fallbackCard(
        icon: String, titleTxt: String, msg: String,
        onMx: () -> Unit, onVlc: () -> Unit
    ): AlertDialog {
        val px = { d: Int -> (d * resources.displayMetrics.density).toInt() }

        fun txt(text: String, size: Float, color: Int, bold: Boolean, center: Boolean): android.widget.TextView =
            android.widget.TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = if (center) android.view.Gravity.CENTER else android.view.Gravity.START
            }

        fun rowParams(top: Int, h: Int = -2): android.widget.LinearLayout.LayoutParams =
            android.widget.LinearLayout.LayoutParams(-1, h).apply { topMargin = px(top) }

        val card = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(px(22), px(24), px(22), px(18))
            background = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_fallback_card)
        }

        val dot = txt(icon, 32f, 0xFFFFFFFF.toInt(), false, true)
        dot.background = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_fallback_icon)
        val dotLp = android.widget.LinearLayout.LayoutParams(px(72), px(72))
        dotLp.gravity = android.view.Gravity.CENTER
        card.addView(dot, dotLp)

        card.addView(txt(titleTxt, 17f, 0xFFE9EEFF.toInt(), true, true), rowParams(14))
        val m = txt(msg, 13.5f, 0xFFB9C2E0.toInt(), false, true)
        m.setLineSpacing(px(3).toFloat(), 1f)
        card.addView(m, rowParams(8))

        /* বাটন-দুটো — MX সবুজ, VLC বেগুনি-আউটলাইন */
        fun btn(label: String, bgRes: Int, txtColor: Int, top: Int, cb: () -> Unit) {
            val b = txt(label, 14f, txtColor, true, true)
            b.background = androidx.core.content.ContextCompat.getDrawable(this, bgRes)
            b.setPadding(0, px(13), 0, px(13))
            b.setOnClickListener { cb() }
            card.addView(b, rowParams(top))
        }

        var dlg: AlertDialog? = null
        btn("▶  MX Player-এ দেখুন", R.drawable.bg_fallback_btn_mx, 0xFF07280F.toInt(), 20, onMx)
        btn("▶  VLC-তে দেখুন", R.drawable.bg_fallback_btn_vlc, 0xFFB9AFFF.toInt(), 10, onVlc)

        val no = txt("এখন না", 13f, 0xFF8B94B8.toInt(), false, true)
        no.setPadding(0, px(12), 0, px(2))
        no.setOnClickListener { dlg?.dismiss() }
        card.addView(no)

        /* বাইরের মার্জিন-র‍্যাপার */
        val wrap = android.widget.FrameLayout(this)
        wrap.setPadding(px(18), px(10), px(18), px(10))
        wrap.addView(card)

        dlg = AlertDialog.Builder(this).setView(wrap).create()
        dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        return dlg
    }

    private fun showSoundHelp() {
        if (soundHelpDialog?.isShowing == true) return
        val MX1 = "com.mxtech.videoplayer.ad"   /* MX Player (ফ্রি) */
        val MX2 = "com.mxtech.videoplayer.pro"  /* MX Player Pro */
        val VLC = "org.videolan.vlc"

        fun launchIn(pkg: String): Boolean {
            return try {
                val i = Intent(Intent.ACTION_VIEW)
                i.setDataAndType(Uri.parse(url), "video/*")
                i.setPackage(pkg)
                i.putExtra("title", title)
                startActivity(i)
                extPlayerUsed = true
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

        soundHelpDialog = fallbackCard(
            "🔊",
            "এই ফোনে সাউন্ড চলছে না",
            "মুভির সাউন্ড ফরম্যাট (Dolby) এই ফোনের প্লেয়ার ডিকোড করতে পারছে না।\n" +
                "MX Player বা VLC দিয়ে খুললে সাউন্ডসহ চলবে।",
            {
                if (!launchIn(MX1) && !launchIn(MX2)) {
                    Toast.makeText(this, "MX Player ইনস্টল নেই — Play Store খুলছে… ইনস্টল করে ফিরে এলেই মুভি চলবে", Toast.LENGTH_LONG).show()
                    openStore(MX1)
                }
            },
            {
                if (!launchIn(VLC)) {
                    Toast.makeText(this, "VLC ইনস্টল নেই — Play Store খুলছে… ইনস্টল করে ফিরে এলেই মুভি চলবে", Toast.LENGTH_LONG).show()
                    openStore(VLC)
                }
            }
        ).also { it.show() }
    }

    /* 🛠 Play Store থেকে MX Player/VLC ইনস্টল করে ফিরে এলে ডায়ালগ নিজেই আবার দেখাও */
    override fun onResume() {
        super.onResume()
        /* 🛡 গেট চলমান থাকলে অ্যাড-পেজে কাটানো সময় যাচাই — পূর্ণ হলে সাথে সাথে সুইচ,
           সময়ের আগে ফিরে এলে সতর্কবার্তা (আরও কত সেকেন্ড বাকি ছিল) */
        if (pendingEpIdx >= 0 && epGateBox.visibility == View.VISIBLE) evaluateEpGate()
        if (audioDisabled && !isFinishing && !extPlayerUsed) {
            ui.removeCallbacks(reShowSoundHelp)
            ui.postDelayed(reShowSoundHelp, 600)
        }
    }

    private val reShowSoundHelp = Runnable {
        if (audioDisabled && !isFinishing && !extPlayerUsed &&
            soundHelpDialog?.isShowing != true) showSoundHelp()
    }

    override fun onPause() {
        super.onPause()
        /* 🛡 (v5.0) গেট চলমান থাকলে এই মুহূর্ত থেকেই অ্যাড-পেজের সময় গোনা হবে */
        if (pendingEpIdx >= 0 && epGateBox.visibility == View.VISIBLE && epAwayTs == 0L) {
            epAwayTs = System.currentTimeMillis()
        }
        ui.removeCallbacks(reShowSoundHelp)
        /* 🎬 (v3.8) ফুলস্ক্রিন বন্ধ হচ্ছে (ব্যাক) → প্লেয়ার এখানেই মিনিতে ফেরত:
           onPause আগে চলে, DetailActivity-র onResume পরে — তাই মিনি ফিরে এসেই
           প্লেয়ার বুঝে যায় (আগে onDestroy-এ ফেরত দিতাম, অথচ onDestroy চলত
           onResume-এর পরে — মিনি কালো+পজড হয়ে যেত!)। প্লে চালু থাকে। */
        if (isFinishing && adopted) handBackToMini()
    }

    private fun showAudioDialog() {
        val p = player ?: return
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (groups.isEmpty()) {
            Toast.makeText(this, "এই ভিডিওতে কোনো অডিও ট্র্যাক নেই", Toast.LENGTH_SHORT).show()
            return
        }

        val items = ArrayList<String>()
        items.add("অটো (ডিফল্ট)")
        val dup = HashMap<String, Int>()
        for ((i, g) in groups.withIndex()) {
            val l = audioRowLabel(g, i)
            val n = (dup[l] ?: 0) + 1
            dup[l] = n
            items.add(if (n > 1) "$l · ট্র্যাক $n" else l)
        }

        /* সুন্দর কাস্টম লিস্ট — বাছাই করা ট্র্যাকে ● বসবে, সব লেখা স্পষ্ট দেখা যাবে */
        var checked = audioPick.coerceIn(0, items.size - 1)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }
        fun redraw() {
            box.removeAllViews()
            items.forEachIndexed { idx, label ->
                val tv = TextView(this).apply {
                    text = if (idx == checked) "●  $label" else "○  $label"
                    textSize = 16f
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(48, 30, 32, 30)
                    if (idx == checked) setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setOnClickListener {
                        checked = idx
                        redraw()
                    }
                }
                box.addView(tv)
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
                audioPick = checked
                audioDisabled = false
                Toast.makeText(
                    this,
                    if (checked == 0) "অডিও: অটো (ডিফল্ট)" else "অডিও: ${items[checked]}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    /* ── 💬 সাবটাইটেল অন/অফ ─────────────────────────────────── */

    private fun toggleSubs() {
        val p = player ?: return
        val hasText = p.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
        if (!hasText && !subsDisabled) {
            Toast.makeText(this, "এই ভিডিওতে কোনো সাবটাইটেল নেই", Toast.LENGTH_SHORT).show()
            return
        }
        subsDisabled = !subsDisabled
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subsDisabled)
            .build()
        Toast.makeText(
            this,
            if (subsDisabled) "সাবটাইটেল অফ করা হলো" else "সাবটাইটেল চালু হলো",
            Toast.LENGTH_SHORT
        ).show()
    }

    /* ── প্লেয়ার তৈরি (ধীর সার্ভারের জন্য বড় টাইমআউট) ────────── */

    /* 🎛 PlayerView-এর সেটিংস (রিজোলিউশন-মোড, কন্ট্রোলার টাইমআউট, লক-রো) —
       নিজের বানানো আর মিনি থেকে গ্রহণ করা — দুই প্লেয়ারেই একই সেটিংস */
    private fun setupPlayerView() {
        pv.resizeMode = SCALE_MODES[scaleIndex]
        pv.controllerShowTimeoutMs = 3500
        pv.setControllerVisibilityListener(object : PlayerView.ControllerVisibilityListener {
            override fun onVisibilityChanged(visibility: Int) {
                controllerVisible = visibility == View.VISIBLE
                lockRow.visibility = if (controllerVisible) View.VISIBLE else View.GONE
            }
        })
    }

    /* 🎧 প্লেয়ার-লিসনার (READY/ENDED/ISPLAYING/VIDEOSIZE/ERROR) — গ্রহণ করা প্লেয়ারেও লাগে */
    private val playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    if (!started) {
                        started = true
                        /* ⏱ মিনি-প্লেয়ার থেকে এলে ওখানকার পজিশন থেকেই চলবে */
                        if (resumePos > 0) player?.seekTo(resumePos)
                        player?.play()
                        Stats.play(this@PlayerActivity, statLabel())
                    }
                    ensureAudioCompat()
                }
                if (state == Player.STATE_ENDED) {
                    /* 📚 পরের এপিসোড থাকলে ফুলস্ক্রিনেই অটো-নেক্সট — একই ১-মিনিট অ্যাড-গেট নিয়মে;
                       পরেরটা না থাকলে প্লেয়ার বন্ধ */
                    if (playIdx + 1 < playUrls.size) switchEpisode(playIdx + 1, auto = true)
                    else finish()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                /* ▶/⏸ মাঝের বড় বাটনের আইকন সবসময় বাস্তব অবস্থার সাথে মিলবে */
                btnPPlay.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    requestedOrientation =
                        if (videoSize.width > videoSize.height)
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handle(error)
            }
        }

    private fun buildPlayer() {
        errorBox.visibility = View.GONE
        retryCount = 0
        audioChecked = false
        audioFallbackDone = false

        val tsel = DefaultTrackSelector(this)
        trackSel = tsel
        var pb = tsel.buildUponParameters()
        if (audioDisabled) pb = pb.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
        if (subsDisabled) pb = pb.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        tsel.parameters = pb.build()

        val dsFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Linux; Android) MovieBoxBD/3.0")

        player = ExoPlayer.Builder(this, DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
            .setTrackSelector(tsel)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        setupPlayerView()
        pv.player = player
        player?.setMediaItem(MediaItem.fromUri(url))
        player?.playbackParameters = PlaybackParameters(SPEEDS[speedIndex])
        player?.playWhenReady = true
        player?.addListener(playerListener)
        player?.prepare()
    }

    /* ── এরর হ্যান্ডলিং: ৩ বার অটো-রিট্রাই (koyeb ঠান্ডা হলেও ধরে ফেলে), নাহলে বাটন ── */

    private val autoRetry = Runnable { prepareAgain() }

    private fun handle(error: PlaybackException) {
        /* ⏱ (v3.5) এররের আগে যেখানে পৌঁছেছিলাম সেখান থেকেই রিট্রাই/রিস্টার্ট হবে —
           আর কখনো শূন্য থেকে শুরু হয়ে যাবে না */
        player?.currentPosition?.takeIf { it > 0 }?.let { resumePos = it }
        val code = error.errorCode
        val audioIssue = code == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                code == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                code == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                code == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                code == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED

        if (audioIssue) {
            if (!audioFallbackDone && tryAudioFallback()) {
                audioFallbackDone = true
                return
            }
            if (!audioDisabled) {
                audioDisabled = true
                Toast.makeText(
                    this,
                    "🔇 এই ফোনে ওই সাউন্ড ফরম্যাট সাপোর্ট নেই — ছবি চলছে",
                    Toast.LENGTH_LONG
                ).show()
                showSoundHelp()
                restartPlayer()
                return
            }
            /* 📱 (v3.9) মিউট-করে রিস্টার্টেও ডিকোড ব্যর্থ = এই ভিডিও এই ফোনে চলবে না —
               চুপচাপ লুপে আটকে রাখা বন্ধ, সরাসরি MX/BS Player ডায়ালগ */
            errorBox.visibility = View.VISIBLE
            errText.text = "এই ভিডিও ফরম্যাট এই ফোনে চলছে না"
            findViewById<View>(R.id.btnRetry).visibility = View.VISIBLE
            btnErrTg.visibility = View.VISIBLE
            showDeviceNotSupported()
            return
        }

        /* 📱 (v3.9) ভিডিও ক্যাপাবিলিটি/প্রসেসিং ব্যর্থ — রিট্রাই নষ্ট, সরাসরি ডায়ালগ */
        if (code == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
            code == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED ||
            code == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED
        ) {
            errorBox.visibility = View.VISIBLE
            errText.text = "এই ভিডিও ফরম্যাট এই ফোনে চলছে না"
            findViewById<View>(R.id.btnRetry).visibility = View.VISIBLE
            btnErrTg.visibility = View.VISIBLE
            showDeviceNotSupported()
            return
        }

        if (retryCount < 3) {
            retryCount++
            started = false
            errorBox.visibility = View.VISIBLE
            findViewById<View>(R.id.btnRetry).visibility = View.GONE
            btnErrTg.visibility = View.GONE
            errText.text = "সার্ভার জাগছে — আবার চেষ্টা চলছে ($retryCount/৩)\nবড় ফাইল হলে একটু সময় লাগে"
            ui.removeCallbacks(autoRetry)
            val delay = when (retryCount) { 1 -> 2500L; 2 -> 5000L; else -> 8000L }
            ui.postDelayed(autoRetry, delay)
        } else {
            started = false
            errText.text = "লোড হচ্ছে না — সার্ভার ব্যস্ত থাকতে পারে\n\nএকটু পরে আবার চেষ্টা করো, অথবা নিচের বাটনে চাপো"
            findViewById<View>(R.id.btnRetry).visibility = View.VISIBLE
            btnErrTg.visibility = View.VISIBLE
            /* 🎨 (v4.1) লোডই না হলে ইউজারকে MX/VLC রাস্তা দেখাও — পুরনো সিস্টেম, নতুন কার্ড-লুক */
            showDeviceNotSupported()
        }
    }

    /** একই লিংকে আবার prepare — retryCount রিসেট হয় না (অটো-রিট্রাই এটাই ডাকে) */
    private fun prepareAgain() {
        errorBox.visibility = View.GONE
        val p = player
        if (p != null) {
            /* ⏱ (v3.5) শূন্য থেকে নয় — শেষ পরিচিত পজিশন থেকেই আবার চলবে */
            p.seekTo(resumePos.coerceAtLeast(0L))
            p.prepare()
            p.playWhenReady = true
        } else {
            buildPlayer()
        }
    }

    /** ইউজারের হাতে রিট্রাই — চেষ্টার কাউন্ট নতুন করে শুরু */
    private fun retry() {
        retryCount = 0
        ui.removeCallbacks(autoRetry)
        prepareAgain()
    }

    private fun restartPlayer() {
        player?.currentPosition?.takeIf { it > 0 }?.let { resumePos = it }
        player?.release()
        player = null
        started = false
        buildPlayer()
    }

    /* ── 📚 ⏮ ⏭ প্রিভিয়াস/নেক্সট — ফুলস্ক্রিনের ভেতরেই (মালিকের নির্দেশ v3.6) ──
       EP 129 দেখা অবস্থায় ⏭ চাপলে EP 130 সেখানেই চলবে, ⏮ চাপলে আগেরটা।
       ইউজার যেন এপিসোড স্কিপ করে অ্যাড এড়াতে না পারে — ▶ প্লে-গেটের হুবহু নিয়ম:
       কুলডাউনের ভেতরে সুইচ = অ্যাড নেই; পার হলে রিয়েল ১০-সেকেন্ডের অ্যাড-গেট →
       তারপর সুইচ। বাতিল করলে আগের এপিসোডেই ফিরে থাকবে (অটো-নেক্সট হলে বের হবে)। */
    private fun switchEpisode(target: Int, auto: Boolean = false) {
        if (target < 0 || target >= playUrls.size) return
        if (target == playIdx) return
        if (Ads.episodeGateNeeded(this)) {
            pendingEpIdx = target
            pendingEpAuto = auto
            /* 🆕 (v5.2) ফ্রেশ-রিসেট বন্ধ: আগের চেষ্টায় আংশিক সেকেন্ড জমা থাকলে সেটুকুই থাকবে —
               বাতিল করে আবার ⏭ চাপলে শূন্য থেকে না, বাকি সেকেন্ড থেকেই শুরু */
            val needMs = RemoteAds.watchSeconds() * 1000L
            if (epWatchMs <= 0L || epWatchMs >= needMs) epWatchMs = 0L
            epAwayTs = 0L
            Ads.openEpisodeGateAd(this)   /* 💰 র‍্যান্ডম ডিরেক্ট-লিংক (প্লে-গেটের মতোই) */
            player?.pause()
            showEpGate()
        } else {
            doSwitch(target)
        }
    }

    /** 🛡 রিয়েল-কোয়ালিটি গেট (v5.0): অ্যাড-পেজে পুরো N সেকেন্ড থাকলে ফিরেই সুইচ —
        অ্যাপের ভেতরে কোনো কাউন্টডাউন চলে না (বসে থাকলে সময় এগোয় না) */
    private fun showEpGate() {
        epGateMsg.text = if (pendingEpAuto)
            "এই এপিসোড শেষ — পরের এপিসোডের আগে অ্যাড দেখো"
        else
            "পরের এপিসোডে যাওয়ার আগে অ্যাড দেখো"
        /* 🆕 (v5.2) ওভারলেতে এখন বাকি সেকেন্ডই দেখাবে — আগে কিছু দেখা জমা থাকলে ১০ না, বাকিটা */
        val remain = ((RemoteAds.watchSeconds() * 1000L - epWatchMs + 999) / 1000L).toInt().coerceAtLeast(1)
        epGateCount.text = remain.toString()
        epGateBox.visibility = View.VISIBLE
    }

    /** 🛡 ফিরে এসে গেট-যাচাই (onResume): অ্যাড-পেজের সময় পূর্ণ হলে সাথে সাথে সুইচ;
        আগে ফিরলে সতর্কবার্তা — আরও কত সেকেন্ড বাকি ছিল + আবার দেখার সুযোগ */
    private fun evaluateEpGate() {
        val now = System.currentTimeMillis()
        if (epAwayTs > 0) {
            epWatchMs += now - epAwayTs
            epAwayTs = 0L
        }
        val need = RemoteAds.watchSeconds() * 1000L
        if (epWatchMs >= need) {
            val t = pendingEpIdx
            pendingEpIdx = -1
            pendingEpAuto = false
            epGateBox.visibility = View.GONE
            Stats.gatePass(this)
            doSwitch(t)
        } else {
            val remain = ((need - epWatchMs + 999) / 1000L).toInt()
            Stats.gateFail(this, remain)
            try {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("অ্যাড পুরো দেখা হয়নি")
                    .setMessage(
                        "আরও $remain সেকেন্ড বাকি ছিল — চিন্তা নেই, এতক্ষণের দেখা সেকেন্ডগুলো ধরে রাখা আছে।\n\n" +
                            "আবার বিজ্ঞাপনে গিয়ে বাকি $remain সেকেন্ড পূর্ণ করে ফিরে আসুন — তখনই পরের এপিসোড চলবে।"
                    )
                    .setPositiveButton("বিজ্ঞাপন আবার খুলুন") { _, _ -> Ads.openEpisodeGateAd(this) }
                    .setNegativeButton("বাতিল") { _, _ ->
                        Ads.resetPlayGate(this)
                        Stats.gateCancel(this)
                        cancelEpGate()
                    }
                    .setCancelable(false)
                    .show()
            } catch (_: Exception) {
                cancelEpGate()
            }
        }
    }

    /** ইউজার গেট বাতিল করলে — আগের এপিসোডেই ফিরে আসে; অটো-নেক্সট (ভিডিও শেষ) হলে বের হয় */
    private fun cancelEpGate() {
        val wasAuto = pendingEpAuto
        pendingEpIdx = -1
        pendingEpAuto = false
        epGateBox.visibility = View.GONE
        /* 🆕 (v5.2) জমা সেকেন্ড মুছি না — আবার ⏭ চাপলে বাকি সেকেন্ড থেকেই গেট শুরু হবে */
        epAwayTs = 0L
        Ads.resetPlayGate(this)   /* 🛡 বাতিল = কুলডাউন-ফাঁক বন্ধ — পরের ক্লিকে আবার গেট */
        Stats.gateCancel(this)
        if (wasAuto) finish() else player?.play()
    }

    /** আসল এপিসোড-সুইচ — গেট শেষ হলে বা ১-মিনিটের ভেতরে হলে এটাই ডাকে */
    private fun doSwitch(target: Int) {
        playIdx = target
        url = playUrls[target]
        title = playNames.getOrNull(target) ?: title
        resumePos = 0L
        started = false
        retryCount = 0
        audioChecked = false
        audioFallbackDone = false
        errorBox.visibility = View.GONE
        findViewById<TextView>(R.id.pTitle).text = creditTitle()
        refreshEpButtons()
        val p = player
        if (p != null) {
            p.setMediaItem(MediaItem.fromUri(url))
            p.prepare()
            p.playWhenReady = true
        } else buildPlayer()
        Toast.makeText(this, "এখন চলছে: $title", Toast.LENGTH_SHORT).show()
    }

    /** ⏮ ⏭ বাটনের দৃশ্যমানতা — আগের/পরের এপিসোড থাকলেই দেখাবে */
    private fun refreshEpButtons() {
        btnPrevEp.visibility = if (playIdx > 0) View.VISIBLE else View.GONE
        btnNextEp.visibility =
            if (playIdx >= 0 && playIdx + 1 < playUrls.size) View.VISIBLE else View.GONE
    }

    /* ── জেসচার — পুরো স্ক্রিনে কাজ করে (রেফারেন্স-প্লেয়ারের মতো) ──
     *  ট্যাপ            → কন্ট্রোলার দেখাও/লুকাও
     *  ডানে-বাঁয়ে টানা   → ⏪ ⏩ সিক — টানতে থাকলে শুধু সময়, ছাড় দিলে একবার সিক
     *  বাঁ পাশে উপর-নিচ → ☀️ ব্রাইটনেস পিল (আইকন + সাদা বার)
     *  ডান পাশে উপর-নিচ → 🔊 ভলিউম পিল
     *  চেপে ধরে রাখা    → ⏩ 2x ফরওয়ার্ড প্লে, ছাড় দিলে আগের স্পিড
     */
    private fun setupGestures() {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val slop = 24f
        var downX = 0f; var downY = 0f
        var seekBase = 0L; var seekTarget = 0L; var seekDur = 0L
        var volBase = 0
        var mode = 0 // 0=none 1=seek 2=bright 3=volume
        var brightBase = 0f
        var pointerDown = false
        var holdEngaged = false
        /* ⚡ ডাবল-ট্যাপ: বাঁয়ে = −১০ সেকেন্ড, ডানে = +১০ সেকেন্ড, মাঝে = প্লে/পজ
           (v3.4 — আলাদা পজ/±10s বাটন বাদ, রেফারেন্স-অ্যাপের মতো জেসচারেই সব) */
        var lastTapAt = 0L
        val pendingTap = Runnable {
            if (controllerVisible) pv.hideController() else pv.showController()
        }

        val holdCheck = Runnable {
            /* চেপে ধরে রাখলে (টান ছাড়া) 2x ফরওয়ার্ড প্লে */
            if (pointerDown && mode == 0 && player != null && !locked) {
                holdEngaged = true
                player?.playbackParameters = PlaybackParameters(2f)
                showPill2x()
            }
        }

        fun endHold() {
            ui.removeCallbacks(holdCheck)
            if (holdEngaged) {
                holdEngaged = false
                player?.playbackParameters = PlaybackParameters(SPEEDS[speedIndex])
                pill2x.visibility = View.GONE
            }
        }

        pv.setOnTouchListener { _, ev ->
            if (locked) return@setOnTouchListener true
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x; downY = ev.y
                    mode = 0
                    pointerDown = true
                    seekBase = player?.currentPosition ?: 0L
                    seekTarget = seekBase
                    seekDur = player?.duration?.takeIf { it > 0 } ?: 0L
                    volBase = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    brightBase = window.attributes.screenBrightness.takeIf { it > 0 } ?: 0.5f
                    ui.removeCallbacks(holdCheck)
                    ui.postDelayed(holdCheck, 450)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (mode == 0 && (abs(dx) > slop || abs(dy) > slop)) {
                        /* টান শুরু হলে 2x-হোল্ড বাতিল */
                        if (abs(dx) > slop && abs(dx) > abs(dy)) mode = 1
                        else mode = if (downX < resources.displayMetrics.widthPixels / 2f) 2 else 3
                        ui.removeCallbacks(holdCheck)
                    }
                    when (mode) {
                        1 -> {
                            val span = resources.displayMetrics.widthPixels.toFloat().takeIf { it > 0 } ?: 1080f
                            val deltaMs = (dx / span * 120_000f).toLong()
                            seekTarget = if (seekDur > 0)
                                (seekBase + deltaMs).coerceIn(0L, seekDur)
                            else (seekBase + deltaMs).coerceAtLeast(0L)
                            val diff = seekTarget - seekBase
                            val extra = if (seekDur > 0) " / ${fmtTime(seekDur)}" else ""
                            val sign = if (diff >= 0) "+" else "-"
                            /* (v3.6) শুধু সময় + অঙ্ক — "সামনে/পিছনে" লেখা নেই, আইকন দিয়ে দিক বোঝায় */
                            showHint(
                                if (diff >= 0) R.drawable.ic_ffwd10 else R.drawable.ic_rew10,
                                "${fmtTime(seekTarget)}$extra  ${sign}${abs(diff) / 1000}"
                            )
                        }
                        2 -> {
                            val f = (brightBase - dy / resources.displayMetrics.heightPixels * 1.2f).coerceIn(0.01f, 1f)
                            val attr = window.attributes
                            attr.screenBrightness = f
                            window.attributes = attr
                            showPill(R.drawable.ic_bright, (f * 100).toInt())
                        }
                        3 -> {
                            val delta = (-dy / 35f).toInt()
                            val nv = (volBase + delta).coerceIn(0, maxVol)
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
                            showPill(R.drawable.ic_vol, nv * 100 / maxVol)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pointerDown = false
                    if (holdEngaged) {
                        /* 2x হোল্ড শেষ — আগের স্পিডে ফেরত, কন্ট্রোলার টগল নয় */
                        endHold()
                    } else if (mode == 1) {
                        ui.removeCallbacks(holdCheck)
                        /* আঙুল তুলে দিলেই একবার সিক */
                        player?.seekTo(seekTarget)
                        hideHintSoon()
                    } else if (mode == 0) {
                        endHold()
                        /* ⬇ নিচে টানলে ইউটিউবের মতো মিনি-প্লেয়ারে নামে —
                           ডিটেইল-পেজের মিনি-প্লেয়ার এখানকার পজিশন থেকেই চলবে */
                        val dy = ev.y - downY
                        if (dy > dp(110) && abs(dy) > abs(ev.x - downX)) {
                            ui.removeCallbacks(pendingTap)
                            finish()
                            return@setOnTouchListener true
                        }
                        /* ⚡ ট্যাপ/ডাবল-ট্যাপ */
                        val now = System.currentTimeMillis()
                        val w = resources.displayMetrics.widthPixels
                        if (now - lastTapAt < 320) {
                            /* ডাবল-ট্যাপ — সিঙ্গেল-ট্যাপের বিলম্বিত কাজ বাতিল করে জেসচার চালু */
                            ui.removeCallbacks(pendingTap)
                            lastTapAt = 0
                            val p = player
                            if (p != null) {
                                when {
                                    ev.x < w / 3f -> {
                                        p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                                        showHint(R.drawable.ic_rew10, "10")
                                    }
                                    ev.x > w * 2f / 3f -> {
                                        p.seekTo(p.currentPosition + 10_000)
                                        showHint(R.drawable.ic_ffwd10, "10")
                                    }
                                    else -> {
                                        if (p.isPlaying) p.pause() else p.play()
                                        /* (v3.6) শুধু প্লে/পজ আইকন — কোনো লেখা নেই */
                                        showHint(
                                            if (p.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                                            ""
                                        )
                                    }
                                }
                            }
                        } else {
                            /* সিঙ্গেল-ট্যাপ → কন্ট্রোলার (ডাবল-ট্যাপ আসলে বিলম্বিতভাবে) */
                            lastTapAt = now
                            ui.removeCallbacks(pendingTap)
                            ui.postDelayed(pendingTap, 320)
                        }
                    } else {
                        endHold()
                        hideHintSoon()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** মিলিসেকেন্ড → "mm:ss" বা "h:mm:ss" */
    private fun fmtTime(ms: Long): String {
        val t = ms.coerceAtLeast(0L) / 1000
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    /* ── 🎛 ভলিউম/ব্রাইটনেস পিল (আইকন + সাদা বার, উপরে-মাঝে) ── */
    private fun showPill(icon: Int, pct: Int) {
        pillIcon.setImageResource(icon)
        pillBar.progress = pct.coerceIn(0, 100)
        gestureHint.visibility = View.GONE
        gesturePill.visibility = View.VISIBLE
        ui.removeCallbacks(hidePill)
        ui.postDelayed(hidePill, 900)
    }

    private val hidePill = Runnable { gesturePill.visibility = View.GONE }

    /* ⏩ 2x হোল্ড-পিল */
    private fun showPill2x() {
        gestureHint.visibility = View.GONE
        pill2x.visibility = View.VISIBLE
    }

    /* (v3.6) হিন্ট = রিয়েল আইকন + শুধু অঙ্ক — কোনো ইমোজি/বাংলা শব্দ নয় (ম্যাক্স-প্লেয়ারের মতো) */
    private fun showHint(icon: Int?, text: String) {
        gesturePill.visibility = View.GONE
        if (icon != null) {
            hintIcon.setImageResource(icon)
            hintIcon.visibility = View.VISIBLE
        } else hintIcon.visibility = View.GONE
        gestureHint.text = text
        gestureHint.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        hintBox.visibility = View.VISIBLE
        ui.removeCallbacks(hideHint)
        ui.postDelayed(hideHint, 900)
    }

    private val hideHint = Runnable { hintBox.visibility = View.GONE }
    private fun hideHintSoon() { ui.postDelayed(hideHint, 400) }

    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
    }

    /* 🎬 (v3.8) ফুলস্ক্রিন বন্ধ হচ্ছে (ব্যাক) → প্লেয়ার এখানেই মিনিতে ফেরত:
       onPause আগে চলে, DetailActivity-র onResume পরে — তাই মিনি ফিরে এসেই
       প্লেয়ার বুঝে যায় (আগে onDestroy-এ ফেরত দিতাম, অথচ onDestroy চলত
       onResume-এর পরে — মিনি কালো+পজড হয়ে যেত!)। প্লে চালু থাকে — লোড/বাফার নেই। */
    private fun handBackToMini() {
        val p = player ?: return
        player = null
        pv.player = null
        DetailActivity.hfUrl = url          /* ফুলস্ক্রিনে এপিসোড-সুইচ হলে নতুনটাই যাবে */
        DetailActivity.hfName = title
        DetailActivity.hfPlayer = p
    }


    override fun onDestroy() {
        super.onDestroy()
        ui.removeCallbacks(watchTick)   /* 📊 (v4.0) হার্টবিট বন্ধ */
        ui.removeCallbacksAndMessages(null)
        if (adopted) {
            /* 🎬 ব্যাক দিলে onPause-এই ফেরত গেছে; সিস্টেম মেরে ফেললে এখানে ফেরত —
               কোনোভাবেই প্লেয়ার হারায় না। ডিটেইল পেজ বন্ধ থাকলে এখানেই বন্ধ। */
            val p = player
            player = null
            pv.player = null
            if (p != null) {
                DetailActivity.hfUrl = url
                DetailActivity.hfName = title
                if (DetailActivity.alive()) DetailActivity.hfPlayer = p else p.release()
            }
        } else {
            /* 🎬 মিনি-স্কোপড হ্যান্ডওফ (v3.5) — শুধু নিজের বানানো প্লেয়ারে;
               গ্রহণ করা প্লেয়ারে লিখলে মিনি ভুল পজিশনে সিক/রিলোড করত */
            DetailActivity.hfMovie = movieName
            DetailActivity.hfUrl = url
            DetailActivity.hfName = title
            DetailActivity.hfPos = player?.currentPosition?.takeIf { it > 0 } ?: resumePos
            player?.release()
            player = null
        }
    }
}
