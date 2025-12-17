package com.shuvostechworld.sonicmemories.ui.settings

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.shuvostechworld.sonicmemories.databinding.ActivitySettingsBinding
import com.shuvostechworld.sonicmemories.receiver.ReminderReceiver
import com.shuvostechworld.sonicmemories.ui.DiaryViewModel
import com.shuvostechworld.sonicmemories.utils.AccessibilityUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: DiaryViewModel by viewModels()
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "sonic_prefs"
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val KEY_REMINDER_HOUR = "reminder_hour"
        private const val KEY_REMINDER_MINUTE = "reminder_minute"
        private const val REMOTE_CONFIG_BIOMETRIC_KEY = "biometric_enabled" // Using same key as typically used
        private const val REQUEST_CODE_ALARM = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        createNotificationChannel()
        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        // Load prefs
        val isEnabled = prefs.getBoolean(KEY_REMINDER_ENABLED, false)
        val hour = prefs.getInt(KEY_REMINDER_HOUR, 20) // Default 8 PM
        val minute = prefs.getInt(KEY_REMINDER_MINUTE, 0)

        binding.switchReminder.isChecked = isEnabled
        updateTimeText(hour, minute)
        
        binding.layoutTimePicker.alpha = if (isEnabled) 1.0f else 0.5f
        binding.layoutTimePicker.isEnabled = isEnabled
        
        binding.switchBiometric.isChecked = prefs.getBoolean("biometric_enabled", true) // Default true as per MainActivity behavior?
        // Actually MainActivity defaults to enforcing it if code is there.
        // Let's assume we want to honor this setting in MainActivity.
    }

    private fun updateTimeText(hour: Int, minute: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        
        val format = java.text.SimpleDateFormat("hh:mm a", Locale.getDefault())
        binding.tvReminderTime.text = format.format(calendar.time)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutTimePicker.alpha = if (isChecked) 1.0f else 0.5f
            binding.layoutTimePicker.isEnabled = isChecked
            
            val editor = prefs.edit()
            editor.putBoolean(KEY_REMINDER_ENABLED, isChecked)
            editor.apply()

            if (isChecked) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                     if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                         requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1002)
                     } else {
                         scheduleAlarm()
                         AccessibilityUtils.announceToScreenReader(binding.root, "Daily reminder enabled")
                     }
                } else {
                    scheduleAlarm()
                    AccessibilityUtils.announceToScreenReader(binding.root, "Daily reminder enabled")
                }
            } else {
                cancelAlarm()
                AccessibilityUtils.announceToScreenReader(binding.root, "Daily reminder disabled")
            }
        }

        binding.layoutTimePicker.setOnClickListener {
            if (binding.switchReminder.isChecked) {
                showTimePicker()
            }
        }

        binding.btnExport.setOnClickListener {
            exportMemories()
        }
        
        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            val editor = prefs.edit()
            editor.putBoolean("biometric_enabled", isChecked)
            editor.apply()
            
            if (isChecked) {
                AccessibilityUtils.announceToScreenReader(binding.root, "Biometric security enabled")
            } else {
                AccessibilityUtils.announceToScreenReader(binding.root, "Biometric security disabled")
            }
        }
        
        binding.btnDeleteAll.setOnClickListener {
            showDeleteConfirmation()
        }
        
        binding.btnContact.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:${getString(com.shuvostechworld.sonicmemories.R.string.contact_email)}")
                putExtra(Intent.EXTRA_SUBJECT, "SonicMemories Support")
            }
            try {
                startActivity(Intent.createChooser(intent, "Send Email"))
            } catch (e: Exception) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
            }
        }
        
        // WhatsApp Listener
        binding.root.findViewById<android.view.View>(com.shuvostechworld.sonicmemories.R.id.btn_whatsapp)?.setOnClickListener {
             openUrl(getString(com.shuvostechworld.sonicmemories.R.string.whatsapp_url))
        }
        
        binding.btnWebsite.setOnClickListener {
            openUrl(getString(com.shuvostechworld.sonicmemories.R.string.website_url))
        }
        
        binding.root.findViewById<android.view.View>(com.shuvostechworld.sonicmemories.R.id.btn_tutorial)?.setOnClickListener {
            startActivity(Intent(this, com.shuvostechworld.sonicmemories.ui.onboarding.OnboardingActivity::class.java))
        }
        
        binding.btnPrivacy.setOnClickListener {
             // Assuming privacy policy is at /privacy or similar
             openUrl(getString(com.shuvostechworld.sonicmemories.R.string.website_url) + "/privacy")
        }
    }
    
    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showDeleteConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete All Data?")
            .setMessage("This will permanently delete all your memories and recordings. This action cannot be undone.")
            .setPositiveButton("Delete Everything") { _, _ ->
                viewModel.deleteAllEntries()
                Toast.makeText(this, "All memories deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTimePicker() {
        val currentHour = prefs.getInt(KEY_REMINDER_HOUR, 20)
        val currentMinute = prefs.getInt(KEY_REMINDER_MINUTE, 0)

        val picker = TimePickerDialog(this, { _, hourOfDay, minute ->
            val editor = prefs.edit()
            editor.putInt(KEY_REMINDER_HOUR, hourOfDay)
            editor.putInt(KEY_REMINDER_MINUTE, minute)
            editor.apply()
            
            updateTimeText(hourOfDay, minute)
            scheduleAlarm() // Reschedule with new time
            
        }, currentHour, currentMinute, false)
        
        picker.show()
    }

    private fun scheduleAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Request permission
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.data = android.net.Uri.parse("package:$packageName")
                startActivity(intent)
                Toast.makeText(this, "Please allow exact alarms for reminders", Toast.LENGTH_LONG).show()
                // Reset switch until granted? Or leave it and let user retry. 
                // Better UX: Leave switch, but warn. For now, just opening settings is clear enough.
                binding.switchReminder.isChecked = false
                return
            }
        }

        val hour = prefs.getInt(KEY_REMINDER_HOUR, 20)
        val minute = prefs.getInt(KEY_REMINDER_MINUTE, 0)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(this, ReminderReceiver::class.java)
        // Use FLAG_IMMUTABLE for API 31+
        val pendingIntent = PendingIntent.getBroadcast(
            this, 
            REQUEST_CODE_ALARM, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use setExactAndAllowWhileIdle for reliable reminders
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            Toast.makeText(this, "Reminder scheduled", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
             Toast.makeText(this, "Permission required for exact alarms", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelAlarm() {
        val intent = Intent(this, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 
            REQUEST_CODE_ALARM, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
        Toast.makeText(this, "Reminder cancelled", Toast.LENGTH_SHORT).show()
    }
    
    private fun exportMemories() {
        Toast.makeText(this, "Generating Export...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val jsonFile = viewModel.exportMemoriesToJson(this@SettingsActivity)
            if (jsonFile != null) {
                shareFile(jsonFile)
            } else {
                Toast.makeText(this@SettingsActivity, "Export failed (No data?)", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun shareFile(file: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.provider",
            file
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        startActivity(Intent.createChooser(intent, "Export Memories"))
    }
    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "Daily Reflection"
            val descriptionText = "Reminders to record your daily memory"
            val importance = android.app.NotificationManager.IMPORTANCE_DEFAULT
            val channel = android.app.NotificationChannel("sonic_daily_reminder", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: android.app.NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002) {
             if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                 // Permission granted, schedule alarm if switch is on
                 if (binding.switchReminder.isChecked) {
                     scheduleAlarm()
                 }
             } else {
                 Toast.makeText(this, "Notifications permission denied", Toast.LENGTH_SHORT).show()
                 binding.switchReminder.isChecked = false
             }
        }
    }
}
