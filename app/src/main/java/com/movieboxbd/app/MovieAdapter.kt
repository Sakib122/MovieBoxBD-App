package com.movieboxbd.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MovieAdapter(
    private val items: List<MovieItem>,
    /* 🔞 true দিলে এডাল্ট কার্ড ঝাপসা + ১৮+ ব্যাজ (AdultActivity ডিফল্ট false — ওখানে সব খোলা) */
    private val blurAdult: Boolean = false,
    private val onClick: (MovieItem) -> Unit
) : RecyclerView.Adapter<MovieAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val poster: ImageView = v.findViewById(R.id.poster)
        val title: TextView = v.findViewById(R.id.title)
        val meta: TextView = v.findViewById(R.id.meta)
        val scrim: View = v.findViewById(R.id.adultScrim)
        val badge: TextView = v.findViewById(R.id.adultBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_movie, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val m = items[pos]
        h.title.text = m.title
        h.meta.text = buildString {
            append(m.labels.take(2).joinToString(" • "))
            if (m.date.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(m.date)
            }
        }
        /* 🔞 এডাল্ট হলে পোস্টার ঝাপসা + লাল ১৮+ ব্যাজ (ওয়েবসাইটের মতো) */
        if (blurAdult && BloggerApi.isAdult(m)) {
            h.scrim.visibility = View.VISIBLE
            h.badge.visibility = View.VISIBLE
            ImageLoader.load(h.poster, m.poster, pixelate = true)
        } else {
            h.scrim.visibility = View.GONE
            h.badge.visibility = View.GONE
            ImageLoader.load(h.poster, m.poster)
        }
        h.itemView.setOnClickListener { onClick(m) }
    }
}
