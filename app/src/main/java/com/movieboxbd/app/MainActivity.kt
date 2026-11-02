package com.movieboxbd.app

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip

class MainActivity : AppCompatActivity() {

    private val items = ArrayList<MovieItem>()
    private lateinit var adapter: MovieAdapter
    private var currentLabel: String? = null
    private var query: String? = null
    private var nextIndex = 1
    private var loading = false
    private var endReached = false
    private val seenLabels = LinkedHashSet<String>()

    private lateinit var recycler: RecyclerView
    private lateinit var spinner: ProgressBar
    private lateinit var emptyBox: View
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recycler = findViewById(R.id.recycler)
        spinner = findViewById(R.id.spinner)
        emptyBox = findViewById(R.id.emptyBox)
        emptyText = findViewById(R.id.emptyText)

        adapter = MovieAdapter(items) { startActivity(DetailActivity.intent(this, it)) }
        recycler.layoutManager = GridLayoutManager(this, 2)
        recycler.adapter = adapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = rv.layoutManager as GridLayoutManager
                if (lm.findLastVisibleItemPosition() >= items.size - 4) loadMore()
            }
        })
        findViewById<View>(R.id.btnRetry).setOnClickListener { load(true) }

        setSupportActionBar(findViewById(R.id.toolbar))

        buildChips()
        load(true)
    }

    /* ── ক্যাটাগরি চিপস ──────────────────────────────────────── */

    private fun buildChips() {
        val group = findViewById<com.google.android.material.chip.ChipGroup>(R.id.chips)
        group.removeAllViews()
        val names = ArrayList<String>()
        names.add("🔥 সব")
        for (l in Config.CATEGORY_LABELS) if (!names.contains(l)) names.add(l)
        for (l in seenLabels) if (!names.contains(l) && names.size < 14) names.add(l)

        for (name in names) {
            val chip = Chip(this)
            chip.text = name
            chip.isCheckable = true
            chip.isChecked =
                (name == "🔥 সব" && currentLabel == null) || name == currentLabel
            chip.setOnClickListener {
                currentLabel = if (name == "🔥 সব") null else name
                buildChips()
                load(true)
            }
            group.addView(chip)
        }
    }

    /* ── লোডিং ───────────────────────────────────────────────── */

    private fun load(reset: Boolean) {
        if (loading) return
        if (reset) {
            items.clear()
            adapter.notifyDataSetChanged()
            nextIndex = 1
            endReached = false
        }
        loading = true
        spinner.visibility = View.VISIBLE
        emptyBox.visibility = View.GONE

        val label = currentLabel
        val q = query
        val start = nextIndex
        Thread {
            try {
                val page = BloggerApi.load(label, q, start)
                runOnUiThread {
                    loading = false
                    spinner.visibility = View.GONE
                    for (it in page.items) {
                        for (l in it.labels) seenLabels.add(l)
                    }
                    items.addAll(page.items)
                    nextIndex = start + page.items.size
                    if (page.items.size < 20) endReached = true
                    adapter.notifyDataSetChanged()
                    if (items.isEmpty()) {
                        emptyBox.visibility = View.VISIBLE
                        emptyText.text = if (query != null) "🔍 কিছু পাওয়া যায়নি"
                        else "😕 কোনো মুভি লোড হয়নি"
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
        if (loading || endReached || items.isEmpty()) return
        load(false)
    }

    /* ── সার্চ ───────────────────────────────────────────────── */

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val sv = menu.findItem(R.id.actionSearch)?.actionView as? SearchView ?: return true
        sv.queryHint = "মুভি খুঁজো..."
        sv.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(text: String?): Boolean {
                query = text?.trim()?.ifEmpty { null }
                load(true)
                sv.clearFocus()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean = false
        })
        return true
    }
}
