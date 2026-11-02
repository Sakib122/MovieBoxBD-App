package com.movieboxbd.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
 *  — ভিডিও অনুযায়ী অটো ল্যান্ডস্কেপ/পোর্ট্রেট
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
    private var started = false

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
        findViewById<TextView>(R.id.pTitle).text = title
        findViewById<ImageButton>(R.id.btnPBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnRetry).setOnClickListener { retry() }
        findViewById<ImageButton>(R.id.btnPRotate).setOnClickListener {
            requestedOrientation =
                if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        setupGestures()
        buildPlayer()
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
                gestureArea.visibility = if (visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        })

        player?.setMediaItem(MediaItem.fromUri(url))
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
                    if (mode == 0) pv.showController()
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
