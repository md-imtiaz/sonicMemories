package com.shuvostechworld.sonicmemories.ui.onboarding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shuvostechworld.sonicmemories.R

data class OnboardingItem(
    val title: String,
    val description: String,
    val imageResId: Int
)

class OnboardingAdapter(private val items: List<OnboardingItem>) : RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

    inner class OnboardingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivImage = view.findViewById<ImageView>(R.id.iv_onboarding_image)
        private val tvTitle = view.findViewById<TextView>(R.id.tv_onboarding_title)
        private val tvDesc = view.findViewById<TextView>(R.id.tv_onboarding_desc)

        fun bind(item: OnboardingItem) {
            tvTitle.text = item.title
            tvDesc.text = item.description
            ivImage.setImageResource(item.imageResId)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_onboarding_page, parent, false)
        return OnboardingViewHolder(view)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
