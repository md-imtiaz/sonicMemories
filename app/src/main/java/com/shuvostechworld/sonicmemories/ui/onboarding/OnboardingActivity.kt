package com.shuvostechworld.sonicmemories.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.shuvostechworld.sonicmemories.MainActivity
import com.shuvostechworld.sonicmemories.R
import com.shuvostechworld.sonicmemories.databinding.ActivityOnboardingBinding
import com.shuvostechworld.sonicmemories.utils.PreferenceManager

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)

        val items = listOf(
            OnboardingItem(
                "Welcome to SonicMemories",
                "Your personal audio diary. Capture the essence of your moments through sound, location, and feelings.",
                R.drawable.ic_onboarding_welcome
            ),
            OnboardingItem(
                "Record Audio",
                "Capture your thoughts instantly with high-quality audio recording. Just tap and speak.",
                R.drawable.ic_onboarding_record
            ),
            OnboardingItem(
                "Add Context",
                "Your memories are automatically tagged with your location. Add manual tags and moods for better organization.",
                R.drawable.ic_onboarding_tag
            ),
            OnboardingItem(
                "Relive Moments",
                "Listen back to your audio diaries anytime. Search by date, tag, or mood to find exactly what you're looking for.",
                R.drawable.ic_onboarding_relive
            )
        )

        val adapter = OnboardingAdapter(items)
        binding.viewPager.adapter = adapter

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateUI(position, items.size)
                
                
                val item = items[position]
                val announcement = "Page ${position + 1} of ${items.size}. ${item.title}. ${item.description}"
                com.shuvostechworld.sonicmemories.utils.AccessibilityUtils.announceToScreenReader(binding.root, announcement)
            }
        })

        binding.btnNext.setOnClickListener {
            if (binding.viewPager.currentItem + 1 < items.size) {
                binding.viewPager.currentItem += 1
            } else {
                completeOnboarding()
            }
        }
    }

    private fun updateUI(position: Int, total: Int) {
        binding.tvIndicator.text = "${position + 1} / $total"
        if (position == total - 1) {
            binding.btnNext.text = "Get Started"
        } else {
            binding.btnNext.text = "Next"
        }
    }

    private fun completeOnboarding() {
        preferenceManager.isFirstRun = false
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
