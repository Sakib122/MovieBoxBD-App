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

    /* ══════════════ 💰 ADSTERRA — তোমার নিজের অ্যাড (v5.1 — প্যানেল-কন্ট্রোলড) ══════════════

       📌 সিস্টেম:
       ① ▶ প্লে / ⏮⏭ এপিসোড-সুইচ / ⬇ ডাউনলোড / 🌐 ব্রাউজার / ✈ টেলিগ্রাম — কুলডাউন পার হলে অ্যাড-গেট
       ② 🛡 রিয়েল-কোয়ালিটি গেট: সময় শুধু অ্যাড-পেজে থাকার সময়ই গোনা হয় — সময়ের আগে ফিরলে
          সতর্কবার্তা (আরও X সেকেন্ড বাকি ছিল), পুরোটা দেখলে ফিরেই কাজ
       ③ 🔞 সাধারণ মুভি → NORMAL_DIRECT_LINKS, ১৮+ মুভি → ADULT_DIRECT_LINKS — কখনো মেশে না
       ④ ☁️ গেট চালু/বন্ধ, সেকেন্ড, কুলডাউন, লিংক পুল — সব অ্যাডমিন প্যানেল থেকে রিমোটলি
          বদলানো যায় (RemoteAds) — নিচের মানগুলো শুধু বিল্ট-ইন ডিফল্ট/ফলব্যাক
       ⑤ রোটেশন: প্রতিবার র‍্যান্ডম পিক, তবে সর্বশেষ দেখানো লিংকটা পরপর দুইবার আসবে না
       ⑥ 🚫 ব্যানার সম্পূর্ণ বাদ (v5.0) — মৃত ইমপ্রেশন CPM নামাত; ইনকাম এখন শুধু রিয়েল গেট-ভিউতে
       ⑦ 🎬 পোস্টার-ক্লিক অ্যাড (v5.1): ডিফল্ট বন্ধ — প্যানেল থেকে চালু করলে প্রতি N-তম
          পোস্টার-ক্লিকে একটা লিংক খুলবে (ফ্রিকোয়েন্সি প্যানেল থেকে বদলায়)। সাজেস্টেড:
          বন্ধই রাখো — ভেরিফায়েড গেট-ভিউ-ই সবচেয়ে ভালো CPM দেয় */

    /* ── ✅ সাধারণ মুভির Direct Link পুল — বিল্ট-ইন ডিফল্ট/ফলব্যাক ──
       প্যানেলে লিংক সেভ করা থাকলে ওগুলোই চলে; প্যানেল খালি/সার্ভার বন্ধ হলে এগুলো চলে।
       ⚠️ নরমাল পুলে কখনো অ্যাডাল্ট নেটওয়ার্কের লিংক দেবেন না — CPM/পলিসি দুটোতেই সমস্যা। */
    val NORMAL_DIRECT_LINKS = listOf(
        "https://asiafilm.org/4/1952b9f3548cd57e994e610fde24e41f",   /* Normal Direct Link 1 */
        "https://asiafilm.org/4/7ef304669bcfc15f3404e1a5e5dcf1ba",   /* Normal Direct Link 2 */
        "https://asiafilm.org/4/dd496e8de5fdbe10f2ccf3defc93c342",   /* Normal Direct Link 3 */
        "https://uplcm.com/4/10918781",                              /* Normal Direct Link 4 */
        "https://uplcm.com/4/12080503"                               /* Normal Direct Link 5 */
    )

    /* ── 🔞 অ্যাডাল্ট মুভির Direct Link পুল — বিল্ট-ইন ডিফল্ট/ফলব্যাক ── */
    val ADULT_DIRECT_LINKS = listOf(
        "https://auctionr.org/4/1b6df569ebc094e3e8e592d2fd017b4f",   /* Adult Direct Link 1 */
        "https://auctionr.org/4/ae2d07d4c0488d06e6aec709d40d437f",   /* Adult Direct Link 2 */
        "https://auctionr.org/4/ef5b10414fe8fdd3f602e74bcdd4225c",   /* Adult Direct Link 3 */
        "https://auctionr.org/4/9a420739bd309e36f6535c4bcad2d3c9",   /* Adult Direct Link 4 */
        "https://auctionr.org/4/b51d4b4d19155b12b384cdd6f2b31daa"    /* Adult Direct Link 5 */
    )

    /* অ্যাড-ব্যাক সেকেন্ড (গেটের ডিফল্ট — প্যানেল থেকে বদলানো যায়):
       🛡 মালিকের নির্দেশ v5.0: ৫ সেকেন্ডের বদলে ১০ সেকেন্ড — আর সময়টা শুধু
       অ্যাড-পেজে থাকার সময়ই গোনা হয় (অ্যাপে ফিরে বসা সময় নয়) — তাতেই রিয়েল কোয়ালিটি। */
    const val AD_WATCH_SECONDS = 10

    /* ⏱ অ্যাড কুলডাউন (সেকেন্ড — প্যানেল থেকে বদলানো যায়): একটা গেট শুরুর পর এই সময়ের
       ভেতরে আর কোনো ডাইরেক্ট-লিংক অ্যাড আসবে না — প্লে, ⏮⏭ এপিসোড-সুইচ, ডাউনলোড —
       সব অ্যাকশনে একই টাইমার। মালিকের নির্দেশ: ২ মিনিট পর পর অ্যাড। */
    const val AD_COOLDOWN_SECONDS = 120

    /* 🎬 (v5.1) পোস্টার-ক্লিক অ্যাডের ডিফল্ট ফ্রিকোয়েন্সি — প্যানেল থেকে চালু করা থাকলে
       প্রতি N-তম পোস্টার-ক্লিকে একটা Direct Link খুলবে। চালু/বন্ধ + N — সব প্যানেল থেকে
       বদলায়; প্যানেলে সেভ না থাকলে এই ডিফল্ট (বন্ধ) চলে। */
    const val POSTER_AD_EVERY_N_DEFAULT = 5

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
