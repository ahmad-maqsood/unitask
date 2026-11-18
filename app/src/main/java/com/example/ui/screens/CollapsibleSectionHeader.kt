package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.example.R

/** Names of the sections of a task list. They are the keys used to remember which are collapsed. */
internal object TaskSectionKey {
    const val OVERDUE = "overdue"
    const val TODAY = "today"
    const val TOMORROW = "tomorrow"
    const val LATER = "later"
    const val TURNED_OFF = "turned_off"
    const val DONE = "done"
}

/** Name of the preferences file that remembers which sections are collapsed. */
private const val SECTION_COLLAPSE_PREFS = "section_collapse_prefs"

/**
 * Remembers which sections of a list are collapsed. The choice survives rotating the phone,
 * switching tabs and closing the app. It starts from [rememberSectionCollapse]'s
 * `initiallyCollapsed` keys until the person changes a section for the first time.
 *
 * [onChange] is called with the new list of collapsed keys after every toggle, so it can be saved.
 */
@Stable
internal class SectionCollapse(
    private val collapsed: MutableState<List<String>>,
    private val onChange: (List<String>) -> Unit = {}
) {

    /** The collapsed keys. Use it as a `remember` key when something depends on what is shown. */
    val keys: List<String> get() = collapsed.value

    fun isCollapsed(key: String): Boolean = key in collapsed.value

    fun toggle(key: String) {
        val current = collapsed.value
        val updated = if (key in current) current - key else current + key
        collapsed.value = updated
        onChange(updated)
    }
}

/**
 * @param storageKey Names the list on disk, so each tab remembers its own sections
 * (for example "my_tasks" and "deadlines"). Keep it the same between app versions.
 * @param initiallyCollapsed Sections that start collapsed before the person has ever
 * collapsed or expanded one in this list.
 */
@Composable
internal fun rememberSectionCollapse(
    storageKey: String,
    vararg initiallyCollapsed: String
): SectionCollapse {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) {
        context.getSharedPreferences(SECTION_COLLAPSE_PREFS, Context.MODE_PRIVATE)
    }
    val state = rememberSaveable(storageKey) {
        // A saved set (even an empty one, meaning "all open") wins over the starting defaults.
        val saved = prefs.getStringSet(storageKey, null)
        mutableStateOf(saved?.toList() ?: initiallyCollapsed.toList())
    }
    return remember(state, prefs, storageKey) {
        SectionCollapse(state) { keys ->
            prefs.edit { putStringSet(storageKey, keys.toSet()) }
        }
    }
}

/** The tasks of a section that are on screen: none while the section is collapsed. */
internal fun <T> List<T>.shownUnless(collapsed: Boolean): List<T> = if (collapsed) emptyList() else this

/**
 * Title and count of a task section. Tapping anywhere on it collapses or expands the section,
 * and the arrow turns to show which way it will go.
 */
@Composable
internal fun CollapsibleSectionHeader(
    title: String,
    count: Int,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    isOverdue: Boolean = false
) {
    val actionLabel = stringResource(
        if (isCollapsed) R.string.section_expand_description else R.string.section_collapse_description,
        title
    )
    val arrowAngle by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else 180f,
        label = "sectionArrow"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onToggle)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = CircleShape,
                color = if (isOverdue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }
        }
        // The click is handled by the whole row, so the arrow is only a picture.
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(arrowAngle)
        )
    }
}
