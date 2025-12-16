package com.shuvostechworld.sonicmemories.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

data class FreesoundResponse(
    val count: Int,
    val results: List<SoundItem>
)

data class SoundItem(
    val id: Int,
    val name: String,
    val previews: Previews?
)

data class Previews(
    @SerializedName("preview-hq-mp3")
    val previewHqMp3: String,
    @SerializedName("preview-lq-mp3")
    val previewLqMp3: String
)

interface FreesoundApiService {
    @GET("search/text/?fields=id,name,previews")
    suspend fun searchSounds(
        @Query("query") query: String,
        @Query("token") token: String
    ): Response<FreesoundResponse>
}
