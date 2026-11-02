package com.movieboxbd.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 🏠 নেটফ্লিক্স স্টাইল হোম:
 *  — উপরে বড় হিরো কার্ড (সর্বশেষ আপলোড)
 *  — তারপর ক্যাটাগরি অনুযায়ী আড়াইড়াই রো: নতুন, ওয়েব সিরিজ, হিন্দি, বাংলা...
 */
class HomeAdapter(
    private val hero: MovieItem?,
    private val sections: List<Pair<String, List<MovieItem>>>,
    private val onClick: (MovieItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private inner class Row(
        val title: String,
        val items: List<MovieItem>
    ) {
        val id: String = title
    }

    private val rows: List<Row> = sections.map { Row(it.first, it.second) }

    class HeroVH(v: View) : RecyclerView.ViewHolder(v) {
        val img: ImageView = v.findViewById(R.id.heroImg)
        val title: TextView = v.findViewById(R.id.heroTitle)
        val meta: TextView = v.findViewById(R.id.heroMeta)
    }

    class SectionVH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.secTitle)
        val list: RecyclerView = v.findViewById(R.id.secList)
    }

    override fun getItemViewType(position: Int) = if (position == 0 && hero != null) 0 else 1

    override fun getItemCount(): Int = rows.size + (if (hero != null) 1 else 0)

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
        if (h is HeroVH && hero != null) {
            h.title.text = hero.title
            h.meta.text = buildString {
                append(hero.labels.take(2).joinToString(" • "))
                if (hero.date.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(hero.date)
                }
            }
            ImageLoader.load(h.img, hero.poster)
            h.itemView.setOnClickListener { onClick(hero) }
            return
        }
        if (h is SectionVH) {
            val row = rows[if (hero != null) pos - 1 else pos]
            h.title.text = row.title
            val inner = RowItemAdapter(row.items, onClick)
            h.list.layoutManager =
                LinearLayoutManager(h.list.context, LinearLayoutManager.HORIZONTAL, false)
            h.list.adapter = inner
        }
    }

    /** রো-এর ভেতরের ছোট পোস্টার লিস্ট */
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
