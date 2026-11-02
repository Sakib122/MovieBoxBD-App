package com.movieboxbd.app

import android.app.Activity
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
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView

/**
 * ▶ MX Player স্টাইলের প্লেয়ার:
 *  — টেনে সামনে/পেছনে নেওয়া (seek)
 *  — বাঁ দিকে উপর-নিচ টানলে ব্রাইটনেস, ডান দিকে ভলিউম
 *  — 🔒 স্ক্রিন লক (MX-এর মতো — লক করলে কোনো ছোঁয়া কাজ করবে না)
 *  — ⚡ প্লে স্পিড (0.5x থেকে 2x)
 *  — 🎧 অডিও ট্র্যাক বদলানো (ডাবল অডিও মুভির জন্য)
 *  — ভিডিও অনুযায়ী অটো ল্যান্ডস্কেপ/পোর্ট্রেট + রোটেট বাটন
 *  — ধীর সার্ভার হলেও অপেক্ষা করে (৩০ সেকেন্ড টাইমআউট) + আবার চেষ্টা বাটন
 *  — ব্যাক বাটন দিয়ে বেরিয়ে আসা যায় ✔
 */
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var audioDisabled = false
    private var retried = false
    private var url: String = ""
    private var title: String = ""

    private lateinit var pv: PlayerView
    private lateinit var gestureArea: View
    private lateinit var gestureHint: TextView
    private lateinit var errorBox: View
    private lateinit var errText: TextView
    private lateinit var mxBar: LinearLayout
    private lateinit var btnMLock: TextView
    private lateinit var btnMUnlock: TextView
    private lateinit var btnMSpeed: TextView
    private lateinit var btnMAudio: TextView
    private var started = false
    private var locked = false

    private val SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private val SPEED_LABELS = arrayOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x")
    private var speedIndex = 2

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
        gestureArea = findViewById(R.id.gestureArea)
        gestureHint = findViewById(R.id.gestureHint)
        errorBox = findViewById(R.id.errorBox)
        errText = findViewById(R.id.errText)
        mxBar = findViewById(R.id.mxBar)
        btnMLock = findViewById(R.id.btnMLock)
        btnMUnlock = findViewById(R.id.btnMUnlock)
        btnMSpeed = findViewById(R.id.btnMSpeed)
        btnMAudio = findViewById(R.id.btnMAudio)
        findViewById<TextView>(R.id.pTitle).text = title
        findViewById<ImageButton>(R.id.btnPBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnRetry).setOnClickListener { retry() }
        findViewById<ImageButton>(R.id.btnPRotate).setOnClickListener {
            requestedOrientation =
                if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        /* 🔒 MX স্টাইল লক */
        btnMLock.setOnClickListener { lockScreen() }
        btnMUnlock.setOnClickListener { unlockScreen() }
        /* ⚡ স্পিড */
        btnMSpeed.setOnClickListener { showSpeedDialog() }
        /* 🎧 অডিও ট্র্যাক */
        btnMAudio.setOnClickListener { showAudioDialog() }

        /* লক থাকলে ব্যাক চাপলে বের হবে না — MX-এর মতো */
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (locked) {
                    Toast.makeText(
                        this@PlayerActivity,
                        "🔒 আগে নিচের 🔓 বাটন দিয়ে আনলক করো",
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
        mxBar.visibility = View.GONE
        gestureArea.visibility = View.VISIBLE
        btnMUnlock.visibility = View.VISIBLE
        Toast.makeText(this, "🔒 স্ক্রিন লক হয়েছে — খুলতে 🔓 চাপো", Toast.LENGTH_SHORT).show()
    }

    private fun unlockScreen() {
        locked = false
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

    /* ── 🎧 অডিও ট্র্যাক (ডাবল অডিও মুভির জন্য) ─────────────── */

    private fun showAudioDialog() {
        val p = player ?: return
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (groups.isEmpty()) {
            Toast.makeText(this, "এই ভিডিওতে কোনো অডিও ট্র্যাক নেই", Toast.LENGTH_SHORT).show()
            return
        }
        if (groups.size < 2) {
            Toast.makeText(this, "এই ভিডিওতে ১টাই অডিও ট্র্যাক আছে", Toast.LENGTH_SHORT).show()
            return
        }
        val items = ArrayList<String>()
        items.add("অটো (ডিফল্ট)")
        for (g in groups) {
            var label: String? = null
            for (i in 0 until g.length) {
                val f = g.getTrackFormat(i)
                label = f.label ?: f.language
                if (!label.isNullOrBlank()) break
            }
            items.add(label ?: "অডিও ট্র্যাক ${items.size}")
        }
        AlertDialog.Builder(this)
            .setTitle("🎧 অডিও ট্র্যাক বাছো")
            .setItems(items.toTypedArray()) { _, w ->
                val tb = p.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                tb.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                if (w > 0) {
                    val g = groups[w - 1]
                    if (g.length > 0) tb.addOverride(TrackSelectionOverride(g.mediaTrackGroup, 0))
                }
                p.trackSelectionParameters = tb.build()
                audioDisabled = false
            }
            .show()
    }

    /* ── প্লেয়ার তৈরি (ধীর সার্ভারের জন্য বড় টাইমআউট) ────────── */

    private fun buildPlayer() {
        errorBox.visibility = View.GONE

        val tsel = DefaultTrackSelector(this)
        var pb = tsel.buildUponParameters()
        if (audioDisabled) pb = pb.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
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
                mxBar.visibility =
                    if (visibility == View.VISIBLE) View.VISIBLE else View.GONE
                gestureArea.visibility =
                    if (visibility == View.VISIBLE && !locked) View.GONE else View.VISIBLE
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

    /* ── MX স্টাইল জেসচার: সিক / ব্রাইটনেস / ভলিউম ───────────── */

    private fun setupGestures() {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        var downX = 0f; var downY = 0f
        var seekBase = 0L; var lastX = 0f
        var volBase = 0
        var mode = 0 // 0=none 1=seek 2=bright 3=volume
        var brightBase = 0f

        gestureArea.setOnTouchListener { _, ev ->
            if (locked) {
                if (ev.actionMasked == MotionEvent.ACTION_DOWN)
                    showHint("🔒 স্ক্রিন লক করা — খুলতে নিচের 🔓 চাপো")
                return@setOnTouchListener true
            }
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x; downY = ev.y; lastX = ev.x
                    mode = 0
                    seekBase = player?.currentPosition ?: 0L
                    volBase = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    brightBase = window.attributes.screenBrightness.takeIf { it > 0 } ?: 0.5f
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (mode == 0) {
                        mode = if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) 1
                        else if (downX < resources.displayMetrics.widthPixels / 2f) 2 else 3
                    }
                    when (mode) {
                        1 -> {
                            val delta = ((ev.x - lastX) * 150).toLong()
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
                            showHint("🔊 ভলিউম $nv/$maxVol")
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (mode == 0 && !locked) pv.showController()
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
