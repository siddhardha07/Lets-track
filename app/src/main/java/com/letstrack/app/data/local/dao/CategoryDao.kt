package com.letstrack.app.data.local.dao

import androidx.room.*
import com.letstrack.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>
    
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): CategoryEntity?
    
    // IGNORE, not REPLACE: the unique index on name means a REPLACE would delete-then-reinsert
    // a row that already exists under a new id whenever two seeders (or a user and a seeder)
    // race to insert the same category name, silently orphaning any expense/budget that already
    // referenced the old id. IGNORE just no-ops and returns -1 in that case - see call sites for
    // how they fall back to the existing row's real id when that happens.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): CategoryEntity?
    
    @Update
    suspend fun updateCategory(category: CategoryEntity)
    
    @Delete
    suspend fun deleteCategory(category: CategoryEntity)
    
    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCategoriesCount(): Int
}
