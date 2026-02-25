package com.example.taglauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AppChangeReceiver(
    private val onAppChanged: () -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                onAppChanged()
            }
        }
    }
}
