package com.letstrack.app.domain.model

data class Category(
    val id: Long = 0,
    val name: String,
    val icon: String = "💰",
    val color: String = "#4CAF50",
    val isDefault: Boolean = false,
    val iconUri: String? = null // For custom uploaded images
)
