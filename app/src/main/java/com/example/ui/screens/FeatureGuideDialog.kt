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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
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
    var expandedIndex by remember { mutableStateOf(-1) }

    // Show the saved list at once, then swap in the fresh one when it arrives.
    LaunchedEffect(Unit) {
        FeatureGuide.refresh(context)?.let { features = it }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Learn to use UniTask") },
        text = {
            Column {
                Text(
                    text = "Tap a feature to see how to use it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(features.size) { index ->
                        val feature: AppFeature = features[index]
                        val expanded = expandedIndex == index
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .clickable { expandedIndex = if (expanded) -1 else index }
                                .animateContentSize()
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (expanded && feature.howTo.isNotBlank()) {
                                    Text(
                                        "How to use",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 10.dp)
                                    )
                                    Text(
                                        feature.howTo,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}