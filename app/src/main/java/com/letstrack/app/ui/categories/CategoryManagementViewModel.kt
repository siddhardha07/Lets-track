package com.letstrack.app.ui.categories

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letstrack.app.data.local.dao.CategoryDao
import com.letstrack.app.data.local.entity.CategoryEntity
import com.letstrack.app.domain.model.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val categoryDao: CategoryDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // All categories (built-in + custom)
    val categories: StateFlow<List<Category>> = categoryDao.getAllCategories()
        .map { entities ->
            entities.map { entity ->
                Category(
                    id = entity.id,
                    name = entity.name,
                    icon = entity.icon,
                    color = entity.color,
                    isDefault = entity.isDefault,
                    iconUri = if (entity.icon.startsWith("file://")) entity.icon else null
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // User's enabled category IDs (stored in SharedPreferences)
    private val _enabledCategoryIds = MutableStateFlow<Set<Long>>(emptySet())
    val enabledCategoryIds: StateFlow<Set<Long>> = _enabledCategoryIds.asStateFlow()

    init {
        loadEnabledCategories()
    }

    private fun loadEnabledCategories() {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val enabledIds = prefs.getStringSet("enabled_categories", null)
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()
        _enabledCategoryIds.value = enabledIds
    }

    private fun saveEnabledCategories() {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("enabled_categories", _enabledCategoryIds.value.map { it.toString() }.toSet())
            .apply()
    }

    fun toggleCategory(categoryId: Long) {
        val current = _enabledCategoryIds.value.toMutableSet()
        if (current.contains(categoryId)) {
            current.remove(categoryId)
        } else {
            current.add(categoryId)
        }
        _enabledCategoryIds.value = current
        saveEnabledCategories()
    }

    fun createCustomCategory(
        name: String,
        icon: String,
        color: String,
        imageUri: String?
    ) {
        viewModelScope.launch {
            val finalIcon = if (imageUri != null) {
                // Save image to app's internal storage
                saveImageToInternalStorage(imageUri)
            } else {
                icon
            }

            val categoryEntity = CategoryEntity(
                name = name,
                icon = finalIcon,
                color = color,
                isDefault = false
            )

            // -1 means a category with this exact name already exists (unique index, IGNORE
            // on conflict) - fall back to its real id rather than enabling a bogus -1 entry.
            val insertedId = categoryDao.insertCategory(categoryEntity)
            val categoryId = if (insertedId != -1L) insertedId else categoryDao.getCategoryByName(name)?.id

            if (categoryId != null) {
                // Auto-enable newly created (or already-existing) category
                val current = _enabledCategoryIds.value.toMutableSet()
                current.add(categoryId)
                _enabledCategoryIds.value = current
                saveEnabledCategories()
            }
        }
    }

    fun updateCategory(
        categoryId: Long,
        name: String,
        icon: String,
        color: String,
        imageUri: String?
    ) {
        viewModelScope.launch {
            val existing = categoryDao.getCategoryById(categoryId) ?: return@launch
            val finalIcon = if (imageUri != null && !imageUri.startsWith("file://")) {
                saveImageToInternalStorage(imageUri)
            } else {
                imageUri ?: icon
            }

            categoryDao.updateCategory(
                existing.copy(
                    name = name,
                    icon = finalIcon,
                    color = color
                )
            )
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            val category = categoryDao.getCategoryById(categoryId) ?: return@launch
            
            // Don't delete default categories
            if (category.isDefault) {
                return@launch
            }
            
            // Delete the icon image file if it exists
            if (category.icon.startsWith("file://")) {
                try {
                    val file = File(category.icon.removePrefix("file://"))
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            // Remove from enabled categories
            val current = _enabledCategoryIds.value.toMutableSet()
            current.remove(categoryId)
            _enabledCategoryIds.value = current
            saveEnabledCategories()
            
            // Delete from database
            categoryDao.deleteCategory(category)
        }
    }

    private fun saveImageToInternalStorage(imageUriString: String): String {
        try {
            val uri = android.net.Uri.parse(imageUriString)
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileName = "category_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, "category_icons")

            if (!file.exists()) {
                file.mkdirs()
            }

            val imageFile = File(file, fileName)
            val outputStream = FileOutputStream(imageFile)

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            return "file://${imageFile.absolutePath}"
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    // Default categories are seeded elsewhere (DatabaseCallback.onCreate for fresh installs,
    // LetsTrackApp.seedDefaultCategoriesIfEmpty as a fallback for upgraded installs that never
    // got that callback) - this ViewModel used to have its own third copy of that list, with
    // names that had already drifted from DefaultCategories.ALL ("Transport" vs
    // "Transportation", "Health & Fitness" vs "Healthcare", an extra "Investments" entry, a
    // missing "Groceries"). Removed rather than reconciled: a screen-scoped ViewModel seeding
    // the database on init was the wrong place for this responsibility regardless of drift.
}
