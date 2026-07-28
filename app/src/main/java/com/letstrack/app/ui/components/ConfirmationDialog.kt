package com.letstrack.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Reusable destructive-action confirmation, so delete flows aren't one accidental tap. */
@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TertiaryButton(text = confirmLabel, onClick = onConfirm)
        },
        dismissButton = {
            TertiaryButton(text = dismissLabel, onClick = onDismiss)
        }
    )
}
