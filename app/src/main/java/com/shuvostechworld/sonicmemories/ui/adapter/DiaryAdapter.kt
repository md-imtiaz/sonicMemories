package com.shuvostechworld.sonicmemories.ui.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shuvostechworld.sonicmemories.R
import com.shuvostechworld.sonicmemories.data.model.DiaryEntry
import com.shuvostechworld.sonicmemories.databinding.ItemDateHeaderBinding
import com.shuvostechworld.sonicmemories.databinding.ItemDiaryEntryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class ListItem {
    data class Header(val id: String, val date: String) : ListItem()
    data class Entry(val entry: DiaryEntry) : ListItem()
}

class DiaryAdapter(
    private val onPlayClick: (DiaryEntry) -> Unit,
    private val onItemClick: (DiaryEntry) -> Unit,
    private val onEditClick: (DiaryEntry) -> Unit,
    private val onDeleteClick: (DiaryEntry) -> Unit
) : ListAdapter<ListItem, RecyclerView.ViewHolder>(DiffCallback) {

    private var currentPlayingId: String? = null

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
    }

    fun submitEntries(entries: List<DiaryEntry>) {
        val groupedList = mutableListOf<ListItem>()
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault()) // e.g. "Monday, 12 December"
        
        // Group by day
        val grouped = entries.sortedByDescending { it.timestamp }.groupBy { 
            dateFormat.format(Date(it.timestamp)) 
        }

        grouped.forEach { (dateString, dayEntries) ->
            groupedList.add(ListItem.Header("header_$dateString", dateString))
            groupedList.addAll(dayEntries.map { ListItem.Entry(it) })
        }
        
        submitList(groupedList)
    }

    fun updatePlayingState(playingId: String?) {
        val previousId = currentPlayingId
        currentPlayingId = playingId
        
        // Find positions to notify (Scanning list since ID map is not kept)
        currentList.forEachIndexed { index, item ->
            if (item is ListItem.Entry) {
                if (item.entry.id == previousId || item.entry.id == playingId) {
                    notifyItemChanged(index)
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ListItem.Header -> TYPE_HEADER
            is ListItem.Entry -> TYPE_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> {
                try {
                     val binding = ItemDateHeaderBinding.inflate(inflater, parent, false)
                     HeaderViewHolder(binding)
                } catch (e: NoClassDefFoundError) {
                     throw e 
                }
            }
            else -> {
                val binding = ItemDiaryEntryBinding.inflate(inflater, parent, false)
                DiaryViewHolder(binding)
            }
        }
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ListItem.Entry -> (holder as DiaryViewHolder).bind(
                item.entry, 
                item.entry.id == currentPlayingId, 
                onPlayClick, 
                onItemClick,
                onEditClick,
                onDeleteClick
            )
        }
    }

    // ...

    class HeaderViewHolder(private val binding: ItemDateHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: ListItem.Header) {
            binding.tvHeaderDate.text = header.date
        }
    }

    class DiaryViewHolder(private val binding: ItemDiaryEntryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            entry: DiaryEntry, 
            isPlaying: Boolean, 
            onPlayClick: (DiaryEntry) -> Unit, 
            onItemClick: (DiaryEntry) -> Unit,
            onEditClick: (DiaryEntry) -> Unit,
            onDeleteClick: (DiaryEntry) -> Unit
        ) {
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val timeString = timeFormat.format(Date(entry.timestamp))
            
            binding.tvTitle.text = entry.title
            binding.tvTime.text = timeString
            
            // Mood Coloring & Accessibility
            val moodColor: Int
            val moodDescription: String
            
            when (entry.mood) {
                1 -> { // Awful (Sad)
                    moodColor = Color.parseColor("#FF5252") // Red
                    moodDescription = "Difficult Memory"
                }
                2 -> { // Bad
                    moodColor = Color.parseColor("#FF9800") // Orange
                    moodDescription = "Bad Memory"
                }
                3 -> { // Okay
                    moodColor = Color.parseColor("#FFEB3B") // Yellow
                    moodDescription = "Okay Memory"
                }
                4 -> { // Good
                    moodColor = Color.parseColor("#CDDC39") // Lime
                    moodDescription = "Good Memory"
                }
                5 -> { // Great
                    moodColor = Color.parseColor("#00E5FF") // Cyan
                    moodDescription = "Great Memory"
                }
                else -> { // Default
                    moodColor = Color.parseColor("#AAAAAA")
                    moodDescription = "Memory"
                }
            }
            
            // Apply Mood Color to Icon and Play Button
            binding.ivMood.setColorFilter(moodColor)
            binding.root.strokeColor = moodColor
            
            // Accessibility
            binding.root.contentDescription = "$moodDescription. ${entry.title}. Created at $timeString."

            if (entry.audioUrl.isNotEmpty()) {
                binding.layoutAudio.visibility = android.view.View.VISIBLE
                
                binding.btnPlay.setTextColor(moodColor)
                binding.btnPlay.iconTint = ColorStateList.valueOf(moodColor)
                binding.chipLocation.chipIconTint = ColorStateList.valueOf(moodColor)
                binding.chipLocation.setTextColor(moodColor)

                if (isPlaying) {
                    binding.btnPlay.text = "Pause"
                    binding.btnPlay.setIconResource(R.drawable.ic_pause_24)
                } else {
                    binding.btnPlay.text = "Play"
                    binding.btnPlay.setIconResource(R.drawable.ic_play_arrow_24)
                }
            } else {
                binding.layoutAudio.visibility = android.view.View.GONE
            }
            
            // Location
            if (!entry.locationAddress.isNullOrEmpty()) {
                binding.chipLocation.visibility = android.view.View.VISIBLE
                binding.chipLocation.text = entry.locationAddress
                binding.chipLocation.contentDescription = "Location: ${entry.locationAddress}. Double tap to open in maps."
                
                binding.chipLocation.setOnClickListener {
                    val uri = "geo:${entry.latitude},${entry.longitude}?q=${android.net.Uri.encode(entry.locationAddress)}"
                    val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                    mapIntent.setPackage("com.google.android.apps.maps")
                    
                    try {
                        binding.root.context.startActivity(mapIntent)
                    } catch (e: Exception) {
                         // Fallback if Maps is not installed
                         val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=${android.net.Uri.encode(entry.locationAddress)}"))
                         binding.root.context.startActivity(webIntent)
                    }
                }
            } else {
                binding.chipLocation.visibility = android.view.View.GONE
            }

            binding.btnPlay.setOnClickListener { onPlayClick(entry) }
            binding.root.setOnClickListener { onItemClick(entry) }
            
            // New Action Buttons
            binding.btnEdit.setOnClickListener { onEditClick(entry) }
            binding.btnDelete.setOnClickListener { onDeleteClick(entry) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return if (oldItem is ListItem.Entry && newItem is ListItem.Entry) {
                oldItem.entry.id == newItem.entry.id
            } else if (oldItem is ListItem.Header && newItem is ListItem.Header) {
                oldItem.id == newItem.id
            } else {
                false
            }
        }

        override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return oldItem == newItem
        }
    }
}
