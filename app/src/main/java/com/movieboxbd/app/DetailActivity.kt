package com.movieboxbd.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DetailActivity : AppCompatActivity() {

    companion object {
        fun intent(ctx: Context, m: MovieItem): Intent {
            val i = Intent(ctx, DetailActivity::class.java)
            i.putExtra("item", m)
            return i
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val m = intent.getSerializableExtra("item") as? MovieItem ?: run { finish(); return }

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.dTitle).text = m.title
        findViewById<TextView>(R.id.dMeta).text = buildString {
            append(m.labels.joinToString(" • "))
            if (m.date.isNotBlank()) {
                if (isNotEmpty()) append("  |  ")
                append(m.date)
            }
        }
        ImageLoader.load(findViewById(R.id.dPoster), m.poster)

        /* টেলিগ্রাম বাটন — Config-এ লিংক দিলেই দেখাবে */
        val tg = findViewById<View>(R.id.btnTelegram)
        if (Config.TELEGRAM_URL.isBlank()) tg.visibility = View.GONE
        else tg.setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.TELEGRAM_URL))) }
            catch (_: Exception) {}
        }

        /* ওয়েবসাইটে বিস্তারিত */
        findViewById<View>(R.id.btnWeb).setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(m.permalink))) }
            catch (_: Exception) {}
        }

        /* ব্যানার অ্যাড (তোমার Adsterra কোড) */
        Ads.applyBanner(findViewById(R.id.adBanner))

        /* ফাইল লিস্ট */
        val list = findViewById<RecyclerView>(R.id.linkList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = LinkAdapter(m.links)
    }

    inner class LinkAdapter(private val links: List<MediaLink>) :
        RecyclerView.Adapter<LinkAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.linkName)
            val play: View = v.findViewById(R.id.btnPlay)
            val dl: View = v.findViewById(R.id.btnDownload)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_link, parent, false)
            return VH(v)
        }

        override fun getItemCount() = links.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val link = links[pos]
            h.name.text = if (links.size > 1) "পার্ট ${pos + 1} — ${link.name}" else link.name
            h.play.setOnClickListener {
                Ads.maybeFireDirectLink(this@DetailActivity)
                try {
                    val i = Intent(this@DetailActivity, PlayerActivity::class.java)
                    i.putExtra("url", link.streamUrl)
                    i.putExtra("title", link.name)
                    startActivity(i)
                } catch (_: Exception) {}
            }
            h.dl.setOnClickListener { download(link) }
        }
    }

    private fun download(link: MediaLink) {
        try {
            val name = link.name.ifBlank { "movie" }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(link.streamUrl))
                .setTitle(name)
                .setDescription("MovieBoxBD ডাউনলোড")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name + ".mkv")
            dm.enqueue(req)
            Toast.makeText(this, "📥 ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "ডাউনলোড শুরু করা যায়নি", Toast.LENGTH_SHORT).show()
        }
    }
}
