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

/** পোস্টের ভেতরের একটা ফাইল — স্ট্রিম + ডাউনলোড একই লিংক (সার্ভার attachment পাঠায়) */
data class MediaLink(
    val name: String,
    val streamUrl: String
) : Serializable

/** Blogger feed-এর এক পেজ */
data class FeedPage(
    val items: List<MovieItem>,
    val total: Int
)
