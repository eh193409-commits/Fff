package com.example.sara.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sara.control.PendingSensitiveAction
import com.example.sara.control.PermissionType

@Composable
fun SensitiveActionConfirmationDialog(
    pending: PendingSensitiveAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = "Sensitive action warning",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(34.dp)
            )
        },
        title = {
            Text(
                text = pending.title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = pending.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.size(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Target: \"${pending.targetLabel}\"",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.height(38.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.testTag("confirm_sensitive_action_button")
            ) {
                Text("Confirm Tap")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_sensitive_action_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

fun permissionTitle(type: PermissionType): String = when (type) {
    PermissionType.RECORD_AUDIO -> "Microphone Access"
    PermissionType.CAMERA -> "Camera / Flashlight Access"
    PermissionType.WRITE_SETTINGS -> "Modify System Settings"
    PermissionType.ACCESSIBILITY_SERVICE -> "Accessibility Automation Service"
    PermissionType.NOTIFICATION_POLICY -> "Do Not Disturb Access"
}

fun permissionExplanation(type: PermissionType): String = when (type) {
    PermissionType.RECORD_AUDIO ->
        "Needed for Sara's push-to-talk voice recognition and hands-free \"Hey Sara\" wake-up detection."
    PermissionType.CAMERA ->
        "Required on some Android devices to toggle the hardware rear camera LED flashlight."
    PermissionType.WRITE_SETTINGS ->
        "Required by Android before Sara can read and adjust system screen brightness."
    PermissionType.ACCESSIBILITY_SERVICE ->
        "Optional manual opt-in that lets Sara click buttons, scroll, swipe, go back/home, and pull down Quick Settings only when you ask."
    PermissionType.NOTIFICATION_POLICY ->
        "Required by Android 7+ before apps can mute or unmute the phone ringtone via Do Not Disturb."
}
