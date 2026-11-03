package com.movieboxbd.app

import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

/**
 * 🏠 নেটফ্লিক্স স্টাইল হোম:
 *  — উপরে হিরো স্লাইডার: সর্বশেষ ৪টা আপলোড, ৩.৫ সেকেন্ড পরপর অটো-স্লাইড,
 *    মাঝখানে ▶ প্লে বাটন, নিচে ডট ইন্ডিকেটর — হাতে টেনেও স্লাইড করা যায়
 *  — তারপর ক্যাটাগরি অনুযায়ী আড়াআড়ি রো: নতুন, ওয়েব সিরিজ, হিন্দি, বাংলা,
 *    ইংলিশ, হরর, কোরিয়ান...
 */
class HomeAdapter(
    private val heroes: List<MovieItem>,
    private val sections: List<Pair<String, List<MovieItem>>>,
    private val onClick: (MovieItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private inner class Row(val title: String, val items: List<MovieItem>)

    private val rows: List<Row> = sections.map { Row(it.first, it.second) }

    /* ── 🎬 হিরো অটো-স্লাইড ── */
    private val heroHandler = Handler(Looper.getMainLooper())
    private var autoRunnable: Runnable? = null
    private var heroHolder: HeroVH? = null

    inner class HeroVH(v: View) : RecyclerView.ViewHolder(v) {
        val pager: ViewPager2 = v.findViewById(R.id.heroPager)
        val dots: LinearLayout = v.findViewById(R.id.heroDots)
        val pageCb = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                paintDots(dots, position)
            }
        }
    }

    class SectionVH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.secTitle)
        val list: RecyclerView = v.findViewById(R.id.secList)
    }

    override fun getItemViewType(position: Int) = if (position == 0 && heroes.isNotEmpty()) 0 else 1

    override fun getItemCount(): Int = rows.size + (if (heroes.isNotEmpty()) 1 else 0)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 0) {
            HeroVH(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_hero, parent, false)
            )
        } else {
            SectionVH(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_section, parent, false)
            )
        }
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
        if (h is HeroVH && heroes.isNotEmpty()) {
            h.pager.adapter = HeroPagerAdapter(heroes, onClick)
            buildDots(h)
            h.pager.unregisterOnPageChangeCallback(h.pageCb) /* দুবার রেজিস্টার যেন না হয় */
            h.pager.registerOnPageChangeCallback(h.pageCb)
            heroHolder = h
            resumeHero()
            return
        }
        if (h is SectionVH) {
            val row = rows[if (heroes.isNotEmpty()) pos - 1 else pos]
            h.title.text = row.title
            h.list.layoutManager =
                LinearLayoutManager(h.list.context, LinearLayoutManager.HORIZONTAL, false)
            h.list.adapter = RowItemAdapter(row.items, onClick)
        }
    }

    override fun onViewRecycled(h: RecyclerView.ViewHolder) {
        super.onViewRecycled(h)
        if (h is HeroVH && heroHolder === h) {
            pauseHero()
            h.pager.adapter = null
            heroHolder = null
        }
    }

    /* ── 🎬 ৩.৫ সেকেন্ড পরপর পরের স্লাইডে যাওয়া ── */
    fun resumeHero() {
        if (heroes.size < 2 || heroHolder == null) return
        pauseHero()
        val r = object : Runnable {
            override fun run() {
                val h = heroHolder ?: return
                if (heroes.isEmpty()) return
                h.pager.currentItem = (h.pager.currentItem + 1) % heroes.size
                heroHandler.postDelayed(this, 3500L)
            }
        }
        autoRunnable = r
        heroHandler.postDelayed(r, 3500L)
    }

    fun pauseHero() {
        autoRunnable?.let { heroHandler.removeCallbacks(it) }
        autoRunnable = null
    }

    /* ── নিচের সাদা গোল ডটগুলা ── */
    private fun buildDots(h: HeroVH) {
        h.dots.removeAllViews()
        h.dots.visibility = if (heroes.size < 2) View.GONE else View.VISIBLE
        val d = h.dots.context.resources.displayMetrics.density
        for (i in heroes.indices) {
            val v = View(h.dots.context)
            val bg = GradientDrawable()
            bg.shape = GradientDrawable.OVAL
            bg.setColor(if (i == 0) DOT_ON else DOT_OFF)
            v.background = bg
            val lp = LinearLayout.LayoutParams((7 * d).toInt(), (7 * d).toInt())
            lp.marginEnd = (5 * d).toInt()
            h.dots.addView(v, lp)
        }
    }

    private fun paintDots(dots: LinearLayout, active: Int) {
        for (i in 0 until dots.childCount) {
            (dots.getChildAt(i).background as? GradientDrawable)
                ?.setColor(if (i == active) DOT_ON else DOT_OFF)
        }
    }

    companion object {
        private val DOT_ON = 0xFFFFFFFF.toInt()
        private val DOT_OFF = 0x66FFFFFF.toInt()
    }

    /* ── 🎬 হিরোর এক একটা স্লাইড ── */
    private class HeroPagerAdapter(
        private val heroes: List<MovieItem>,
        private val onClick: (MovieItem) -> Unit
    ) : RecyclerView.Adapter<HeroPagerAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.heroImg)
            val title: TextView = v.findViewById(R.id.heroTitle)
            val meta: TextView = v.findViewById(R.id.heroMeta)
            val play: View = v.findViewById(R.id.heroPlay)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context)
                .inflate(R.layout.item_hero_page, parent, false))

        override fun getItemCount() = heroes.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val m = heroes[pos]
            h.title.text = m.title
            h.meta.text = buildString {
                append(m.labels.take(2).joinToString(" • "))
                if (m.date.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(m.date)
                }
            }
            ImageLoader.load(h.img, m.poster)
            val open = View.OnClickListener { onClick(m) }
            h.itemView.setOnClickListener(open)
            h.play.setOnClickListener(open)
        }
    }

    /* ── রো-এর ভেতরের ছোট পোস্টার লিস্ট ── */
    private inner class RowItemAdapter(
        private val items: List<MovieItem>,
        private val onClick: (MovieItem) -> Unit
    ) : RecyclerView.Adapter<RowItemAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.smallImg)
            val name: TextView = v.findViewById(R.id.smallName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_movie_small, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val m = items[pos]
            h.name.text = m.title
            ImageLoader.load(h.img, m.poster)
            h.itemView.setOnClickListener { onClick(m) }
        }
    }
}
