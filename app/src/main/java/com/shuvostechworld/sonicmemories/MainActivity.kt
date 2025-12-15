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

    private var isActionProcessing = false

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
        setupBackStackListener()
        setupSearch()
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
            },
            onEditClick = { entry ->
                openDetailFragment(entry.id)
            },
            onDeleteClick = { entry ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Memory?")
                    .setMessage("Are you sure you want to delete this memory? This action cannot be undone.")
                    .setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setPositiveButton("Yes") { dialog, _ ->
                        viewModel.deleteEntry(entry)
                        AccessibilityUtils.vibrate(this, 100)
                        AccessibilityUtils.announceToScreenReader(binding.root, "Memory deleted")
                        Snackbar.make(binding.root, "Memory deleted", Snackbar.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .show()
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
    
    private fun setupBackStackListener() {
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount > 0) {
                 // In Detail Fragment
                 binding.recyclerView.visibility = android.view.View.GONE
                 binding.fabWrite.hide()
                 binding.fabRecord.hide()
            } else {
                 // Home
                 binding.recyclerView.visibility = android.view.View.VISIBLE
                 binding.fabWrite.show()
                 binding.fabRecord.show()
            }
        }
    }

    private fun setupSearch() {
        val searchView = findViewById<androidx.appcompat.widget.SearchView>(R.id.searchView)
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.updateSearchQuery(newText ?: "")
                return true
            }
        })
    }

    private fun setupSwipeToDelete() {
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = adapter.currentList[position]
                    
                    if (item is com.shuvostechworld.sonicmemories.ui.adapter.ListItem.Entry) {
                        val entry = item.entry
                        viewModel.deleteEntry(entry)
                        AccessibilityUtils.vibrate(this@MainActivity, 100)
                        AccessibilityUtils.announceToScreenReader(binding.root, "Memory deleted")
                        
                        Snackbar.make(binding.root, "Memory deleted", Snackbar.LENGTH_LONG)
                            .setAction("Undo") {
                                // Undo placeholder
                            }
                            .show()
                    } else {
                        // Header or other type - reset swipe
                        adapter.notifyItemChanged(position)
                    }
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
            if (!isActionProcessing) {
                 handleMainFabClick()
            }
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
        if (isActionProcessing) return
        isActionProcessing = true
        
        lifecycleScope.launch {
            try {
                // Play start tone
                soundManager.playSound(R.raw.start_sound, SoundManager.TONE_START)
                
                // Wait 500ms to avoid recording TalkBack announcement
                kotlinx.coroutines.delay(500)
                
                AccessibilityUtils.vibrate(this@MainActivity, 50)
                viewModel.startRecording()
                AccessibilityUtils.announceToScreenReader(binding.fabRecord, "Recording Started")
            } finally {
                // Allow interactions again after a short buffer
                kotlinx.coroutines.delay(200)
                isActionProcessing = false
            }
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
                binding.fabRecord.text = "Record"
                binding.fabRecord.icon = androidx.core.content.ContextCompat.getDrawable(this, android.R.drawable.ic_btn_speak_now)
                binding.fabRecord.contentDescription = "Double tap to start recording"
                
                // Stop Animation
                binding.fabRecord.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                
                if (adapter.itemCount > 0) {
                     binding.recyclerView.visibility = android.view.View.VISIBLE
                     binding.layoutEmptyState.visibility = android.view.View.GONE
                } else {
                     binding.recyclerView.visibility = android.view.View.GONE
                     binding.layoutEmptyState.visibility = android.view.View.VISIBLE
                }
            }
            DiaryViewModel.RecordingState.Recording -> {
                binding.fabPause.visibility = android.view.View.VISIBLE
                binding.fabPause.setImageResource(android.R.drawable.ic_media_pause)
                binding.fabPause.contentDescription = "Pause Recording"
                binding.recyclerView.visibility = android.view.View.GONE
                binding.layoutEmptyState.visibility = android.view.View.GONE
                
                binding.fabRecord.text = "Stop"
                binding.fabRecord.icon = androidx.core.content.ContextCompat.getDrawable(this, com.google.android.material.R.drawable.ic_m3_chip_close) 
                binding.fabRecord.contentDescription = "Double tap to stop recording"
                
                // Start Pulse Animation
                binding.fabRecord.animate().scaleX(1.1f).scaleY(1.1f).setDuration(800).withEndAction { 
                     // Simple continuous pulse using ViewPropertyAnimator loop
                     runPulseAnimation()
                }.start()
            }
            DiaryViewModel.RecordingState.Paused -> {
                binding.fabPause.visibility = android.view.View.VISIBLE
                binding.fabPause.setImageResource(android.R.drawable.ic_media_play)
                binding.fabPause.contentDescription = "Resume Recording"
                binding.recyclerView.visibility = android.view.View.GONE
                binding.layoutEmptyState.visibility = android.view.View.GONE
                
                binding.fabRecord.text = "Stop"
                
                // Pause Animation (Freeze or Reset)
                binding.fabRecord.animate().cancel()
                binding.fabRecord.scaleX = 1f
                binding.fabRecord.scaleY = 1f
            }
        }
        
        if (state != DiaryViewModel.RecordingState.Idle) {
             binding.fabRecord.setIconResource(R.drawable.ic_stop_24)
        }
    }
    
    private fun runPulseAnimation() {
        if (viewModel.recordingState.value == DiaryViewModel.RecordingState.Recording) {
             binding.fabRecord.animate().scaleX(1f).scaleY(1f).setDuration(800).withEndAction {
                  if (viewModel.recordingState.value == DiaryViewModel.RecordingState.Recording) {
                      binding.fabRecord.animate().scaleX(1.1f).scaleY(1.1f).setDuration(800).withEndAction {
                           runPulseAnimation()
                      }.start()
                  }
             }.start()
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                adapter.submitEntries(state.entries)
                                if (viewModel.recordingState.value == DiaryViewModel.RecordingState.Idle) {
                                    if (state.entries.isEmpty()) {
                                        binding.layoutEmptyState.visibility = android.view.View.VISIBLE
                                        binding.recyclerView.visibility = android.view.View.GONE
                                    } else {
                                        binding.layoutEmptyState.visibility = android.view.View.GONE
                                        binding.recyclerView.visibility = android.view.View.VISIBLE
                                    }
                                }
                            }
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