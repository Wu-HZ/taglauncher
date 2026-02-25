package com.example.taglauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HiddenAppsAdapter(
    private val apps: List<AppInfo>,
    private var hiddenApps: Set<String>,
    private val onToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<HiddenAppsAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.appIcon)
        val label: TextView = itemView.findViewById(R.id.appLabel)
        val checkbox: CheckBox = itemView.findViewById(R.id.appCheckbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hidden_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        holder.checkbox.isChecked = hiddenApps.contains(app.packageName)

        holder.itemView.setOnClickListener {
            val newState = !holder.checkbox.isChecked
            holder.checkbox.isChecked = newState
            onToggle(app.packageName, newState)
            hiddenApps = if (newState) {
                hiddenApps + app.packageName
            } else {
                hiddenApps - app.packageName
            }
        }

        holder.checkbox.setOnClickListener {
            onToggle(app.packageName, holder.checkbox.isChecked)
            hiddenApps = if (holder.checkbox.isChecked) {
                hiddenApps + app.packageName
            } else {
                hiddenApps - app.packageName
            }
        }
    }

    override fun getItemCount(): Int = apps.size
}
