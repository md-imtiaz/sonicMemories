package com.shuvostechworld.sonicmemories.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shuvostechworld.sonicmemories.R
import com.shuvostechworld.sonicmemories.databinding.ItemCalendarDayBinding
import java.util.Calendar

data class CalendarDay(
    val dayOfMonth: Int,
    val timestamp: Long,
    val isCurrentMonth: Boolean,
    val hasMemory: Boolean = false,
    val isSelected: Boolean = false
)

class CalendarAdapter(
    private var currentMonthName: String = "",
    private val onDateClick: (CalendarDay) -> Unit
) : ListAdapter<CalendarDay, CalendarAdapter.DayViewHolder>(DayDiffCallback()) {
    
    fun setMonthName(name: String) {
        currentMonthName = name
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val binding = ItemCalendarDayBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DayViewHolder(private val binding: ItemCalendarDayBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(day: CalendarDay) {
            if (day.dayOfMonth == -1) {
                // Placeholder for empty slots
                binding.root.visibility = View.INVISIBLE
                binding.root.setOnClickListener(null)
                return
            } else {
                binding.root.visibility = View.VISIBLE
            }

            binding.tvDay.text = day.dayOfMonth.toString()
            
            // Highlight selected
            if (day.isSelected) {
                binding.cardDay.setCardBackgroundColor(
                    androidx.core.content.ContextCompat.getColor(binding.root.context, R.color.purple_200) // Using default accent or similar
                )
                binding.tvDay.setTextColor(
                    androidx.core.content.ContextCompat.getColor(binding.root.context, android.R.color.black)
                )
            } else {
                binding.cardDay.setCardBackgroundColor(
                    androidx.core.content.ContextCompat.getColor(binding.root.context, android.R.color.transparent)
                )
                binding.tvDay.setTextColor(
                     androidx.core.content.ContextCompat.getColor(binding.root.context, android.R.color.white) // Assuming dark theme default
                )
            }

            // Indicator
            binding.ivIndicator.visibility = if (day.hasMemory) View.VISIBLE else View.GONE

            binding.root.setOnClickListener {
                onDateClick(day)
            }
            
            // Accessibility
            val niceDate = "$currentMonthName ${day.dayOfMonth}"
            val memoryState = if(day.hasMemory) "Has saved memories." else "No memories."
            val selectionState = if(day.isSelected) "Selected." else "Double tap to select."
            
            binding.root.contentDescription = "$niceDate. $memoryState $selectionState"
        }
    }

    class DayDiffCallback : DiffUtil.ItemCallback<CalendarDay>() {
        override fun areItemsTheSame(oldItem: CalendarDay, newItem: CalendarDay): Boolean {
            return oldItem.timestamp == newItem.timestamp && oldItem.dayOfMonth == newItem.dayOfMonth
        }

        override fun areContentsTheSame(oldItem: CalendarDay, newItem: CalendarDay): Boolean {
            return oldItem == newItem
        }
    }
}
