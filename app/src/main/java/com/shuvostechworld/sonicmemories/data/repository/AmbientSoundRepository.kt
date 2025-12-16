package com.shuvostechworld.sonicmemories.data.repository

import com.shuvostechworld.sonicmemories.data.remote.FreesoundApiService
import com.shuvostechworld.sonicmemories.data.remote.SoundItem
import javax.inject.Inject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AmbientSoundRepository @Inject constructor() {

    private val api: FreesoundApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://freesound.org/apiv2/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FreesoundApiService::class.java)
    }

    // TODO: Move this to local.properties or Secrets.kt
    private val API_KEY = "AQWkpxVRwUr7bFki6atph6sgZGKtWaTGLqYOchyZ"

    suspend fun fetchSounds(query: String): List<SoundItem>? {
        return try {
            val response = api.searchSounds(query = query, token = API_KEY)
            if (response.isSuccessful) {
                val results = response.body()?.results
                android.util.Log.d("AmbientRepo", "Search '$query' success. Found ${results?.size ?: 0} items.")
                results
            } else {
                android.util.Log.e("AmbientRepo", "Search failed: Code ${response.code()}, Msg: ${response.message()}, Body: ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("AmbientRepo", "Search Exception", e)
            e.printStackTrace()
            null
        }
    }
}
