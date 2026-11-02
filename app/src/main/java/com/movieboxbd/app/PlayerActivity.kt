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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import androidx.media3.ui.PlayerView
import kotlin.math.abs

/**
 * ▶ MX Player স্টাইলের প্লেয়ার v3:
 *  — ✅ প্লে/পজ বাটন + স্ক্রিনে ট্যাপ করলেই কন্ট্রোলার আসে-যায়
 *  — ✅ আঙুল টেনে সামনে/পেছনে (seek), বাঁ পাশে উপর-নিচ = ব্রাইটনেস,
 *     ডান পাশে উপর-নিচ = ভলিউম (MX Player-এর মতোই)
 *  — ✅ 🔓 আনলক আইকন উপরের কোণায় (MX Player-এর মতো)
 *  — ⚡ প্লে স্পিড (0.5x থেকে 2x)
 *  — 🎧 অডিও ট্র্যাক বদলানো (ভাষার নামসহ — হিন্দি/বাংলা দেখাবে)
 *  — 💬 সাবটাইটেল অন/অফ টগল
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
    private lateinit var lockLayer: View
    private var started = false
    private var locked = false
    private var controllerVisible = false

    private val SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private val SPEED_LABELS = arrayOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x")
    private var speedIndex = 2

    /** ম্যানুয়ালি বাছা অডিও ট্র্যাক (-1 = অটো) */
    private var audioPick = -1

    /** ভাষার কোড → সুন্দর নাম */
    private val LANG_NAMES = mapOf(
        "hin" to "হিন্দি", "ben" to "বাংলা", "bn" to "বাংলা", "eng" to "English",
        "tam" to "তামিল", "tel" to "তেলুগু", "mal" to "মালয়ালম", "kan" to "কন্নড়",
        "urd" to "উর্দু", "ara" to "আরবি", "spa" to "স্প্যানিশ", "kor" to "কোরিয়ান",
        "jpn" to "জাপানি", "chi" to "চাইনিজ", "zho" to "চাইনিজ", "nep" to "নেপালি",
        "mar" to "মারাঠি", "guj" to "গুজরাটি", "pan" to "পাঞ্জাবি", "tha" to "থাই"
    )

    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())
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

    /* ── 🎧 অডিও ট্র্যাক (ভাষার নামসহ — ফাঁকা রো আর থাকবে না) ── */

    private fun audioRowLabel(g: Tracks.Group): String {
        for (i in 0 until g.length) {
            val f = g.getTrackFormat(i)
            val name = f.label?.takeIf { it.isNotBlank() }
            val lang = f.language?.takeIf { it.isNotBlank() }
                ?.let { LANG_NAMES[it.lowercase().substringBefore('-')] ?: it }
            val base = name ?: lang
            val ch = when (f.channelCount) {
                1 -> "মোনো"; 2 -> "স্টেরিও"; 6 -> "5.1"; 8 -> "7.1"; else -> null
            }
            val bits = ArrayList<String>()
            if (!base.isNullOrBlank()) bits.add(base)
            if (ch != null) bits.add(ch)
            if (bits.isNotEmpty()) return bits.joinToString("  •  ")
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
            .setUserAgent("Mozilla/5.0 (Linux; Android) MovieBoxBD/2.0")

        player = ExoPlayer.Builder(this, DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
            .setTrackSelector(tsel)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

        pv.player = player
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
     *  ডানে-বাঁয়ে টানা   → ⏪ ⏩ সামনে/পেছনে
     *  বাঁ পাশে উপর-নিচ → ☀️ ব্রাইটনেস
     *  ডান পাশে উপর-নিচ → 🔊 ভলিউম
     */
    private fun setupGestures() {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val slop = 24f
        var downX = 0f; var downY = 0f
        var seekBase = 0L; var lastX = 0f
        var volBase = 0
        var mode = 0 // 0=none 1=seek 2=bright 3=volume
        var brightBase = 0f

        pv.setOnTouchListener { _, ev ->
            if (locked) return@setOnTouchListener true
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x; downY = ev.y; lastX = ev.x
                    mode = 0
                    seekBase = player?.currentPosition ?: 0L
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
                            val delta = ((ev.x - lastX) * 250).toLong()
                            lastX = ev.x
                            val dur = player?.duration?.takeIf { it > 0 } ?: 0L
                            var target = seekBase + delta
                            if (dur > 0) target = target.coerceIn(0L, dur)
                            player?.seekTo(target)
                            showHint(if (delta >= 0) "⏩ +${delta / 1000} সেকেন্ড" else "⏪ ${-delta / 1000} সেকেন্ড")
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
                    if (mode == 0) {
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
