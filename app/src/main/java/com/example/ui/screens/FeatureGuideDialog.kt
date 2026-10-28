package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import com.example.util.AppFeature
import com.example.util.FeatureGuide

/** The icon names you can type in the Supabase "icon" column. Unknown names show a star. */
private fun featureIcon(name: String): ImageVector? = when (name.trim().lowercase()) {
    "assignment" -> Icons.Default.Assignment
    "schedule" -> Icons.Default.Schedule
    "school" -> Icons.Default.School
    "groups" -> Icons.Default.Groups
    "chat" -> Icons.Default.ChatBubble
    "notifications" -> Icons.Default.Notifications
    "calendar" -> Icons.Default.CalendarMonth
    "backup" -> Icons.Default.Backup
    "sun" -> Icons.Default.WbSunny
    "star", "" -> Icons.Default.Star
    else -> null
}

@Composable
fun FeatureGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var features by remember { mutableStateOf(FeatureGuide.cachedOrDefault(context)) }

    // Show the saved list at once, then swap in the fresh one when it arrives.
    LaunchedEffect(Unit) {
        FeatureGuide.refresh(context)?.let { features = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What UniTask can do") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(features) { feature: AppFeature ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        val vector = featureIcon(feature.icon)
                        if (vector != null) {
                            Icon(
                                vector,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        } else {
                            Text(feature.icon.trim(), fontSize = 20.sp, modifier = Modifier.size(24.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(feature.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                feature.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}