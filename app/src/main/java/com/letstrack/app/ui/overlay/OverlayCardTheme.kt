package com.letstrack.app.ui.overlay

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.letstrack.app.ui.theme.AccentTheme
import com.letstrack.app.ui.theme.LetsTrackShapes
import com.letstrack.app.ui.theme.Typography
import com.letstrack.app.ui.theme.buildColorScheme

/**
 * The same real ColorScheme LetsTrackTheme builds - accent color, light/dark - minus the
 * Activity-only status-bar side effect (LetsTrackTheme casts LocalView's context to Activity,
 * which crashes in a bare Service). This is what let the system overlay's card use its own
 * hardcoded dark palette instead of ever reflecting the user's actual accent theme.
 * isSystemInDarkTheme() itself is safe here - it just reads a CompositionLocal, not the
 * Activity/Window.
 */
@Composable
fun OverlayCardTheme(accentTheme: AccentTheme, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = buildColorScheme(accentTheme, isSystemInDarkTheme()),
        typography = Typography,
        shapes = LetsTrackShapes,
        content = content
    )
}
