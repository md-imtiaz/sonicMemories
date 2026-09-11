package com.shuvostechworld.sonicmemories.data.model

data class DiaryEntry(
    var id: String = "",
    var userId: String = "",
    var title: String = "",
    var content: String = "",
    var audioUrl: String = "",
    var ambientSoundUrl: String = "",
    var mood: Int = 0,
    var timestamp: Long = 0L,
    var synced: Boolean = true,
    var tags: List<String> = listOf(),
    var latitude: Double? = null,
    var longitude: Double? = null,
    var locationAddress: String? = null
)
