package com.example.taglauncher

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

class HiddenAppsActivity : AppCompatActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HiddenAppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupEdgeToEdge()
        setContentView(R.layout.activity_hidden_apps)

        preferencesManager = PreferencesManager(this)

        setupToolbar()
        setupRecyclerView()
        setupWindowInsets()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.hiddenAppsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val allApps = loadAllApps()
        val hiddenApps = preferencesManager.getHiddenApps()

        adapter = HiddenAppsAdapter(allApps, hiddenApps) { packageName, isHidden ->
            if (isHidden) {
                preferencesManager.hideApp(packageName)
            } else {
                preferencesManager.unhideApp(packageName)
            }
        }
        recyclerView.adapter = adapter
    }

    private fun loadAllApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfoList: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)

        return resolveInfoList
            .filter { it.activityInfo.packageName != packageName }
            .map { resolveInfo ->
                AppInfo(
                    label = resolveInfo.loadLabel(packageManager).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = resolveInfo.loadIcon(packageManager)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }

    private fun setupWindowInsets() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.hiddenAppsRoot)) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.setPadding(0, insets.top, 0, 0)
            recyclerView.setPadding(0, 0, 0, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }
}
