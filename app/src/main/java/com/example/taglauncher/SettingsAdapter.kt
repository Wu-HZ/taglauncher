package com.example.taglauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class SettingsAdapter(
    private var items: List<SettingsItem>,
    private val onItemClick: (SettingsItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_TOGGLE = 1
        private const val TYPE_CHOICE = 2
        private const val TYPE_NAVIGATION = 3
        private const val TYPE_INFO = 4
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is SettingsItem.Header -> TYPE_HEADER
            is SettingsItem.Toggle -> TYPE_TOGGLE
            is SettingsItem.Choice -> TYPE_CHOICE
            is SettingsItem.Navigation -> TYPE_NAVIGATION
            is SettingsItem.Info -> TYPE_INFO
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_settings_header, parent, false)
            )
            TYPE_TOGGLE -> ToggleViewHolder(
                inflater.inflate(R.layout.item_settings_toggle, parent, false)
            )
            else -> StandardViewHolder(
                inflater.inflate(R.layout.item_settings_standard, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is SettingsItem.Header -> (holder as HeaderViewHolder).bind(item)
            is SettingsItem.Toggle -> (holder as ToggleViewHolder).bind(item, onItemClick)
            is SettingsItem.Choice -> (holder as StandardViewHolder).bind(item, onItemClick)
            is SettingsItem.Navigation -> (holder as StandardViewHolder).bind(item, onItemClick)
            is SettingsItem.Info -> (holder as StandardViewHolder).bind(item, null)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<SettingsItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.headerTitle)

        fun bind(item: SettingsItem.Header) {
            titleText.text = item.title
        }
    }

    class ToggleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.settingIcon)
        private val titleText: TextView = itemView.findViewById(R.id.settingTitle)
        private val summaryText: TextView = itemView.findViewById(R.id.settingSummary)
        private val toggle: SwitchCompat = itemView.findViewById(R.id.settingToggle)

        fun bind(item: SettingsItem.Toggle, onClick: (SettingsItem) -> Unit) {
            titleText.text = item.title
            summaryText.text = item.summary
            toggle.isChecked = item.isChecked

            if (item.icon != null) {
                iconView.setImageResource(item.icon)
                iconView.visibility = View.VISIBLE
            } else {
                iconView.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onClick(item)
            }
            toggle.setOnClickListener {
                onClick(item)
            }
        }
    }

    class StandardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.settingIcon)
        private val titleText: TextView = itemView.findViewById(R.id.settingTitle)
        private val summaryText: TextView = itemView.findViewById(R.id.settingSummary)

        fun bind(item: SettingsItem.Choice, onClick: ((SettingsItem) -> Unit)?) {
            titleText.text = item.title
            summaryText.text = item.summary

            if (item.icon != null) {
                iconView.setImageResource(item.icon)
                iconView.visibility = View.VISIBLE
            } else {
                iconView.visibility = View.GONE
            }

            if (onClick != null) {
                itemView.setOnClickListener { onClick(item) }
            }
        }

        fun bind(item: SettingsItem.Navigation, onClick: ((SettingsItem) -> Unit)?) {
            titleText.text = item.title
            summaryText.text = item.summary

            if (item.icon != null) {
                iconView.setImageResource(item.icon)
                iconView.visibility = View.VISIBLE
            } else {
                iconView.visibility = View.GONE
            }

            if (onClick != null) {
                itemView.setOnClickListener { onClick(item) }
            }
        }

        fun bind(item: SettingsItem.Info, onClick: ((SettingsItem) -> Unit)?) {
            titleText.text = item.title
            summaryText.text = item.summary

            if (item.icon != null) {
                iconView.setImageResource(item.icon)
                iconView.visibility = View.VISIBLE
            } else {
                iconView.visibility = View.GONE
            }

            if (onClick != null) {
                itemView.setOnClickListener { onClick(item) }
            } else {
                itemView.isClickable = false
            }
        }
    }
}
