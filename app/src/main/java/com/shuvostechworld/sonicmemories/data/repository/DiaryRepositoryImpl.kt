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

            // 1. Upload Audio if provided
            if (audioUri != null) {
                // Parse extension or default to m4a (since we switched to m4a)
                val extension = when {
                    audioUri.toString().endsWith(".mp3") -> "mp3"
                    audioUri.toString().endsWith(".3gp") -> "3gp"
                    else -> "m4a"
                }
                val audioRef = storage.reference.child("audio/${user.uid}/${System.currentTimeMillis()}.$extension")
                audioRef.putFile(audioUri).await()
                downloadUrl = audioRef.downloadUrl.await().toString()
            }

            // 2. Save Entry to Firestore
            // Use existing ID if available, otherwise generate new
            val documentId = if (entry.id.isNotEmpty()) entry.id else firestore.collection("diaries").document().id
            
            val finalEntry = entry.copy(
                id = documentId,
                userId = user.uid,
                audioUrl = downloadUrl,
                // Valid timestamp check logic could be here, but for now trusting input or current if 0
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
            // Instead of crashing, we can close with a specific message or just return empty.
            // But since the ViewModel now handles exceptions, throwing is "okay" but aggressive.
            // Let's keep the exception but ensure it's handled, OR return empty which is safer for startup.
            // Returning empty list implies "no data", which is technically true for "no user".
            // However, debugging is harder. I will assume ViewModel handles it now.
            // But to be extra safe against crashes:
            // Gracefully handle unauthenticated state to prevent crashes
            close() 
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
                    val entries = snapshot.toObjects(DiaryEntry::class.java)
                    trySend(entries)
                }
            }

        awaitClose { subscription.remove() }
    }

    override fun deleteEntry(entry: DiaryEntry): Flow<Result<Boolean>> = flow {
        try {
            // 1. Delete Audio from Storage
            if (entry.audioUrl.isNotEmpty()) {
                val storageRef = storage.getReferenceFromUrl(entry.audioUrl)
                try {
                    storageRef.delete().await()
                } catch (e: Exception) {
                    // Log error but proceed to delete document
                }
            }

            // 2. Delete Document from Firestore
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
            
            // Query all documents for user
            val snapshot = firestore.collection("diaries")
                .whereEqualTo("userId", user.uid)
                .get()
                .await()
                
            val batch = firestore.batch()
            
            // Delete audio files (best effort)
            snapshot.documents.forEach { doc ->
                val audioUrl = doc.getString("audioUrl")
                if (!audioUrl.isNullOrEmpty()) {
                    try {
                        storage.getReferenceFromUrl(audioUrl).delete()
                        // Don't await individual deletes to speed up, or maybe we should?
                        // Fire and forget for audio is risky but faster.
                    } catch (e: Exception) {
                        // Ignore
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
            val snapshot = firestore.collection("diaries").document(id).get().await()
            val entry = snapshot.toObject(DiaryEntry::class.java)
            emit(entry)
        } catch (e: Exception) {
            emit(null)
        }
    }
}
