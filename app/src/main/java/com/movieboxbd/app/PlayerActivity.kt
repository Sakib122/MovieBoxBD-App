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

    companion object {
        /* 🎬 ডিটেইল-পেজের মিনি-প্লেয়ার ও ফুলস্ক্রিনের পজিশন-হ্যান্ডওফ */
        var handoffUrl: String = ""
        var handoffPos: Long = -1L
    }

    private var player: ExoPlayer? = null
    private var trackSel: DefaultTrackSelector? = null
    private var audioDisabled = false
    private var subsDisabled = false
    private var retryCount = 0
    private var url: String = ""
    private var title: String = ""
    private var movieName: String = ""   /* 🏷️ আসল মুভির নাম (ক্রেডিট + প্যানেল) */
    private var resumePos = 0L           /* ⏱ মিনি-প্লেয়ার থেকে আসলে ওখানকার পজিশন */

    private lateinit var pv: PlayerView
    private lateinit var gestureHint: TextView
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
    private lateinit var btnMQ: TextView
    private lateinit var lockLayer: View
    private var started = false
    private var locked = false
    private var controllerVisible = false
    /* অডিও কম্প্যাট: READY হলেই একবার চেক (ইউজার নিজে বাছলে আর নাক গলানো হয় না) */
    private var audioChecked = false
    private var audioFallbackDone = false
    /* 🔊 সাউন্ড সাপোর্ট নেই এমন ফোনে MX Player ফলব্যাক ডায়ালগ (বারবার দেখানো যায়) */
    private var soundHelpDialog: AlertDialog? = null
    private var extPlayerUsed = false

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
    private val QUALITY_HEIGHTS = intArrayOf(Int.MAX_VALUE, 2160, 1080, 720, 480, 360)
    private val QUALITY_LABELS = arrayOf("Auto", "2160P", "1080P", "720P", "480P", "360P")
    private var qualityIndex = 0

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
        resumePos = intent.getLongExtra("resume_pos", 0L).coerceAtLeast(0L)

        pv = findViewById(R.id.playerView)
        gestureHint = findViewById(R.id.gestureHint)
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
        btnMQ = findViewById(R.id.btnMQ)
        lockLayer = findViewById(R.id.lockLayer)
        findViewById<TextView>(R.id.pTitle).text = creditTitle()
        findViewById<ImageButton>(R.id.btnPBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnRetry).setOnClickListener { retry() }
        btnErrTg.setOnClickListener { openTelegramChannel() }
        findViewById<TextView>(R.id.btnHelp).setOnClickListener { openTelegramChannel() }
        findViewById<TextView>(R.id.btnSetting).setOnClickListener { showSettings() }

        /* ⏪10 / ⏩10 — রেফারেন্স-প্লেয়ারের মতো */
        findViewById<View>(R.id.btnRew10).setOnClickListener { player?.seekBack() }
        findViewById<View>(R.id.btnFwd10).setOnClickListener { player?.seekForward() }

        findViewById<ImageButton>(R.id.btnPRotate).setOnClickListener { rotateNow() }

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
        lockLayer.setOnClickListener { showHint("স্ক্রিন লক করা — খুলতে উপরের বাঁ কোণার আনলক বাটন চাপো") }
        btnMSpeed.setOnClickListener { showSpeedDialog() }
        btnMAudio.setOnClickListener { showAudioDialog() }
        btnMScale.setOnClickListener { cycleScale() }
        btnMQ.setOnClickListener { showQualityDialog() }

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
        buildPlayer()
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

    /* ── 🔄 রোটেট ── */
    private fun rotateNow() {
        requestedOrientation =
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    /* ── ⛶ স্ক্রিন-মোড সাইকেল ── */
    private fun cycleScale() {
        scaleIndex = (scaleIndex + 1) % SCALE_MODES.size
        pv.resizeMode = SCALE_MODES[scaleIndex]
        btnMScale.text = SCALE_LABELS[scaleIndex]
        showHint(SCALE_HINTS[scaleIndex])
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

    /* ── 🎬 ভিডিও কোয়ালিটি-ক্যাপ ─────────────────────────────── */

    private fun showQualityDialog() {
        val ts = trackSel ?: return
        AlertDialog.Builder(this)
            .setTitle("ভিডিও কোয়ালিটি")
            .setItems(QUALITY_LABELS) { _, w ->
                qualityIndex = w
                ts.parameters = ts.buildUponParameters()
                    .setMaxVideoHeight(QUALITY_HEIGHTS[w])
                    .build()
                btnMQ.text = QUALITY_LABELS[w]
                Toast.makeText(this, "কোয়ালিটি: ${QUALITY_LABELS[w]}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    /* ── ⚙ সেটিংস-ডায়ালগ (রেফারেন্সের Setting) ─────────────── */

    private fun showSettings() {
        val opts = arrayOf(
            "প্লে স্পিড", "ভিডিও কোয়ালিটি", "অডিও (ভাষা)",
            "সাবটাইটেল অন/অফ", "স্ক্রিন মোড", "রোটেট করো"
        )
        AlertDialog.Builder(this)
            .setTitle("সেটিংস")
            .setItems(opts) { _, w ->
                when (w) {
                    0 -> showSpeedDialog()
                    1 -> showQualityDialog()
                    2 -> showAudioDialog()
                    3 -> toggleSubs()
                    4 -> cycleScale()
                    5 -> rotateNow()
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

    /* ── 🔊 MX Player ফলব্যাক (সাউন্ড সাপোর্ট নেই এমন ফোনের জন্য) ────── */
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

        soundHelpDialog = AlertDialog.Builder(this)
            .setTitle("এই ফোনে সাউন্ড চলছে না")
            .setMessage(
                "মুভির সাউন্ড ফরম্যাট (Dolby) এই ফোনের প্লেয়ার ডিকোড করতে পারছে না।\n\n" +
                "MX Player বা VLC দিয়ে খুললে সাউন্ডসহ চলবে।\n\n" +
                "• আগে থেকে ইনস্টল থাকলে চাপ দিলেই সঙ্গে সঙ্গে মুভি চলবে\n" +
                "• না থাকলে Play Store থেকে ইনস্টল করো — ফিরে এলেই ডায়ালগ আবার আসবে"
            )
            .setPositiveButton("MX Player-এ চালাও") { _, _ ->
                if (!launchIn(MX1) && !launchIn(MX2)) {
                    Toast.makeText(this, "MX Player ইনস্টল নেই — Play Store খুলছে… ইনস্টল করে ফিরে এলেই মুভি চলবে", Toast.LENGTH_LONG).show()
                    openStore(MX1)
                }
            }
            .setNeutralButton("VLC-তে চালাও") { _, _ ->
                if (!launchIn(VLC)) {
                    Toast.makeText(this, "VLC ইনস্টল নেই — Play Store খুলছে… ইনস্টল করে ফিরে এলেই মুভি চলবে", Toast.LENGTH_LONG).show()
                    openStore(VLC)
                }
            }
            .setNegativeButton("এখন না", null)
            .show()
    }

    /* 🛠 Play Store থেকে MX Player/VLC ইনস্টল করে ফিরে এলে ডায়ালগ নিজেই আবার দেখাও */
    override fun onResume() {
        super.onResume()
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
        ui.removeCallbacks(reShowSoundHelp)
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
        if (qualityIndex > 0) pb = pb.setMaxVideoHeight(QUALITY_HEIGHTS[qualityIndex])
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

        pv.player = player
        pv.resizeMode = SCALE_MODES[scaleIndex]
        pv.controllerShowTimeoutMs = 3500
        pv.setControllerVisibilityListener(object : PlayerView.ControllerVisibilityListener {
            override fun onVisibilityChanged(visibility: Int) {
                controllerVisible = visibility == View.VISIBLE
                lockRow.visibility = if (controllerVisible) View.VISIBLE else View.GONE
            }
        })

        player?.setMediaItem(MediaItem.fromUri(url))
        player?.playbackParameters = PlaybackParameters(SPEEDS[speedIndex])
        player?.playWhenReady = true
        player?.addListener(object : Player.Listener {
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
                if (state == Player.STATE_ENDED) finish()
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
        })
        player?.prepare()
    }

    /* ── এরর হ্যান্ডলিং: ৩ বার অটো-রিট্রাই (koyeb ঠান্ডা হলেও ধরে ফেলে), নাহলে বাটন ── */

    private val autoRetry = Runnable { prepareAgain() }

    private fun handle(error: PlaybackException) {
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
            }
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
        }
    }

    /** একই লিংকে আবার prepare — retryCount রিসেট হয় না (অটো-রিট্রাই এটাই ডাকে) */
    private fun prepareAgain() {
        errorBox.visibility = View.GONE
        val p = player
        if (p != null) {
            p.seekToDefaultPosition()
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
        player?.release()
        player = null
        started = false
        buildPlayer()
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
                            val dir = if (diff >= 0) "সামনে" else "পিছনে"
                            val extra = if (seekDur > 0) " / ${fmtTime(seekDur)}" else ""
                            val sign = if (diff >= 0) "+" else "-"
                            showHint("$dir ${fmtTime(seekTarget)}$extra  (${abs(diff) / 1000} সেকেন্ড)")
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
                        /* সাধারণ ট্যাপ → কন্ট্রোলার আসবে/যাবে */
                        if (controllerVisible) pv.hideController() else pv.showController()
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

    private fun showHint(text: String) {
        gesturePill.visibility = View.GONE
        gestureHint.text = text
        gestureHint.visibility = View.VISIBLE
        ui.removeCallbacks(hideHint)
        ui.postDelayed(hideHint, 900)
    }

    private val hideHint = Runnable { gestureHint.visibility = View.GONE }
    private fun hideHintSoon() { ui.postDelayed(hideHint, 400) }

    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        /* 🎬 ফিরে মিনি-প্লেয়ার এখানকার পজিশন থেকে চলবে */
        handoffUrl = url
        handoffPos = player?.currentPosition ?: -1L
        ui.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }
}
