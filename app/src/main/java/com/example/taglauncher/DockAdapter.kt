package com.example.taglauncher

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView

class DockAdapter(
    private val context: Context,
    private var dockApps: MutableList<AppInfo>,
    private val onAppClick: (AppInfo) -> Unit,
    private val onRemoveFromDock: (AppInfo) -> Unit
) : RecyclerView.Adapter<DockAdapter.DockViewHolder>() {

    class DockViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.dockAppIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DockViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dock_app, parent, false)
        return DockViewHolder(view)
    }

    override fun onBindViewHolder(holder: DockViewHolder, position: Int) {
        val appInfo = dockApps[position]
        holder.appIcon.setImageDrawable(appInfo.icon)

        holder.itemView.setOnClickListener {
            onAppClick(appInfo)
        }

        holder.itemView.setOnLongClickListener { view ->
            showContextMenu(view, appInfo)
            true
        }
    }

    override fun getItemCount(): Int = dockApps.size

    private fun showContextMenu(view: View, appInfo: AppInfo) {
        val popup = PopupMenu(context, view)
        popup.menuInflater.inflate(R.menu.dock_context_menu, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_remove_from_dock -> {
                    onRemoveFromDock(appInfo)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    fun updateDockApps(newList: List<AppInfo>) {
        dockApps.clear()
        dockApps.addAll(newList)
        notifyDataSetChanged()
    }

    fun addApp(appInfo: AppInfo) {
        if (dockApps.size < 5 && !dockApps.any { it.packageName == appInfo.packageName }) {
            dockApps.add(appInfo)
            notifyItemInserted(dockApps.size - 1)
        }
    }

    fun removeApp(appInfo: AppInfo) {
        val index = dockApps.indexOfFirst { it.packageName == appInfo.packageName }
        if (index != -1) {
            dockApps.removeAt(index)
            notifyItemRemoved(index)
        }
    }
}
