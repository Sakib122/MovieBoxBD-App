package com.movieboxbd.app

/**
 * ⚙️ এই ফাইলটাই অ্যাপের মূল সেটিংস — সবকিছু এখান থেকে বদলাবে।
 * কিছু বদলানোর পর GitHub-এ Commit দিলেই অটোমেটিক নতুন APK তৈরি হবে।
 */
object Config {

    /* ── তোমার ওয়েবসাইট (Blogger) ─────────────────────────────── */
    const val SITE = "https://www.movieboxbd.xyz"

    /* ── ক্যাটাগরি — ব্লগের Labels হুবহু লিখো (বড়/ছোট হাতের অক্ষর মিলতে হবে) ── */
    val CATEGORY_LABELS = listOf("Hindi", "Hindi Dubbed", "Bangla", "Horror", "Web Series", "South")

    /* ── টেলিগ্রাম চ্যানেল/গ্রুপ (খালি রাখলে বাটন দেখাবে না) ───── */
    const val TELEGRAM_URL = ""

    /* ══════════════ 💰 ADSTERRA — তোমার অ্যাড ══════════════
       ✅ তোমার সাইটে যে Direct Link চলছে সেটাই বসানো আছে।
       বদলাতে চাইলে Adsterra Dashboard → Direct Links → নতুন লিংক
       কপি করে নিচে পেস্ট করো। খালি ("") রাখলে অ্যাড দেখাবে না।   */

    /** ▶ প্লে চাপলে (প্রতি ৬ ঘণ্টায় ১ বার) এই লিংক খুলবে — তোমার সাইটের মতই */
    const val ADSTERRA_DIRECT_LINK =
        "https://www.effectivecpmnetwork.com/t0t62pprhs?key=1952b9f3548cd57e994e610fde24e41f"

    /** ঘণ্টায় কতক্ষণ পর পর Direct Link খুলবে */
    const val DIRECT_LINK_COOLDOWN_HOURS = 6

    /** নিচে ছোট ব্যানার অ্যাড — Adsterra ড্যাশবোর্ড থেকে ব্যানার কোড কপি করে
        পুরো <script>...</script> অংশ এখানে পেস্ট করো।
        যেমন: "<script type='text/javascript'> atOptions = {...} </script>"
        খালি ("") রাখলে ব্যানার দেখাবে না। */
    const val ADSTERRA_BANNER_HTML = ""
}
