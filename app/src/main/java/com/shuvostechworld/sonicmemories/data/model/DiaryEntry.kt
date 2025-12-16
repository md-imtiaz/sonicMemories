package com.shuvostechworld.sonicmemories.data.model

data class DiaryEntry(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val audioUrl: String = "",
    val ambientSoundUrl: String = "",
    val mood: Int = 0,
    val timestamp: Long = 0L,
    val synced: Boolean = true,
    val tags: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAddress: String? = null
)
