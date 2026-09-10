package com.shuvostechworld.sonicmemories.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.shuvostechworld.sonicmemories.R
import com.shuvostechworld.sonicmemories.databinding.FragmentCalendarBinding
import com.shuvostechworld.sonicmemories.ui.DiaryViewModel
import com.shuvostechworld.sonicmemories.ui.UiState
import com.shuvostechworld.sonicmemories.ui.adapter.CalendarAdapter
import com.shuvostechworld.sonicmemories.utils.DateUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: DiaryViewModel by activityViewModels()
    private lateinit var calendarAdapter: CalendarAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCalendar()
        observeUiState()
    }

    private fun setupCalendar() {
        val calendar = Calendar.getInstance()
        val currentMonthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
        binding.tvMonthLabel.text = currentMonthName

        calendarAdapter = CalendarAdapter(
            onDateClick = { day ->
                if (day.dayOfMonth != -1) {
                    viewModel.selectDate(day.timestamp)
                    findNavController().navigate(R.id.action_calendar_to_home)
                }
            }
        )
        
        
        
        
        
        
        
        calendarAdapter.setMonthName(SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time))
        binding.calendarRecyclerView.adapter = calendarAdapter
    }
    
    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                
                launch {
                    viewModel.uiState.collect { state ->
                        if (state is UiState.Success) {
                            updateCalendar(state.entries)
                        }
                    }
                }
                
                
                launch {
                    viewModel.selectedDate.collect { date ->
                         
                         val state = viewModel.uiState.value
                         if (state is UiState.Success) {
                             updateCalendar(state.entries)
                         }
                    }
                }
            }
        }
    }

    private fun updateCalendar(entries: List<com.shuvostechworld.sonicmemories.data.model.DiaryEntry>) {
        val calendar = Calendar.getInstance()
        
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)

        
        
        
        
        val days = generateDaysForMonth(currentYear, currentMonth, viewModel.activeDates.value, viewModel.selectedDate.value)
        calendarAdapter.submitList(days)
    }

    private fun generateDaysForMonth(year: Int, month: Int, activeDates: Set<Long>, selectedDate: Long?): List<com.shuvostechworld.sonicmemories.ui.adapter.CalendarDay> {
        val calendar = Calendar.getInstance()
        calendar.set(year, month, 1)
        
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) 
        
        val days = mutableListOf<com.shuvostechworld.sonicmemories.ui.adapter.CalendarDay>()
        
        
        for (i in 1 until firstDayOfWeek) {
            days.add(com.shuvostechworld.sonicmemories.ui.adapter.CalendarDay(-1, 0, false))
        }
        
        for (day in 1..daysInMonth) {
            calendar.set(year, month, day, 0, 0, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val timestamp = calendar.timeInMillis
            
            
            
            
            
            
            
            
            
            val hasMemory = activeDates.any { DateUtils.isSameDay(it, timestamp) }
            val isSelected = selectedDate?.let { DateUtils.isSameDay(it, timestamp) } ?: false
            
            days.add(com.shuvostechworld.sonicmemories.ui.adapter.CalendarDay(
                dayOfMonth = day,
                timestamp = timestamp,
                isCurrentMonth = true,
                hasMemory = hasMemory,
                isSelected = isSelected
            ))
        }
        
        return days
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
