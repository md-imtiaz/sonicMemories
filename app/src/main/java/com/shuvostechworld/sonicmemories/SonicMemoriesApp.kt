package com.shuvostechworld.sonicmemories

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SonicMemoriesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            android.util.Log.e("SonicMemoriesApp", "Firebase initialization failed", e)
        }
    }
}