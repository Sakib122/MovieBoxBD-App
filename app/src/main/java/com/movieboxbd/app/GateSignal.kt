package com.movieboxbd.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 🔊 (v5.7) অ্যাড-গেট "১০ সেকেন্ড পূর্ণ" সিগন্যাল — মালিকের নির্দেশ: টং করে সাউন্ড।
 *
 * ইতিহাস: v5.5-এ ভাইব্রেশন দিয়ে সিগন্যালের চেষ্টা হয়েছিল — মালিকের ফোনে
 * (OnePlus/Samsung) ইউজার ১০ সেকেন্ডেই ব্যাক করে ফিরে আসে বলে ভাইব্রেশন
 * কাজ করছিল না। v5.7-তে মালিকের নির্দেশে সিগন্যাল এখন **সাউন্ড**:
 *
 *   ① 🔊 প্রধান সিগন্যাল — "টং" করে একটা ছোট টোন (০.৪৫ সেকেন্ড)। ToneGenerator
 *      ব্যবহার করা হয়েছে: কোনো সাউন্ড-ফাইল/পারমিশন লাগে না, ব্যাকগ্রাউন্ড
 *      রিসিভার থেকেও সব ফোনে (API 21+) বাজে, ফোন সাইলেন্ট মোডে থাকলেও
 *      মিডিয়া-ভলিউমে শব্দ শোনা যায় (ইউজার মুভি দেখবে — মিডিয়া-ভলিউম শূন্য হয় না)।
 *   ② 💓 ব্যাকআপ — সাথে একবার ভাইব্রেটও হয় (যেসব ফোনে মিডিয়া-ভলিউম শূন্য, সেখানেও
 *      সিগন্যাল পৌঁছায়; ভাইব্রেশন কাজ না করলেও সাউন্ডই প্রধান)।
 *   ③ ⏱ সময় — বাকি সেকেন্ড পূর্ণ হওয়ার সাথে সাথেই (নিচে EXTRA_MS), ইউজার ব্যাক
 *      করার আগেই।
 *   ④ 🔁 দুই পথে পাঠানো হয় — হ্যান্ডলার-টাইমার (প্রসেস জীবিত থাকলে ঠিক সময়ে) +
 *      AlarmManager অ্যালার্ম (প্রসেস ফ্রিজ/কিল হলেও সিস্টেম জেগে সিগন্যাল দেয়)।
 *      fire()-এ ডাবল-গার্ড আছে — দুই পথ কাছাকাছি সময়ে গেলেও ঠিক একবারই বাজবে।
 *
 * ▶ নিয়ম: গেটের লিংক খোলামাত্র শিডিউল; অ্যাপে ফিরে এলে বা গেট বাতিল/পূর্ণ হলে বাতিল।
 *   ▶ প্লে-গেটে আগের জমা সেকেন্ড থাকলে বাকিটুকুর হিসাবেই সিগন্যাল; ⬇ ডাউনলোড-সেক্টরে
 *   প্রতিবার ফ্রেশ — পুরো সেকেন্ড পূর্ণ হলেই।
 */
object GateSignal {

    /* লিংক খোলার মুহূর্ত থেকে অ্যাপের ঘড়ি চালু হতে (onPause) সামান্য সময় লাগে — ব্রাউজার
       সুইচের সেই ব্যবধান ঢাকতে সিগন্যাল need-এর সামান্য পরে দেওয়া হয়; নইলে টং শুনে
       সাথে সাথে ফিরে এসে "১ সেকেন্ড বাকি ছিল" সতর্কবার্তা দেখাতে পারে */
    const val EXTRA_MS = 300L

    private const val REQ = 55501

    /* ডাবল-ফায়ার গার্ড — হ্যান্ডলার ও অ্যালার্ম কাছাকাছি সময়ে গেলে একবারই বাজবে */
    private const val DEDUP_MS = 8000L

    private val ui = Handler(Looper.getMainLooper())
    private var uiTask: Runnable? = null
    @Volatile private var lastFireTs = 0L

    /** delayMs পরে একবার সিগন্যাল — আগে থেকে শিডিউল থাকলে সেটা বাতিল হয়ে নতুনটা বসে */
    fun schedule(ctx: Context, delayMs: Long) {
        cancel(ctx)
        val app = ctx.applicationContext
        /* ① হ্যান্ডলার-টাইমার — প্রসেস জীবিত থাকলে (সাধারণ ক্ষেত্রে) এটাই ঠিক সময়ে বাজে */
        val task = Runnable { fire(app) }
        uiTask = task
        ui.postDelayed(task, delayMs)
        /* ② অ্যালার্ম — অ্যাপ ব্যাকগ্রাউন্ডে প্রসেস ফ্রিজ/কিল হলেও সিস্টেম জেগে সিগন্যাল দেবে */
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val at = System.currentTimeMillis() + delayMs.coerceAtLeast(1000L)
            val pi = pending(ctx)
            try {
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms())
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else if (Build.VERSION.SDK_INT >= 23)
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else
                    am.setExact(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (_: Exception) {
                /* কিছু ফোনে exact অ্যালার্ম নেওয়া যায় না — সাধারণ অ্যালার্মেই চলবে */
                try { am.set(AlarmManager.RTC_WAKEUP, at, pi) } catch (_: Exception) {}
            }
        } catch (_: Exception) {
        }
    }

    /** শিডিউল-করা সিগন্যাল বাতিল — অ্যাপে ফিরে এলে / গেট বাতিল বা পূর্ণ হলে */
    fun cancel(ctx: Context) {
        uiTask?.let { try { ui.removeCallbacks(it) } catch (_: Exception) {} }
        uiTask = null
        try {
            (ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(pending(ctx))
        } catch (_: Exception) {
        }
    }

    /** 🔊 টং করে টোন + 💓 একবার ভাইব্রেট — ইউজার বুঝবে "১০ সেকেন্ড শেষ, এখন ব্যাক"।
        হ্যান্ডলার ও অ্যালার্ম দুই পথই এখানে আসে — ডাবল-গার্ডে ঠিক একবারই বাজে */
    @Suppress("DEPRECATION")
    fun fire(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now - lastFireTs < DEDUP_MS) return
        lastFireTs = now
        ding()
        vibrate(ctx)
    }

    /* 🔊 "টং" টোন — ToneGenerator: কোনো সাউন্ড-ফাইল/পারমিশন ছাড়াই সব ফোনে বাজে।
       মিডিয়া-স্ট্রিমে ভলিউম ১০০ — অ্যাড দেখার সময় মিডিয়া-ভলিউম শূন্য হয় না বললেই চলে।
       টোনের পরে release() — সাথে সাথে না করে (টোন কাটা পড়বে), ০.৬৫ সেকেন্ড পরে */
    private fun ding() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 450)
            ui.postDelayed({
                try { tg.release() } catch (_: Exception) {}
            }, 650L)
        } catch (_: Exception) {
        }
    }

    /* 💓 ব্যাকআপ ভাইব্রেশন (০.৭ সেকেন্ড) — সাউন্ডের সাথে; মিডিয়া-ভলিউম শূন্য হলেও কাজে দেয় */
    private fun vibrate(ctx: Context) {
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (v == null || !v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26)
                v.vibrate(VibrationEffect.createOneShot(700L, VibrationEffect.DEFAULT_AMPLITUDE))
            else
                v.vibrate(700L)
        } catch (_: Exception) {
        }
    }

    private fun pending(ctx: Context): PendingIntent {
        val it = Intent(ctx, GateSignalReceiver::class.java)
        return PendingIntent.getBroadcast(
            ctx, REQ, it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** 🔊 অ্যালার্ম পৌঁছালে GateSignal.fire() — ডাবল-গার্ড আছে, ঠিক একবারই বাজবে */
class GateSignalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GateSignal.fire(context.applicationContext)
    }
}
