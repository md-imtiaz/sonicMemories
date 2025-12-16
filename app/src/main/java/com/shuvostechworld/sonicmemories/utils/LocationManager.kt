package com.shuvostechworld.sonicmemories.utils

import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import com.google.android.gms.tasks.Task

@Singleton
class LocationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    suspend fun getCurrentLocation(): Location? {
        // checks permission first
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return null
            }
            
            return kotlinx.coroutines.withContext(Dispatchers.IO) {
                var finalLocation: Location? = null
                
                // 1. Try Last Known First (Fastest)
                try {
                     finalLocation = awaitTask(fusedLocationClient.lastLocation)
                } catch (e: Exception) {
                     // Ignore
                }
                
                if (finalLocation != null) return@withContext finalLocation

                // 2. If null, Try Fresh w/ High Accuracy
                try {
                    val cancellationTokenSource = com.google.android.gms.tasks.CancellationTokenSource()
                    finalLocation = awaitTask(
                         fusedLocationClient.getCurrentLocation(
                             com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                             cancellationTokenSource.token
                         )
                    )
                } catch (e: Exception) {
                    // Ignore
                }
                
                return@withContext finalLocation
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    // Helper to avoid dependency and extension issues
    private suspend fun <T> awaitTask(task: Task<T>): T? = suspendCancellableCoroutine { cont ->
        task.addOnSuccessListener { result ->
            cont.resume(result)
        }
        task.addOnFailureListener { e ->
            cont.resume(null) // Return null on failure for our use case
        }
    }

    suspend fun getAddressFromLocation(location: Location): String? {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    // API 33+ Async
                    return@withContext suspendCancellableCoroutine<String?> { cont ->
                        geocoder.getFromLocation(location.latitude, location.longitude, 1) { addresses ->
                            if (addresses.isNotEmpty()) {
                                cont.resume(addresses[0].getAddressLine(0))
                            } else {
                                cont.resume(null)
                            }
                        }
                    }
                } else {
                    // Legacy Synchronous
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        return@withContext addresses[0].getAddressLine(0)
                    } else {
                        return@withContext null
                    }
                }
            } catch (e: Exception) {
                 e.printStackTrace()
                 return@withContext null
            }
        }
    }
}
