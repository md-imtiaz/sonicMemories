package com.shuvostechworld.sonicmemories.data.repository

import android.net.Uri
import com.shuvostechworld.sonicmemories.data.model.DiaryEntry
import kotlinx.coroutines.flow.Flow

interface DiaryRepository {
    fun saveEntry(audioUri: Uri?, entry: DiaryEntry): Flow<Result<Boolean>>
    fun getAllEntries(): Flow<List<DiaryEntry>>
    fun deleteEntry(entry: DiaryEntry): Flow<Result<Boolean>>
    fun deleteAllEntries(): Flow<Result<Boolean>>
    fun getEntryById(id: String): Flow<DiaryEntry?>
}
