package com.movieboxbd.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * 💰 অ্যাড-গেট v5.3 — সব কন্ট্রোল অ্যাডমিন প্যানেল থেকে (RemoteAds)।
 *
 * ▶ ফ্লো (মালিকের নির্দেশ):
 *   ১. ▶ প্লে / ⏮⏭ এপিসোড-সুইচ — প্লে-সেক্টর: শেষ প্লে-গেটের কুলডাউন (ডিফল্ট ২ মিনিট)
 *      পার হলে গেট, ভেতরে থাকলে অ্যাড-ফ্রি।
 *      🆕 (v5.3) 🔞 নরমাল আর ১৮+ প্লে আলাদা ঘড়িতে গোনা হয় — নরমালের ফ্রি-উইন্ডো
 *      ১৮+ প্লেতে চলে না; ১৮+ নিজের গেট দেখে নিজের ২ মিনিট পায় (উল্টোটাও তাই)।
 *   ২. 🆕 (v5.3) ⬇ ডাউনলোড / ✈ টেলিগ্রাম / 🌐 ব্রাউজার — আলাদা সেক্টর, আলাদা নিয়ম:
 *      কুলডাউন নেইই — যতবারই ক্লিক, ততবারই গেট (প্লে-গেটের ২-মিনিট ফ্রি এখানে চলে না)।
 *      আর প্রতিবারই ফ্রেশ — পুরো ১০ সেকেন্ড এক নিঃশ্বাসে দেখতে হবে; মাঝপথে ব্যাক করলে
 *      ওই চেষ্টার সেকেন্ড জমা থাকে না, পরের ক্লিকে আবার শূন্য থেকে পুরো ১০। পুরোটা
 *      দেখানো হলেই ওই কাজটা সাথে সাথে হয়ে যায়।
 *   ৩. গেটে র‍্যান্ডম Direct Link ব্রাউজারে — সর্বশেষ দেখানোটা পরপর দুইবার আসে না;
 *      নরমাল মুভি → নরমাল পুল, ১৮+ মুভি → ১৮+ পুল (কখনো মেশে না)।
 *   ৪. 🛡 রিয়েল-কোয়ালিটি গেট (movieboxx-bot-এর মতো): সময় শুধু অ্যাড-পেজে থাকার
 *      সময়ই গোনা হয় (onPause ↔ onResume) — অ্যাপে ফিরে বসে থাকলে টাইমার এগোয় না।
 *      সময়ের আগে ফিরে এলে সতর্কবার্তা: "আরও X সেকেন্ড বাকি ছিল" → আবার অ্যাড দেখে
 *      ফিরতে হবে; পুরো সেকেন্ড পূর্ণ হলে ফিরেই কাজটা সাথে সাথে হয়।
 *      ▶ প্লে-গেটে (v5.2 নিয়ম) আংশিক দেখা সেকেন্ড জমা থাকে — বাকিটুকু দেখলেই প্লে;
 *      ⬇ ডাউনলোড-সেক্টরে (v5.3) জমা নেইই — প্রতিবার পুরো ১০।
 *   ৫. 🆕 (v5.3) 🔄 রিফ্রেশ — হোমের রিফ্রেশ চাপলেই প্লে-ফ্রি-উইন্ডো শূন্য (নরমাল +
 *      ১৮+ দুটোই): আবার পুরো ১০ সেকেন্ড গেট দেখে তবেই নতুন ২ মিনিটের ফ্রি।
 *   ৬. 🚫 ব্যানার সম্পূর্ণ বাদ (v5.0) — মৃত ইমপ্রেশন CPM নামাত; ইনকাম এখন শুধু রিয়েল গেট-ভিউতে।
 *   ৭. গেট চালু/বন্ধ, সেকেন্ড, কুলডাউন, লিংক পুল — সব প্যানেল থেকে রিমোটলি বদলায়।
 *   ৮. 🎬 পোস্টার-ক্লিক অ্যাড (v5.1) — প্যানেল থেকে চালু করলে প্রতি N-তম পোস্টার-ক্লিকে
 *      একটা লিংক; ডিফল্ট বন্ধ। গেটের কুলডাউন টাইমারের সাথে জড়ায় না — গেটের রিয়েল-ওয়াচ
 *      যাচাই আগের মতোই চলে, কেউ পোস্টার-অ্যাড দেখে গেট স্কিপ করতে পারে না।
 *   ৯. 🆕 (v5.5) 💓 গেট-ভাইব্রেশন — অ্যাড-পেজে বাকি সেকেন্ড পূর্ণ হলে ফোন নিজে থেকে
 *      একবার ভাইব্রেট করে জানায় (সাইলেন্ট মোডেও): "সময় শেষ, এখন ব্যাক করে ফিরে এসো"।
 *      প্লে • ডাউনলোড • ব্রাউজার • টেলিগ্রাম • এপিসোড-সুইচ — সব গেটে একই নিয়ম
 *      (GateVibe)। অ্যাপে ফিরে এলে বা গেট বাতিল হলে সিগন্যাল বাতিল; আবার অ্যাড খুললে
 *      বাকি সেকেন্ড অনুযায়ী নতুন করে শিডিউল।
 */
object Ads {

    /* ── গেটের অ্যাকশন টাইপ ── */
    const val ACT_PLAY = 0
    const val ACT_DOWNLOAD = 1
    const val ACT_TELEGRAM = 2
    const val ACT_BROWSER = 3

    private const val PREF = "mbx_ad"
    private const val K_PEND = "gate_pending"
    private const val K_TYPE = "gate_type"
    private const val K_URL = "gate_url"
    private const val K_EXTRA = "gate_extra"
    private const val K_TITLE = "gate_title"
    private const val K_TS = "gate_ts"                    /* গেট শুরুর সময় */
    /* ⏱ প্লে-সেক্টরের কুলডাউন ঘড়ি — নরমাল আর ১৮+ আলাদা (v5.3):
       নরমাল মুভির গেট পার হলে শুধু নরমাল প্লে ফ্রি; ১৮+ নিজের ঘড়িতে নিজের ২ মিনিট */
    private const val K_LAST_GATE = "last_play_gate_ts"       /* ▶ প্লে কুলডাউন (নরমাল) */
    private const val K_LAST_GATE_AD = "last_adult_gate_ts"   /* ▶ প্লে কুলডাউন (১৮+) */

    /* 🛡 রিয়েল-কোয়ালিটি গেটের হিসাব:
       K_WATCH = অ্যাড-পেজে কাটানো মোট মিলিসেকেন্ড (অ্যাপে ফিরে বসা সময় গোনা হয় না)
       K_AWAY  = অ্যাপ শেষ কবে লুকালো (onPause) — 0 মানে এখন অ্যাপে ফিরে আছে */
    private const val K_WATCH = "gate_watch_ms"
    private const val K_AWAY = "gate_away_ts"
    private const val K_PCLICKS = "poster_click_count"   /* 🎬 v5.1: পোস্টার-ক্লিক কাউন্টার */

    /* 🔞 এখন যে মুভির পেজ খোলা সেটা ১৮+ কি না — DetailActivity সেট করে */
    var adultNow: Boolean = false

    /* ══════════ 🛡 লাইফসাইকেল-হুক — অ্যাক্টিভিটি দুটোই ডাকবে ══════════ */

    /** অ্যাপ লুকালো (onPause) — গেট চলমান থাকলে এই মুহূর্ত থেকেই অ্যাড-পেজের সময় গোনা হবে */
    fun onGatePause(ctx: Context) {
        try {
            val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            if (p.getBoolean(K_PEND, false) && p.getLong(K_AWAY, 0L) == 0L) {
                p.edit().putLong(K_AWAY, System.currentTimeMillis()).apply()
            }
        } catch (_: Exception) {
        }
    }

    /** গেটের রেজাল্ট — অ্যাপে ফিরে এসে (onResume) যাচাই */
    sealed class GateResume {
        object None : GateResume()                            /* কোনো চলমান গেট নেই */
        object Done : GateResume()                            /* অ্যাড পুরো দেখা হয়েছে — কাজটা এখনই করো */
        data class Early(val remaining: Int) : GateResume()   /* আগে ফিরে এসেছে — আরও remaining সেকেন্ড বাকি */
    }

    /** ফিরে-এসে গেট-যাচাই: অ্যাড-পেজে কাটানো সময় জমা করে প্রয়োজনের সাথে মেলায় */
    fun onGateResume(ctx: Context): GateResume {
        /* 💓 (v5.5) অ্যাপে ফিরে এসেছি — বাকি-সেকেন্ড ভাইব্রেশন সিগন্যাল আর দরকার নেই
           (ঘড়ি এখন থেমে আছে); আবার অ্যাড খুললে requestAction/reopenGateLink নতুন করে শিডিউল করবে */
        GateVibe.cancel(ctx)
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!p.getBoolean(K_PEND, false)) return GateResume.None
        val now = System.currentTimeMillis()
        val ts = p.getLong(K_TS, 0L)
        /* 🧹 ৩০ মিনিটের পুরনো গেট আর কার্যকর নয় — নাহলে অ্যাপ খুললেই দিন আগে
           ক্লিক করা মুভি হঠাৎ প্লে/ডাউনলোড হয়ে যেত */
        if (ts > 0 && now - ts > 30 * 60_000L) {
            clearPending(ctx)
            /* 🧹 (v5.2) পুরনো গেটের আংশিক সেকেন্ডও বাতিল — এত পরে ফিরে এলে পরেরবার ফ্রেশ শুরু */
            try {
                ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                    .putLong(K_WATCH, 0L).putLong(K_AWAY, 0L).apply()
            } catch (_: Exception) {
            }
            return GateResume.None
        }
        /* ফিরে এসেছি — লুকানো-সময়টা এখনই অ্যাড-ভিউতে জমা করো */
        val away = p.getLong(K_AWAY, 0L)
        var watch = p.getLong(K_WATCH, 0L)
        if (away > 0) watch += now - away
        val need = RemoteAds.watchSeconds() * 1000L
        /* 🆕 (v5.3) ⬇ ডাউনলোড-সেক্টর = প্রতিবার ফ্রেশ: শুধু এই চেষ্টার অ্যাড-টাইমই গোনা
           হয়, আগের চেষ্টার জমা যোগ হয় না; আবার বাকি থাকলে জমাও শূন্য — পরের ক্লিকে
           আবার পুরো ১০ সেকেন্ড দেখতেই হবে (মালিকের নির্দেশ)। */
        if (p.getInt(K_TYPE, ACT_PLAY) != ACT_PLAY) {
            val ok = watch >= need
            val left = ((need - watch + 999) / 1000L).toInt().coerceAtLeast(1)
            p.edit().putLong(K_WATCH, 0L).putLong(K_AWAY, 0L).apply()
            return if (ok) GateResume.Done else GateResume.Early(left)
        }
        p.edit().putLong(K_WATCH, watch).putLong(K_AWAY, 0L).apply()
        return if (watch >= need) GateResume.Done
        else GateResume.Early(((need - watch + 999) / 1000L).toInt())
    }

    /* ══════════ গেট চালু / কুলডাউন ══════════ */

    /* ⏱ প্লে-গেটের কুলডাউন-কী — এখন যে মুভির কাজ চলছে সেটা নরমাল না ১৮+ সেই ঘড়ি (v5.3) */
    private fun lastGateKey() = if (adultNow) K_LAST_GATE_AD else K_LAST_GATE

    /** ▶ শুধু প্লে-সেক্টরের ফ্রি-চেক — শেষ প্লে-গেটের কুলডাউনের ভেতরে হলে ফ্রি
     *  (প্যানেল থেকে গেট বন্ধ থাকলেও ফ্রি)। ⬇ ডাউনলোড / 🌐 ব্রাউজার / ✈ টেলিগ্রাম এই
     *  ঘড়িতে জড়ায় না — ওদের নিয়ম আলাদা: প্রতিবার ক্লিকেই গেট (v5.3)। */
    private fun playGateFree(ctx: Context): Boolean {
        if (!RemoteAds.gateOn()) return true   /* ☁️ প্যানেল থেকে গেট বন্ধ */
        val last = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(lastGateKey(), 0L)
        return System.currentTimeMillis() - last < RemoteAds.cooldownSeconds() * 1000L
    }

    /** গেট বাতিল/ফেইল হলে কুলডাউন-ফাঁক বন্ধ — পরের ক্লিকে আবার গেট (বাইপাস-প্লাগ:
     *  আগে বাতিল করে সাথে সাথে আবার চাপলে অ্যাড-ছাড়া চলে যেত — ওই ফাঁকই এখন বন্ধ) */
    fun resetPlayGate(ctx: Context) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putLong(K_LAST_GATE, 0L).putLong(K_LAST_GATE_AD, 0L).apply()
        } catch (_: Exception) {
        }
    }

    /** 🆕 (v5.3) 🔄 রিফ্রেশ — নরমাল + ১৮+ দুই প্লে-ফ্রি-উইন্ডোই শূন্য। হোমের রিফ্রেশ
     *  বাটন চাপলেই ডাকা হয়: রিফ্রেশের পরের প্লে আবার পুরো ১০-সেকেন্ড গেট দেখে তবেই
     *  নতুন ২ মিনিটের ফ্রি পাবে। (ডাউনলোড-সেক্টরে ফ্রি-উইন্ডো নেইই — ওখানে লাগে না) */
    fun resetPlayerWindows(ctx: Context) {
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putLong(K_LAST_GATE, 0L).putLong(K_LAST_GATE_AD, 0L).apply()
        } catch (_: Exception) {
        }
    }

    /** 🆕 (v5.3) চলমান পেন্ডিং গেটটা ডাউনলোড-সেক্টরের (⬇/🌐/✈) কি না — সতর্কবার্তার
     *  লেখা সেক্টর-বুঝে দেখানোর জন্য (DetailActivity) */
    fun pendingIsDownloadSector(ctx: Context): Boolean {
        return try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(K_TYPE, ACT_PLAY) != ACT_PLAY
        } catch (_: Exception) {
            false
        }
    }

    /** সরাসরি র‍্যান্ডম Direct Link ব্রাউজারে খোলা — খুললে true */
    fun openDirectLink(ctx: Context, adult: Boolean = false): Boolean {
        val url = RemoteAds.directLinkNow(adult)
        if (url.isBlank()) return false
        return try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        } catch (_: Exception) {
            false
        }
    }

    /* ══════════ 🎬 পোস্টার-ক্লিক অ্যাড (v5.1) ══════════
       প্যানেল থেকে চালু করা থাকলে প্রতি N-তম পোস্টার-ক্লিকে একটা Direct Link খুলবে
       (নরমাল মুভি → নরমাল পুল, ১৮+ → ১৮+ পুল)। ডিফল্ট বন্ধ — বন্ধ থাকলে পোস্টার-ক্লিক
       সম্পূর্ণ অ্যাড-ফ্রি। চলমান গেট থাকলে পোস্টার-অ্যাড নয় — দুটো অ্যাড একসাথে জামা হবে না।
       গেটের কুলডাউন টাইমারের সাথে জড়ায় না — তাই পোস্টার-অ্যাড দেখে গেট স্কিপ করার পথ নেই। */

    /** true মানে পোস্টার-অ্যাড খুলেছে; false মানে কিছু খোলেনি (বন্ধ/কাউন্টার মেলেনি/গেট চলছে) */
    fun maybePosterAd(ctx: Context, adult: Boolean): Boolean {
        if (!RemoteAds.posterAdOn()) return false
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        /* চলমান গেট থাকলে পোস্টার-অ্যাড নয় — একসাথে দুটো অ্যাড ইউজার বিরক্ত করবে */
        if (p.getBoolean(K_PEND, false)) return false
        val n = RemoteAds.posterEveryN().coerceAtLeast(2)
        val clicks = p.getInt(K_PCLICKS, 0) + 1
        p.edit().putInt(K_PCLICKS, clicks).apply()
        if (clicks % n != 0) return false
        val opened = openDirectLink(ctx, adult)
        if (opened) Stats.posterAd(ctx)
        return opened
    }

    /* ── ⏮⏭ এপিসোড-সুইচের গেট (PlayerActivity) ──
       ইউজার যেন এপিসোড স্কিপ করে অ্যাড এড়াতে না পারে — ▶ প্লে-গেটের হুবহু নিয়ম:
       কুলডাউনের ভেতরে সুইচ = অ্যাড নেই; পার হলে রিয়েল ১০-সেকেন্ডের গেট + র‍্যান্ডম লিংক। */

    /** true মানে গেট দেখাতে হবে (কুলডাউন পার); false মানে অ্যাড ছাড়াই সুইচ করো */
    fun episodeGateNeeded(activity: Activity): Boolean {
        if (playGateFree(activity)) return false
        /* ⏱ এই মিনিটের অ্যাড গোনা হলো — নিজের (নরমাল/১৮+) ঘড়িতেই; পরের গেট এর কুলডাউন পরে */
        activity.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putLong(lastGateKey(), System.currentTimeMillis()).apply()
        return true
    }

    /** গেট দেখানোর সময় র‍্যান্ডম ডিরেক্ট-লিংক খোলে (প্লে-গেটের মতোই)
     *  💓 (v5.6) remainingMs দিলে বাকি সেকেন্ড পূর্ণ হলে একবার ভাইব্রেট (PlayerActivity-র এপিসোড-গেট) */
    fun openEpisodeGateAd(activity: Activity, remainingMs: Long = -1L) {
        if (remainingMs > 0)
            GateVibe.schedule(activity, remainingMs.coerceAtLeast(1000L) + GateVibe.EXTRA_MS)
        openDirectLink(activity, adultNow)
    }

    /** 🛡 গেট চলাকালীন আবার অ্যাড দেখতে চাইলে — নতুন র‍্যান্ডম লিংক
     *  ▶ প্লে-গেট: আগের অ্যাড-ভিউ-প্রগ্রেস থাকে, নতুন করে শূন্য হয় না (v5.2)।
     *  ⬇ ডাউনলোড-সেক্টর: প্রতিবার ফ্রেশ — জমা শূন্য করে আবার পুরো ১০ সেকেন্ড (v5.3)। */
    fun reopenGateLink(activity: Activity) {
        try {
            val p = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            if (p.getInt(K_TYPE, ACT_PLAY) != ACT_PLAY)
                p.edit().putLong(K_WATCH, 0L).putLong(K_AWAY, 0L).apply()
        } catch (_: Exception) {
        }
        /* 💓 (v5.6) আবার খোলা গেটেও ভাইব্রেশন — বাকি সেকেন্ড পূর্ণ হলেই (EXTRA_MS-সহ):
           ⬇ ডাউনলোড-সেক্টরে জমা শূন্য (তাই পুরো ১০), ▶ প্লে-গেটে জমা বাদ দিয়ে বাকিটুকু */
        try {
            val p = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            val needMs = RemoteAds.watchSeconds() * 1000L
            if (needMs > 0)
                GateVibe.schedule(activity, (needMs - p.getLong(K_WATCH, 0L)).coerceAtLeast(1000L) + GateVibe.EXTRA_MS)
        } catch (_: Exception) {
        }
        openDirectLink(activity, adultNow)
    }

    /** ▶ প্লে / ⬇ ডাউনলোড / ✈ টেলিগ্রাম / 🌐 ব্রাউজার — কাজের আগে অ্যাড-গেট।
     *  কাজ ফ্রি হলে true; নাহলে গেট শুরু + র‍্যান্ডম ডিরেক্ট-লিংক খোলে ও false দেয় —
     *  ফিরে এসে onGateResume() দেখে কাজটা করতে হবে।
     *  🆕 (v5.3) দুই সেক্টর, দুই নিয়ম:
     *  ▶ প্লে-সেক্টর (আগের মতোই): শেষ প্লে-গেটের কুলডাউনের ভেতরে হলে অ্যাড-ফ্রি; নরমাল
     *    আর ১৮+ আলাদা ঘড়িতে গোনা হয়। আংশিক দেখা সেকেন্ড জমা থাকে — বাকিটুকু দেখলেই প্লে।
     *  ⬇ ডাউনলোড-সেক্টর (ডাউনলোড • ব্রাউজার • টেলিগ্রাম): কুলডাউন নেইই — যতবারই ক্লিক,
     *    ততবারই গেট; প্লে-গেটের ২-মিনিট ফ্রি এখানে চলে না। আর প্রতিবারই ফ্রেশ — পুরো ১০
     *    সেকেন্ড এক নিঃশ্বাসে দেখতে হবে, আগের চেষ্টার সেকেন্ড কখনো জমা থাকে না (মালিকের নির্দেশ)।
     *  চলমান গেটের সেকেন্ডের মধ্যে দ্বিতীয় লিংক-ওপেন নয় (স্প্যাম-প্রোটেকশন)। */
    fun requestAction(activity: Activity, type: Int, url: String, title: String, extra: String = ""): Boolean {
        val dlSector = type != ACT_PLAY
        /* ⬇ ডাউনলোড-সেক্টরে কুলডাউন-চেকই নেই — প্রতিবার ক্লিকে গেট; ▶ প্লে: আগের মতো */
        if (!dlSector && playGateFree(activity)) return true
        val p = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        /* 💡 চলমান গেটের ভেতরে আরেকটা ক্লিক এলে নতুন লিংক খোলা হয় না —
           এক গেট = একটাই ওপেন (ইমপ্রেশন ফুলে যাওয়া বন্ধ) */
        val gateRunning = p.getBoolean(K_PEND, false) &&
            System.currentTimeMillis() - p.getLong(K_TS, 0L) < (RemoteAds.watchSeconds() + 3) * 1000L
        /* ▶ প্লে (v5.2): আংশিক জমা থাকলে সেটুকুই ধরে রাখা — বাকিটুকু দেখলেই প্লে।
           ⬇ ডাউনলোড-সেক্টর (v5.3): জমা রাখাই নেই — প্রতিবার ফ্রেশ পুরো ১০। */
        val needMs = RemoteAds.watchSeconds() * 1000L
        val watchSoFar = p.getLong(K_WATCH, 0L)
        val keepProgress = !dlSector && watchSoFar in 1L until needMs
        val now = System.currentTimeMillis()
        val ed = p.edit()
        /* ⏱ কুলডাউন-ঘড়ি শুধু ▶ প্লে-গেটেই বসে (নরমাল/১৮+ নিজের ঘড়িতে) — ডাউনলোড-গেট
           প্লেয়ারের ফ্রি-উইন্ডো ধরে না, প্লেয়ারের ফ্রি-উইন্ডও ডাউনলোড ধরে না */
        if (!dlSector) ed.putLong(lastGateKey(), now)
        ed.putLong(K_TS, now)
            .putBoolean(K_PEND, true)
            .putInt(K_TYPE, type)
            .putString(K_URL, url)
            .putString(K_EXTRA, extra)
            .putString(K_TITLE, title)
        if (!gateRunning && !keepProgress) ed.putLong(K_WATCH, 0L).putLong(K_AWAY, 0L)
        ed.apply()
        if (!gateRunning) {
            /* ব্রাউজার খুলতে না পারলে গেট বাতিল — ইউজার আটকে থাকবে না */
            if (!openDirectLink(activity, adultNow)) {
                clearPending(activity)
                if (!dlSector) resetPlayGate(activity)   /* ▶ প্লে-গেটের ফাঁকই শুধু বন্ধ */
                return true
            }
            /* 💓 (v5.6) অ্যাড-পেজে বাকি সেকেন্ড পূর্ণ হলে সাথে সাথেই একবার ভাইব্রেট —
               ইউজার ব্যাক করার আগেই সিগন্যাল পৌঁছায় (v5.5-এ ২.৫ সেকেন্ড দেরিতে যেত,
               ইউজার ১০ সেকেন্ডেই ব্যাক করে ফিরে এসে সিগন্যাল বাতিল হয়ে যেত)।
               ▶ প্লে-গেটে আগের জমা সেকেন্ড বাদ দিয়ে বাকিটুকুর হিসাব;
               ⬇ ডাউনলোড-সেক্টরে সবসময় পুরোটা। */
            val seen = if (keepProgress) watchSoFar else 0L
            if (needMs > 0)
                GateVibe.schedule(activity, (needMs - seen).coerceAtLeast(1000L) + GateVibe.EXTRA_MS)
        }
        return false
    }

    /** 🆕 (v5.2) গেটে এখন আর কত সেকেন্ড বাকি — টোস্ট/ওভারলেতে দেখানোর জন্য
     *  (▶ প্লে-গেটে আগে কিছু দেখা জমা থাকলে ১০ না, বাকিটাই দেখাবে;
     *  ⬇ ডাউনলোড-সেক্টরে প্রতিবার ফ্রেশ — তাই সবসময় পুরোটাই দেখাবে) */
    fun gateRemainingSec(ctx: Context): Int {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val needMs = RemoteAds.watchSeconds() * 1000L
        val watch = p.getLong(K_WATCH, 0L)
        val away = p.getLong(K_AWAY, 0L)
        val eff = if (away > 0) watch + (System.currentTimeMillis() - away) else watch
        return ((needMs - eff + 999) / 1000L).toInt().coerceAtLeast(1)
    }

    /* ══════════ পেন্ডিং অ্যাকশন ══════════ */

    /** গেট শেষ — যে কাজটার অপেক্ষায় ছিল সেটা */
    data class PendingAction(val type: Int, val url: String, val title: String, val extra: String)

    fun consume(activity: Activity): PendingAction? {
        val p = activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val type = p.getInt(K_TYPE, ACT_PLAY)
        val url = p.getString(K_URL, null) ?: return null
        val title = p.getString(K_TITLE, "MovieBoxBD") ?: "MovieBoxBD"
        val extra = p.getString(K_EXTRA, "") ?: ""
        clearPending(activity)
        /* 🆕 (v5.2) গেট পূর্ণ + কাজ বুঝে গেছে — জমা সেকেন্ডও শূন্য; পরের গেট ফ্রেশ শুরু হবে */
        try {
            activity.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putLong(K_WATCH, 0L).putLong(K_AWAY, 0L).apply()
        } catch (_: Exception) {
        }
        return PendingAction(type, url, title, extra)
    }

    /** ইউজার বাতিল করলে — পেন্ডিং মোছে; ▶ প্লে-গেট হলে কুলডাউন-ফাঁকও বন্ধ (পরের ক্লিকে
     *  আবার গেট)। ⬇ ডাউনলোড-সেক্টরে কুলডাউন জড়িতই না — প্লেয়ারের জেতা ফ্রি-উইন্ডো
     *  অক্ষত থাকবে (v5.3)। */
    fun cancelGate(activity: Activity) {
        val wasPlay = try {
            activity.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getInt(K_TYPE, ACT_PLAY) == ACT_PLAY
        } catch (_: Exception) {
            true
        }
        clearPending(activity)
        if (wasPlay) resetPlayGate(activity)
    }

    private fun clearPending(ctx: Context) {
        GateVibe.cancel(ctx)   /* 💓 (v5.5) গেট মুছলে বাকি-সেকেন্ড ভাইব্রেশন-সিগন্যালও বাতিল */
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putBoolean(K_PEND, false).remove(K_URL).remove(K_EXTRA).apply()
        } catch (_: Exception) {
        }
    }
}
