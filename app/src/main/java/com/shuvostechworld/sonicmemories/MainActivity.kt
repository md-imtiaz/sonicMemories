package com.shuvostechworld.sonicmemories

import android.os.Bundle
import android.os.Vibrator
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.shuvostechworld.sonicmemories.databinding.ActivityMainBinding
import com.shuvostechworld.sonicmemories.ui.DiaryViewModel
import com.shuvostechworld.sonicmemories.ui.UiState
import com.shuvostechworld.sonicmemories.utils.AccessibilityUtils
import dagger.hilt.android.AndroidEntryPoint
import com.shuvostechworld.sonicmemories.ui.dialog.ReviewBottomSheet
import com.shuvostechworld.sonicmemories.utils.SoundManager
import com.shuvostechworld.sonicmemories.utils.BiometricAuthManager
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private var isActionProcessing = false

    private lateinit var binding: ActivityMainBinding
    private val viewModel: DiaryViewModel by viewModels()
    private lateinit var soundManager: SoundManager
    
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Auth Check
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
             startActivity(android.content.Intent(this, com.shuvostechworld.sonicmemories.ui.auth.SignInActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Onboarding Check
        val preferenceManager = com.shuvostechworld.sonicmemories.utils.PreferenceManager(this)
        if (preferenceManager.isFirstRun) {
            startActivity(android.content.Intent(this, com.shuvostechworld.sonicmemories.ui.onboarding.OnboardingActivity::class.java))
            finish()
            return
        }
        
        // Biometric Unlock
        val prefs = android.content.Context.MODE_PRIVATE.let { getSharedPreferences("sonic_prefs", it) }
        val isBiometricEnabled = prefs.getBoolean("biometric_enabled", true)
        
        if (isBiometricEnabled) {
            binding.layoutLockScreen.visibility = android.view.View.VISIBLE
            authenticateUser()
            binding.btnUnlock.setOnClickListener { authenticateUser() }
        } else {
             binding.layoutLockScreen.visibility = android.view.View.GONE
             viewModel.loadEntries() // Can load early if unlocked
        }
        
        setupNavigation()
        setupSearch()
        observeUiState()
    }
    
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        
        binding.bottomNav.setupWithNavController(navController)
        
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                 R.id.navigation_create, R.id.navigation_memory_detail -> {
                     binding.appBarLayout.visibility = android.view.View.GONE
                     binding.floatingDock.visibility = android.view.View.GONE
                 }
                 else -> {
                     binding.appBarLayout.visibility = android.view.View.VISIBLE
                     binding.floatingDock.visibility = android.view.View.VISIBLE
                 }
            }
        }
        
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_settings -> {
                    startActivity(android.content.Intent(this, com.shuvostechworld.sonicmemories.ui.settings.SettingsActivity::class.java))
                    false 
                }
                else -> {
                    androidx.navigation.ui.NavigationUI.onNavDestinationSelected(item, navController)
                    true
                }
            }
        }
    }

    private fun authenticateUser() {
        BiometricAuthManager.authenticate(
            activity = this,
            onSuccess = {
                binding.layoutLockScreen.visibility = android.view.View.GONE
                viewModel.loadEntries()
            },
            onError = {
                Toast.makeText(this, "Authentication required", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun setupSearch() {
        val searchView = findViewById<androidx.appcompat.widget.SearchView>(R.id.searchView)
        searchView?.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
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
    
    private fun observeUiState() {
         lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state is UiState.Error) {
                        Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
         }
    }


    
    override fun onDestroy() {
        super.onDestroy()
    }
}