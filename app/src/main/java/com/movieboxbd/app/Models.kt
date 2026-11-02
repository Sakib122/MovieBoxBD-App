package com.movieboxbd.app

import java.io.Serializable

/** একটা মুভির সব তথ্য (Blogger পোস্ট থেকে) */
data class MovieItem(
    val title: String,
    val permalink: String,
    val poster: String?,
    val labels: List<String>,
    val date: String,
    val links: List<MediaLink>
) : Serializable

/**
 * পোস্টের ভেতরের একটা ফাইল — ওয়েবসাইটের আসল ডাটা:
 *  streamUrl → সরাসরি ভিডিও স্ট্রিম লিংক (▶ দেখো বাটন; খালি হলে বাটন লুকাবে)
 *  pageUrl   → ওয়েবসাইটের /watch/ পেজ (🌐 ব্রাউজার বাটন — ওখানে ডাউনলোড পেজও আছে)
 *  tgUrl     → টেলিগ্রাম বটের আসল ডাউনলোড লিংক (⬇ → t.me/bot?start=file_ID)
 */
data class MediaLink(
    val name: String,
    val streamUrl: String,
    val pageUrl: String = "",
    val tgUrl: String = ""
) : Serializable

/** Blogger feed-এর এক পেজ */
data class FeedPage(
    val items: List<MovieItem>,
    val total: Int
)
