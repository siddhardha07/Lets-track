package com.letstrack.app.data.repository

import com.letstrack.app.data.local.dao.CategoryDao
import com.letstrack.app.data.local.entity.CategoryEntity
import com.letstrack.app.domain.model.Category
import com.letstrack.app.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao
) : CategoryRepository {

    override fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun getCategoryById(id: Long): Category? {
        return categoryDao.getCategoryById(id)?.toDomainModel()
    }

    override suspend fun insertCategory(category: Category): Long {
        return categoryDao.insertCategory(category.toEntity())
    }

    override suspend fun updateCategory(category: Category) {
        categoryDao.updateCategory(category.toEntity())
    }

    override suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(category.toEntity())
    }

    // A custom uploaded image is stored by repurposing the `icon` column to hold a `file://`
    // path (see CategoryManagementViewModel.saveImageToInternalStorage) -- iconUri needs to be
    // derived from it here so every consumer of this repository (not just category management
    // screen, which used to do this mapping locally) renders the actual image instead of the
    // raw path string.
    private fun CategoryEntity.toDomainModel() = Category(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isDefault = isDefault,
        iconUri = icon.takeIf { it.startsWith("file://") }
    )

    private fun Category.toEntity() = CategoryEntity(
        id = id,
        name = name,
        icon = icon,
        color = color,
        isDefault = isDefault
    )
}
