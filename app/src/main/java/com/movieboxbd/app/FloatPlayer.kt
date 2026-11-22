package com.movieboxbd.app

import android.app.Activity
import android.content.Intent
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.PlayerView
import kotlin.math.abs

/**
 * 🪟 ভাসমান পপআপ-প্লেয়ার (রেফারেন্স-অ্যাপের মতো — v3.3):
 *  — মিনি-প্লেয়ারের ⧉ বাটনে (বা ভিডিওতে নিচে টানলে) ভিডিও ছোট ভাসমান উইন্ডো হয়ে যায়
 *  — স্ক্রিনের যেকোনো জায়গায় আঙুল দিয়ে টেনে সরানো যায়
 *  — হোম / সার্চ / ১৮+ — অ্যাপের যে পেজে ইউজার যাবে, পপআপ সেখানেই ভেসে থাকবে
 *  — ⛶ দিলে ফুলস্ক্রিন প্লেয়ার, ✕ দিলে বন্ধ, ⚙ দিলে সেটিংস (স্পিড + অডিও ট্র্যাক)
 *  — ⏪10 ▶/⏸ ⏩10 — পপআপের ভেতরেই কন্ট্রোল; ভিডিওতে ট্যাপ করলে কন্ট্রোল দেখা/লুকানো
 *  — একই ExoPlayer ইনস্ট্যান্স চলতে থাকে, তাই ভাসালেও রি-বাফার হয় না
 */
object FloatPlayer {

    private var player: ExoPlayer? = null
    private var url = ""
    private var name = ""
    private var movieTitle = ""
    private var audioPick = -1
    private var speedNow = 1f

    private var host: Activity? = null
    private var wm: WindowManager? = null
    private var view: FrameLayout? = null
    private var lp: WindowManager.LayoutParams? = null
    private var fBtnPlay: ImageButton? = null
    private var fTopRow: LinearLayout? = null
    private var fBottomRow: LinearLayout? = null

    private val ui = Handler(Looper.getMainLooper())

    /* 🎧 ভাষার কোড → ইংরেজি নাম (সেটিংসের অডিও-লিস্টে) */
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

    /** পপআপ এখন ভাসছে কি না */
    val isActive: Boolean get() = player != null

    private val tick = object : Runnable {
        override fun run() {
            fBtnPlay?.setImageResource(
                if (player?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play
            )
            ui.postDelayed(this, 500)
        }
    }

    /** 🪟 মিনি-প্লেয়ার থেকে চলমান প্লেয়ারটাই পপআপে নিয়ে আসা (রি-বাফার ছাড়াই) */
    fun floatFromDetail(act: DetailActivity, src: ExoPlayer, u: String, n: String, title: String) {
        stopIfActive()
        url = u
        name = n
        movieTitle = title
        audioPick = -1
        speedNow = 1f
        player = src
        src.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) ui.post { close() }
            }

            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(act.applicationContext, "পপআপ-প্লেয়ারে সমস্যা — বন্ধ করা হলো", Toast.LENGTH_SHORT).show()
                ui.post { close() }
            }
        })
        attach(act)
        ui.removeCallbacks(tick)
        ui.post(tick)
    }

    /** 🏠 ইউজার যে পেজে আসলে পপআপটা সেখানে এসে বসে (পজিশন মনে থাকে) */
    fun reattach(act: Activity) {
        if (player == null) return
        if (host === act && view != null) return
        attach(act)
    }

    /** হোস্ট-পেজ বন্ধ হলে: অ্যাপের ভেতরে সুইচ হলে পরের পেজ সামলা নেবে,
     *  অ্যাপ থেকে বের হয়ে গেলে (finishing) প্লেয়ারও বন্ধ */
    fun onHostDestroyed(act: Activity) {
        if (host !== act) return
        detach()
        if (act.isFinishing) release()
    }

    /** ✕ — পপআপ বন্ধ + প্লেয়ার বন্ধ */
    fun close() {
        release()
        detach()
    }

    /** ডিটেইল/অন্য প্লেয়ার নতুন করে চালু করার আগে পপআপ থাকলে থামাও */
    fun stopIfActive() {
        if (player != null) close()
    }

    /** ⛶ — ফুলস্ক্রিন প্লেয়ারে পাঠানো (এখনকার পজিশনসহ) */
    fun expand(act: Activity) {
        val p = player ?: return
        val pos = p.currentPosition
        val u = url
        val n = name
        val t = movieTitle
        release()
        detach()
        try {
            val i = Intent(act, PlayerActivity::class.java)
            i.putExtra("url", u)
            i.putExtra("title", n)
            i.putExtra("movie_name", t)
            i.putExtra("resume_pos", pos)
            act.startActivity(i)
        } catch (_: Exception) {
        }
    }

    private fun release() {
        ui.removeCallbacks(tick)
        player?.release()
        player = null
        url = ""
        name = ""
    }

    private fun detach() {
        ui.removeCallbacks(tick)
        try {
            view?.let { wm?.removeView(it) }
        } catch (_: Exception) {
        }
        view = null
        wm = null
        host = null
        fBtnPlay = null
        fTopRow = null
        fBottomRow = null
    }

    /* ── উইন্ডো অ্যাটাচ — অ্যাক্টিভিটির নিজের উইন্ডোতে সাব-প্যানেল হিসেবে বসে
        (কোনো অতিরিক্ত পারমিশন লাগে না; পেজ লুকালে প্যানেলও লুকায়,
        নতুন পেজের onResume-এ reattach করলেই সেখানে ভাসতে থাকে) ── */
    private fun attach(act: Activity) {
        detach()
        val v = buildView(act)
        val d = act.resources.displayMetrics.density
        val l = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        l.gravity = Gravity.TOP or Gravity.START
        l.token = act.window.decorView.windowToken
        if (lp == null) {
            val dm = act.resources.displayMetrics
            val w = (320 * d).toInt()
            l.x = (dm.widthPixels - w - 12 * d).toInt()
            l.y = (64 * d).toInt()
        } else {
            l.x = lp!!.x
            l.y = lp!!.y
        }
        lp = l
        host = act
        wm = act.windowManager
        view = v
        try {
            wm!!.addView(v, l)
        } catch (e: Exception) {
            view = null
            wm = null
            host = null
        }
    }

    /* ── পপআপের লুক: গোল-কোণা কালো বাক্স + ⚙ ⛶ ✕ উপরে + ⏪10 ▶ ⏩10 নিচে ── */
    private fun buildView(act: Activity): FrameLayout {
        val dm = act.resources.displayMetrics
        val d = dm.density
        val W = (320 * d).toInt()
        val H = (180 * d).toInt()

        val root = FrameLayout(act)
        val bg = GradientDrawable().apply {
            setColor(0xF2080C14.toInt())
            cornerRadius = 14 * d
        }
        root.background = bg
        root.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) = o.setRoundRect(0, 0, W, H, 14 * d)
        }
        root.clipToOutline = true

        val pv = PlayerView(act)
        pv.layoutParams = FrameLayout.LayoutParams(W, H)
        pv.useController = false
        pv.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        root.addView(pv)
        pv.player = player

        /* উপরের সারি: ⚙ ⛶ ✕ */
        val top = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setBackgroundColor(0x55000000)
            setPadding(6 * d.toInt(), 4 * d.toInt(), 6 * d.toInt(), 4 * d.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP
            )
        }
        top.addView(circleBtn(act, R.drawable.ic_settings, 32) { showSettings(act) })
        top.addView(circleBtn(act, R.drawable.ic_fullscreen, 32) { expand(act) })
        top.addView(
            circleBtn(act, R.drawable.ic_close, 32) {
                Toast.makeText(act, "পপআপ বন্ধ হলো", Toast.LENGTH_SHORT).show()
                close()
            }
        )
        root.addView(top)
        fTopRow = top

        /* নিচের সারি: ⏪10 ▶/⏸ ⏩10 */
        val bottom = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(0x55000000)
            setPadding(6 * d.toInt(), 4 * d.toInt(), 6 * d.toInt(), 4 * d.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
            )
        }
        bottom.addView(
            circleBtn(act, R.drawable.ic_rew10, 36) {
                player?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0)) }
            }
        )
        val play = circleBtn(act, R.drawable.ic_pause, 42) { togglePlay() }
        fBtnPlay = play
        bottom.addView(play)
        bottom.addView(
            circleBtn(act, R.drawable.ic_ffwd10, 36) {
                player?.let { it.seekTo(it.currentPosition + 10_000) }
            }
        )
        root.addView(bottom)
        fBottomRow = bottom

        /* 🖐 ভিডিওতে ট্যাপ = কন্ট্রোল দেখা/লুকানো; টেনে ধরলে পপআপ স্ক্রিনে সরে */
        var rx0 = 0f
        var ry0 = 0f
        var bx = 0
        var by = 0
        var moved = false
        pv.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    rx0 = ev.rawX
                    ry0 = ev.rawY
                    bx = lp?.x ?: 0
                    by = lp?.y ?: 0
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(ev.rawX - rx0) > 8 * d || abs(ev.rawY - ry0) > 8 * d) moved = true
                    if (moved) {
                        lp?.let {
                            it.x = (bx + (ev.rawX - rx0).toInt()).coerceIn(0, dm.widthPixels - W)
                            it.y = (by + (ev.rawY - ry0).toInt()).coerceIn(0, dm.heightPixels - H - (32 * d).toInt())
                            try {
                                wm?.updateViewLayout(view, it)
                            } catch (_: Exception) {
                            }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!moved) {
                        val show = fTopRow?.visibility != View.VISIBLE
                        fTopRow?.visibility = if (show) View.VISIBLE else View.GONE
                        fBottomRow?.visibility = if (show) View.VISIBLE else View.GONE
                    }
                    true
                }
                else -> false
            }
        }
        return root
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    /** 🎧 পপআপ ভাসা অবস্থায় ডিটেইল-পেজের Audio চিপ থেকেও অডিও বদলানো */
    fun showAudioDialogFor(act: Activity) {
        if (player != null) showAudioList(act)
    }

    private fun circleBtn(act: Activity, icon: Int, sizeDp: Int, onClick: () -> Unit): ImageButton {
        val d = act.resources.displayMetrics.density
        val b = ImageButton(act)
        b.setBackgroundResource(R.drawable.player_btn_circle)
        b.setImageResource(icon)
        b.scaleType = ImageButton.ScaleType.FIT_CENTER
        val s = (sizeDp * d).toInt()
        b.setPadding(7 * d.toInt(), 7 * d.toInt(), 7 * d.toInt(), 7 * d.toInt())
        b.layoutParams = LinearLayout.LayoutParams(s, s).apply {
            marginEnd = 8 * d.toInt()
        }
        b.setOnClickListener { onClick() }
        return b
    }

    private fun fmtSpeed(sp: Float) = if (sp == sp.toInt().toFloat()) "${sp.toInt()}x" else "${sp}x"

    /* ── ⚙ সেটিংস: প্লে-স্পিড + অডিও ট্র্যাক ── */
    private fun showSettings(act: Activity) {
        val p = player ?: return
        val d = act.resources.displayMetrics.density

        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * d).toInt(), (12 * d).toInt(), (24 * d).toInt(), 0)
        }

        box.addView(TextView(act).apply {
            text = "প্লে-স্পিড"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
        })

        var chipsBox: LinearLayout? = null
        fun speedChips() {
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                row.addView(TextView(act).apply {
                    text = fmtSpeed(sp)
                    textSize = 13f
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding((14 * d).toInt(), (8 * d).toInt(), (14 * d).toInt(), (8 * d).toInt())
                    background = ContextCompat.getDrawable(
                        act,
                        if (sp == speedNow) R.drawable.bg_chip_watched else R.drawable.bg_chip
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = (8 * d).toInt() }
                    setOnClickListener {
                        p.playbackParameters = PlaybackParameters(sp)
                        speedNow = sp
                        chipsBox?.let { parent -> (parent.parent as? LinearLayout)?.removeView(parent) }
                        speedChips()
                    }
                })
            }
            chipsBox = row
            box.addView(row, 1)
        }
        speedChips()

        box.addView(TextView(act).apply {
            text = "🎧  অডিও ট্র্যাক বাছো"
            textSize = 15f
            setTextColor(0xFF22C55E.toInt())
            setPadding(0, (16 * d).toInt(), 0, (8 * d).toInt())
            setOnClickListener { showAudioList(act) }
        })

        AlertDialog.Builder(act)
            .setTitle("সেটিংস")
            .setView(box)
            .setPositiveButton("ঠিক আছে", null)
            .show()
    }

    /* ── 🎧 অডিও-ট্র্যাক লিস্ট (● = বাছাই করা ট্র্যাক) ── */
    private fun showAudioList(act: Activity) {
        val p = player ?: return
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (groups.isEmpty()) {
            Toast.makeText(act, "এই ভিডিওতে কোনো অডিও ট্র্যাক নেই", Toast.LENGTH_SHORT).show()
            return
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

        var checked = audioPick.coerceIn(0, items.size - 1)
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }
        fun redraw() {
            box.removeAllViews()
            items.forEachIndexed { idx, label ->
                box.addView(TextView(act).apply {
                    text = if (idx == checked) "●  $label" else "○  $label"
                    textSize = 16f
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(48, 30, 32, 30)
                    if (idx == checked) setTypeface(typeface, Typeface.BOLD)
                    setOnClickListener { checked = idx; redraw() }
                })
            }
        }
        redraw()

        AlertDialog.Builder(act)
            .setTitle("অডিও ট্র্যাক বাছো")
            .setView(box)
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
                Toast.makeText(
                    act,
                    if (checked == 0) "অডিও: অটো (ডিফল্ট)" else "অডিও: ${items[checked]}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }
}
