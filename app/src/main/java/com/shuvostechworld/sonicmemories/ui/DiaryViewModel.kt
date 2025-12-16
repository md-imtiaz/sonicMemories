package com.shuvostechworld.sonicmemories.ui

import android.content.Context
import android.media.audiofx.PresetReverb
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shuvostechworld.sonicmemories.data.model.DiaryEntry
import com.shuvostechworld.sonicmemories.data.remote.SoundItem
import com.shuvostechworld.sonicmemories.data.repository.AmbientSoundRepository
import com.shuvostechworld.sonicmemories.data.repository.DiaryRepository
import com.shuvostechworld.sonicmemories.utils.AudioPlayer
import com.shuvostechworld.sonicmemories.utils.AudioRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DiaryViewModel @Inject constructor(
    private val repository: DiaryRepository,
    private val audioRecorder: AudioRecorder,
    private val audioPlayer: AudioPlayer,
    private val ambientSoundRepository: AmbientSoundRepository,
    private val ambientSoundManager: com.shuvostechworld.sonicmemories.utils.AmbientSoundManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    enum class RecordingState {
        Idle, Recording, Paused
    }

    private val _recordingState = MutableStateFlow(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _currentPlayingId = MutableStateFlow<String?>(null)
    val currentPlayingId: StateFlow<String?> = _currentPlayingId.asStateFlow()

    private val _ambientSounds = MutableStateFlow<List<SoundItem>>(emptyList())
    val ambientSounds: StateFlow<List<SoundItem>> = _ambientSounds.asStateFlow()

    private var currentRecordingFile: File? = null

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedDate = MutableStateFlow<Long?>(null)
    val selectedDate: StateFlow<Long?> = _selectedDate.asStateFlow()

    private val _activeDates = MutableStateFlow<Set<Long>>(emptySet())
    val activeDates: StateFlow<Set<Long>> = _activeDates.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _allTags = MutableStateFlow<Set<String>>(emptySet())
    val allTags: StateFlow<Set<String>> = _allTags.asStateFlow()

    private val _flashbackEntry = MutableStateFlow<DiaryEntry?>(null)
    val flashbackEntry: StateFlow<DiaryEntry?> = _flashbackEntry.asStateFlow()

    init {
        loadEntries()
    }

    fun loadEntries() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                // Combine entries from repository with search query, selected date, AND selected tag
                combine(
                    repository.getAllEntries(),
                    _searchQuery,
                    _selectedDate,
                    _selectedTag
                ) { entries, query, date, tag ->
                    
                    // Update active dates
                    _activeDates.value = entries.map { normalizeDate(it.timestamp) }.toSet()
                    
                    // Update all available tags (aggregate from all entries)
                    _allTags.value = entries.flatMap { it.tags }.toSet()

                    // Check for Flashback (On This Day)
                    // Use clean calendar instances
                    val todayCal = java.util.Calendar.getInstance()
                    val todayMonth = todayCal.get(java.util.Calendar.MONTH)
                    val todayDay = todayCal.get(java.util.Calendar.DAY_OF_MONTH)
                    val todayYear = todayCal.get(java.util.Calendar.YEAR)
                    
                    val pCal = java.util.Calendar.getInstance()
                    
                    val flashback = entries.firstOrNull { entry ->
                        pCal.timeInMillis = entry.timestamp
                        val entryMonth = pCal.get(java.util.Calendar.MONTH)
                        val entryDay = pCal.get(java.util.Calendar.DAY_OF_MONTH)
                        val entryYear = pCal.get(java.util.Calendar.YEAR)
                        
                        val isSameDay = (entryMonth == todayMonth) && (entryDay == todayDay)
                        val isPastYear = (entryYear < todayYear)
                        
                        // Log for debugging (in Logcat)
                        if (isSameDay) {
                            android.util.Log.d("DiaryViewModel", "Match Found! Entry: ${entry.title}, Year: $entryYear vs $todayYear")
                        }
                        
                        isSameDay && isPastYear
                    }
                    
                    _flashbackEntry.value = flashback

                    var filtered = entries
                    
                    // Filter by Date
                    if (_activeDates.value.isNotEmpty() && _selectedDate.value != null) {
                        filtered = filtered.filter { 
                            com.shuvostechworld.sonicmemories.utils.DateUtils.isSameDay(it.timestamp, _selectedDate.value!!)
                        }
                    }
                    
                    // Filter by Tag
                    if (_selectedTag.value != null) {
                        if (_selectedTag.value == "📅 On This Day") {
                             // "On This Day" Filter Logic
                            val nowCal = java.util.Calendar.getInstance()
                            val nowMonth = nowCal.get(java.util.Calendar.MONTH)
                            val nowDay = nowCal.get(java.util.Calendar.DAY_OF_MONTH)
                            val nowYear = nowCal.get(java.util.Calendar.YEAR)
                            
                            val checkCal = java.util.Calendar.getInstance()
                            
                            filtered = filtered.filter { entry ->
                                checkCal.timeInMillis = entry.timestamp
                                val eMonth = checkCal.get(java.util.Calendar.MONTH)
                                val eDay = checkCal.get(java.util.Calendar.DAY_OF_MONTH)
                                val eYear = checkCal.get(java.util.Calendar.YEAR)
                                
                                (eMonth == nowMonth && eDay == nowDay && eYear < nowYear)
                            }
                        } else {
                            filtered = filtered.filter { it.tags.contains(_selectedTag.value) }
                        }
                    }

                    // Filter by Query
                    if (query.isNotBlank()) {
                        filtered = filtered.filter { 
                            it.title.contains(query, ignoreCase = true) || 
                            it.content.contains(query, ignoreCase = true) 
                        }
                    }
                    filtered
                }
                .collect { filteredEntries ->
                    _uiState.value = UiState.Success(filteredEntries)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to load entries: ${e.message}")
            }
        }
    }

    fun selectDate(timestamp: Long?) {
        _selectedDate.value = timestamp
    }
    
    fun selectTag(tag: String?) {
        _selectedTag.value = tag
    }

    private fun normalizeDate(timestamp: Long): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun startRecording() {
        try {
            val file = File(context.cacheDir, "temp_recording_${System.currentTimeMillis()}.m4a")
            currentRecordingFile = file
            audioRecorder.startRecording(context, file)
            _recordingState.value = RecordingState.Recording
        } catch (e: Exception) {
            currentRecordingFile = null
            _uiState.value = UiState.Error("Failed to start recording: ${e.message}")
            _recordingState.value = RecordingState.Idle
        }
    }

    fun pauseRecording() {
        audioRecorder.pauseRecording()
        _recordingState.value = RecordingState.Paused
    }

    fun resumeRecording() {
        audioRecorder.resumeRecording()
        _recordingState.value = RecordingState.Recording
    }

    fun stopRecording(): File? {
        try {
            audioRecorder.stopRecording()
            _recordingState.value = RecordingState.Idle
            val file = currentRecordingFile
            currentRecordingFile = null
            
            if (file != null && file.exists() && file.length() > 0) {
                 return file
            } else {
                 _uiState.value = UiState.Error("Recording failed: File is empty")
                 return null
            }
        } catch (e: Exception) {
            _uiState.value = UiState.Error("Error stopping recording: ${e.message}")
            return null
        }
    }

    fun saveFinalEntry(file: File, mood: Int, ambientUrl: String?, tags: List<String>?, lat: Double?, lng: Double?, address: String?) {
        // Ensure file path is stored with correct extension logic if needed, 
        // but here we just pass the file. Repository uploads it.
        val entry = DiaryEntry(
            title = "Audio Memory",
            content = "Recorded on ${java.util.Date()}",
            mood = mood,
            ambientSoundUrl = ambientUrl ?: "",
            tags = tags ?: emptyList(),
            latitude = lat,
            longitude = lng,
            locationAddress = address
        )
        saveEntry(entry, file)
    }

    fun saveEntry(entry: DiaryEntry, audioFile: File? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = if (audioFile != null && audioFile.exists()) Uri.fromFile(audioFile) else null
                repository.saveEntry(uri, entry).collect { result ->
                    if (result.isFailure) {
                        _uiState.value = UiState.Error("Failed to save: ${result.exceptionOrNull()?.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error saving entry: ${e.message}")
            }
        }
    }

    fun getEntryById(id: String): kotlinx.coroutines.flow.Flow<DiaryEntry?> {
        return repository.getEntryById(id)
    }

    fun getMaxAmplitude(): Int {
        return audioRecorder.getMaxAmplitude()
    }

    fun playMemory(entry: DiaryEntry) {
        if (_currentPlayingId.value == entry.id && audioPlayer.isPlaying()) {
            audioPlayer.stop()
            ambientSoundManager.stop()
            _currentPlayingId.value = null
        } else {
            // Stop previous if any
            if (_currentPlayingId.value != null) {
                audioPlayer.stop()
                ambientSoundManager.stop()
            }

            _currentPlayingId.value = entry.id
            
            // Play Ambient if exists
            if (entry.ambientSoundUrl.isNotEmpty()) {
                android.util.Log.d("DiaryViewModel", "Playing ambient: ${entry.ambientSoundUrl} for entry: ${entry.id}")
                ambientSoundManager.playLooping(entry.ambientSoundUrl)
            } else {
                android.util.Log.d("DiaryViewModel", "No ambient sound for entry: ${entry.id}")
            }

            // Play Main Audio
            try {
                audioPlayer.playFile(entry.audioUrl) {
                    // On Completion
                    _currentPlayingId.value = null
                    ambientSoundManager.stop()
                }
                
                // Reverb disabled to prevent Error 1 -22 on some devices
                // if (entry.ambientSoundUrl.isNotEmpty()) {
                //     audioPlayer.applyReverb(PresetReverb.PRESET_LARGEROOM)
                // }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Playback failed: ${e.message}")
                _currentPlayingId.value = null
                ambientSoundManager.stop()
            }
        }
    }

    fun deleteEntry(entry: DiaryEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteEntry(entry).collect {
                // Deletion handled by repository, UI updates via Firestore snapshot listener automatically
            }
        }
    }
    
    fun deleteAllEntries() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAllEntries().collect { result ->
                if (result.isFailure) {
                    _uiState.value = UiState.Error("Failed to delete all: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }
    
    fun restoreEntry(entry: DiaryEntry) {
        saveEntry(entry, null) // Audio might be gone if local file deleted, but if it was cloud URL, it persists? 
        // If entry has cloud URL, saveEntry handles it.
        // Ideally we shouldn't delete immediately but mark as deleted. 
        // For MVP, 'Undo' effectively re-saves the entry.
    }

    fun searchAmbientSounds(query: String) {
        viewModelScope.launch {
            val results = ambientSoundRepository.fetchSounds(query)
            if (!results.isNullOrEmpty()) {
                _ambientSounds.value = results
            } else {
                _uiState.value = UiState.Error("No ambient sounds found for '$query'")
                // Optional: Clear list or keep previous? Keeping previous might be better UX than clearing to empty.
            }
        }
    }

    fun exportMemoriesToJson(context: Context): File? {
        val entries = (_uiState.value as? UiState.Success)?.entries ?: return null
        if (entries.isEmpty()) return null

        try {
            val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
            val jsonString = gson.toJson(entries)
            
            val file = File(context.cacheDir, "sonic_memories_export.json")
            file.writeText(jsonString)
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopRecording()
        audioPlayer.stop()
        ambientSoundManager.stop()
    }
}
