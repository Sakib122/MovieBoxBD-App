package com.movieboxbd.app

import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * ▶ MX Player স্টাইলের প্লেয়ার v4 (২.৩):
 *  — ✅ প্লে/পজ এক বাটনে (exo_play_pause) — Media3 নিজেই আইকন বদলায় ও ক্লিক ধরে
 *  — ✅ ফুল ইমারসিভ: স্ট্যাটাস/নেভিগেশন বার উঠে এলে আবার লুকিয়ে দেওয়া হয়,
 *     আর কন্ট্রোল বারে ইনসেট প্যাডিং — তাই বাটন কখনো নেভবারের নিচে চাপা পড়বে না
 *  — ✅ MX-এর মতো টেনে সিক: টানতে থাকলে শুধু সময় দেখায়, ছাড় দিলে একবার সিক হয়
 *     (আগে প্রতি নড়াচড়ায় seekTo হচ্ছিল — তাই ০ সেকেন্ডে লাফিয়ে ফিরে আসত)
 *  — ✅ বাঁ পাশে উপর-নিচ = ☀️ ব্রাইটনেস, ডান পাশে উপর-নিচ = 🔊 ভলিউম
 *  — ✅ ⛶ স্ক্রিন মোড: ফিট → জুম (পুরো স্ক্রিন ভরে) → টানাটানি → ফিট
 *  — 🎧 অডিও ট্র্যাক: ভাষার ইংরেজি নাম (Hindi/Tamil/Bangla/Telugu…) +
 *     ফাইলের ভেতরের ক্রেডিট (MovieLinkBD.one…) সরিয়ে MovieBoxBD বসে
 *  — 💬 সাবটাইটেল অন/অফ, ⚡ স্পিড, 🔓 লক (আনলক উপরের কোণায়)
 *  — ভিডিও অনুযায়ী অটো ল্যান্ডস্কেপ/পোর্ট্রেট + রোটেট বাটন
 *  — ধীর সার্ভার হলেও অপেক্ষা করে (৩০ সেকেন্ড টাইমআউট) + আবার চেষ্টা বাটন
 */
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var audioDisabled = false
    private var subsDisabled = false
    private var retried = false
    private var url: String = ""
    private var title: String = ""

    private lateinit var pv: PlayerView
    private lateinit var gestureHint: TextView
    private lateinit var errorBox: View
    private lateinit var errText: TextView
    private lateinit var mxBar: LinearLayout
    private lateinit var btnMLock: TextView
    private lateinit var btnMUnlock: TextView
    private lateinit var btnMSpeed: TextView
    private lateinit var btnMAudio: TextView
    private lateinit var btnMSub: TextView
    private lateinit var btnMScale: TextView
    private lateinit var lockLayer: View
    private var started = false
    private var locked = false
    private var controllerVisible = false

    private val SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private val SPEED_LABELS = arrayOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x")
    private var speedIndex = 2

    /** ⛶ স্ক্রিন মোড: ফিট → জুম → টানাটানি */
    private val SCALE_MODES = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    )
    private val SCALE_HINTS = arrayOf(
        "⛶ ফিট — অনুপাত ঠিক রেখে পুরো ভিডিও দেখা যায়",
        "⛶ জুম — পুরো স্ক্রিন ভরে যায় (কিছু অংশ কাটা যায়)",
        "⛶ টানাটানি — স্ক্রিনের মাপে ভরে যায় (মুখ লম্বা/চ্যাপ্টা লাগতে পারে)"
    )
    private var scaleIndex = 0

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

        pv = findViewById(R.id.playerView)
        gestureHint = findViewById(R.id.gestureHint)
        errorBox = findViewById(R.id.errorBox)
        errText = findViewById(R.id.errText)
        mxBar = findViewById(R.id.mxBar)
        btnMLock = findViewById(R.id.btnMLock)
        btnMUnlock = findViewById(R.id.btnMUnlock)
        btnMSpeed = findViewById(R.id.btnMSpeed)
        btnMAudio = findViewById(R.id.btnMAudio)
        btnMSub = findViewById(R.id.btnMSub)
        btnMScale = findViewById(R.id.btnMScale)
        lockLayer = findViewById(R.id.lockLayer)
        findViewById<TextView>(R.id.pTitle).text = title
        findViewById<ImageButton>(R.id.btnPBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnRetry).setOnClickListener { retry() }
        findViewById<ImageButton>(R.id.btnPRotate).setOnClickListener {
            requestedOrientation =
                if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

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
            /* mxBar-ও নেভবার + কন্ট্রোল বারের উপরে উঠে থাকবে */
            mxBar.layoutParams = (mxBar.layoutParams as android.widget.FrameLayout.LayoutParams).also {
                it.bottomMargin = 116 + bottom
            }
            /* স্ট্যাটাস/নেভিগেশন বার স্থায়ীভাবে ফিরে এলে আবার লুকাও */
            if (insets.isVisible(WindowInsetsCompat.Type.systemBars())) hideSystemBars()
            WindowInsetsCompat.CONSUMED
        }

        /* 🔒 MX স্টাইল লক — আনলক আইকন উপরের কোণায় */
        btnMLock.setOnClickListener { lockScreen() }
        btnMUnlock.setOnClickListener { unlockScreen() }
        lockLayer.setOnClickListener { showHint("🔒 স্ক্রিন লক করা — খুলতে উপরের কোণার 🔓 চাপো") }
        /* ⚡ স্পিড */
        btnMSpeed.setOnClickListener { showSpeedDialog() }
        /* 🎧 অডিও ট্র্যাক */
        btnMAudio.setOnClickListener { showAudioDialog() }
        /* 💬 সাবটাইটেল অন/অফ */
        btnMSub.setOnClickListener { toggleSubs() }
        /* ⛶ স্ক্রিন মোড (ফিট → জুম → টানাটানি) */
        btnMScale.setOnClickListener {
            scaleIndex = (scaleIndex + 1) % SCALE_MODES.size
            pv.resizeMode = SCALE_MODES[scaleIndex]
            showHint(SCALE_HINTS[scaleIndex])
        }

        /* লক থাকলে ব্যাক চাপলে বের হবে না — MX-এর মতো */
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (locked) {
                    Toast.makeText(
                        this@PlayerActivity,
                        "🔒 আগে উপরের কোণার 🔓 দিয়ে আনলক করো",
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

    /* ── 🔒 স্ক্রিন লক / আনলক ───────────────────────────────── */

    private fun lockScreen() {
        locked = true
        pv.hideController()
        controllerVisible = false
        mxBar.visibility = View.GONE
        lockLayer.visibility = View.VISIBLE
        btnMUnlock.visibility = View.VISIBLE
        Toast.makeText(this, "🔒 স্ক্রিন লক হয়েছে — খুলতে উপরের কোণার 🔓 চাপো", Toast.LENGTH_SHORT).show()
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
            .setTitle("⚡ প্লে স্পিড")
            .setItems(SPEED_LABELS) { _, w ->
                speedIndex = w
                player?.playbackParameters = PlaybackParameters(SPEEDS[w])
                btnMSpeed.text = SPEED_LABELS[w]
            }
            .show()
    }

    /* ── 🎧 অডিও ট্র্যাক (ইংরেজি ভাষার নাম + ক্রেডিট বাদ) ─────── */

    /** ফাইলের ভেতরের লেবেল থেকে ক্রেডিট (MovieLinkBD.one …) সরিয়ে পরিষ্কার করে */
    private fun cleanLabel(raw: String?): String? {
        var s = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        s = s.replace(Regex("(?i)movie\\s*link\\s*bd(\\s*\\.\\s*(one|com|net|org|xyz|info))?"), "MovieBoxBD")
        s = s.replace(Regex("(?i)movie\\s*box\\s*bd(\\s*\\.\\s*(one|com|net|org|xyz|info))?"), "MovieBoxBD")
        s = s.replace("|", " ").replace(Regex("\\s+"), " ").trim()
        s = s.replace(Regex("^[•\\-–—_\\s]+"), "").replace(Regex("[•\\-–—_\\s]+$"), "")
        return s.ifBlank { null }
    }

    private fun audioRowLabel(g: Tracks.Group): String {
        for (i in 0 until g.length) {
            val f = g.getTrackFormat(i)
            val langCode = f.language?.trim()?.lowercase()?.substringBefore('-')
            val langName = langCode?.let { LANG_NAMES[it] }
            /* ভাষার কোড চেনা গেলে ইংরেজি নাম — নাহলে লেবেল (ক্রেডিট সরিয়ে) */
            val base = langName ?: cleanLabel(f.label) ?: "অডিও ট্র্যাক"
            val ch = when (f.channelCount) {
                1 -> "মোনো"; 2 -> "স্টেরিও"; 6 -> "5.1"; 8 -> "7.1"; else -> null
            }
            val bits = ArrayList<String>()
            bits.add(base)
            if (ch != null) bits.add(ch)
            return bits.joinToString("  •  ")
        }
        return "অডিও ট্র্যাক"
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
        for ((i, g) in groups.withIndex()) {
            items.add("${i + 1}. ${audioRowLabel(g)}")
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
            .setTitle("🎧 অডিও ট্র্যাক বাছো")
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
                    if (checked == 0) "🎧 অডিও: অটো (ডিফল্ট)" else "🎧 অডিও: ${items[checked]}",
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
        btnMSub.text = "💬"
        btnMSub.alpha = if (subsDisabled) 0.45f else 1f
        Toast.makeText(
            this,
            if (subsDisabled) "💬 সাবটাইটেল অফ করা হলো" else "💬 সাবটাইটেল চালু হলো",
            Toast.LENGTH_SHORT
        ).show()
    }

    /* ── প্লেয়ার তৈরি (ধীর সার্ভারের জন্য বড় টাইমআউট) ────────── */

    private fun buildPlayer() {
        errorBox.visibility = View.GONE

        val tsel = DefaultTrackSelector(this)
        var pb = tsel.buildUponParameters()
        if (audioDisabled) pb = pb.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
        if (subsDisabled) pb = pb.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        tsel.parameters = pb.build()

        val dsFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Linux; Android) MovieBoxBD/2.3")

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
                mxBar.visibility = if (controllerVisible) View.VISIBLE else View.GONE
            }
        })

        player?.setMediaItem(MediaItem.fromUri(url))
        player?.playbackParameters = PlaybackParameters(SPEEDS[speedIndex])
        player?.playWhenReady = true
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY && !started) {
                    started = true
                    player?.play()
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

    /* ── এরর হ্যান্ডলিং: একবার অটো-রিট্রাই, নাহলে রিট্রাই বাটন ──── */

    private fun handle(error: PlaybackException) {
        val code = error.errorCode
        val audioIssue = code == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                code == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                code == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                code == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                code == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED

        if (audioIssue && !audioDisabled) {
            audioDisabled = true
            Toast.makeText(this, "🔇 এই ফোনে ওই সাউন্ড ফরম্যাট সাপোর্ট নেই — ছবি চলছে", Toast.LENGTH_LONG).show()
            restartPlayer()
        } else if (!retried) {
            retried = true
            ui.postDelayed({ retry() }, 2500)
        } else {
            started = false
            errText.text = "⚠️ লোড হচ্ছে না — সার্ভার ব্যস্ত থাকতে পারে"
            errorBox.visibility = View.VISIBLE
        }
    }

    private fun retry() {
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

    private fun restartPlayer() {
        player?.release()
        player = null
        started = false
        buildPlayer()
    }

    /* ── MX স্টাইল জেসচার — পুরো স্ক্রিনে কাজ করে ────────────────
     *  ট্যাপ            → কন্ট্রোলার দেখাও/লুকাও (প্লে/পজ বাটন ওঠে)
     *  ডানে-বাঁয়ে টানা   → ⏪ ⏩ সিক — টানতে থাকলে শুধু সময় দেখায়,
     *                       আঙুল তুলে দিলে একবার সিক হয় (MX Player-এর মতো)
     *  বাঁ পাশে উপর-নিচ → ☀️ ব্রাইটনেস
     *  ডান পাশে উপর-নিচ → 🔊 ভলিউম
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

        pv.setOnTouchListener { _, ev ->
            if (locked) return@setOnTouchListener true
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x; downY = ev.y
                    mode = 0
                    seekBase = player?.currentPosition ?: 0L
                    seekTarget = seekBase
                    seekDur = player?.duration?.takeIf { it > 0 } ?: 0L
                    volBase = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    brightBase = window.attributes.screenBrightness.takeIf { it > 0 } ?: 0.5f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (mode == 0) {
                        mode = if (abs(dx) > slop && abs(dx) > abs(dy)) 1
                        else if (abs(dy) > slop)
                            if (downX < resources.displayMetrics.widthPixels / 2f) 2 else 3
                        else 0
                    }
                    when (mode) {
                        1 -> {
                            /* MX-এর মতো: পুরো স্ক্রিন-প্রস্থ টানলে ~২ মিনিট।
                               টানার সময় শুধু টার্গেট সময় দেখাই — seekTo হয় না! */
                            val span = resources.displayMetrics.widthPixels.toFloat().takeIf { it > 0 } ?: 1080f
                            val deltaMs = (dx / span * 120_000f).toLong()
                            seekTarget = if (seekDur > 0)
                                (seekBase + deltaMs).coerceIn(0L, seekDur)
                            else (seekBase + deltaMs).coerceAtLeast(0L)
                            val diff = seekTarget - seekBase
                            val arrow = if (diff >= 0) "⏩" else "⏪"
                            val extra = if (seekDur > 0) " / ${fmtTime(seekDur)}" else ""
                            val sign = if (diff >= 0) "+" else "-"
                            showHint("$arrow ${fmtTime(seekTarget)}$extra  ($sign${abs(diff) / 1000} সে)")
                        }
                        2 -> {
                            val f = (brightBase - dy / resources.displayMetrics.heightPixels * 1.2f).coerceIn(0.01f, 1f)
                            val attr = window.attributes
                            attr.screenBrightness = f
                            window.attributes = attr
                            showHint("☀️ ব্রাইটনেস ${(f * 100).toInt()}%")
                        }
                        3 -> {
                            val delta = (-dy / 35f).toInt()
                            val nv = (volBase + delta).coerceIn(0, maxVol)
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
                            showHint("🔊 ভলিউম ${(nv * 100 / maxVol)}%")
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (mode == 1) {
                        /* আঙুল তুলে দিলেই একবার সিক — এখানেই আসল seekTo */
                        player?.seekTo(seekTarget)
                    } else if (mode == 0) {
                        /* সাধারণ ট্যাপ → কন্ট্রোলার আসবে/যাবে (প্লে/পজ বাটনসহ) */
                        if (controllerVisible) pv.hideController() else pv.showController()
                    }
                    hideHintSoon()
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

    private fun showHint(text: String) {
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
        ui.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }
}
