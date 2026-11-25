package com.movieboxbd.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 💓 (v5.5) অ্যাড-গেট ভাইব্রেশন-সিগন্যাল — মালিকের নির্দেশ।
 *
 * ইউজার অ্যাড-গেট দেখতে ব্রাউজারে গেলে অ্যাপ ব্যাকগ্রাউন্ডে চলে যায় — তখন অ্যাপের
 * সাধারণ হ্যান্ডলার/টাইমার আটকে যেতে পারে (দমন করা ROM-এ প্রসেস ফ্রিজও হয়)। তাই
 * "১০ সেকেন্ড পূর্ণ" সিগন্যালটা AlarmManager দিয়ে পাঠানো হয় — অ্যাপ ব্যাকগ্রাউন্ডে
 * থাকলেও, ফোন ডু মোডে থাকলেও (RTC_WAKEUP) ঠিক সময়ে জেগে উঠে একবার ভাইব্রেট করবে।
 *
 * ▶ নিয়ম (মালিকের নির্দেশ):
 *   • গেটের লিংক খোলামাত্র "বাকি সেকেন্ড + ছোট গ্রেস-টাইম" পরে ভাইব্রেশন শিডিউল হয় —
 *     ইউজার ভাইব্রেশন পেয়ে ব্যাক করে ফিরে এলেই অ্যাপের রিয়েল-টাইমারও পূর্ণ থাকে,
 *     ফিরেই প্লে/ডাউনলোড/টেলিগ্রাম/ব্রাউজার — সব সেক্টরে একই নিয়ম।
 *   • ▶ প্লে-গেটে আগের জমা সেকেন্ড থাকলে বাকিটুকুর হিসাবেই ভাইব্রেশন; ⬇ ডাউনলোড-সেক্টরে
 *     প্রতিবার ফ্রেশ — পুরো সেকেন্ড পূর্ণ হলেই।
 *   • অ্যাপে ফিরে এলে বা গেট বাতিল/পূর্ণ হলে শিডিউল বাতিল — ভাইব্রেশন ঠিক একবারই,
 *     শুধুমাত্র অ্যাড-পেজে সময় কাটানোর সময়ই আসবে।
 *   • সাউন্ড নয় — শুধু ভাইব্রেশন; ফোন সাইলেন্ট/ভাইব্রেট মোডে থাকলেও ইউজার টের পাবে।
 */
object GateVibe {

    /* গ্রেস-টাইম: ভাইব্রেশন ঠিক need-এর মুহূর্তে না দিয়ে সামান্য পরে দেওয়া হয় —
       অ্যাপের ঘড়ি চলা শুরু হয় onPause থেকে (লিংক খোলার সামান্য পরে), ব্রাউজার-সুইচের
       ওই ব্যবধানটা ঢেকে দেয়; নইলে ভাইব্রেশন পেয়ে ফিরে এসে "১ সেকেন্ড বাকি ছিল"
       সতর্কবার্তা দেখাতে পারে */
    const val GRACE_MS = 2500L

    private const val REQ = 55501

    /** delayMs পরে একবার ভাইব্রেট — আগে থেকে শিডিউল থাকলে সেটাই বদলে যায় (একবারই বাজবে) */
    fun schedule(ctx: Context, delayMs: Long) {
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
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            am.cancel(pending(ctx))
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

/** 💓 অ্যালার্ম পৌঁছালে একবার জোরে ভাইব্রেট — ইউজার বুঝবে "১০ সেকেন্ড শেষ, এখন ব্যাক" */
@Suppress("DEPRECATION")
class GateVibeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (v == null || !v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26)
                v.vibrate(VibrationEffect.createOneShot(700L, VibrationEffect.DEFAULT_AMPLITUDE))
            else
                v.vibrate(700L)
        } catch (_: Exception) {
        }
    }
}
