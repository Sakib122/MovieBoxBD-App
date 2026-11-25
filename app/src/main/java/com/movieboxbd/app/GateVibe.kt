package com.movieboxbd.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 💓 (v5.6) অ্যাড-গেট ভাইব্রেশন-সিগন্যাল — মালিকের নির্দেশ।
 *
 * v5.5-এর বাগ (মালিকের রিপোর্ট: OnePlus/Samsung-এ ভাইব্রেশন কাজই করছিল না):
 *   সিগন্যাল পাঠানো হতো লিংক-খোলার ১২.৫ সেকেন্ড পরে — কিন্তু ইউজার নিজের গোনা
 *   ১০ সেকেন্ড শেষ করে (~১১ সেকেন্ডে) ব্যাক করে অ্যাপে ফিরে আসে, আর অ্যাপে ফিরে-এসেই
 *   শিডিউল বাতিল হয়ে যেত → ভাইব্রেশন কোনোদিন বাজতই না।
 *
 * v5.6-তে দুটো বড় ফিক্স:
 *   ① ⏱ সময়: এখন বাকি সেকেন্ড পূর্ণ হওয়ার সাথে সাথেই (নিচে EXTRA_MS দেখো) ভাইব্রেট
 *      হয় — ইউজার ব্যাক করার আগেই, তাই ভাইব্রেশনটা সবসময় টের পাওয়া যায়।
 *   ② 🔁 দুই পথে পাঠানো হয় —
 *        ১. হ্যান্ডলার-টাইমার: প্রসেস জীবিত থাকলে (৯৯% ক্ষেত্রে) ঠিক সময়ে বাজে;
 *        ২. AlarmManager অ্যালার্ম: প্রসেস ফ্রিজ/কিল হলেও সিস্টেম জেগে ভাইব্রেট করায়।
 *      fire()-এ ডাবল-গার্ড আছে — দুই পথ কাছাকাছি সময়ে গেলেও ঠিক একবারই ভাইব্রেট হবে।
 *
 * ▶ নিয়ম: গেটের লিংক খোলামাত্র শিডিউল; অ্যাপে ফিরে এলে বা গেট বাতিল/পূর্ণ হলে বাতিল।
 *   ▶ প্লে-গেটে আগের জমা সেকেন্ড থাকলে বাকিটুকুর হিসাবেই ভাইব্রেশন; ⬇ ডাউনলোড-সেক্টরে
 *   প্রতিবার ফ্রেশ — পুরো সেকেন্ড পূর্ণ হলেই। সাউন্ড নয় — শুধু ভাইব্রেশন; ফোন
 *   সাইলেন্ট/ভাইব্রেট মোডে থাকলেও টের পাওয়া যায়।
 */
object GateVibe {

    /* লিংক খোলার মুহূর্ত থেকে অ্যাপের ঘড়ি চালু হতে (onPause) সামান্য সময় লাগে — ব্রাউজার
       সুইচের সেই ব্যবধান ঢাকতে ভাইব্রেশন need-এর সামান্য পরে দেওয়া হয়; নইলে ভাইব্রেশন
       পেয়ে সাথে সাথে ফিরে এসে "১ সেকেন্ড বাকি ছিল" সতর্কবার্তা দেখাতে পারে।
       (v5.5-এ এটা ছিল ২৫০০ms — ইউজারের ব্যাক-প্রেসের পরে পড়ে যেত, তাই ৩০০ms) */
    const val EXTRA_MS = 300L

    private const val REQ = 55501

    /* ডাবল-ফায়ার গার্ড — হ্যান্ডলার ও অ্যালার্ম কাছাকাছি সময়ে গেলে একবারই ভাইব্রেট */
    private const val DEDUP_MS = 8000L

    private val ui = Handler(Looper.getMainLooper())
    private var uiTask: Runnable? = null
    @Volatile private var lastFireTs = 0L

    /** delayMs পরে একবার ভাইব্রেট — আগে থেকে শিডিউল থাকলে সেটা বাতিল হয়ে নতুনটা বসে */
    fun schedule(ctx: Context, delayMs: Long) {
        cancel(ctx)
        val app = ctx.applicationContext
        /* ① হ্যান্ডলার-টাইমার — প্রসেস জীবিত থাকলে (সাধারণ ক্ষেত্রে) এটাই ঠিক সময়ে বাজে */
        val task = Runnable { fire(app) }
        uiTask = task
        ui.postDelayed(task, delayMs)
        /* ② অ্যালার্ম — অ্যাপ ব্যাকগ্রাউন্ডে প্রসেস ফ্রিজ/কিল হলেও সিস্টেম জেগে ভাইব্রেট করাবে */
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

    /** শিডিউল-করা ভাইব্রেশন বাতিল — অ্যাপে ফিরে এলে / গেট বাতিল বা পূর্ণ হলে */
    fun cancel(ctx: Context) {
        uiTask?.let { try { ui.removeCallbacks(it) } catch (_: Exception) {} }
        uiTask = null
        try {
            (ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(pending(ctx))
        } catch (_: Exception) {
        }
    }

    /** 💓 একবার জোরে ভাইব্রেট (০.৭ সেকেন্ড) — ইউজার বুঝবে "১০ সেকেন্ড শেষ, এখন ব্যাক"।
        হ্যান্ডলার ও অ্যালার্ম দুই পথই এখানে আসে — ডাবল-গার্ডে ঠিক একবারই বাজে */
    @Suppress("DEPRECATION")
    fun fire(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now - lastFireTs < DEDUP_MS) return
        lastFireTs = now
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
        val it = Intent(ctx, GateVibeReceiver::class.java)
        return PendingIntent.getBroadcast(
            ctx, REQ, it,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** 💓 অ্যালার্ম পৌঁছালে GateVibe.fire() — ডাবল-গার্ড আছে, ঠিক একবারই ভাইব্রেট হবে */
class GateVibeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GateVibe.fire(context.applicationContext)
    }
}
