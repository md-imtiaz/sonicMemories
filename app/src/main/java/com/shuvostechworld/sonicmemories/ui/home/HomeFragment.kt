package com.shuvostechworld.sonicmemories.ui.home
import androidx.navigation.fragment.findNavController

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.shuvostechworld.sonicmemories.R
import com.shuvostechworld.sonicmemories.databinding.FragmentHomeBinding
import com.shuvostechworld.sonicmemories.ui.DiaryViewModel
import com.shuvostechworld.sonicmemories.ui.UiState
import com.shuvostechworld.sonicmemories.ui.adapter.DiaryAdapter
import com.shuvostechworld.sonicmemories.utils.AccessibilityUtils
import com.shuvostechworld.sonicmemories.utils.DateUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    // Use activityViewModels to share data with Activity and other fragments
    private val viewModel: DiaryViewModel by activityViewModels()
    
    // Reuse adapter
    private lateinit var adapter: DiaryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeUiState()
        observeTags()
    }
    
    // setupRecordingFabs removed
    // handleMainFabClick removed
    // checkPermissionAndStartRecording removed
    // startRecording removed
    // stopRecording removed
    // observeRecordingState removed
    // updateUiForState removed
    // runPulseAnimation removed
    // setupReviewResultListener removed (handled in CreateFragment now) – 
    // Wait, if we edit existing? No, Home is list. Create is for new. OK.
    
    // ... setupRecyclerView ...

    private fun setupRecyclerView() {
        adapter = DiaryAdapter(
            onPlayClick = { entry ->
                viewModel.playMemory(entry)
            },
            onItemClick = { entry ->
                 // Navigate to Detail
                 val bundle = Bundle().apply { putString("entry_id", entry.id) }
                 findNavController().navigate(R.id.navigation_memory_detail, bundle)
            },
            onEditClick = { entry ->
                 val bundle = Bundle().apply { putString("entry_id", entry.id) }
                 findNavController().navigate(R.id.navigation_memory_detail, bundle)
            },
            onDeleteClick = { entry ->
                 showDeleteConfirmation(entry)
            }
        )

        binding.recyclerView.adapter = adapter
        
        // Swipe to Delete
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                
                val item = adapter.currentList[position]
                if (item is com.shuvostechworld.sonicmemories.ui.adapter.ListItem.Entry) {
                    val entry = item.entry
                    viewModel.deleteEntry(entry)
                    
                    Snackbar.make(binding.root, "Memory deleted", Snackbar.LENGTH_LONG)
                        .setAction("Undo") {
                            viewModel.restoreEntry(entry)
                        }.show()
                } else {
                    // Header swiped? Reset.
                    adapter.notifyItemChanged(position)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }
    
    // ... observeUiState, observeTags ... 

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            // Show loading?
                        }
                        is UiState.Success -> {
                            val filtered = if (viewModel.activeDates.value.isNotEmpty() && viewModel.selectedDate.value != null) {
                                state.entries.filter { 
                                    com.shuvostechworld.sonicmemories.utils.DateUtils.isSameDay(it.timestamp, viewModel.selectedDate.value!!)
                                }
                            } else {
                                state.entries
                            }
                            
                            adapter.submitEntries(filtered)
                            
                            val isEmpty = filtered.isEmpty()
                            binding.layoutEmptyState.isVisible = isEmpty
                            binding.recyclerView.isVisible = !isEmpty
                            
                            if (isEmpty) {
                                AccessibilityUtils.announceToScreenReader(binding.root, "No memories found")
                            }
                        }
                        is UiState.Error -> {
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        }
                        is UiState.Idle -> {
                             // Do nothing
                        }
                    }
                }
            }
        }
    }
    
    private fun observeTags() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // ... (existing tag observation logic) ...
                viewModel.allTags.collect { tagsSet ->
                     val tagsList = tagsSet.toList().sorted()
                     val adapter = android.widget.ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, tagsList)
                     binding.autoCompleteFilter.setAdapter(adapter)
                     
                     binding.autoCompleteFilter.setOnItemClickListener { parent, _, position, _ ->
                         val selectedTag = parent.getItemAtPosition(position) as String
                         viewModel.selectTag(selectedTag)
                     }
                     
                     // Adding "All Memories" and "On This Day"
                     val displayTags = listOf("All Memories", "📅 On This Day") + tagsList
                     val robustAdapter = android.widget.ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, displayTags)
                     binding.autoCompleteFilter.setAdapter(robustAdapter)

                     binding.autoCompleteFilter.setOnItemClickListener { parent, _, position, _ ->
                         val selected = parent.getItemAtPosition(position) as String
                         if (selected == "All Memories") {
                             viewModel.selectTag(null)
                         } else {
                             viewModel.selectTag(selected)
                         }
                     }
                }
            }
        }
        
        // Observe Flashback
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.flashbackEntry.collect { entry ->
                    if (entry != null) {
                        binding.cardFlashback.visibility = View.VISIBLE
                        binding.tvFlashbackTitle.text = entry.title.ifEmpty { "Audio Memory" }
                        
                        val yearsAgo = getYearsAgo(entry.timestamp)
                        binding.tvFlashbackDate.text = if (yearsAgo > 0) "$yearsAgo Year${if (yearsAgo > 1) "s" else ""} Ago Today" else "Memory from Today (Preview)"
                        
                        binding.cardFlashback.setOnClickListener {
                            viewModel.playMemory(entry)
                        }
                        
                        // Accessibility Announce
                        binding.cardFlashback.contentDescription = "On This Day: ${binding.tvFlashbackTitle.text}, ${binding.tvFlashbackDate.text}. Double tap to listen."
                    } else {
                        binding.cardFlashback.visibility = View.GONE
                    }
                }
            }
        }
    }
    
    private fun getYearsAgo(timestamp: Long): Int {
        val calendar = java.util.Calendar.getInstance()
        val currentYear = calendar.get(java.util.Calendar.YEAR)
        calendar.timeInMillis = timestamp
        val entryYear = calendar.get(java.util.Calendar.YEAR)
        return currentYear - entryYear
    }

    private fun showDeleteConfirmation(entry: com.shuvostechworld.sonicmemories.data.model.DiaryEntry) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete Memory?")
            .setMessage("Are you sure you want to delete '${entry.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteEntry(entry)
                Snackbar.make(binding.root, "Memory deleted", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                // Refresh adapter to ensure item state is correct if needed (mainly for swipe, less for button)
                adapter.notifyDataSetChanged() 
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
