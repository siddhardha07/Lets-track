package com.letstrack.app.data.local.dao

import androidx.room.*
import com.letstrack.app.data.local.entity.SubCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubCategoryDao {

    @Query("SELECT * FROM sub_categories ORDER BY displayOrder ASC")
    fun getAllSubCategories(): Flow<List<SubCategoryEntity>>

    @Query("SELECT * FROM sub_categories WHERE mainCategory = :mainCategory ORDER BY displayOrder ASC")
    suspend fun getSubCategoriesByMainCategory(mainCategory: String): List<SubCategoryEntity>

    @Query("SELECT * FROM sub_categories WHERE mainCategory = :mainCategory ORDER BY displayOrder ASC")
    fun observeSubCategoriesByMainCategory(mainCategory: String): Flow<List<SubCategoryEntity>>

    @Query("SELECT * FROM sub_categories WHERE isDefault = 1")
    suspend fun getDefaultSubCategories(): List<SubCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subCategory: SubCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subCategories: List<SubCategoryEntity>)

    @Update
    suspend fun update(subCategory: SubCategoryEntity)

    @Query("DELETE FROM sub_categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sub_categories WHERE mainCategory = :mainCategory AND isDefault = 0")
    suspend fun deleteCustomSubCategories(mainCategory: String)
}
