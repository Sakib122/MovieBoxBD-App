package com.movieboxbd.app

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

/**
 * 🔞 18+ Adult — শুধু "Adult" লেবেলের পোস্ট এখানে আসবে।
 * ঢোকার পথ একটাই: হোম টুলবারের 🔒 আইকন → ১৮+ পারমিশন ডায়ালগ।
 */
class AdultActivity : AppCompatActivity() {

    private val items = ArrayList<MovieItem>()
    private lateinit var adapter: MovieAdapter
    private lateinit var spinner: ProgressBar
    private lateinit var emptyBox: View
    private var nextIndex = 1
    private var loading = false
    private var endReached = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adult)

        findViewById<MaterialToolbar>(R.id.adultToolbar).setNavigationOnClickListener { finish() }

        val recycler = findViewById<RecyclerView>(R.id.adultGrid)
        spinner = findViewById(R.id.adultSpinner)
        emptyBox = findViewById(R.id.adultEmpty)
        adapter = MovieAdapter(items) { m -> startActivity(DetailActivity.intent(this, m)) }
        recycler.layoutManager = GridLayoutManager(this, 2)
        recycler.adapter = adapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = rv.layoutManager as GridLayoutManager
                if (lm.findLastVisibleItemPosition() >= items.size - 4) loadMore()
            }
        })
        findViewById<View>(R.id.btnAdultRetry).setOnClickListener { load(true) }
        load(true)
    }

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
        findViewById<View>(R.id.btnAdultRetry).visibility = View.GONE

        val start = nextIndex
        Thread {
            try {
                val page = BloggerApi.load(Config.ADULT_LABEL, null, start)
                runOnUiThread {
                    loading = false
                    spinner.visibility = View.GONE
                    items.addAll(page.items)
                    nextIndex = start + page.items.size
                    if (page.items.size < 20) endReached = true
                    adapter.notifyDataSetChanged()
                    if (items.isEmpty()) emptyBox.visibility = View.VISIBLE
                }
            } catch (_: Exception) {
                runOnUiThread {
                    loading = false
                    spinner.visibility = View.GONE
                    if (items.isEmpty()) {
                        emptyBox.visibility = View.VISIBLE
                        findViewById<View>(R.id.btnAdultRetry).visibility = View.VISIBLE
                    }
                }
            }
        }.start()
    }

    private fun loadMore() {
        if (loading || endReached || items.isEmpty()) return
        load(false)
    }
}
