package com.movieboxbd.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 🔔 গেট-বিপ (v5.6) — অ্যাডের সময় (ডিফল্ট ১০ সেকেন্ড) পূর্ণ হলেই ইউজারের ফোন
 * **ফোন-কলের মতো জোরে ভাইব্রেট** করবে + স্ক্রিনে হেডস-আপ নোটিফিকেশন + বিপ শব্দ।
 *
 * ▶ কেন এই সিস্টেম (মালিকের নির্দেশ — টেলিগ্রাম-বটের বিপের অ্যাপ-ভার্সন, আরও শক্ত):
 *   • ইউজার অ্যাড দেখতে ব্রাউজারে চলে যায় — অ্যাপ তখন লুকানো (onPause)।
 *   • ঠিক বাকি সেকেন্ড পূর্ণ হওয়ার মুহূর্তে এই ফাইলের টাইমার জাগে → ফোন জোরে কাঁপে
 *     + "বিপ!" নোটিফিকেশন ওঠে।
 *   • ইউজার ব্রাউজারে বসে থেকেই বুঝবে: ফিরার সময় হলো → ব্যাক দিয়ে অ্যাপে এলেই
 *     মুভি প্লে / ডাউনলোড / টেলিগ্রাম সাথে সাথে হয়ে যায়।
 *   • নোটিফিকেশনে ট্যাপ করলে সরাসরি অ্যাপ খোলে — ব্যাক-বাটন খোঁজারও দরকার নেই।
 *
 * ▶ ভাইব্রেশন কতটা জোরে (মালিকের সরাসরি নির্দেশ — "একবারই বিপ, কিন্তু কলের মতো জোরে"):
 *   • মোট **১টাই টানা ২.৩ সেকেন্ডের ফুল-শক্তি (amplitude 255) ধাক্কা** — তিন-চারবার
 *     ঝাঁকুনি নয়; ফোন যখন কল আসে তখন যেভাবে কাঁপে, ঠিক সেই লেভেল, একবারই।
 *   • নোটিফিকেশন-চ্যানেলের নিজস্ব ভাইব্রেশন বন্ধ রাখা হয়েছে — ভাইব্রেশন এখন ১০০%
 *     আমাদের হাতে (Vibrator API), তাই ফোন-সেটিং যা-ই থাকুক, কলের মতোই কাঁপবেই।
 *
 * ▶ নির্ভরযোগ্যতা — তিন স্তরের ফলব্যাক (কোনো ফোনেই মিস হবে না):
 *   ১. অ্যাপ-প্রসেস জীবিত থাকলে Handler-টাইমার সেকেন্ড-নির্ভুল বাজে (অ্যাড দেখার সময়
 *      প্রসেস সাধারণত জীবিতই থাকে — ওপো/আইটেল/স্যামসাং-ও দেরি করাতে পারে না)
 *   ২. প্রসেস সিস্টেম দিয়ে ফেলে দিলেও AlarmManager জাগিয়ে দেয় (GateAlertRc)
 *   ৩. IMPORTANCE_HIGH নোটিফিকেশন — লক-স্ক্রিনেও পপ করবে + বিপ শব্দ; নোটিফিকেশন
 *      আটকে গেলে (পারমিশন নেই) ডিফল্ট নোটিফিকেশন-রিংটোন নিজেই বাজবে
 *
 * ▶ কোথায় কোথায় বাঁধা আছে:
 *   • Ads.onGatePause()  → অ্যাপ লুকানোর মুহূর্তে বাকি সময় হিসাব করে schedule()
 *   • Ads.onGateResume() → অ্যাপে ফিরতেই cancel() (পূর্ণ হলে successBuzz() — ছোট খুশির এক ধাক্কা)
 *   • PlayerActivity-র এপিসোড-গেটও একই দুই জায়গায় বাঁধা
 */
object GateAlert {

    private const val CHANNEL_ID = "mbx_gate_bip"
    private const val ALARM_REQ = 2001
    private const val TAP_REQ = 2002

    /* ফোন-কলের মতো এক ধাক্কা (মালিকের নির্দেশ: একবারই বিপ): ০ থেকে শুরু করে
       টানা ২.৩ সেকেন্ড ফুল-শক্তি (amplitude 255) — বড়, লম্বা, একটাই ঝাঁকুনি */
    private val STRONG_TIMINGS = longArrayOf(0L, 2300L)
    private val STRONG_AMPS = intArrayOf(0, 255)

    /* গেট পাস হলে ছোট খুশির এক ধাক্কা — "কাজ হয়ে গেছে" বোঝাতে */
    private val OK_TIMINGS = longArrayOf(0L, 160L)
    private val OK_AMPS = intArrayOf(0, 255)

    /* ⏱ অ্যাপ-প্রসেস জীবিত থাকলে সেকেন্ড-নির্ভুল ফায়ার — Handler-টাইমার (অ্যালার্ম ব্যাকআপে থাকে) */
    private val mainHandler = Handler(Looper.getMainLooper())
    private var fireRunnable: Runnable? = null
    private var appCtx: Context? = null

    /* ══════════ অ্যালার্ম + টাইমার ══════════ */

    /** delayMs পরে বিপ বাজবে — Handler (নির্ভুল) + AlarmManager (ব্যাকআপ: প্রসেস মরলেও) */
    fun schedule(ctx: Context, delayMs: Long) {
        try {
            appCtx = ctx.applicationContext
            cancelTimer()  /* আগের টাইমার থাকলে তুলে নাও — নতুন গেটের ভিত্তিতে বসছি */
            val delay = delayMs.coerceAtLeast(1000L)
            /* ১) Handler: প্রসেস জীবিত থাকলে ঠিক সময়েই বাজবে — কোনো ব্যাটারি-ম্যানেজারই দেরি করাতে পারে না */
            val r = Runnable { fireIfPending() }
            fireRunnable = r
            mainHandler.postDelayed(r, delay)
            /* ২) AlarmManager ব্যাকআপ: প্রসেস ফেলে দিলেও জাগবে */
            try {
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val pi = alarmPI(ctx)
                val at = System.currentTimeMillis() + delay
                try {
                    if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    else if (Build.VERSION.SDK_INT >= 23)
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    else
                        @Suppress("DEPRECATION")
                        am.setExact(AlarmManager.RTC_WAKEUP, at, pi)
                } catch (_: Exception) {
                    /* এক্স্যাক্ট-অ্যালার্ম পারমিশন না থাকলে সাধারণ অ্যালার্মই —
                       স্ক্রিন চালু অবস্থায় (ইউজার অ্যাড দেখছে) এটা ঠিক সময়েই বাজে */
                    try {
                        if (Build.VERSION.SDK_INT >= 23)
                            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                        else
                            am.set(AlarmManager.RTC_WAKEUP, at, pi)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }

    /** অ্যাপে ফিরে এসেছি / গেট বাতিল — টাইমার + অ্যালার্ম দুটোই তুলে নাও */
    fun cancel(ctx: Context) {
        cancelTimer()
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(alarmPI(ctx))
        } catch (_: Exception) {
        }
    }

    private fun cancelTimer() {
        try {
            fireRunnable?.let { mainHandler.removeCallbacks(it) }
        } catch (_: Exception) {
        }
        fireRunnable = null
    }

    private fun alarmPI(ctx: Context): PendingIntent {
        val it = Intent(ctx, GateAlertRc::class.java)
        val imm = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(ctx, ALARM_REQ, it,
            PendingIntent.FLAG_UPDATE_CURRENT or imm)
    }

    /* ══════════ বিপ ══════════ */

    /** গেট চলমান থাকলেই বিপ (Handler/অ্যালার্ম — দুই পথেই একই চেক) */
    fun fireIfPending() {
        val ctx = appCtx ?: return
        try {
            val p = ctx.getSharedPreferences("mbx_ad", Context.MODE_PRIVATE)
            if (!p.getBoolean("gate_pending", false)) return
            fire(ctx)
        } catch (_: Exception) {
        }
    }

    /** রিসিভার/টাইমার ডাকে — জোরে ভাইব্রেট + হেডস-আপ নোটিফিকেশন + বিপ */
    fun fire(ctx: Context) {
        vibrateStrong(ctx)
        val posted = try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ensureChannel(ctx, nm, sound)
            val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                ?: Intent()
            @Suppress("DEPRECATION")
            val tap = PendingIntent.getActivity(ctx, TAP_REQ, launch,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
            val n = androidx.core.app.NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_play)
                .setContentTitle("🔔 বিপ! অ্যাডের সময় শেষ")
                .setContentText("এখন অ্যাড থেকে ব্যাক দিয়ে অ্যাপে ফিরে আসুন — মুভি চালু হবে")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(tap)
                /* ভাইব্রেশন আমরা নিজেই কলের মতো দিচ্ছি — সিস্টেমের ডিফল্ট ভাইব্রেশন বন্ধ,
                   শুধু শব্দ + লাইট; নাহলে দুই ভাইব্রেশন একসাথে মিশে এলোমেলো লাগত */
                .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_SOUND or
                    androidx.core.app.NotificationCompat.DEFAULT_LIGHTS)
                .build()
            /* POST_NOTIFICATIONS পারমিশন না থাকলে (Android 13+) notify এখানেই ব্যর্থ হয় */
            nm.notify(ALARM_REQ, n)
            true
        } catch (_: Exception) {
            false
        }
        /* নোটিফিকেশন আটকে গেলে বিপ-শব্দটা নিজেই বাজাও — শব্দ কোনোভাবেই মিস হবে না */
        if (!posted) {
            try {
                val r = RingtoneManager.getRingtone(ctx,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                r.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                r.play()
            } catch (_: Exception) {
            }
        }
    }

    /** 🔓 গেট পাস — ছোট খুশির এক ধাক্কা (কাজ হয়ে যাওয়ার সংকেত) */
    fun successBuzz(ctx: Context) {
        try {
            val v = vib(ctx) ?: return
            try {
                if (Build.VERSION.SDK_INT >= 26)
                    v.vibrate(VibrationEffect.createWaveform(OK_TIMINGS, OK_AMPS, -1))
                else
                    @Suppress("DEPRECATION")
                    v.vibrate(OK_TIMINGS, -1)
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }

    /* ── ভেতরের যন্ত্র ── */

    /** ফোন-কলের মতো জোরে — একবারই: টানা ২.৩ সেকেন্ড ফুল-শক্তি এক ধাক্কা */
    private fun vibrateStrong(ctx: Context) {
        try {
            val v = vib(ctx) ?: return
            try {
                if (Build.VERSION.SDK_INT >= 26)
                    v.vibrate(VibrationEffect.createWaveform(STRONG_TIMINGS, STRONG_AMPS, -1))
                else
                    @Suppress("DEPRECATION")
                    v.vibrate(STRONG_TIMINGS, -1)
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }

    private fun vib(ctx: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31)
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    } catch (_: Exception) {
        null
    }

    private fun ensureChannel(ctx: Context, nm: NotificationManager, sound: android.net.Uri) {
        if (Build.VERSION.SDK_INT < 26) return
        try {
            /* চ্যানেলের নিজস্ব ভাইব্রেশন বন্ধ — ভাইব্রেশন ১০০% আমাদের হাতে (কলের মতো এক ধাক্কা) */
            val fresh = nm.getNotificationChannel(CHANNEL_ID) == null
            if (!fresh) {
                /* আগের ভার্সনে চ্যানেল ভাইব্রেশন চালু ছিল — সেটা বন্ধ করে নাও */
                val old = nm.getNotificationChannel(CHANNEL_ID)
                if (old != null && old.shouldVibrate()) {
                    old.enableVibration(false)
                    nm.createNotificationChannel(old)
                }
                return
            }
            val ch = NotificationChannel(CHANNEL_ID,
                "অ্যাড টাইমার বিপ (জোরে ভাইব্রেট)",
                NotificationManager.IMPORTANCE_HIGH)
            ch.description = "অ্যাডের সময় শেষ হলে জোরে ভাইব্রেট + বিপ — তখন অ্যাড থেকে অ্যাপে ফিরে আসতে হয়"
            ch.enableVibration(false)
            ch.enableLights(true)
            ch.setSound(sound, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            nm.createNotificationChannel(ch)
        } catch (_: Exception) {
        }
    }
}

/**
 * AlarmManager-এর রিসিভার — অ্যাপ-প্রসেস সিস্টেম ফেলে দিলেও ঠিক সময়ে জাগায়।
 * গেট তখনও চলমান থাকলেই শুধু বিপ বাজবে (ইউজার ফিরে গেট শেষ করে থাকলে নীরব)।
 * প্রিফ-কীগুলো Ads.kt-র "mbx_ad" স্টোরেরই — ওখানে gate_pending ফ্ল্যাগ দেখা হয়।
 */
class GateAlertRc : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val p = context.getSharedPreferences("mbx_ad", Context.MODE_PRIVATE)
            if (!p.getBoolean("gate_pending", false)) return
            GateAlert.fire(context)
        } catch (_: Exception) {
        }
    }
}
