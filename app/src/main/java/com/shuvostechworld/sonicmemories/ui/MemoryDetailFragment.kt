package com.shuvostechworld.sonicmemories.ui
import androidx.navigation.fragment.findNavController

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shuvostechworld.sonicmemories.data.model.DiaryEntry
import com.shuvostechworld.sonicmemories.databinding.FragmentMemoryDetailBinding
import com.shuvostechworld.sonicmemories.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.Intent

@AndroidEntryPoint
class MemoryDetailFragment : Fragment() {

    private var _binding: FragmentMemoryDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DiaryViewModel by activityViewModels()
    
    // If null, we are in Create Mode. If set, Edit Mode.
    private var currentEntryId: String? = null
    private var currentEntry: DiaryEntry? = null
    
    // Recording
    private var recordedFile: java.io.File? = null
    private var isRecording = false
    private val selectedTags = mutableListOf<String>()

    private val requestRecordPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                viewModel.startRecording()
            } else {
                Toast.makeText(context, "Recording permission required", Toast.LENGTH_SHORT).show()
            }
        }

    companion object {
        const val ARG_ENTRY_ID = "entry_id"

        fun newInstance(entryId: String?): MemoryDetailFragment {
            val fragment = MemoryDetailFragment()
            val args = Bundle()
            if (entryId != null) {
                args.putString(ARG_ENTRY_ID, entryId)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentEntryId = arguments?.getString(ARG_ENTRY_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMemoryDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initUi()
        observeData()
    }

    private fun initUi() {
        if (currentEntryId == null) {
            setupCreateMode()
        } else {
            // Data will be loaded in observeData
        }

        binding.fabSave.setOnClickListener {
            saveMemory()
        }

        binding.btnPlayPause.setOnClickListener {
            currentEntry?.let { entry ->
                viewModel.playMemory(entry)
            }
        }
        
        binding.btnRecord.setOnClickListener {
            toggleRecording()
        }

        binding.btnDictate.setOnClickListener {
            startDictation()
        }
        
        binding.btnAddTag.setOnClickListener {
            addTagFromInput()
        }
        
        binding.etTagInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                addTagFromInput()
                true
            } else {
                false
            }
        }
    }

    private fun addTagFromInput() {
        val tagText = binding.etTagInput.text.toString().trim()
        if (tagText.isNotEmpty()) {
            if (!selectedTags.contains(tagText)) {
                selectedTags.add(tagText)
                addChipToGroup(tagText)
                binding.etTagInput.setText("")
            } else {
                Toast.makeText(context, "Tag already exists", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addChipToGroup(tag: String) {
        val chip = com.google.android.material.chip.Chip(context)
        chip.text = tag
        chip.isCloseIconVisible = true
        chip.setOnCloseIconClickListener {
            binding.chipGroupTags.removeView(chip)
            selectedTags.remove(tag)
        }
        chip.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.black))
        chip.chipBackgroundColor = androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.teal_200)
        binding.chipGroupTags.addView(chip)
    }

    private var speechRecognizer: SpeechRecognizer? = null

    private fun startDictation() {
         if (androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            initSpeechRecognizer()
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            
            try {
                speechRecognizer?.startListening(intent)
                binding.btnDictate.text = "Listening..."
                binding.btnDictate.setIconResource(R.drawable.ic_mic_24) // pulse/change icon if needed
                binding.btnDictate.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.holo_red_light))
            } catch (e: Exception) {
                Toast.makeText(context, "Dictation failed to start", Toast.LENGTH_SHORT).show()
            }
        } else {
            requestRecordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun initSpeechRecognizer() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    resetDictateButton()
                }

                override fun onError(error: Int) {
                    resetDictateButton()
                    val msg = when(error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        else -> "Error: $error"
                    }
                    if (error != SpeechRecognizer.ERROR_NO_MATCH) { // no match is common if silent
                         Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        appendToContent(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    // Optional: Update UI with live text? For now just wait for final
                    // Implementing partials requires complex EditText handling to avoid duplicating.
                    // Let's stick to final results for stability.
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun resetDictateButton() {
        binding.btnDictate.text = "Fast Dictation"
        binding.btnDictate.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.zen_accent))
        binding.btnDictate.setIconResource(R.drawable.ic_mic_24)
    }

    private fun appendToContent(text: String) {
        val currentText = binding.etContent.text.toString()
        val newText = if (currentText.isNotEmpty()) "$currentText $text" else text
        binding.etContent.setText(newText)
        binding.etContent.setSelection(newText.length)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }

    private fun toggleRecording() {
        if (isRecording) {
            val file = viewModel.stopRecording()
            if (file != null) {
                recordedFile = file
                Toast.makeText(context, "Recording saved temporarily", Toast.LENGTH_SHORT).show()
                // Show player? maybe not until saved, but visualizer stops
            }
        } else {
            // Check permission
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.startRecording()
            } else {
                requestRecordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun setupCreateMode() {
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy, hh:mm a", Locale.getDefault())
        binding.tvDate.text = dateFormat.format(Date())
        binding.cardAudioPlayer.visibility = View.GONE
        binding.etTitle.setText("") // Ensure empty
        binding.etContent.setText("") // Ensure empty
        binding.etTitle.requestFocus()
    }


    
    private fun observeData() {
        // Load Entry if ID exists
        if (currentEntryId != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.getEntryById(currentEntryId!!).collect { entry ->
                        if (entry != null) {
                            currentEntry = entry
                            populateUi(entry)
                        } else {
                            // Entry not found or deleted
                            Toast.makeText(context, "Memory not found", Toast.LENGTH_SHORT).show()
                            Toast.makeText(context, "Memory not found", Toast.LENGTH_SHORT).show()
                            findNavController().popBackStack()
                        }
                    }
                }
            }
        }

        // Observe Playing State
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentPlayingId.collect { playingId ->
                    updatePlayerUi(playingId)
                }
            }
        }
        
        // Observe Recording State
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recordingState.collect { state ->
                    updateRecordingUi(state)
                }
            }
        }
    }
    
    private fun updateRecordingUi(state: DiaryViewModel.RecordingState) {
        when (state) {
            DiaryViewModel.RecordingState.Recording -> {
                isRecording = true
                binding.btnRecord.text = "Stop Recording"
                binding.btnRecord.setIconResource(R.drawable.ic_stop_24)
                binding.btnRecord.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_dark)
                
                binding.waveformView.visibility = View.VISIBLE
                binding.cardAudioPlayer.visibility = View.GONE
                
                startVisualizer()
            }
            DiaryViewModel.RecordingState.Idle -> {
                isRecording = false
                binding.btnRecord.text = "Record Memory"
                binding.btnRecord.setIconResource(R.drawable.ic_mic_24)
                binding.btnRecord.backgroundTintList = androidx.core.content.ContextCompat.getColorStateList(requireContext(), android.R.color.holo_red_light)
                
                binding.waveformView.visibility = View.GONE
                // Don't auto-show player yet unless we link recordedFile to player preview, but let's keep simple
            }
            else -> {}
        }
    }
    
    private fun startVisualizer() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (isRecording) {
                val amplitude = viewModel.getMaxAmplitude()
                binding.waveformView.addAmplitude(amplitude.toFloat())
                kotlinx.coroutines.delay(50)
            }
        }
    }

    private fun populateUi(entry: DiaryEntry) {
        // Only populate if fields are empty to avoid overwriting user edits during updates
        // BUT, getEntryById is a flow. If it updates locally, we might overwrite.
        // It's safer to populate once or check if text matches.
        // For simplicity: Populate ONLY if binding.etTitle.text is empty (first load).
        // This is a naive approach but prevents typing glitches if Flow re-emits.
        
        if (binding.etTitle.text.toString().isEmpty()) {
            binding.etTitle.setText(entry.title)
        }
        if (binding.etContent.text.toString().isEmpty()) {
            binding.etContent.setText(entry.content)
        }
        
        // Tags
        binding.chipGroupTags.removeAllViews()
        selectedTags.clear()
        selectedTags.addAll(entry.tags)
        entry.tags.forEach { tag ->
            addChipToGroup(tag)
        }

        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy, hh:mm a", Locale.getDefault())
        binding.tvDate.text = dateFormat.format(Date(entry.timestamp))

        if (entry.audioUrl.isNotEmpty()) {
            binding.cardAudioPlayer.visibility = View.VISIBLE
        } else {
            binding.cardAudioPlayer.visibility = View.GONE
        }
        
        // Hide record button if audio already exists? Or allow overwrite? 
        // For now, if audio exists, hide record button to prevent accidental overwrite without delete
        if (entry.audioUrl.isNotEmpty()) {
             binding.btnRecord.visibility = View.GONE
        }
    }

    private fun updatePlayerUi(playingId: String?) {
        val isPlaying = playingId == currentEntryId && currentEntryId != null
        if (isPlaying) {
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            binding.tvAudioStatus.text = "Playing..."
        } else {
            binding.btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            binding.tvAudioStatus.text = "Tap to play"
        }
    }

    private fun saveMemory() {
        val title = binding.etTitle.text.toString().trim()
        val content = binding.etContent.text.toString().trim()

        if (title.isEmpty()) {
            binding.etTitle.error = "Title required"
            return
        }

        val entryToSave = if (currentEntry != null) {
            // Update existing
            currentEntry!!.copy(
                title = title,
                content = content,
                tags = selectedTags.toList()
                // Keep ID, AudioUrl, Mood, Timestamp
            )
        } else {
            // Create new
            DiaryEntry(
                title = title,
                content = content,
                timestamp = System.currentTimeMillis(),
                tags = selectedTags.toList()
            )
        }

        viewModel.saveEntry(entryToSave, recordedFile)
        
        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
