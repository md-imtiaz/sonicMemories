package com.shuvostechworld.sonicmemories.ui.create
import androidx.navigation.fragment.findNavController

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.shuvostechworld.sonicmemories.R
import com.shuvostechworld.sonicmemories.databinding.FragmentCreateBinding
import com.shuvostechworld.sonicmemories.ui.DiaryViewModel
import com.shuvostechworld.sonicmemories.utils.AccessibilityUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CreateFragment : Fragment() {

    private var _binding: FragmentCreateBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: DiaryViewModel by activityViewModels()
    private lateinit var soundManager: com.shuvostechworld.sonicmemories.utils.SoundManager
    private var isActionProcessing = false

    // Request Permission logic moved here
    private val requestPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                AccessibilityUtils.announceToScreenReader(binding.cardRecord, "Permission granted. Tap again to record.")
                startRecording()
            } else {
                Snackbar.make(binding.root, "Permission required to record audio", Snackbar.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            soundManager = com.shuvostechworld.sonicmemories.utils.SoundManager(requireContext())
        } catch (e: Exception) {
            // Handle error
        }

        setupListeners()
        observeRecordingState()
        setupReviewResultListener()
    }

    private fun setupListeners() {
        binding.cardRecord.setOnClickListener {
             handleRecordClick()
        }

        binding.cardPause.setOnClickListener {
            handlePauseClick()
        }

        binding.cardWrite.setOnClickListener {
             if (viewModel.recordingState.value == DiaryViewModel.RecordingState.Idle) {
                 findNavController().navigate(R.id.navigation_memory_detail)
             } else {
                 Snackbar.make(binding.root, getString(R.string.stop_first_hint), Snackbar.LENGTH_SHORT).show()
                 AccessibilityUtils.announceToScreenReader(binding.cardWrite, getString(R.string.stop_first_accessibility))
             }
        }
    }

    private fun handleRecordClick() {
        when (viewModel.recordingState.value) {
            DiaryViewModel.RecordingState.Idle -> checkPermissionAndStartRecording()
            else -> stopRecording()
        }
    }

    private fun handlePauseClick() {
        if (viewModel.recordingState.value == DiaryViewModel.RecordingState.Recording) {
            viewModel.pauseRecording()
            soundManager.playSound(R.raw.pause_sound, com.shuvostechworld.sonicmemories.utils.SoundManager.TONE_PAUSE)
            AccessibilityUtils.announceToScreenReader(binding.cardPause, getString(R.string.recording_paused))
        } else if (viewModel.recordingState.value == DiaryViewModel.RecordingState.Paused) {
            viewModel.resumeRecording()
            soundManager.playSound(R.raw.resume_sound, com.shuvostechworld.sonicmemories.utils.SoundManager.TONE_RESUME)
            AccessibilityUtils.announceToScreenReader(binding.cardPause, getString(R.string.recording_resumed))
        }
    }

    private fun checkPermissionAndStartRecording() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        if (isActionProcessing) return
        isActionProcessing = true
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                soundManager.playSound(R.raw.start_sound, com.shuvostechworld.sonicmemories.utils.SoundManager.TONE_START)
                kotlinx.coroutines.delay(500)
                AccessibilityUtils.vibrate(requireContext(), 50)
                viewModel.startRecording()
                AccessibilityUtils.announceToScreenReader(binding.cardRecord, "Recording Started. Tap to Stop.")
            } finally {
                kotlinx.coroutines.delay(200)
                isActionProcessing = false
            }
        }
    }

    private fun stopRecording() {
        AccessibilityUtils.vibrate(requireContext(), 50)
        val file = viewModel.stopRecording()
        soundManager.playSound(R.raw.stop_sound, com.shuvostechworld.sonicmemories.utils.SoundManager.TONE_STOP)
        
        if (file != null) {
            AccessibilityUtils.announceToScreenReader(binding.cardRecord, "Review Memory")
            com.shuvostechworld.sonicmemories.ui.dialog.ReviewBottomSheet.newInstance(file.absolutePath)
                .show(parentFragmentManager, com.shuvostechworld.sonicmemories.ui.dialog.ReviewBottomSheet.TAG)
        }
    }

    private fun observeRecordingState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recordingState.collect { state ->
                    updateUiForState(state)
                }
            }
        }
    }

    private fun updateUiForState(state: DiaryViewModel.RecordingState) {
        when (state) {
            DiaryViewModel.RecordingState.Idle -> {
                binding.tvRecordStatus.text = getString(R.string.record_audio)
                binding.tvRecordHint.text = getString(R.string.tap_to_start)
                binding.ivRecordIcon.setImageResource(R.drawable.ic_mic_24)
                binding.ivRecordIcon.imageTintList = ColorStateList.valueOf(resources.getColor(R.color.zen_accent, null))
                binding.cardRecord.strokeColor = resources.getColor(R.color.zen_accent, null)
                binding.cardRecord.animate().scaleX(1f).scaleY(1f).duration = 200
                
                binding.cardPause.visibility = View.GONE
                
                binding.cardWrite.alpha = 1.0f
                binding.cardWrite.strokeColor = resources.getColor(R.color.zen_surface, null)
            }
            DiaryViewModel.RecordingState.Recording -> {
                binding.tvRecordStatus.text = getString(R.string.tap_to_stop)
                binding.tvRecordHint.text = getString(R.string.recording_started)
                binding.ivRecordIcon.setImageResource(R.drawable.ic_stop_24)
                binding.ivRecordIcon.imageTintList = ColorStateList.valueOf(android.graphics.Color.RED)
                binding.cardRecord.strokeColor = android.graphics.Color.RED
                
                binding.cardPause.visibility = View.VISIBLE
                binding.tvPauseLabel.text = getString(R.string.pause_recording)
                binding.ivPauseIcon.setImageResource(R.drawable.ic_pause_24)
                
                runPulseAnimation()
                
                // Keep enabled for accessibility discovery
                binding.cardWrite.alpha = 0.5f 
            }
            DiaryViewModel.RecordingState.Paused -> {
                binding.tvRecordStatus.text = "Stop Recording"
                binding.tvRecordHint.text = getString(R.string.recording_paused)
                // Stop button remains Red/Stop
                
                binding.cardPause.visibility = View.VISIBLE
                binding.tvPauseLabel.text = getString(R.string.resume_recording)
                binding.ivPauseIcon.setImageResource(android.R.drawable.ic_media_play) // Reuse play icon for resume
                
                binding.cardRecord.animate().cancel()
                binding.cardRecord.scaleX = 1.0f
                binding.cardRecord.scaleY = 1.0f
                
                binding.cardWrite.alpha = 0.5f
            }
        }
    }
    
    private fun runPulseAnimation() {
         if (viewModel.recordingState.value == DiaryViewModel.RecordingState.Recording) {
             binding.cardRecord.animate().scaleX(1.02f).scaleY(1.02f).setDuration(800).withEndAction {
                  if (viewModel.recordingState.value == DiaryViewModel.RecordingState.Recording) {
                       binding.cardRecord.animate().scaleX(1.0f).scaleY(1.0f).setDuration(800).withEndAction {
                            runPulseAnimation()
                       }.start()
                  }
             }.start()
        }
    }

    private fun setupReviewResultListener() {
        parentFragmentManager.setFragmentResultListener(com.shuvostechworld.sonicmemories.ui.dialog.ReviewBottomSheet.REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
            val saved = bundle.getBoolean(com.shuvostechworld.sonicmemories.ui.dialog.ReviewBottomSheet.RESULT_SAVED)
            val audioPath = bundle.getString(com.shuvostechworld.sonicmemories.ui.dialog.ReviewBottomSheet.RESULT_FILE_PATH)
            val mood = bundle.getInt(com.shuvostechworld.sonicmemories.ui.dialog.ReviewBottomSheet.RESULT_MOOD, 5)
            val ambientUrl = bundle.getString(com.shuvostechworld.sonicmemories.ui.dialog.ReviewBottomSheet.RESULT_AMBIENT_SOUND_URL)
            val tagsList = bundle.getStringArrayList(com.shuvostechworld.sonicmemories.ui.dialog.ReviewBottomSheet.RESULT_TAGS)
            val lat = bundle.getDouble(com.shuvostechworld.sonicmemories.ui.dialog.ReviewBottomSheet.RESULT_LATITUDE) // returns 0.0 if default, handled manually check
            val lng = bundle.getDouble(com.shuvostechworld.sonicmemories.ui.dialog.ReviewBottomSheet.RESULT_LONGITUDE)
            // check bundle existence keys first or just accept 0.0 if valid range
            val address = bundle.getString(com.shuvostechworld.sonicmemories.ui.dialog.ReviewBottomSheet.RESULT_ADDRESS)
            
            // Handle primitives defaults
            val hasLat = bundle.containsKey(com.shuvostechworld.sonicmemories.ui.dialog.ReviewBottomSheet.RESULT_LATITUDE)
            val finalLat = if (hasLat) lat else null
            val finalLng = if (hasLat) lng else null
            
            if (saved && audioPath != null) {
                viewModel.saveFinalEntry(java.io.File(audioPath), mood, ambientUrl, tagsList, finalLat, finalLng, address)
                Snackbar.make(binding.root, "Memory Saved", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::soundManager.isInitialized) {
            soundManager.release()
        }
        _binding = null
    }
}
