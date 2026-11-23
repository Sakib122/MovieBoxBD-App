package com.movieboxbd.app

/**
 * ⚙️ এই ফাইলটাই অ্যাপের মূল সেটিংস — সবকিছু এখান থেকে বদলাবে।
 * কিছু বদলানোর পর GitHub-এ Commit দিলেই অটোমেটিক নতুন APK তৈরি হবে।
 */
object Config {

    /* ── তোমার ওয়েবসাইট (Blogger) ─────────────────────────────── */
    const val SITE = "https://www.movieboxbd.xyz"

    /* ── ক্যাটাগরি চিপস (মুভি ট্যাব) — ব্লগের Labels হুবহু লিখো ── */
    val CATEGORY_LABELS = listOf(
        "Hindi", "Hindi Dubbed", "Bangla", "Korean", "English", "Horror", "Anime"
    )

    /* ── ওয়েব সিরিজ চেনার লেবেল ──────────────────────────────── */
    val WEB_SERIES_LABELS = listOf("Web Series")

    /* ── বাংলা ডাব লেবেল — ব্লগে এই লেবেল দিলেই অ্যাপে অটো সেকশন আসবে ── */
    val BANGLA_DUB_LABELS = listOf("Bangla Dub", "বাংলা ডাব", "Bangla Dubbing", "Bangla Dubbed", "Bengali")

    /* ── 🇧🇩 বাংলা দুই ভাগ (মালিকের নির্দেশ v4.3):
       🎙️ বাংলা ডাব — অন্য ভাষা থেকে বাংলায় ডাব করা → ব্লগের "Bengali" লেবেল
       🎬 বাংলা (সরাসরি) — আসল বাংলা মুভি → ব্লগের "Bangla" লেবেল
       হোমে দুটো আলাদা সেকশন, মুভি ট্যাবে দুটো আলাদা চিপ ── */
    const val BANGLA_DUB_TAB_NAME = "বাংলা ডাব"
    const val BANGLA_DUB_QUERY = "Bengali"
    const val BANGLA_TAB_NAME = "বাংলা"
    const val BANGLA_MAIN_LABEL = "Bangla"
    val BANGLA_LABELS = listOf("Bangla", "বাংলা")

    /* ── 🔞 Adult লেবেল — সামনে কোথাও দেখাবে না; হোম টুলবারের 🔒 → ১৮+ পারমিশনের পরে ── */
    const val ADULT_LABEL = "adult"
    val ADULT_LABELS = listOf("Adult", "18+", "Adults")

    /* ── টেলিগ্রাম বটের ইউজারনেম (@ ছাড়া) — যেমন: "MovieBoxBDBot"
          এটা দিলেই ডিটেইল পেজে ✈️ টেলিগ্রাম বাটন চালু হবে ── */
        
    const val TELEGRAM_BOT_USERNAME = "movieboxxpro_bot"

    /* ── ✈️ টেলিগ্রাম চ্যানেল (হোম টুলবার, ডিটেইল পেজ, প্লেয়ারের এরর বক্স —
          সব জায়গায় এই লিংকটাই খুলবে; খালি রাখলে বাটন লুকাবে) ───── */
    const val TELEGRAM_URL = "https://t.me/SakibMovieBox"

    /* ══════════════ 💰 ADSTERRA — তোমার নিজের অ্যাড (v3.1 — বটের সিস্টেমের মতো) ══════════════

       📌 সিস্টেম (বটগুলোর pick_random_link-এর হুবহু কপি):
       ① মুভি পোস্টারে ক্লিক → সাথে সাথে একটা Direct Link ব্রাউজারে (র‍্যান্ডম লিংক)
       ② ব্যাক দিয়ে ফিরে এসে ▶ প্লে চাপলে → আরেকটা Direct Link (আগেরটা নয়) +
          অ্যাপে ৫ সেকেন্ডের কাউন্টডাউন → শেষ হলেই প্লে
       ③ ⬇ ডাউনলোড / 🌐 ব্রাউজার / ✈ টেলিগ্রাম — একইভাবে অ্যাড → ৫ সেকেন্ড → কাজ
       ④ 🔞 সাধারণ মুভি → NORMAL_DIRECT_LINKS, ১৮+ মুভি → ADULT_DIRECT_LINKS
       ⑤ রোটেশন: প্রতিবার র‍্যান্ডম পিক, তবে সর্বশেষ দেখানো লিংকটা পরপর দুইবার আসবে না
          (বটের pick_random_link যেমন) — সব স্লট ভরা, কোনো খালি স্লটে অ্যাড যায় না
       ⑥ ব্যানার ৪টাই রিয়েল Adsterra কোড — রিয়েল ইমপ্রেশন; প্লেয়ারে কোনো অ্যাড নেই          */

    /* ── ✅ সাধারণ মুভির Direct Link পুল — ৫টা স্লট, ৫টাই রিয়েল ──
       নতুন লিংক পেলে নিচে যোগ করো — অ্যাপ নিজে থেকেই নতুন পুলে র‍্যান্ডম পিক করবে

       ⚠️ মালিকের জন্য জরুরি নোট (v3.5): "সাধারণ মুভিতে অ্যাডাল্ট অ্যাড" দেখানোর কারণ
       অ্যাপের রাউটিং নয় — অ্যাপ সাধারণ মুভিতে শুধুই নিচের NORMAL লিংক/ব্যানার খোলে
       (কোডে adult=false হলে অ্যাডাল্ট পুলে যাওয়ার কোনো পথই নেই)। আসল কারণ:
       asiafilm.org / uplcm.com এই অ্যাড-নেটওয়ার্কগুলো মাঝে মাঝে নিজে থেকেই ১৮+
       ক্রিয়েটিভ/ল্যান্ডিং দেখায়। সেটা থামাতে যে স্লট দোষী, সেই লিংকটা বদলে
       নতুন "ক্লিন" স্লট বসাও — নিচের লিস্টে বদলালেই পুরো অ্যাপে বদলে যাবে। */
    val NORMAL_DIRECT_LINKS = listOf(
        "https://asiafilm.org/4/1952b9f3548cd57e994e610fde24e41f",   /* Normal Direct Link 1 */
        "https://asiafilm.org/4/7ef304669bcfc15f3404e1a5e5dcf1ba",   /* Normal Direct Link 2 */
        "https://asiafilm.org/4/dd496e8de5fdbe10f2ccf3defc93c342",   /* Normal Direct Link 3 */
        "https://uplcm.com/4/10918781",                              /* Normal Direct Link 4 */
        "https://uplcm.com/4/12080503"                               /* Normal Direct Link 5 */
    )

    /* ── 🔞 অ্যাডাল্ট মুভির Direct Link পুল — ৫টা স্লট, ৫টাই রিয়েল ── */
    val ADULT_DIRECT_LINKS = listOf(
        "https://auctionr.org/4/1b6df569ebc094e3e8e592d2fd017b4f",   /* Adult Direct Link 1 */
        "https://auctionr.org/4/ae2d07d4c0488d06e6aec709d40d437f",   /* Adult Direct Link 2 */
        "https://auctionr.org/4/ef5b10414fe8fdd3f602e74bcdd4225c",   /* Adult Direct Link 3 */
        "https://auctionr.org/4/9a420739bd309e36f6535c4bcad2d3c9",   /* Adult Direct Link 4 */
        "https://auctionr.org/4/b51d4b4d19155b12b384cdd6f2b31daa"    /* Adult Direct Link 5 */
    )

    /* 🎲 বটের pick_random_link-এর মতো: পুল থেকে র‍্যান্ডম লিংক,
       সর্বশেষ দেখানো লিংকটা পরপর দুইবার যায় না */
    private val lastServed = HashMap<String, String>()
    private val rnd = java.util.Random()

    fun directLinkNow(adult: Boolean = false): String {
        val pool = (if (adult) ADULT_DIRECT_LINKS else NORMAL_DIRECT_LINKS)
            .filter { it.isNotBlank() }
            .ifEmpty { NORMAL_DIRECT_LINKS.filter { it.isNotBlank() } }
        if (pool.isEmpty()) return ""
        if (pool.size == 1) return pool[0]
        val key = if (adult) "adult" else "normal"
        val last = lastServed[key]
        val sub = pool.filter { it != last }.ifEmpty { pool }
        val pick = sub[rnd.nextInt(sub.size)]
        lastServed[key] = pick
        return pick
    }

    /* অ্যাড-ব্যাক সেকেন্ড (প্লে চাপলে অ্যাপে ৫ সেকেন্ডের কাউন্টডাউন) */
    const val AD_WATCH_SECONDS = 5

    /* ⏱ অ্যাড কুলডাউন (সেকেন্ড): একটা অ্যাড দেখার পর এই সময়ের ভেতরে আর কোনো
       ডাইরেক্ট-লিংক অ্যাড আসবে না — ইউজার যা খুশি করতে পারবে (প্লে, ⏮⏭ এপিসোড-সুইচ,
       ডাউনলোড — সব অ্যাকশনে একই টাইমার)। মালিকের নির্দেশ v3.7: ২ মিনিট পর পর অ্যাড।
       সময় বদলাতে চাইলে শুধু এই সংখ্যাটা বদলাও। */
    const val AD_COOLDOWN_SECONDS = 120

    /* ── 📢 ব্যানার অ্যাড — ৪টাই রিয়েল Adsterra কোড (রিয়েল ইমপ্রেশন) ──
       সাধারণ মুভি/হোম → NORMAL ব্যানার, ১৮+ মুভি → ADULT ব্যানার।
       নতুন ব্যানার-কোড পেলে সংশ্লিষ্ট লিস্টে যোগ করো — র‍্যান্ডম ঘুরবে। */
    val BANNER_NORMAL_320 = listOf(
        "<script type='text/javascript'>\n" +
            "atOptions = {\n" +
            "'key' : 'a19161feecb13d6d32cfa3a7f599f5a2',\n" +
            "'format' : 'iframe',\n" +
            "'height' : 50,\n" +
            "'width' : 320,\n" +
            "'params' : {}\n" +
            "};\n" +
            "</script>\n" +
            "<script src='https://bicea.org/22/a19161feecb13d6d32cfa3a7f599f5a2'></script>"
    )

    /* 300×250 — ডিটেইল পেজের ভেতরে */
    val BANNER_NORMAL_300 = listOf(
        "<script type='text/javascript'>\n" +
            "atOptions = {\n" +
            "'key' : '091649489016302f51dd722fff20f22b',\n" +
            "'format' : 'iframe',\n" +
            "'height' : 250,\n" +
            "'width' : 300,\n" +
            "'params' : {}\n" +
            "};\n" +
            "</script>\n" +
            "<script src='https://bicea.org/22/091649489016302f51dd722fff20f22b'></script>"
    )

    /* 🔞 অ্যাডাল্ট ব্যানার — ১৮+ মুভির পেজে */
    val BANNER_ADULT_320 = listOf(
        "<script type='text/javascript'>\n" +
            "atOptions = {\n" +
            "'key' : 'f96407c772e15f558541667b8d868edc',\n" +
            "'format' : 'iframe',\n" +
            "'height' : 50,\n" +
            "'width' : 320,\n" +
            "'params' : {}\n" +
            "};\n" +
            "</script>\n" +
            "<script src='https://brijmohan.org/22/f96407c772e15f558541667b8d868edc'></script>"
    )

    /* 🔞 অ্যাডাল্ট বড় ব্যানার (300×250) — ১৮+ মুভির ডিটেইল পেজে */
    val BANNER_ADULT_300 = listOf(
        "<script type='text/javascript'>\n" +
            "atOptions = {\n" +
            "'key' : 'f1ac37c4ab0a5dab60781b14dd5696bb',\n" +
            "'format' : 'iframe',\n" +
            "'height' : 250,\n" +
            "'width' : 300,\n" +
            "'params' : {}\n" +
            "};\n" +
            "</script>\n" +
            "<script src='https://brijmohan.org/22/f1ac37c4ab0a5dab60781b14dd5696bb'></script>"
    )

    /* ── 🎬 ডাউনলোড করা ফাইলের নামের শুরুতে এই ব্র্যান্ড বসবে ──
       যেমন: "MovieBoxBD.xyz — Kill Zone (2026) [720p WEB-DL]" ── */
    const val BRAND = "MovieBoxBD.xyz"

    /* ── 📊 অ্যাডমিন প্যানেল API — তোমার নিজের koyeb সার্ভার (স্ট্যাট + আপডেট) ──
       খালি ("") দিলে ট্র্যাকিং/আপডেট-চেক সম্পূর্ণ বন্ধ, অ্যাপ আগের মতোই চলবে ── */
    const val ADMIN_API = "https://fit-orangutan-sakiblifestyle-f51d1556.koyeb.app"

    /* ══════════════ 🔔 নোটিফিকেশন ══════════════
       ব্লগে নতুন মুভি দিলে অ্যাপ নিজে থেকেই চেক করে নোটিফিকেশন দেখাবে
       (কোনো ফায়ারবেস/সার্ভার লাগে না — সম্পূর্ণ ফ্রি)।                  */
    const val NOTIFY_NEW_UPLOADS = true
}
