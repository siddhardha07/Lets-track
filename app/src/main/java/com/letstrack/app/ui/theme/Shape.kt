package com.letstrack.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object Radius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 20.dp
    val xl = 28.dp
}

val ShapeXs = RoundedCornerShape(Radius.xs)
val ShapeSm = RoundedCornerShape(Radius.sm)
val ShapeMd = RoundedCornerShape(Radius.md)
val ShapeLg = RoundedCornerShape(Radius.lg)
val ShapeXl = RoundedCornerShape(Radius.xl)
val ShapeFull = CircleShape

val LetsTrackShapes = Shapes(
    extraSmall = ShapeXs,
    small = ShapeSm,
    medium = ShapeMd,
    large = ShapeLg,
    extraLarge = ShapeXl
)
