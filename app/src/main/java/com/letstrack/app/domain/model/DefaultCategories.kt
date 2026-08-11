package com.letstrack.app.domain.model

/**
 * Single source of truth for the default category set.
 *
 * This used to be defined twice - once in [com.letstrack.app.data.local.DatabaseCallback]
 * (fires when Room creates a brand new DB file) and once in [com.letstrack.app.LetsTrackApp]
 * (a fallback for when the categories table is empty but the DB file already existed - e.g.
 * after wiping table contents without uninstalling). The two lists had drifted apart: different
 * counts, different names ("Health & Fitness" vs "Healthcare"), different colors. Depending on
 * which one happened to fire first you'd end up with a different, inconsistent category set.
 * Both call sites now read from here instead.
 *
 * Travel and Transportation were merged into a single "Transportation" category (per product
 * decision) since they overlapped too much to be useful as separate buckets.
 */
object DefaultCategories {
    val ALL: List<Category> = listOf(
        Category(name = "Food", icon = "🍔", color = "#FF5722"),
        Category(name = "Transportation", icon = "🚗", color = "#9C27B0"),
        Category(name = "Shopping", icon = "🛍️", color = "#E91E63"),
        Category(name = "Entertainment", icon = "🎬", color = "#673AB7"),
        Category(name = "Bills & Utilities", icon = "💡", color = "#3F51B5"),
        Category(name = "Healthcare", icon = "🏥", color = "#2196F3"),
        Category(name = "Education", icon = "📚", color = "#009688"),
        Category(name = "Groceries", icon = "🛒", color = "#4CAF50"),
        Category(name = "Personal Care", icon = "💆", color = "#8BC34A"),
        Category(name = "Gifts & Donations", icon = "🎁", color = "#FFC107"),
        Category(name = "Other", icon = "📝", color = "#795548")
    )
}
