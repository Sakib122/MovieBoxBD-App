package com.movieboxbd.app

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView

/**
 * ▶ ইন-অ্যাপ প্লেয়ার (ExoPlayer — YouTube-এর প্লেয়ার ইঞ্জিন)।
 * সাউন্ড কোডেক ফোন সাপোর্ট না করলে অটো-সাউন্ড-অফ করে ছবি চালিয়ে দেয়,
 * যাতে ইউজার অন্তত মুভি দেখতে পারে।
 */
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var audioDisabled = false
    private var url: String = ""
    private var title: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        url = intent.getStringExtra("url") ?: run { finish(); return }
        title = intent.getStringExtra("title") ?: "MovieBoxBD"

        findViewById<TextView>(R.id.playerTitle).text = title
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }

        buildPlayer()
    }

    private fun buildPlayer() {
        val pv = findViewById<PlayerView>(R.id.playerView)

        val tsel = DefaultTrackSelector(this)
        var pb = tsel.buildUponParameters()
        if (audioDisabled) {
            pb = pb.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
        }
        tsel.parameters = pb.build()

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(
                DefaultRenderersFactory(this).setEnableDecoderFallback(true)
            )
            .setTrackSelector(tsel)
            .build()

        pv.player = player
        pv.controllerShowTimeoutMs = 4000

        player?.setMediaItem(MediaItem.fromUri(url))
        player?.playWhenReady = true
        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                handle(error)
            }
        })
        player?.prepare()
    }

    private fun handle(error: PlaybackException) {
        val code = error.errorCode
        val audioIssue = code == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                code == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                code == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                code == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                code == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED

        if (audioIssue && !audioDisabled) {
            audioDisabled = true
            Toast.makeText(
                this,
                "🔇 এই ফোনে ওই সাউন্ড ফরম্যাট সাপোর্ট নেই — ছবি চলছে (সাউন্ড ছাড়া)",
                Toast.LENGTH_LONG
            ).show()
            restart()
        } else if (!audioIssue) {
            Toast.makeText(
                this,
                "⚠️ লোড হচ্ছে না — একটু পরে আবার চেষ্টা করো",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun restart() {
        player?.release()
        player = null
        buildPlayer()
    }

    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
