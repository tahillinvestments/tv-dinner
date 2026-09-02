package com.troyh.tvdinner.data.model

data class MusicGenre(
    val id: String,
    val name: String,
    val icon: String
)

data class MusicArtist(
    val id: String,
    val artistName: String,
    val genre: String,
    val avatar: String,
    val subscribers: String = "YouTube Official Artist",
    val bio: String = "",
    val ytChannelId: String = ""
)

data class MusicVideo(
    val id: String,
    val title: String,
    val artistName: String,
    val videoId: String,
    val thumbnailUrl: String,
    val duration: String = "Official Music Video",
    val views: String = "Popular",
    val published: String = "Recent",
    val publishedTimestamp: Long = System.currentTimeMillis()
)
