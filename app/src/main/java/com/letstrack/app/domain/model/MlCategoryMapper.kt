package com.letstrack.app.domain.model

/**
 * Maps the AI/ML categorizer's fixed output labels (Bills, Entertainment, Food, Groceries,
 * Income, Medical, Shopping, Transport) onto whatever the app's actual category names are
 * (see [DefaultCategories]).
 *
 * This used to be copy-pasted separately into SmsProcessor.kt, JsonImporter.kt, and
 * PdfImportViewModel.kt. All three copies mapped "Medical" -> "Health & Fitness", a name that
 * doesn't exist in [DefaultCategories] (it's "Healthcare") - so every medical SMS/CSV/JSON/PDF
 * import silently missed its category lookup and fell through to a fallback category instead.
 * One shared mapper means a fix here reaches every import path instead of just whichever file
 * happened to get edited.
 */
object MlCategoryMapper {
    fun toAppCategoryName(mlCategory: String): String = when (mlCategory) {
        "Food" -> "Food"
        "Bills" -> "Bills & Utilities"
        "Medical" -> "Healthcare"
        "Groceries" -> "Groceries"
        "Income" -> "Other" // Income not in default categories, map to Other
        "Entertainment" -> "Entertainment"
        "Shopping" -> "Shopping"
        "Transport" -> "Transportation"
        else -> "Other" // Fallback to Other for unknown categories
    }
}
