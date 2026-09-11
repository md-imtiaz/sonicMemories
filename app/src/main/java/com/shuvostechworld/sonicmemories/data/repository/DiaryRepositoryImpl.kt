package com.shuvostechworld.sonicmemories.data.repository

import android.net.Uri
import com.shuvostechworld.sonicmemories.data.model.DiaryEntry
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaryRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) : DiaryRepository {

    override fun saveEntry(audioUri: Uri?, entry: DiaryEntry): Flow<Result<Boolean>> = flow {
        try {
            val user = auth.currentUser
            if (user == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }

            var downloadUrl = entry.audioUrl

            
            if (audioUri != null) {
                
                val extension = when {
                    audioUri.toString().endsWith(".mp3") -> "mp3"
                    audioUri.toString().endsWith(".3gp") -> "3gp"
                    else -> "m4a"
                }
                val audioRef = storage.reference.child("audio/${user.uid}/${System.currentTimeMillis()}.$extension")
                audioRef.putFile(audioUri).await()
                downloadUrl = audioRef.downloadUrl.await().toString()
            }

            
            
            val documentId = if (entry.id.isNotEmpty()) entry.id else firestore.collection("diaries").document().id
            
            val finalEntry = entry.copy(
                id = documentId,
                userId = user.uid,
                audioUrl = downloadUrl,
                
                timestamp = if (entry.timestamp == 0L) System.currentTimeMillis() else entry.timestamp
            )

            firestore.collection("diaries")
                .document(finalEntry.id)
                .set(finalEntry)
                .await()

            emit(Result.success(true))

        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun getAllEntries(): Flow<List<DiaryEntry>> = callbackFlow {
        val user = auth.currentUser
        if (user == null) {
            close(Exception("User not authenticated"))
            return@callbackFlow
        }

        val subscription = firestore.collection("diaries")
            .whereEqualTo("userId", user.uid)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    try {
                        val entries = snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toObject(DiaryEntry::class.java)?.apply {
                                    if (this.id.isEmpty()) this.id = doc.id
                                }
                            } catch (e: Exception) {
                                try {
                                    DiaryEntry(
                                        id = doc.id,
                                        userId = doc.getString("userId") ?: "",
                                        title = doc.getString("title") ?: "",
                                        content = doc.getString("content") ?: "",
                                        audioUrl = doc.getString("audioUrl") ?: "",
                                        ambientSoundUrl = doc.getString("ambientSoundUrl") ?: "",
                                        mood = doc.getLong("mood")?.toInt() ?: 0,
                                        timestamp = doc.getLong("timestamp") ?: 0L,
                                        synced = doc.getBoolean("synced") ?: true,
                                        tags = (doc.get("tags") as? List<String>) ?: listOf(),
                                        latitude = doc.getDouble("latitude"),
                                        longitude = doc.getDouble("longitude"),
                                        locationAddress = doc.getString("locationAddress")
                                    )
                                } catch (innerE: Exception) {
                                    null
                                }
                            }
                        }
                        trySend(entries)
                    } catch (e: Exception) {
                        
                        
                        close(e)
                    }
                }
            }

        awaitClose { subscription.remove() }
    }

    override fun deleteEntry(entry: DiaryEntry): Flow<Result<Boolean>> = flow {
        try {
            
            if (entry.audioUrl.isNotEmpty()) {
                val storageRef = storage.getReferenceFromUrl(entry.audioUrl)
                try {
                    storageRef.delete().await()
                } catch (e: Exception) {
                    
                }
            }

            
            firestore.collection("diaries").document(entry.id).delete().await()
            emit(Result.success(true))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }


    override fun deleteAllEntries(): Flow<Result<Boolean>> = flow {
        try {
            val user = auth.currentUser
            if (user == null) {
                emit(Result.failure(Exception("User not authenticated")))
                return@flow
            }
            
            
            val snapshot = firestore.collection("diaries")
                .whereEqualTo("userId", user.uid)
                .get()
                .await()
                
            val batch = firestore.batch()
            
            
            snapshot.documents.forEach { doc ->
                val audioUrl = doc.getString("audioUrl")
                if (!audioUrl.isNullOrEmpty()) {
                    try {
                        storage.getReferenceFromUrl(audioUrl).delete()
                        
                        
                    } catch (e: Exception) {
                        
                    }
                }
                batch.delete(doc.reference)
            }
            
            batch.commit().await()
            emit(Result.success(true))
            
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    override fun getEntryById(id: String): Flow<DiaryEntry?> = flow {
        try {
            val doc = firestore.collection("diaries").document(id).get().await()
            val entry = try {
                doc.toObject(DiaryEntry::class.java)?.apply {
                    if (this.id.isEmpty()) this.id = doc.id
                }
            } catch (e: Exception) {
                try {
                    DiaryEntry(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        title = doc.getString("title") ?: "",
                        content = doc.getString("content") ?: "",
                        audioUrl = doc.getString("audioUrl") ?: "",
                        ambientSoundUrl = doc.getString("ambientSoundUrl") ?: "",
                        mood = doc.getLong("mood")?.toInt() ?: 0,
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        synced = doc.getBoolean("synced") ?: true,
                        tags = (doc.get("tags") as? List<String>) ?: listOf(),
                        latitude = doc.getDouble("latitude"),
                        longitude = doc.getDouble("longitude"),
                        locationAddress = doc.getString("locationAddress")
                    )
                } catch (inner: Exception) {
                    null
                }
            }
            emit(entry)
        } catch (e: Exception) {
            emit(null)
        }
    }
}
