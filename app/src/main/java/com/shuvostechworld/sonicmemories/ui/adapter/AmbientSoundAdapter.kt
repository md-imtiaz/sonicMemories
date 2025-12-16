package com.shuvostechworld.sonicmemories.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shuvostechworld.sonicmemories.data.remote.SoundItem
import com.shuvostechworld.sonicmemories.databinding.ItemAmbientSoundBinding

class AmbientSoundAdapter(
    private val onPreviewClick: (SoundItem, Boolean) -> Unit,
    private val onAddClick: (SoundItem) -> Unit
) : ListAdapter<SoundItem, AmbientSoundAdapter.SoundViewHolder>(SoundDiffCallback()) {

    private var playingId: Int? = null
    private var selectedId: Int? = null

    fun updatePlayingState(id: Int?) {
        val previous = playingId
        playingId = id
        if (previous != null) notifyItemChanged(currentList.indexOfFirst { it.id == previous })
        if (id != null) notifyItemChanged(currentList.indexOfFirst { it.id == id })
    }
    
    fun setSelection(id: Int?) {
        val previous = selectedId
        selectedId = id
        if (previous != null) notifyItemChanged(currentList.indexOfFirst { it.id == previous })
        if (id != null) notifyItemChanged(currentList.indexOfFirst { it.id == id })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SoundViewHolder {
        val binding = ItemAmbientSoundBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SoundViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SoundViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, item.id == playingId, item.id == selectedId)
    }

    inner class SoundViewHolder(private val binding: ItemAmbientSoundBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SoundItem, isPlaying: Boolean, isSelected: Boolean) {
            binding.tvSoundName.text = item.name
            
            // Play/Pause Icon State
            if (isPlaying) {
                binding.ivIcon.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                binding.ivIcon.setImageResource(android.R.drawable.ic_media_play)
            }

            // Added/Selected State
            if (isSelected) {
                binding.ivCheck.visibility = android.view.View.VISIBLE
                binding.btnAdd.visibility = android.view.View.GONE
                binding.cardSoundItem.strokeWidth = 4
            } else {
                binding.ivCheck.visibility = android.view.View.GONE
                binding.btnAdd.visibility = android.view.View.VISIBLE
                binding.cardSoundItem.strokeWidth = 0
            }
            
            // Click Listeners
            binding.root.setOnClickListener {
                if (isPlaying) {
                    onPreviewClick(item, false) // Stop
                } else {
                    onPreviewClick(item, true) // Play
                }
            }
            
            binding.btnAdd.setOnClickListener {
                onAddClick(item)
            }
            
            binding.root.contentDescription = "${item.name}. ${if(isPlaying) "Playing" else "Paused"}. ${if(isSelected) "Selected" else "Not selected"}"
        }
    }

    class SoundDiffCallback : DiffUtil.ItemCallback<SoundItem>() {
        override fun areItemsTheSame(oldItem: SoundItem, newItem: SoundItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SoundItem, newItem: SoundItem): Boolean {
            return oldItem == newItem
        }
    }
}
