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
        "Hindi", "Hindi Dubbed", "Bangla", "Korean", "English", "Horror"
    )

    /* ── ওয়েব সিরিজ চেনার লেবেল ──────────────────────────────── */
    val WEB_SERIES_LABELS = listOf("Web Series")

    /* ── বাংলা ডাব লেবেল — ব্লগে এই লেবেল দিলেই অ্যাপে অটো সেকশন আসবে ── */
    val BANGLA_DUB_LABELS = listOf("Bangla Dub", "বাংলা ডাব", "Bangla Dubbing", "Bangla Dubbed", "Bengali")

    /* ── 🇧🇩 বাংলা — ব্লগে লেবেলের নাম "Bengali" (ওয়েবসাইটে /search/label/Bengali)।
      হোমে "🎙️ বাংলা ডাব" সেকশনে দেখাবে, মুভি ট্যাবে "🇧🇩 বাংলা" চিপে খুলবে ── */
    const val BANGLA_TAB_NAME = "🇧🇩 বাংলা ডাব"
    const val BANGLA_MAIN_LABEL = "Bengali"
    val BANGLA_LABELS = listOf("Bangla", "Bengali", "বাংলা")

    /* ── 🔞 Adult লেবেল — সামনে কোথাও দেখাবে না; হোম টুলবারের 🔒 → ১৮+ পারমিশনের পরে ── */
    const val ADULT_LABEL = "adult"
    val ADULT_LABELS = listOf("Adult", "18+", "Adults")

    /* ── টেলিগ্রাম বটের ইউজারনেম (@ ছাড়া) — যেমন: "MovieBoxBDBot"
          এটা দিলেই ডিটেইল পেজে ✈️ টেলিগ্রাম বাটন চালু হবে ── */
        
    const val TELEGRAM_BOT_USERNAME = "movieboxxpro_bot"

    /* ── ✈️ টেলিগ্রাম চ্যানেল (হোম টুলবার, ডিটেইল পেজ, প্লেয়ারের এরর বক্স —
          সব জায়গায় এই লিংকটাই খুলবে; খালি রাখলে বাটন লুকাবে) ───── */
    const val TELEGRAM_URL = "https://t.me/SakibMovieBox"

    /* ══════════════ 💰 ADSTERRA — তোমার নিজের অ্যাড ══════════════

       📌 সিস্টেম (ইনকাম বেশি + বিরক্তি কম):
       ① ▶ প্লে / ⬇ ডাউনলোড / 🌐 ব্রাউজার / ✈ টেলিগ্রাম — এই ৪টা বাটনে চাপলেই
          সাথে সাথে Direct Link খুলবে (প্রতিবার, কোনো কুলডাউন নেই)
       ② ইউজার অ্যাডে কমপক্ষে AD_WATCH_SECONDS সেকেন্ড থাকতে হবে —
          তার আগে ফিরে এলে অ্যাপে কাউন্টডাউন দেখিয়ে তারপর কাজটা হবে
       ③ মুভি কার্ডে ক্লিক করলে প্রতি ২ বারে ১ বার (POPUNDER_EVERY_N) Direct Link
       ④ ব্যানার: হোম-নিচ + ডিটেইল-নিচ (320×50) + ডিটেইল ভেতরে (300×250)
       ⑤ প্লেয়ারে কোনো অ্যাড নেই — মুভি দেখার সময় শূন্য বিরক্তি          */

    /* ── Direct Link পুল — একটার পর একটা ঘণ্টা করে বদলাবে ────────
       🔑 একই লিংক বারবার ইউজ করলে CPM কমে যায়, তাই একাধিক লিংক এখানে রাখো —
       অ্যাপ প্রতি ~১ ঘণ্টা পরপর পরের লিংকটা ইউজ করবে (অটো রোটেশন)।
       নতুন লিংক পেলে কমার মাঝে এভাবে যোগ করো:
           "https://asiafilm.org/4/xxxxxxxxxxxx",
       লিংক ১টাই রাখলে সেটাই সবসময় চলবে — কোনো সমস্যা নেই।  */
    val DIRECT_LINKS = listOf(
        "https://asiafilm.org/4/dd496e8de5fdbe10f2ccf3defc93c342",
        "https://asiafilm.org/4/1952b9f3548cd57e994e610fde24e41f"
    )

    /** এই মুহূর্তে কোন Direct Link চলবে (ঘণ্টা অনুযায়ী ঘুরে ঘুরে বদলায়) */
    fun directLinkNow(): String {
        val pool = DIRECT_LINKS.filter { it.isNotBlank() }
        if (pool.isEmpty()) return ""
        if (pool.size == 1) return pool[0]
        val hour = System.currentTimeMillis() / 3_600_000L
        return pool[(hour % pool.size).toInt()]
    }

    /** ▶ প্লের আগে অ্যাডে কত সেকেন্ড থাকতে হবে (৫–১০, যেটা বেস্ট) */
    const val AD_WATCH_SECONDS = 10

    /** মুভি কার্ডে কতবার ক্লিকে ১ বার পপআন্ডার খুলবে (২ = প্রতি ২ বারে ১ বার) */
    const val POPUNDER_EVERY_N = 2

    /* ── 320×50 ব্যানার (হোম + ডিটেইল পেজের নিচে)। খালি ("") দিলে দেখাবে না ── */
    const val ADSTERRA_BANNER_320x50 = "<script type='text/javascript'>\n" +
        "atOptions = {\n" +
        "'key' : 'a19161feecb13d6d32cfa3a7f599f5a2',\n" +
        "'format' : 'iframe',\n" +
        "'height' : 50,\n" +
        "'width' : 320,\n" +
        "'params' : {}\n" +
        "};\n" +
        "</script>\n" +
        "<script src='https://bicea.org/22/a19161feecb13d6d32cfa3a7f599f5a2'></script>"

    /* ── 300×250 ব্যানার (ডিটেইল পেজের ভেতরে)। খালি ("") দিলে দেখাবে না ── */
    const val ADSTERRA_BANNER_300x250 = "<script type='text/javascript'>\n" +
        "atOptions = {\n" +
        "'key' : '091649489016302f51dd722fff20f22b',\n" +
        "'format' : 'iframe',\n" +
        "'height' : 250,\n" +
        "'width' : 300,\n" +
        "'params' : {}\n" +
        "};\n" +
        "</script>\n" +
        "<script src='https://bicea.org/22/091649489016302f51dd722fff20f22b'></script>"

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
