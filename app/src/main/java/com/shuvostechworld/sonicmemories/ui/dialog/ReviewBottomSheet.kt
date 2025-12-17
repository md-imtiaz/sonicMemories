package com.shuvostechworld.sonicmemories.ui.dialog

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.os.bundleOf
// import androidx.fragment.app.setFragmentResult removed
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.shuvostechworld.sonicmemories.databinding.FragmentReviewBottomSheetBinding
import com.shuvostechworld.sonicmemories.ui.DiaryViewModel
import com.shuvostechworld.sonicmemories.ui.adapter.AmbientSoundAdapter
import com.shuvostechworld.sonicmemories.utils.AccessibilityUtils
import com.shuvostechworld.sonicmemories.utils.AmbientSoundManager
import com.shuvostechworld.sonicmemories.utils.AudioPlayer
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import com.shuvostechworld.sonicmemories.utils.LocationManager
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ReviewBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentReviewBottomSheetBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var audioPlayer: AudioPlayer

    @Inject
    lateinit var ambientSoundManager: AmbientSoundManager

    @Inject
    lateinit var locationManager: LocationManager

    private val viewModel: DiaryViewModel by activityViewModels()

    private var recordedFilePath: String? = null
    private var currentMood: Int = 5
    private var selectedAmbientSoundId: Int? = null
    private var selectedAmbientSoundUrl: String? = null
    private var currentLocation: android.location.Location? = null
    private var currentLocationAddress: String? = null
    private lateinit var ambientAdapter: AmbientSoundAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordedFilePath = arguments?.getString(ARG_FILE_PATH)
        isCancelable = false // Modal behavior
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReviewBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    private val selectedTags = mutableListOf<String>()
    
    // Voice Input Launcher
    private val voiceInputLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val matches = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val spokenText = matches[0]
                onVoiceInputResult(spokenText)
            }
        }
    }
    
    // Location Permission Launcher
    private val locationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineGranted || coarseGranted) {
             fetchLocationInternal()
        } else {
             binding.tvLocationText.text = "Permission denied"
             AccessibilityUtils.announceToScreenReader(binding.tvLocationText, "Location permission denied.")
        }
    }
    
    private var pendingVoiceInputEditText: com.google.android.material.textfield.TextInputEditText? = null
    
    private fun onVoiceInputResult(text: String) {
        pendingVoiceInputEditText?.setText(text)
        pendingVoiceInputEditText = null // Reset
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPlayer()
        setupMoodSlider()
        setupAmbientSounds()
        setupTags()

        setupButtons()
        fetchLocation()
    }

    private fun fetchLocation() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            fetchLocationInternal()
        } else {
            // Request permissions
            locationPermissionLauncher.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun fetchLocationInternal() {
        // Show fetching state
        binding.tvLocationText.text = "Fetching location..."
        binding.layoutLocationContainer.contentDescription = "Fetching location..."
        
        viewLifecycleOwner.lifecycleScope.launch {
            val location = locationManager.getCurrentLocation()
            if (location != null) {
                currentLocation = location
                val address = locationManager.getAddressFromLocation(location)
                currentLocationAddress = address
                
                updateLocationUI(address)
            } else {
                binding.tvLocationText.text = "Location not found"
                binding.layoutLocationContainer.contentDescription = "Location not found. Double tap to edit."
            }
        }
        
        binding.layoutLocationContainer.setOnClickListener {
            showEditLocationDialog()
        }
    }
    
    private fun updateLocationUI(address: String?) {
        val text = address ?: "Unknown Location"
        binding.tvLocationText.text = text
        binding.layoutLocationContainer.contentDescription = "Location: $text. Double tap to edit."
        if (address != null) {
             AccessibilityUtils.announceToScreenReader(binding.root, "Location found: $address")
        }
    }
    
    private fun showEditLocationDialog() {
        val editText = com.google.android.material.textfield.TextInputEditText(requireContext())
        editText.setText(currentLocationAddress ?: "")
        editText.hint = "Enter Location"
        
        // Container for EditText and Mic
        val container = android.widget.LinearLayout(requireContext())
        container.orientation = android.widget.LinearLayout.HORIZONTAL
        container.setPadding(48, 24, 48, 24)
        container.gravity = android.view.Gravity.CENTER_VERTICAL
        
        // Layout params for EditText
        val params = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
        params.weight = 1f
        editText.layoutParams = params
        
        // Mic Button
        val micButton = android.widget.ImageButton(requireContext())
        micButton.setImageResource(android.R.drawable.ic_btn_speak_now)
        micButton.background = null // Transparent
        micButton.contentDescription = "Voice Type Location"
        micButton.setOnClickListener {
            startVoiceInput(editText)
        }
        
        container.addView(editText)
        container.addView(micButton)
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Edit Location")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                 val newAddress = editText.text.toString()
                 currentLocationAddress = newAddress
                 updateLocationUI(newAddress)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun startVoiceInput(targetEditText: com.google.android.material.textfield.TextInputEditText) {
        pendingVoiceInputEditText = targetEditText
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak location name")
        }
        try {
            voiceInputLauncher.launch(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(requireContext(), "Voice input not supported", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupTags() {
        val categories = listOf("Daily", "Idea", "Family", "Work", "Travel", "Important", "Feeling", "Memory")
        
        // Dropdown Adapter
        val adapter = android.widget.ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        binding.autoCompleteAddTag.setAdapter(adapter)

        binding.autoCompleteAddTag.setOnItemClickListener { parent, _, position, _ ->
            val selectedTag = parent.getItemAtPosition(position) as? String
            if (selectedTag != null) {
                if (!selectedTags.contains(selectedTag)) {
                    selectedTags.add(selectedTag)
                    addChipToGroup(selectedTag)
                }
                binding.autoCompleteAddTag.setText("") // Clear input
            }
        }
        
        // Manual Entry via Keyboard action
        binding.inputLayoutAddTag.setEndIconOnClickListener {
             // If we had an end icon for manual add, but dropdown handles typing + enter usually.
             // But TextInputLayout endIconMode="dropdown" usually.
             // Let's check text on IME Action if user types new tag not in list?
             addTagFromInput()
        }
        
        binding.autoCompleteAddTag.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                addTagFromInput()
                true
            } else {
                false
            }
        }
    }
    
    // Helper to get text from autocomplete
    private fun addTagFromInput() {
         val tagText = binding.autoCompleteAddTag.text.toString().trim()
         if (tagText.isNotEmpty()) {
             if (!selectedTags.contains(tagText)) {
                 selectedTags.add(tagText)
                 addChipToGroup(tagText)
                 binding.autoCompleteAddTag.setText("")
             }
         }
    }

    // ... 

    private fun setupAmbientSounds() {
        ambientAdapter = AmbientSoundAdapter(
            onPreviewClick = { soundItem, shouldPlay ->
                if (shouldPlay) {
                    val url = soundItem.previews?.previewHqMp3
                    if (url != null) {
                        ambientSoundManager.playLooping(url, null)
                        ambientAdapter.updatePlayingState(soundItem.id)
                    }
                } else {
                    ambientSoundManager.stop()
                    ambientAdapter.updatePlayingState(null)
                }
            },
            onAddClick = { soundItem ->
                selectedAmbientSoundId = soundItem.id
                selectedAmbientSoundUrl = soundItem.previews?.previewHqMp3
                ambientAdapter.setSelection(soundItem.id)
                
                // Update UI: Hide list, show selected
                showSelectedAmbientState(soundItem.name)
                
                AccessibilityUtils.announceToScreenReader(binding.layoutSelectedAmbient, "Selected ${soundItem.name}. List hidden.")
            }
        )

        binding.rvAmbientSounds.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = ambientAdapter
        }

        binding.btnChangeAmbient.setOnClickListener {
            resetAmbientSelection()
        }

        // Dropdown selection logic
        val categories = listOf("Rain", "Forest", "Ocean", "Fire", "Night", "Train", "Cafe", "Birds", "Wind", "Thunder", "Cave")
        val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, categories)
        (binding.inputLayoutAmbient.editText as? android.widget.AutoCompleteTextView)?.apply {
            setAdapter(adapter)
            setOnItemClickListener { _, _, position, _ ->
                val selectedCategory = categories[position]
                viewModel.searchAmbientSounds(selectedCategory)
            }
        }

        // Collect results
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ambientSounds.collect { sounds ->
                ambientAdapter.submitList(sounds)
            }
        }
    }
    
    private fun showSelectedAmbientState(name: String) {
        binding.rvAmbientSounds.visibility = View.GONE
        binding.inputLayoutAmbient.visibility = View.GONE
        binding.layoutSelectedAmbient.visibility = View.VISIBLE
        binding.tvSelectedAmbientName.text = name
    }
    
    private fun resetAmbientSelection() {
        binding.layoutSelectedAmbient.visibility = View.GONE
        binding.rvAmbientSounds.visibility = View.VISIBLE
        binding.inputLayoutAmbient.visibility = View.VISIBLE
        AccessibilityUtils.announceToScreenReader(binding.rvAmbientSounds, "Sound list visible.")
    }



    private fun addChipToGroup(tag: String) {
        val chip = com.google.android.material.chip.Chip(context, null, com.shuvostechworld.sonicmemories.R.style.Widget_SonicMemories_Chip_Choice)
        chip.text = tag
        chip.isCloseIconVisible = true
        chip.isChecked = true
        chip.setOnCloseIconClickListener {
            binding.chipGroupTags.removeView(chip)
            selectedTags.remove(tag)
        }
        // Style handles colors now
        // chip.setTextColor(...)
        // chip.chipBackgroundColor = ...
        binding.chipGroupTags.addView(chip)
    }

    // ... (Existing Setup Methods) ...

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            // Return result to Activity
                val resultBundle = Bundle().apply {
                    putBoolean(RESULT_SAVED, true)
                    putInt(RESULT_MOOD, currentMood)
                    putString(RESULT_FILE_PATH, recordedFilePath)
                    putString(RESULT_AMBIENT_SOUND_URL, selectedAmbientSoundUrl)
                    if (selectedAmbientSoundId != null) {
                        putInt(RESULT_AMBIENT_SOUND_ID, selectedAmbientSoundId!!)
                    }
                    putStringArrayList(RESULT_TAGS, java.util.ArrayList(selectedTags))
                    if (currentLocation != null) {
                        putDouble(RESULT_LATITUDE, currentLocation!!.latitude)
                        putDouble(RESULT_LONGITUDE, currentLocation!!.longitude)
                        putString(RESULT_ADDRESS, currentLocationAddress)
                    }
                }
                parentFragmentManager.setFragmentResult(REQUEST_KEY, resultBundle)
            dismiss()
        }

        binding.btnDiscard.setOnClickListener {
            showDiscardConfirmation()
        }
    }

    private fun showDiscardConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(com.shuvostechworld.sonicmemories.R.string.discard))
            .setMessage("Are you sure you want to discard this recording?")
            .setPositiveButton(getString(com.shuvostechworld.sonicmemories.R.string.discard)) { _, _ ->
                parentFragmentManager.setFragmentResult(REQUEST_KEY, bundleOf(RESULT_SAVED to false))
                dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ... 



    override fun onStart() {
        super.onStart()
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        dialog?.behavior?.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dialog?.behavior?.skipCollapsed = true
    }

    private fun setupPlayer() {
        binding.btnPlayPause.setOnClickListener {
            if (audioPlayer.isPlaying()) {
                audioPlayer.stop()
                ambientSoundManager.stop()
                updatePlayPauseIcon(false)
            } else {
                val path = recordedFilePath
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) {
                        // Play Ambient if selected
                        val startVoice = {
                            // Play Voice
                            audioPlayer.playFile(path) {
                                Handler(Looper.getMainLooper()).post {
                                    updatePlayPauseIcon(false)
                                    ambientSoundManager.stop() // Stop ambient when voice ends
                                }
                            }
                        }

                        if (selectedAmbientSoundUrl != null) {
                            ambientSoundManager.playLooping(selectedAmbientSoundUrl!!, startVoice)
                        } else {
                            startVoice()
                        }

                        updatePlayPauseIcon(true)
                    } else {
                        android.widget.Toast.makeText(requireContext(), getString(com.shuvostechworld.sonicmemories.R.string.error_file_not_found), android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.widget.Toast.makeText(requireContext(), getString(com.shuvostechworld.sonicmemories.R.string.error_no_recording_path), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        binding.btnPlayPause.setIconResource(iconRes)
        val contentDesc = if (isPlaying) getString(com.shuvostechworld.sonicmemories.R.string.pause_recording) else getString(com.shuvostechworld.sonicmemories.R.string.play_recording)
        binding.btnPlayPause.contentDescription = contentDesc
    }

    private fun setupMoodSlider() {
        // Range 1-5 (SeekBar is 0-4, plus 1)
        binding.seekbarMood.max = 4 
        // Initial State
        val initialMoodIndex = currentMood - 1
        binding.seekbarMood.progress = if (initialMoodIndex < 0) 2 else initialMoodIndex // Default to 3 (Okay) if 0
        updateMoodLabel(binding.seekbarMood.progress + 1)
        
        binding.seekbarMood.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val moodValue = progress + 1
                currentMood = moodValue
                
                updateMoodLabel(moodValue)
                
                if (fromUser) {
                    // Haptic Feedback
                     if (moodValue == 5) { // Max
                        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    } else {
                        view?.performHapticFeedback(HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING) // Light tick
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val moodValue = (seekBar?.progress ?: 2) + 1
                val moodText = getMoodString(moodValue)
                AccessibilityUtils.announceToScreenReader(binding.root, getString(com.shuvostechworld.sonicmemories.R.string.mood_accessibility_announcement, moodText))
            }
        })
    }

    private fun updateMoodLabel(moodValue: Int) {
        val moodText = getMoodString(moodValue)
        binding.tvMoodLabel.text = getString(com.shuvostechworld.sonicmemories.R.string.mood_label_format, moodText)
        // Set State Description for Accessibility (API 30+)
        androidx.core.view.ViewCompat.setStateDescription(binding.seekbarMood, moodText)
    }

    private fun getMoodString(moodValue: Int): String {
        return when (moodValue) {
            1 -> getString(com.shuvostechworld.sonicmemories.R.string.mood_1)
            2 -> getString(com.shuvostechworld.sonicmemories.R.string.mood_2)
            3 -> getString(com.shuvostechworld.sonicmemories.R.string.mood_3)
            4 -> getString(com.shuvostechworld.sonicmemories.R.string.mood_4)
            5 -> getString(com.shuvostechworld.sonicmemories.R.string.mood_5)
            else -> getString(com.shuvostechworld.sonicmemories.R.string.mood_3)
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        audioPlayer.stop()
        ambientSoundManager.stop()
        _binding = null
    }

    companion object {
        const val TAG = "ReviewBottomSheet"
        const val ARG_FILE_PATH = "arg_file_path"
        const val REQUEST_KEY = "request_review_result"
        const val RESULT_SAVED = "result_saved"
        const val RESULT_MOOD = "result_mood"
        const val RESULT_FILE_PATH = "result_file_path"
        const val RESULT_AMBIENT_SOUND_URL = "result_ambient_sound_url"
        const val RESULT_AMBIENT_SOUND_ID = "result_ambient_sound_id"
        const val RESULT_TAGS = "result_tags"
        const val RESULT_LATITUDE = "result_latitude"
        const val RESULT_LONGITUDE = "result_longitude"
        const val RESULT_ADDRESS = "result_address"

        fun newInstance(filePath: String): ReviewBottomSheet {
            return ReviewBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILE_PATH, filePath)
                }
            }
        }
    }
}
