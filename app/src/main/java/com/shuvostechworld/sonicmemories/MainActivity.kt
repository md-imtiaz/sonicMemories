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
import java.io.File
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: DiaryViewModel by viewModels()
    private lateinit var adapter: DiaryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) {
            startActivity(android.content.Intent(this, com.shuvostechworld.sonicmemories.ui.auth.SignInActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupFab()
        setupReviewResultListener()
        observeUiState()
    }

    private fun setupReviewResultListener() {
        supportFragmentManager.setFragmentResultListener(ReviewBottomSheet.REQUEST_KEY, this) { _, bundle ->
            val saved = bundle.getBoolean(ReviewBottomSheet.RESULT_SAVED)
            if (saved) {
                val mood = bundle.getInt(ReviewBottomSheet.RESULT_MOOD)
                val path = bundle.getString(ReviewBottomSheet.RESULT_FILE_PATH)
                if (path != null) {
                    val file = File(path)
                    viewModel.saveFinalEntry(file, mood)
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
        adapter = DiaryAdapter { entry ->
            viewModel.playMemory(entry)
            if (viewModel.currentPlayingId.value != entry.id) {
               AccessibilityUtils.announceToScreenReader(binding.root, "Playing memory") 
            }
        }
        binding.recyclerView.adapter = adapter
        
        setupSwipeToDelete()
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
        binding.fabRecord.setOnTouchListener { v, event ->
            v.performClick()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    checkPermissionAndStartRecording()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecording()
                    true
                }
                else -> false
            }
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
        AccessibilityUtils.vibrate(this, 50)
        AccessibilityUtils.announceToScreenReader(binding.fabRecord, "Recording Started")
        viewModel.startRecording()
    }

    private fun stopRecording() {
        AccessibilityUtils.vibrate(this, 50)
        val file = viewModel.stopRecording()
        if (file != null) {
            AccessibilityUtils.announceToScreenReader(binding.fabRecord, "Review Memory")
            ReviewBottomSheet.newInstance(file.absolutePath).show(supportFragmentManager, ReviewBottomSheet.TAG)
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
}