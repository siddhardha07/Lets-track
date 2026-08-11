package com.letstrack.app.domain.model

import com.letstrack.app.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.first

/**
 * Resolves a category name (from any source: the live ML categorizer's fixed 8-label output,
 * or a known-merchant lookup's stored category) to a real category id.
 *
 * Tries an exact match against the app's actual category names first - this is what a
 * known-merchant hit needs, since SmartCategorizer.categorize() returns a merchant's stored
 * mainCategory as-is (see common_merchants.json, which already uses real names like
 * "Bills & Utilities", not ML labels). Only if that fails does it fall back to
 * [MlCategoryMapper], for the live model's fixed Bills/Entertainment/Food/Groceries/Income/
 * Medical/Shopping/Transport labels. Skipping the exact-match step (as every one of
 * SmsProcessor/JsonImporter/CsvImporter/PdfImportViewModel's own copies of this used to do)
 * meant a merchant tagged "Bills & Utilities" never matched any of MlCategoryMapper's literal
 * `when` branches and silently fell through to its `else -> "Other"`.
 */
suspend fun resolveCategoryId(categoryRepository: CategoryRepository, categoryName: String): Long? {
    val categories = categoryRepository.getAllCategories().first()
    categories.find { it.name.equals(categoryName, ignoreCase = true) }?.let { return it.id }
    val mapped = MlCategoryMapper.toAppCategoryName(categoryName)
    return categories.find { it.name.equals(mapped, ignoreCase = true) }?.id
}
