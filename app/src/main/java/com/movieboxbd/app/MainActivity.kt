package com.movieboxbd.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.concurrent.TimeUnit

/**
 * 🏠 হোম v2 — নিচে ৪টা ট্যাব:
 *  🏠 হোম (নেটফ্লিক্স স্টাইল রো) | 📺 ওয়েব সিরিজ | 🎬 মুভি | 🔍 সার্চ
 */
class MainActivity : AppCompatActivity() {

    /* ── ভিউ ── */
    private lateinit var homeList: RecyclerView
    private lateinit var homePane: View
    private lateinit var homeSpinner: ProgressBar
    private lateinit var homeEmpty: View
    private lateinit var paneGrid: View
    private lateinit var chipsWrap: View
    private lateinit var chips: ChipGroup
    private lateinit var recycler: RecyclerView
    private lateinit var spinner: ProgressBar
    private lateinit var emptyBox: View
    private lateinit var emptyText: TextView
    private lateinit var searchPane: View
    private lateinit var searchInput: EditText
    private lateinit var searchBtn: ImageButton
    private lateinit var searchGrid: RecyclerView
    private lateinit var searchSpinner: ProgressBar
    private lateinit var searchEmpty: View

    /* ── অবস্থা ── */
    private val items = ArrayList<MovieItem>()
    private val searchItems = ArrayList<MovieItem>()
    private lateinit var adapter: MovieAdapter
    private lateinit var searchAdapter: MovieAdapter
    private var currentLabel: String? = null
    private var nextIndex = 1
    private var loading = false
    private var endReached = false
    private var tab = R.id.navHome
    private var homeLoaded = false
    private var homeAdapter: HomeAdapter? = null   /* 🎬 হিরো স্লাইডার থামানো/চালানোর জন্য */    

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        homePane = findViewById(R.id.homePane)
        homeList = findViewById(R.id.homeList)
        homeSpinner = findViewById(R.id.homeSpinner)
        homeEmpty = findViewById(R.id.homeEmpty)
        paneGrid = findViewById(R.id.paneGrid)
        chipsWrap = findViewById(R.id.chipsWrap)
        chips = findViewById(R.id.chips)
        recycler = findViewById(R.id.recycler)
        spinner = findViewById(R.id.spinner)
        emptyBox = findViewById(R.id.emptyBox)
        emptyText = findViewById(R.id.emptyText)
        searchPane = findViewById(R.id.searchPane)
        searchInput = findViewById(R.id.searchInput)
        searchBtn = findViewById(R.id.searchBtn)
        searchGrid = findViewById(R.id.searchGrid)
        searchSpinner = findViewById(R.id.searchSpinner)
        searchEmpty = findViewById(R.id.searchEmpty)

        adapter = MovieAdapter(items) { openMovie(it) }
        recycler.layoutManager = GridLayoutManager(this, 2)
        recycler.adapter = adapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = rv.layoutManager as GridLayoutManager
                if (lm.findLastVisibleItemPosition() >= items.size - 4) loadMore()
            }
        })

        searchAdapter = MovieAdapter(searchItems) { openMovie(it) }
        searchGrid.layoutManager = GridLayoutManager(this, 2)
        searchGrid.adapter = searchAdapter

        homeList.layoutManager = LinearLayoutManager(this)

        /* 💰 হোম স্ক্রিনের নিচের ব্যানার অ্যাড (তোমার Adsterra) */
        Ads.applyBanner(findViewById(R.id.adBanner), Config.ADSTERRA_BANNER_320x50)

        findViewById<View>(R.id.btnRetry).setOnClickListener { load(true) }
        findViewById<View>(R.id.btnRetryHome).setOnClickListener { loadHome(true) }
        findViewById<View>(R.id.btnRetrySearch).setOnClickListener { doSearch() }
        searchBtn.setOnClickListener { doSearch() }

        /* 🔄 রিলোড — হোম ট্যাবে হোম নতুন করে লোড হবে, অন্য ট্যাবে সেই লিস্ট */
        findViewById<View>(R.id.btnReload).setOnClickListener {
            Toast.makeText(this, "🔄 রিফ্রেশ করা হচ্ছে…", Toast.LENGTH_SHORT).show()
            when (tab) {
                R.id.navHome -> { homeLoaded = false; loadHome(true) }
                R.id.navSearch -> doSearch()
                else -> load(true)
            }
        }

        /* ✈️ টেলিগ্রাম চ্যানেল — হোম টুলবারে চোখে পড়ে এমন জায়গায় */
        findViewById<View>(R.id.btnTgIcon).setOnClickListener {
            if (Config.TELEGRAM_URL.isBlank()) return@setOnClickListener
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.TELEGRAM_URL)))
                Toast.makeText(this, "✈️ টেলিগ্রাম চ্যানেল খুলছে…", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
        }
        /* 🔞 18+ Adult — লুকানো এন্ট্রি: হোম টুলবারের ছোট 🔒 আইকন।
           প্রথমে ১৮+ পারমিশন চাইবে, "হ্যাঁ" দিলে তবেই Adult পেজ খুলবে */
        findViewById<View>(R.id.btnAdult).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("🔞 18+ Adult Content")
                .setMessage("এই সেকশনে শুধু ১৮ বছরের ঊর্ধ্বেদের জন্য কনটেন্ট আছে।\n\nআপনি কি ১৮ বছর বা তার বেশি বয়সী?")
                .setPositiveButton("হ্যাঁ, আমি ১৮+") { _, _ ->
                    startActivity(Intent(this, AdultActivity::class.java))
                }
                .setNegativeButton("না", null)
                .setCancelable(true)
                .show()
        }
        
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            tab = item.itemId
            when (item.itemId) {
                R.id.navHome -> { showPane(homePane); loadHome(false) }
                R.id.navSeries -> { currentLabel = "Web Series"; showPane(paneGrid); load(true) }
                R.id.navMovies -> { currentLabel = null; showPane(paneGrid); load(true) }
                R.id.navSearch -> showPane(searchPane)
            }
            true
        }
        showPane(homePane)

        askNotificationPermission()
        scheduleUploadCheck()
        loadHome(true)

        /* 📊 অ্যাডমিন প্যানেল: অ্যাপ-ওপেন স্ট্যাট + নতুন আপডেট ব্যানার */
        Stats.appOpen(this)
        checkAppUpdate()
    }

    /* ── 🚀 অ্যাপ আপডেট — পপআপ + ব্যানার + নোটিফিকেশন (প্রতি ওপেনে চেক) ──
       v2.6: আপডেট থাকলে হোম খুললেই ক্লোজ বাটনসহ পপআপ আসবে (পুরো নোটস দেখাবে),
       আর নোটিফিকেশনও সাথে সাথে যাবে (প্রতি ভার্সনে একবার)। */
    private fun checkAppUpdate() {
        Thread {
            val info = Updater.fetch() ?: return@Thread
            if (!Updater.isNewer(info)) return@Thread

            /* 🔔 নোটিফিকেশন: পারমিশন থাকলে সাথে সাথে (প্রতি ভার্সনে একবার) */
            if (Updater.hasPermission(this) && !Updater.wasNotified(this, info)) {
                if (Updater.postUpdateNotification(this, info)) {
                    Updater.markNotified(this, info)
                }
            }

            runOnUiThread {
                /* ① হোমের উপরে ব্যানার */
                val banner = findViewById<View>(R.id.updateBanner) ?: return@runOnUiThread
                banner.visibility = View.VISIBLE
                findViewById<TextView>(R.id.updateText).text =
                    "🚀 নতুন ভার্সন ${info.name} এসেছে — ট্যাপ করে আপডেট করো"
                banner.setOnClickListener {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                    } catch (_: Exception) {}
                }

                /* ② 🪟 পপআপ — ক্লোজ বাটনসহ; আপডেট না করা পর্যন্ত প্রতিবার অ্যাপ খুললেই আসবে */
                val msg = if (info.msg.isNotBlank()) info.msg
                else "নতুন ভার্সন ${info.name} এসেছে — আপডেট করে সবচেয়ে ভালো অভিজ্ঞতা নাও।"
                MaterialAlertDialogBuilder(this)
                    .setTitle("🚀 নতুন ভার্সন ${info.name} এসেছে!")
                    .setMessage(msg)
                    .setPositiveButton("⬇️ আপডেট করো") { _, _ ->
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                        } catch (_: Exception) {}
                    }
                    .setNegativeButton("পরে", null)
                    .setCancelable(true)
                    .show()
            }
        }.start()
    }

    /* 🔔 ইউজার নোটিফিকেশন পারমিশন Allow করলেই আপডেট-চেক নতুন করে —
       নাহলে প্রথম ওপেনে (পারমিশন এখনো না থাকায়) নোটিফিকেশন মিস হয়ে যেত (v2.5-এর বাগ) */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            checkAppUpdate()
        }
    }

    private fun showPane(pane: View) {
        homePane.visibility = if (pane === homePane) View.VISIBLE else View.GONE
        paneGrid.visibility = if (pane === paneGrid) View.VISIBLE else View.GONE
        searchPane.visibility = if (pane === searchPane) View.VISIBLE else View.GONE
        if (pane === paneGrid) {
            chipsWrap.visibility =
                if (tab == R.id.navSeries) View.GONE else View.VISIBLE
        }
    }

    private fun openMovie(m: MovieItem) {
        Ads.onMovieOpened(this) /* 💰 পপআন্ডার (প্রতি ২ বারে) */
        startActivity(DetailActivity.intent(this, m))
    }

    /* ══════════ 🏠 হোম (হিরো + রো) ══════════ */

    private fun loadHome(force: Boolean) {
        if (homeLoaded && !force) return
        homeSpinner.visibility = View.VISIBLE
        homeEmpty.visibility = View.GONE
        Thread {
            try {
                val p1 = BloggerApi.load(null, null, 1)
                val p2 = try { BloggerApi.load(null, null, 1 + p1.items.size) } catch (_: Exception) { FeedPage(emptyList(), 0) }
                val all = ArrayList(p1.items)
                all.addAll(p2.items)
                all.removeAll { BloggerApi.isAdult(it) }   /* 🔞 এডাল্ট হোমে আসবে না (এখন p2-এরটাও বাদ পড়বে) */

                /* 🏷️ English / Horror / Korean — সাম্প্রতিক পোস্টে কম থাকে, তাই নিজের লেবেল-ফিড
                   থেকে সর্বশেষ ১০টা করে আনি → হিন্দি/বাংলার মতোই সেকশন ভরা দেখাবে (৩ থ্রেডে দ্রুত) */
                var english: List<MovieItem> = emptyList()
                var horror: List<MovieItem> = emptyList()
                var korean: List<MovieItem> = emptyList()
                val tEn = Thread { english = labelFeed("English") }
                val tHo = Thread { horror = labelFeed("Horror") }
                val tKo = Thread { korean = labelFeed("Korean") }
                tEn.start(); tHo.start(); tKo.start()
                tEn.join(8000); tHo.join(8000); tKo.join(8000)

                val heroes = all.take(4)   /* 🎬 হিরো স্লাইডার — সর্বশেষ ৪টা */

                val series = all.filter { BloggerApi.isWebSeries(it.title, it.labels) }
                val hindi = all.filter { !BloggerApi.isWebSeries(it.title, it.labels) &&
                        it.labels.any { l -> l.equals("Hindi", true) || l.equals("Hindi Dubbed", true) } }
                val bangla = all.filter { !BloggerApi.isWebSeries(it.title, it.labels) &&
                        it.labels.any { l -> Config.BANGLA_LABELS.any { b -> l.equals(b, true) } } }
                val dub = all.filter { it.labels.any { l -> Config.BANGLA_DUB_LABELS.any { b -> l.equals(b, true) } } }

                val sections = ArrayList<Pair<String, List<MovieItem>>>()
                sections.add("🔥 নতুন আপলোড" to all.take(10))
                if (series.isNotEmpty()) sections.add("📺 ওয়েব সিরিজ" to series.take(10))
                if (dub.isNotEmpty()) sections.add("🎙️ বাংলা ডাব" to dub.take(10))
                if (hindi.isNotEmpty()) sections.add("🇮🇳 হিন্দি মুভি" to hindi.take(10))
                if (bangla.isNotEmpty()) sections.add("🇧🇩 বাংলা মুভি" to bangla.take(10))
                if (english.isNotEmpty()) sections.add("🇬🇧 ইংলিশ মুভি" to english.take(10))
                if (horror.isNotEmpty()) sections.add("👻 হরর মুভি" to horror.take(10))
                if (korean.isNotEmpty()) sections.add("🇰🇷 কোরিয়ান মুভি" to korean.take(10))

                runOnUiThread {
                    homeSpinner.visibility = View.GONE
                    homeLoaded = true
                    homeAdapter?.pauseHero()
                    homeAdapter = HomeAdapter(heroes, sections) { openMovie(it) }
                    homeList.adapter = homeAdapter
                    if (all.isEmpty()) homeEmpty.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    homeSpinner.visibility = View.GONE
                    if (!homeLoaded) homeEmpty.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    /** 🏷️ এক লেবেলের সর্বশেষ পোস্টগুলা (হোম সেকশনের জন্য) — এডাল্ট বাদ */
    private fun labelFeed(label: String): List<MovieItem> = try {
        BloggerApi.load(label, null, 1).items.filter { !BloggerApi.isAdult(it) }
    } catch (_: Exception) {
        emptyList()
    }

    /* ══════════ 🎬 মুভি / 📺 ওয়েব সিরিজ গ্রিড ══════════ */

    private fun buildChips() {
        chips.removeAllViews()
        val names = ArrayList<String>()
        if (tab == R.id.navSeries) {
            names.add("📺 ওয়েব সিরিজ")
            for (l in Config.WEB_SERIES_LABELS) if (!names.contains(l)) names.add(l)
        } else {
            names.add("🔥 সব")
            for (l in Config.CATEGORY_LABELS) {
                /* 🇧🇩 ব্লগে লেবেল "Bengali" — অ্যাপে দেখাবে "বাংলা" ট্যাব হিসেবে */
                val shown = if (Config.BANGLA_LABELS.any { b -> l.equals(b, true) })
                    Config.BANGLA_TAB_NAME else l
                if (!names.contains(shown)) names.add(shown)
            }
        }
        for (name in names) {
            val chip = Chip(this)
            chip.text = name
            chip.isCheckable = true
            val isAll = name == "🔥 সব" || name == "📺 ওয়েব সিরিজ"
            /* ট্যাবের দেখার নাম → ব্লগের আসল লেবেল (বাংলা → Bengali) */
            val query = when {
                isAll -> if (tab == R.id.navSeries) "Web Series" else null
                name == Config.BANGLA_TAB_NAME -> Config.BANGLA_MAIN_LABEL
                else -> name
            }
            chip.isChecked = when {
                tab == R.id.navSeries -> name == "📺 ওয়েব সিরিজ" && currentLabel == "Web Series"
                isAll -> currentLabel == null
                else -> query == currentLabel
            }
            chip.setOnClickListener {
                currentLabel = query
                buildChips()
                load(true)
            }
            chips.addView(chip)
        }
    }

    private fun load(reset: Boolean) {
        if (loading || tab == R.id.navHome) return
        if (reset) {
            items.clear()
            adapter.notifyDataSetChanged()
            nextIndex = 1
            endReached = false
            buildChips()
        }
        loading = true
        spinner.visibility = View.VISIBLE
        emptyBox.visibility = View.GONE

        val label = currentLabel
        val start = nextIndex
        Thread {
            try {
                val page = BloggerApi.load(label, null, start)
                runOnUiThread {
                    loading = false
                    spinner.visibility = View.GONE
                    val fetched = page.items.size
                    val fresh = page.items.filter { !BloggerApi.isAdult(it) } /* 🔞 এডাল্ট বাদ */
                    items.addAll(fresh)
                    nextIndex = start + fetched          /* আসল সংখ্যা — নাহলে পেজ স্কিপ হবে */
                    if (fetched < 20) endReached = true
                    adapter.notifyDataSetChanged()
                    if (items.isEmpty()) {
                        emptyBox.visibility = View.VISIBLE
                        emptyText.text = "😕 কোনো মুভি লোড হয়নি"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loading = false
                    spinner.visibility = View.GONE
                    if (items.isEmpty()) {
                        emptyBox.visibility = View.VISIBLE
                        emptyText.text = "📡 ইন্টারনেট সমস্যা — আবার চেষ্টা করো"
                    } else {
                        Toast.makeText(this, "লোডিং ব্যর্থ — আবার চেষ্টা করো", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }

    private fun loadMore() {
        if (loading || endReached || items.isEmpty() || tab == R.id.navHome) return
        load(false)
    }

    /* ══════════ 🔍 সার্চ ══════════ */

    private fun doSearch() {
        val q = searchInput.text?.toString()?.trim().orEmpty()
        if (q.isEmpty()) {
            Toast.makeText(this, "🔎 কী খুঁজবে লিখো", Toast.LENGTH_SHORT).show()
            return
        }
        searchSpinner.visibility = View.VISIBLE
        searchEmpty.visibility = View.GONE
        findViewById<View>(R.id.btnRetrySearch).visibility = View.GONE
        Thread {
            try {
                val page = BloggerApi.load(null, q, 1)
                runOnUiThread {
                    searchSpinner.visibility = View.GONE
                    searchItems.clear()
                    searchItems.addAll(page.items.filter { !BloggerApi.isAdult(it) }) /* 🔞 এডাল্ট বাদ */
                    searchAdapter.notifyDataSetChanged()
                    if (searchItems.isEmpty()) searchEmpty.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    searchSpinner.visibility = View.GONE
                    searchEmpty.visibility = View.VISIBLE
                    findViewById<View>(R.id.btnRetrySearch).visibility = View.VISIBLE
                }
            }
        }.start()
    }

    /* ══════════ 🔔 নোটিফিকেশন ══════════ */

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101
            )
        }
    }

    private fun scheduleUploadCheck() {
        try {
            val wm = WorkManager.getInstance(this)
            wm.enqueueUniquePeriodicWork(
                "mbx_upload_check",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequest.Builder(UploadCheckWorker::class.java, 15, TimeUnit.MINUTES).build()
            )
            wm.enqueueUniqueWork(
                "mbx_upload_now",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequest.Builder(UploadCheckWorker::class.java).build()
            )
        } catch (_: Exception) {
        }
    }

    /* 💓 লাইভ হার্টবিট — অ্যাপ সামনে খোলা থাকলে ১ মিনিট পরপর সার্ভারে বিট যায়,
       তাতে প্যানেলের "🔴 এখন লাইভ" সংখ্যা সত্যিকারের লাইভ দেখায় */
    private val beatUi = Handler(Looper.getMainLooper())
    private val beatTick = object : Runnable {
        override fun run() {
            Stats.beat(this@MainActivity)
            beatUi.postDelayed(this, 60_000L)
        }
    }

    override fun onResume() {
        super.onResume()
        Stats.beat(this)
        beatUi.removeCallbacks(beatTick)
        beatUi.postDelayed(beatTick, 60_000L)
        homeAdapter?.resumeHero()          /* 🎬 হোমে ফিরলেই স্লাইডার চালু */
    }

    override fun onPause() {
        super.onPause()
        beatUi.removeCallbacks(beatTick)
        homeAdapter?.pauseHero()           /* 🎬 অ্যাপ না দেখলে স্লাইডার থামবে (ব্যাটারি বাঁচবে) */
    }
}
