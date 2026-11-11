package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.util.AppTip
import com.example.util.TipPrefs

/**
 * A small hint shown the first time a screen opens. It is marked as seen as soon as it appears,
 * so it never comes back on a later visit, but it stays on screen (also after a rotation) until
 * the person closes it or taps "Learn more", which opens the guide at that topic.
 */
@Composable
fun OneTimeTip(tip: AppTip, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { TipPrefs(context) }
    var visible by rememberSaveable(tip.name) { mutableStateOf(!prefs.isSeen(tip)) }
    var showGuide by rememberSaveable(tip.name) { mutableStateOf(false) }

    LaunchedEffect(tip) { prefs.markSeen(tip) }

    if (visible) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .testTag("tip_${tip.name.lowercase()}"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
            )
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, top = 4.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                    Text(
                        text = tip.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    TextButton(
                        onClick = {
                            showGuide = true
                            visible = false
                        },
                        modifier = Modifier.testTag("tip_${tip.name.lowercase()}_learn_more")
                    ) {
                        Text("Learn more")
                    }
                }
                IconButton(
                    onClick = { visible = false },
                    modifier = Modifier.testTag("tip_${tip.name.lowercase()}_dismiss")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss tip",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showGuide) {
        FeatureGuideDialog(
            onDismiss = { showGuide = false },
            initialFeatureTitle = tip.guideTitle
        )
    }
}
