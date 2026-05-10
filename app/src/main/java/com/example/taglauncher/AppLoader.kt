package com.example.taglauncher

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager

object AppLoader {

    fun loadAllApps(context: Context): List<AppInfo> {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val ownPackage = context.packageName
        val mainUser = Process.myUserHandle()

        val result = mutableListOf<AppInfo>()

        for (user in userManager.userProfiles) {
            val activities = launcherApps.getActivityList(null, user) ?: continue
            val isMain = user == mainUser
            val serial = if (isMain) 0L else userManager.getSerialNumberForUser(user)

            for (activity in activities) {
                val pkg = activity.applicationInfo.packageName
                if (isMain && pkg == ownPackage) continue

                val icon = activity.getBadgedIcon(0)
                val label = activity.label?.toString().orEmpty()

                result.add(
                    AppInfo(
                        label = label,
                        packageName = AppKey.build(pkg, serial),
                        icon = icon
                    )
                )
            }
        }

        return result.sortedBy { it.label.lowercase() }
    }
}
