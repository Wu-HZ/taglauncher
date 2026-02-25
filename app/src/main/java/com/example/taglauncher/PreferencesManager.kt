package com.example.taglauncher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "tag_launcher_prefs"

        // Dock
        private const val KEY_DOCK_APPS = "dock_apps"
        private const val KEY_DOCK_ENABLED = "dock_enabled"
        private const val KEY_MAX_DOCK_APPS = "max_dock_apps"

        // Appearance
        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_SHOW_APP_LABELS = "show_app_labels"
        private const val KEY_ICON_SIZE = "icon_size"

        // Gestures
        private const val KEY_SWIPE_DOWN_ACTION = "swipe_down_action"
        private const val KEY_DOUBLE_TAP_ACTION = "double_tap_action"

        // Search
        private const val KEY_SEARCH_BAR_ENABLED = "search_bar_enabled"

        // Hidden Apps
        private const val KEY_HIDDEN_APPS = "hidden_apps"

        // Tag Button
        private const val KEY_TAG_BUTTON_POSITION = "tag_button_position"
        private const val KEY_TAG_BUTTON_OFFSET_X = "tag_button_offset_x"
        private const val KEY_TAG_BUTTON_OFFSET_Y = "tag_button_offset_y"
        private const val KEY_TAG_BUTTON_ICON = "tag_button_icon"
        private const val KEY_TAG_BUTTON_SIZE = "tag_button_size"
        private const val KEY_TAG_BUTTON_ALPHA = "tag_button_alpha"
        private const val KEY_TAG_BUTTON_COLOR = "tag_button_color"

        // App Tags
        private const val KEY_USER_TAGS = "user_tags"
        private const val KEY_APP_TAG_ASSOCIATIONS = "app_tag_associations"
        private const val KEY_PINNED_TAG_POSITIONS = "pinned_tag_positions"

        // Defaults
        const val DEFAULT_GRID_COLUMNS = 4
        const val DEFAULT_MAX_DOCK_APPS = 5
        const val DEFAULT_ICON_SIZE = 48
        const val SWIPE_ACTION_NOTIFICATIONS = 0
        const val SWIPE_ACTION_SEARCH = 1
        const val DOUBLE_TAP_ACTION_NONE = 0
        const val DOUBLE_TAP_ACTION_LOCK = 1
        const val DOUBLE_TAP_ACTION_SLEEP = 2

        // Tag Button Positions
        const val TAG_POSITION_BOTTOM_RIGHT = 0
        const val TAG_POSITION_BOTTOM_LEFT = 1
        const val TAG_POSITION_BOTTOM_CENTER = 2
        const val TAG_POSITION_RIGHT_CENTER = 3
        const val TAG_POSITION_LEFT_CENTER = 4

        // Tag Button Icons
        const val TAG_ICON_TAG = 0
        const val TAG_ICON_LABEL = 1
        const val TAG_ICON_CIRCLE = 2
        const val TAG_ICON_GRID = 3
        const val TAG_ICON_STAR = 4
        const val TAG_ICON_PLUS = 5

        // Tag Button Defaults
        const val DEFAULT_TAG_BUTTON_SIZE = 56
        const val DEFAULT_TAG_BUTTON_ALPHA = 80  // 0-100 percentage

        // Tag Limits
        const val MAX_TAGS = 25

        // Desktop Layout (Component-based)
        private const val KEY_DESKTOP_LAYOUT = "desktop_layout"
        private const val KEY_COMPONENT_TEMPLATES = "component_templates"
    }

    // ==================== Dock ====================

    fun getDockApps(): List<String> {
        val json = prefs.getString(KEY_DOCK_APPS, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveDockApps(packageNames: List<String>) {
        val jsonArray = JSONArray(packageNames.take(getMaxDockApps()))
        prefs.edit().putString(KEY_DOCK_APPS, jsonArray.toString()).apply()
    }

    fun removeFromDock(packageName: String): Boolean {
        val current = getDockApps().toMutableList()
        val removed = current.remove(packageName)
        if (removed) {
            saveDockApps(current)
        }
        return removed
    }

    fun isInDock(packageName: String): Boolean {
        return getDockApps().contains(packageName)
    }

    fun isDockEnabled(): Boolean {
        return prefs.getBoolean(KEY_DOCK_ENABLED, true)
    }

    fun setDockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DOCK_ENABLED, enabled).apply()
    }

    fun getMaxDockApps(): Int {
        return prefs.getInt(KEY_MAX_DOCK_APPS, DEFAULT_MAX_DOCK_APPS)
    }

    fun setMaxDockApps(count: Int) {
        prefs.edit().putInt(KEY_MAX_DOCK_APPS, count.coerceIn(3, 7)).apply()
        // Trim dock apps if needed
        val currentApps = getDockApps()
        if (currentApps.size > count) {
            saveDockApps(currentApps.take(count))
        }
    }

    // ==================== Appearance ====================

    fun getGridColumns(): Int {
        return prefs.getInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS)
    }

    fun setGridColumns(columns: Int) {
        prefs.edit().putInt(KEY_GRID_COLUMNS, columns.coerceIn(3, 6)).apply()
    }

    fun getShowAppLabels(): Boolean {
        return prefs.getBoolean(KEY_SHOW_APP_LABELS, true)
    }

    fun setShowAppLabels(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_APP_LABELS, show).apply()
    }

    fun getIconSize(): Int {
        return prefs.getInt(KEY_ICON_SIZE, DEFAULT_ICON_SIZE)
    }

    fun setIconSize(size: Int) {
        prefs.edit().putInt(KEY_ICON_SIZE, size.coerceIn(36, 72)).apply()
    }

    // ==================== Gestures ====================

    fun getSwipeDownAction(): Int {
        return prefs.getInt(KEY_SWIPE_DOWN_ACTION, SWIPE_ACTION_NOTIFICATIONS)
    }

    fun setSwipeDownAction(action: Int) {
        prefs.edit().putInt(KEY_SWIPE_DOWN_ACTION, action).apply()
    }

    fun getDoubleTapAction(): Int {
        return prefs.getInt(KEY_DOUBLE_TAP_ACTION, DOUBLE_TAP_ACTION_NONE)
    }

    fun setDoubleTapAction(action: Int) {
        prefs.edit().putInt(KEY_DOUBLE_TAP_ACTION, action).apply()
    }

    // ==================== Search ====================

    fun isSearchBarEnabled(): Boolean {
        return prefs.getBoolean(KEY_SEARCH_BAR_ENABLED, true)
    }

    fun setSearchBarEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SEARCH_BAR_ENABLED, enabled).apply()
    }

    // ==================== Hidden Apps ====================

    fun getHiddenApps(): Set<String> {
        return prefs.getStringSet(KEY_HIDDEN_APPS, emptySet()) ?: emptySet()
    }

    fun setHiddenApps(packageNames: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN_APPS, packageNames).apply()
    }

    fun hideApp(packageName: String) {
        val hidden = getHiddenApps().toMutableSet()
        hidden.add(packageName)
        setHiddenApps(hidden)
    }

    fun unhideApp(packageName: String) {
        val hidden = getHiddenApps().toMutableSet()
        hidden.remove(packageName)
        setHiddenApps(hidden)
    }

    fun isAppHidden(packageName: String): Boolean {
        return getHiddenApps().contains(packageName)
    }

    // ==================== Tag Button ====================

    fun getTagButtonPosition(): Int {
        return prefs.getInt(KEY_TAG_BUTTON_POSITION, TAG_POSITION_BOTTOM_RIGHT)
    }

    fun setTagButtonPosition(position: Int) {
        prefs.edit().putInt(KEY_TAG_BUTTON_POSITION, position.coerceIn(0, 4)).apply()
    }

    fun getTagButtonOffsetX(): Int {
        return prefs.getInt(KEY_TAG_BUTTON_OFFSET_X, 0)
    }

    fun setTagButtonOffsetX(offset: Int) {
        prefs.edit().putInt(KEY_TAG_BUTTON_OFFSET_X, offset.coerceIn(-500, 500)).apply()
    }

    fun getTagButtonOffsetY(): Int {
        return prefs.getInt(KEY_TAG_BUTTON_OFFSET_Y, 0)
    }

    fun setTagButtonOffsetY(offset: Int) {
        prefs.edit().putInt(KEY_TAG_BUTTON_OFFSET_Y, offset.coerceIn(-500, 500)).apply()
    }

    fun getTagButtonIcon(): Int {
        return prefs.getInt(KEY_TAG_BUTTON_ICON, TAG_ICON_TAG)
    }

    fun setTagButtonIcon(icon: Int) {
        prefs.edit().putInt(KEY_TAG_BUTTON_ICON, icon.coerceIn(0, 5)).apply()
    }

    fun getTagButtonSize(): Int {
        return prefs.getInt(KEY_TAG_BUTTON_SIZE, DEFAULT_TAG_BUTTON_SIZE)
    }

    fun setTagButtonSize(size: Int) {
        prefs.edit().putInt(KEY_TAG_BUTTON_SIZE, size.coerceIn(32, 80)).apply()
    }

    fun getTagButtonAlpha(): Int {
        return prefs.getInt(KEY_TAG_BUTTON_ALPHA, DEFAULT_TAG_BUTTON_ALPHA)
    }

    fun setTagButtonAlpha(alpha: Int) {
        prefs.edit().putInt(KEY_TAG_BUTTON_ALPHA, alpha.coerceIn(10, 100)).apply()
    }

    fun getTagButtonColor(): Int {
        return prefs.getInt(KEY_TAG_BUTTON_COLOR, android.graphics.Color.BLACK)
    }

    fun setTagButtonColor(color: Int) {
        prefs.edit().putInt(KEY_TAG_BUTTON_COLOR, color).apply()
    }

    // ==================== App Tags ====================

    fun getAllTags(): List<TagItem> {
        val json = prefs.getString(KEY_USER_TAGS, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                TagItem(
                    id = obj.getString("id"),
                    label = obj.getString("label"),
                    color = obj.getInt("color")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAllTags(tags: List<TagItem>) {
        val jsonArray = JSONArray()
        tags.forEach { tag ->
            val obj = JSONObject().apply {
                put("id", tag.id)
                put("label", tag.label)
                put("color", tag.color)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_USER_TAGS, jsonArray.toString()).apply()
    }

    fun addTag(tag: TagItem): Boolean {
        val tags = getAllTags().toMutableList()
        if (tags.any { it.id == tag.id }) return false
        if (tags.size >= MAX_TAGS) return false
        tags.add(tag)
        saveAllTags(tags)
        return true
    }

    fun updateTag(tag: TagItem): Boolean {
        val tags = getAllTags().toMutableList()
        val index = tags.indexOfFirst { it.id == tag.id }
        if (index == -1) return false
        tags[index] = tag
        saveAllTags(tags)
        return true
    }

    fun removeTag(tagId: String) {
        val tags = getAllTags().toMutableList()
        tags.removeAll { it.id == tagId }
        saveAllTags(tags)
        // Also remove this tag from all app associations
        val associations = getAppTagAssociations().toMutableMap()
        associations.forEach { (packageName, tagIds) ->
            if (tagIds.contains(tagId)) {
                associations[packageName] = tagIds.filter { it != tagId }
            }
        }
        saveAppTagAssociations(associations)
    }

    fun getTagById(tagId: String): TagItem? {
        return getAllTags().find { it.id == tagId }
    }

    // App-Tag Associations
    private fun getAppTagAssociations(): Map<String, List<String>> {
        val json = prefs.getString(KEY_APP_TAG_ASSOCIATIONS, "{}") ?: "{}"
        return try {
            val jsonObject = JSONObject(json)
            val result = mutableMapOf<String, List<String>>()
            jsonObject.keys().forEach { key ->
                val tagArray = jsonObject.getJSONArray(key)
                val tagIds = (0 until tagArray.length()).map { tagArray.getString(it) }
                result[key] = tagIds
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveAppTagAssociations(associations: Map<String, List<String>>) {
        val jsonObject = JSONObject()
        associations.forEach { (packageName, tagIds) ->
            if (tagIds.isNotEmpty()) {
                jsonObject.put(packageName, JSONArray(tagIds))
            }
        }
        prefs.edit().putString(KEY_APP_TAG_ASSOCIATIONS, jsonObject.toString()).apply()
    }

    fun getTagsForApp(packageName: String): List<String> {
        return getAppTagAssociations()[packageName] ?: emptyList()
    }

    fun setTagsForApp(packageName: String, tagIds: List<String>) {
        val associations = getAppTagAssociations().toMutableMap()
        if (tagIds.isEmpty()) {
            associations.remove(packageName)
        } else {
            associations[packageName] = tagIds
        }
        saveAppTagAssociations(associations)
        // Clean up orphan tags
        removeOrphanTags()
    }

    fun removeOrphanTags() {
        val allTags = getAllTags()
        val associations = getAppTagAssociations()
        val usedTagIds = associations.values.flatten().toSet()

        val orphanTags = allTags.filter { !usedTagIds.contains(it.id) }
        if (orphanTags.isNotEmpty()) {
            val remainingTags = allTags.filter { usedTagIds.contains(it.id) }
            saveAllTags(remainingTags)
        }
    }

    fun cleanupUninstalledApps(installedPackages: Set<String>) {
        val associations = getAppTagAssociations().toMutableMap()
        val uninstalledApps = associations.keys.filter { !installedPackages.contains(it) }

        if (uninstalledApps.isNotEmpty()) {
            uninstalledApps.forEach { associations.remove(it) }
            saveAppTagAssociations(associations)
            // Also clean up any orphan tags
            removeOrphanTags()
        }
    }

    fun getAppsWithTag(tagId: String): List<String> {
        return getAppTagAssociations()
            .filter { it.value.contains(tagId) }
            .keys
            .toList()
    }

    fun getAppsWithAnyTag(tagIds: List<String>): List<String> {
        if (tagIds.isEmpty()) return emptyList()
        return getAppTagAssociations()
            .filter { it.value.any { tagId -> tagIds.contains(tagId) } }
            .keys
            .toList()
    }

    // ==================== Pinned Tag Positions ====================

    // Returns map of tagId -> Pair(ringIndex, segmentIndex)
    fun getPinnedTagPositions(): Map<String, Pair<Int, Int>> {
        val json = prefs.getString(KEY_PINNED_TAG_POSITIONS, "{}") ?: "{}"
        return try {
            val jsonObject = JSONObject(json)
            val result = mutableMapOf<String, Pair<Int, Int>>()
            jsonObject.keys().forEach { tagId ->
                val posStr = jsonObject.getString(tagId)
                val parts = posStr.split("_")
                if (parts.size == 2) {
                    val ringIndex = parts[0].toIntOrNull()
                    val segmentIndex = parts[1].toIntOrNull()
                    if (ringIndex != null && segmentIndex != null) {
                        result[tagId] = Pair(ringIndex, segmentIndex)
                    }
                }
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun savePinnedTagPositions(positions: Map<String, Pair<Int, Int>>) {
        val jsonObject = JSONObject()
        positions.forEach { (tagId, pos) ->
            jsonObject.put(tagId, "${pos.first}_${pos.second}")
        }
        prefs.edit().putString(KEY_PINNED_TAG_POSITIONS, jsonObject.toString()).apply()
    }

    fun setPinnedTagPosition(tagId: String, ringIndex: Int, segmentIndex: Int) {
        val positions = getPinnedTagPositions().toMutableMap()
        // Remove any existing pin at this position (only one tag per position)
        positions.entries.removeAll { it.value == Pair(ringIndex, segmentIndex) }
        positions[tagId] = Pair(ringIndex, segmentIndex)
        savePinnedTagPositions(positions)
    }

    fun removePinnedTagPosition(tagId: String) {
        val positions = getPinnedTagPositions().toMutableMap()
        positions.remove(tagId)
        savePinnedTagPositions(positions)
    }

    fun togglePinnedTagPosition(tagId: String, ringIndex: Int, segmentIndex: Int): Boolean {
        val positions = getPinnedTagPositions().toMutableMap()
        val currentPos = positions[tagId]
        return if (currentPos == Pair(ringIndex, segmentIndex)) {
            // Already pinned here, unpin it
            positions.remove(tagId)
            savePinnedTagPositions(positions)
            false
        } else {
            // Pin it (remove any other tag at this position first)
            positions.entries.removeAll { it.value == Pair(ringIndex, segmentIndex) }
            positions[tagId] = Pair(ringIndex, segmentIndex)
            savePinnedTagPositions(positions)
            true
        }
    }

    fun getTagIdAtPinnedPosition(ringIndex: Int, segmentIndex: Int): String? {
        return getPinnedTagPositions().entries.find {
            it.value == Pair(ringIndex, segmentIndex)
        }?.key
    }

    fun isTagPinned(tagId: String): Boolean {
        return getPinnedTagPositions().containsKey(tagId)
    }

    fun cleanupInvalidPins(validTagIds: Set<String>) {
        val positions = getPinnedTagPositions().toMutableMap()
        val invalidKeys = positions.keys.filter { !validTagIds.contains(it) }
        if (invalidKeys.isNotEmpty()) {
            invalidKeys.forEach { positions.remove(it) }
            savePinnedTagPositions(positions)
        }
    }

    // ==================== Import/Export ====================

    fun exportTagsToJson(allApps: List<AppInfo>): String {
        val jsonObject = JSONObject()

        // Export tags
        val tagsArray = JSONArray()
        getAllTags().forEach { tag ->
            val tagObj = JSONObject().apply {
                put("id", tag.id)
                put("label", tag.label)
                put("color", tag.color)
            }
            tagsArray.put(tagObj)
        }
        jsonObject.put("tags", tagsArray)

        // Export app-tag associations
        val appsArray = JSONArray()
        val associations = getAppTagAssociations()
        allApps.forEach { app ->
            val appTags = associations[app.packageName] ?: emptyList()
            if (appTags.isNotEmpty()) {
                val appObj = JSONObject().apply {
                    put("name", app.label)
                    put("packageName", app.packageName)
                    put("tagIds", JSONArray(appTags))
                }
                appsArray.put(appObj)
            }
        }
        jsonObject.put("apps", appsArray)

        jsonObject.put("exportVersion", 1)
        jsonObject.put("exportDate", System.currentTimeMillis())

        return jsonObject.toString(2)
    }

    fun importTagsFromJson(jsonString: String, installedPackages: Set<String>): ImportResult {
        return try {
            val jsonObject = JSONObject(jsonString)

            var tagsImported = 0
            var appsUpdated = 0
            var appsSkipped = 0

            // Import tags
            val tagsArray = jsonObject.optJSONArray("tags") ?: JSONArray()
            val existingTags = getAllTags().toMutableList()
            val tagIdMapping = mutableMapOf<String, String>() // old id -> new id

            for (i in 0 until tagsArray.length()) {
                val tagObj = tagsArray.getJSONObject(i)
                val oldId = tagObj.getString("id")
                val label = tagObj.getString("label")
                val color = tagObj.getInt("color")

                // Check if tag with same label exists
                val existingTag = existingTags.find { it.label == label }
                if (existingTag != null) {
                    // Use existing tag's ID
                    tagIdMapping[oldId] = existingTag.id
                } else {
                    // Create new tag if under limit
                    if (existingTags.size < MAX_TAGS) {
                        val newId = "tag_${System.currentTimeMillis()}_$i"
                        val newTag = TagItem(newId, label, color)
                        existingTags.add(newTag)
                        tagIdMapping[oldId] = newId
                        tagsImported++
                    }
                }
            }
            saveAllTags(existingTags)

            // Import app-tag associations
            val appsArray = jsonObject.optJSONArray("apps") ?: JSONArray()
            for (i in 0 until appsArray.length()) {
                val appObj = appsArray.getJSONObject(i)
                val packageName = appObj.getString("packageName")
                val tagIdsArray = appObj.getJSONArray("tagIds")

                if (installedPackages.contains(packageName)) {
                    // Map old tag IDs to new/existing IDs
                    val newTagIds = mutableListOf<String>()
                    for (j in 0 until tagIdsArray.length()) {
                        val oldTagId = tagIdsArray.getString(j)
                        tagIdMapping[oldTagId]?.let { newTagIds.add(it) }
                    }

                    if (newTagIds.isNotEmpty()) {
                        // Merge with existing tags for this app
                        val existingAppTags = getTagsForApp(packageName).toMutableList()
                        newTagIds.forEach { tagId ->
                            if (!existingAppTags.contains(tagId)) {
                                existingAppTags.add(tagId)
                            }
                        }
                        setTagsForAppWithoutCleanup(packageName, existingAppTags)
                        appsUpdated++
                    }
                } else {
                    appsSkipped++
                }
            }

            ImportResult(true, tagsImported, appsUpdated, appsSkipped, null)
        } catch (e: Exception) {
            ImportResult(false, 0, 0, 0, e.message)
        }
    }

    private fun setTagsForAppWithoutCleanup(packageName: String, tagIds: List<String>) {
        val associations = getAppTagAssociations().toMutableMap()
        if (tagIds.isEmpty()) {
            associations.remove(packageName)
        } else {
            associations[packageName] = tagIds
        }
        saveAppTagAssociations(associations)
    }

    data class ImportResult(
        val success: Boolean,
        val tagsImported: Int,
        val appsUpdated: Int,
        val appsSkipped: Int,
        val error: String?
    )

    // ==================== Desktop Layout ====================

    /**
     * Get the saved desktop layout data.
     * Returns null if no layout has been saved yet.
     */
    fun getDesktopLayout(): com.example.taglauncher.persistence.DesktopLayoutData? {
        val json = prefs.getString(KEY_DESKTOP_LAYOUT, null) ?: return null
        return try {
            com.example.taglauncher.persistence.DesktopLayoutData.fromJson(JSONObject(json))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save the desktop layout data.
     */
    fun saveDesktopLayout(layout: com.example.taglauncher.persistence.DesktopLayoutData) {
        prefs.edit().putString(KEY_DESKTOP_LAYOUT, layout.toJson().toString()).apply()
    }

    /**
     * Check if a desktop layout exists.
     */
    fun hasDesktopLayout(): Boolean {
        return prefs.contains(KEY_DESKTOP_LAYOUT)
    }

    /**
     * Clear the saved desktop layout.
     */
    fun clearDesktopLayout() {
        prefs.edit().remove(KEY_DESKTOP_LAYOUT).apply()
    }

    // ==================== Component Templates ====================

    /**
     * Get all saved component templates.
     */
    fun getComponentTemplates(): List<com.example.taglauncher.persistence.ComponentTemplate> {
        val json = prefs.getString(KEY_COMPONENT_TEMPLATES, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).mapNotNull { i ->
                try {
                    com.example.taglauncher.persistence.ComponentTemplate.fromJson(jsonArray.getJSONObject(i))
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save all component templates.
     */
    fun saveComponentTemplates(templates: List<com.example.taglauncher.persistence.ComponentTemplate>) {
        val jsonArray = JSONArray()
        templates.forEach { template ->
            jsonArray.put(template.toJson())
        }
        prefs.edit().putString(KEY_COMPONENT_TEMPLATES, jsonArray.toString()).apply()
    }

    /**
     * Add a new component template.
     */
    fun addComponentTemplate(template: com.example.taglauncher.persistence.ComponentTemplate): Boolean {
        val templates = getComponentTemplates().toMutableList()
        // Check if template with same name exists
        if (templates.any { it.name == template.name }) {
            return false
        }
        templates.add(template)
        saveComponentTemplates(templates)
        return true
    }

    /**
     * Remove a component template by ID.
     */
    fun removeComponentTemplate(templateId: String) {
        val templates = getComponentTemplates().filter { it.templateId != templateId }
        saveComponentTemplates(templates)
    }

    /**
     * Get a template by ID.
     */
    fun getComponentTemplate(templateId: String): com.example.taglauncher.persistence.ComponentTemplate? {
        return getComponentTemplates().find { it.templateId == templateId }
    }

    // ==================== Reset ====================

    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
}
