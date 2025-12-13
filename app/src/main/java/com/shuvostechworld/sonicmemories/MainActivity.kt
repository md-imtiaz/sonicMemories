package com.shuvostechworld.sonicmemories


import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.shuvostechworld.sonicmemories.databinding.ActivityMainBinding
import com.shuvostechworld.sonicmemories.ui.DiaryViewModel
import com.shuvostechworld.sonicmemories.ui.UiState
import com.shuvostechworld.sonicmemories.ui.adapter.DiaryAdapter
import com.shuvostechworld.sonicmemories.utils.AccessibilityUtils
import dagger.hilt.android.AndroidEntryPoint
import com.shuvostechworld.sonicmemories.ui.dialog.ReviewBottomSheet
import com.shuvostechworld.sonicmemories.utils.SoundManager
import java.io.File
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: DiaryViewModel by viewModels()

    private lateinit var adapter: DiaryAdapter
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                 com.google.firebase.FirebaseApp.initializeApp(this)
            }
            
            if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
                startActivity(android.content.Intent(this, com.shuvostechworld.sonicmemories.ui.auth.SignInActivity::class.java))
                finish()
                return
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Auth check failed", e)
            // Fallback to sign in if anything goes wrong with auth check
            startActivity(android.content.Intent(this, com.shuvostechworld.sonicmemories.ui.auth.SignInActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Biometric Security Layer
        binding.layoutLockScreen.visibility = android.view.View.VISIBLE
        authenticateUser()
        
        binding.btnUnlock.setOnClickListener {
            authenticateUser()
        }

        try {
            soundManager = SoundManager(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "SoundManager init failed", e)
            // Continue without sound or handle gracefully? 
            // SoundManager itself handles internal errors, but construction shouldn't crash unless context is null which is impossible here.
            // Leaving as is but logging.
        }
        
        setupRecyclerView()
        setupFab()
        setupWriteFab()
        setupPauseFab()
        setupReviewResultListener()
        observeUiState()
        observeRecordingState()
    }

    private fun setupReviewResultListener() {
        supportFragmentManager.setFragmentResultListener(ReviewBottomSheet.REQUEST_KEY, this) { _, bundle ->
            val saved = bundle.getBoolean(ReviewBottomSheet.RESULT_SAVED)
            if (saved) {
                val mood = bundle.getInt(ReviewBottomSheet.RESULT_MOOD)
                val path = bundle.getString(ReviewBottomSheet.RESULT_FILE_PATH)
                val ambientUrl = bundle.getString(ReviewBottomSheet.RESULT_AMBIENT_SOUND_URL)
                if (path != null) {
                    val file = File(path)
                    viewModel.saveFinalEntry(file, mood, ambientUrl)
                    AccessibilityUtils.announceToScreenReader(binding.root, "Memory Saved")
                    AccessibilityUtils.vibrate(this, 100)
                    Snackbar.make(binding.root, "Memory Saved", Snackbar.LENGTH_SHORT).show()
                }
            } else {
                AccessibilityUtils.announceToScreenReader(binding.root, "Memory Discarded")
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = DiaryAdapter(
            onPlayClick = { entry ->
                viewModel.playMemory(entry)
                if (viewModel.currentPlayingId.value != entry.id) {
                   AccessibilityUtils.announceToScreenReader(binding.root, "Playing memory") 
                }
            },
            onItemClick = { entry ->
                openDetailFragment(entry.id)
            }
        )
        binding.recyclerView.adapter = adapter
        
        setupSwipeToDelete()
    }

    private fun openDetailFragment(entryId: String?) {
        val fragment = com.shuvostechworld.sonicmemories.ui.MemoryDetailFragment.newInstance(entryId)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun setupSwipeToDelete() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val entry = adapter.currentList[position]
                    
                    viewModel.deleteEntry(entry)
                    AccessibilityUtils.vibrate(this@MainActivity, 100)
                    AccessibilityUtils.announceToScreenReader(binding.root, "Memory deleted")
                    
                    Snackbar.make(binding.root, "Memory deleted", Snackbar.LENGTH_LONG)
                        .setAction("Undo") {
                            // Undo placeholder
                        }
                        .show()
                }
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(binding.recyclerView)
    }

    private val requestPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Permission granted. Press and hold to record.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission required to record audio", Toast.LENGTH_SHORT).show()
            }
        }

    private fun setupFab() {
        binding.fabRecord.setOnClickListener {
            handleMainFabClick()
        }
    }

    private fun setupWriteFab() {
        binding.fabWrite.setOnClickListener {
             openDetailFragment(null)
        }
    }

    private fun setupPauseFab() {
        binding.fabPause.setOnClickListener {
            when (viewModel.recordingState.value) {
                DiaryViewModel.RecordingState.Recording -> {
                    viewModel.pauseRecording()
                    soundManager.playSound(R.raw.pause_sound, SoundManager.TONE_PAUSE)
                    AccessibilityUtils.announceToScreenReader(binding.fabPause, "Recording Paused")
                }
                DiaryViewModel.RecordingState.Paused -> {
                    viewModel.resumeRecording()
                    soundManager.playSound(R.raw.resume_sound, SoundManager.TONE_RESUME)
                    AccessibilityUtils.announceToScreenReader(binding.fabPause, "Recording Resumed")
                }
                else -> Unit
            }
        }
    }

    private fun handleMainFabClick() {
        when (viewModel.recordingState.value) {
            DiaryViewModel.RecordingState.Idle -> checkPermissionAndStartRecording()
            else -> stopRecording()
        }
    }

    private fun checkPermissionAndStartRecording() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        lifecycleScope.launch {
            // Play start tone
            soundManager.playSound(R.raw.start_sound, SoundManager.TONE_START)
            
            // Wait 500ms to avoid recording TalkBack announcement
            kotlinx.coroutines.delay(500)
            
            AccessibilityUtils.vibrate(this@MainActivity, 50)
            viewModel.startRecording()
            AccessibilityUtils.announceToScreenReader(binding.fabRecord, "Recording Started")
        }
    }

    private fun stopRecording() {
        AccessibilityUtils.vibrate(this, 50)
        val file = viewModel.stopRecording()
        soundManager.playSound(R.raw.stop_sound, SoundManager.TONE_STOP)
        
        if (file != null) {
            AccessibilityUtils.announceToScreenReader(binding.fabRecord, "Review Memory")
            ReviewBottomSheet.newInstance(file.absolutePath).show(supportFragmentManager, ReviewBottomSheet.TAG)
        }
    }

    private fun observeRecordingState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recordingState.collect { state ->
                    updateUiForState(state)
                }
            }
        }
    }

    private fun updateUiForState(state: DiaryViewModel.RecordingState) {
        when (state) {
            DiaryViewModel.RecordingState.Idle -> {
                binding.fabPause.visibility = android.view.View.GONE
                binding.fabRecord.text = "Record Memory"
                binding.fabRecord.icon = androidx.core.content.ContextCompat.getDrawable(this, android.R.drawable.ic_btn_speak_now)
                binding.fabRecord.contentDescription = "Double tap to start recording"
            }
            DiaryViewModel.RecordingState.Recording -> {
                binding.fabPause.visibility = android.view.View.VISIBLE
                binding.fabPause.setImageResource(android.R.drawable.ic_media_pause)
                binding.fabPause.contentDescription = "Pause Recording"
                
                binding.fabRecord.text = "Stop Recording"
                binding.fabRecord.icon = androidx.core.content.ContextCompat.getDrawable(this, com.google.android.material.R.drawable.ic_m3_chip_close) // Fallback or use standard stop icon
                binding.fabRecord.contentDescription = "Double tap to stop recording"
            }
            DiaryViewModel.RecordingState.Paused -> {
                binding.fabPause.visibility = android.view.View.VISIBLE
                binding.fabPause.setImageResource(android.R.drawable.ic_media_play)
                binding.fabPause.contentDescription = "Resume Recording"
                
                binding.fabRecord.text = "Stop Recording"
                 // stop icon
            }
        }
        
        // Fix for icon not updating correctly if using standard drawable directly in code without context compat sometimes
        if (state != DiaryViewModel.RecordingState.Idle) {
             binding.fabRecord.setIconResource(R.drawable.ic_stop_24) // Attempt to use a local resource if exists, otherwise fallback
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is UiState.Success -> adapter.submitList(state.entries)
                            is UiState.Error -> Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                            else -> Unit
                        }
                    }
                }
                launch {
                     viewModel.currentPlayingId.collect { id ->
                         adapter.updatePlayingState(id)
                     }
                }
            }
        }
    }
    private fun authenticateUser() {
        com.shuvostechworld.sonicmemories.utils.BiometricAuthManager.authenticate(
            activity = this,
            onSuccess = {
                binding.layoutLockScreen.visibility = android.view.View.GONE
                AccessibilityUtils.announceToScreenReader(binding.root, "Welcome back")
                Toast.makeText(this, "Welcome back", Toast.LENGTH_SHORT).show()
            },
            onError = {
                // Keep lock screen visible
                binding.layoutLockScreen.visibility = android.view.View.VISIBLE
                Toast.makeText(this, "Authentication required", Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::soundManager.isInitialized) {
            soundManager.release()
        }
    }
}